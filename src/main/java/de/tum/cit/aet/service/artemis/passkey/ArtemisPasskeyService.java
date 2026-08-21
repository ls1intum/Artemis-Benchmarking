package de.tum.cit.aet.service.artemis.passkey;

import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.service.artemis.util.AuthToken;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Drives the two WebAuthn ceremonies against Artemis: registering a passkey once, and asserting it on every
 * login afterwards.
 * <p>
 * Artemis gates administrator features behind a passkey where
 * {@code artemis.user-management.passkey.require-for-administrator-features} is on. The gate is a claim check on
 * the JWT rather than a per-request ceremony, so a token obtained through a passkey login unlocks the admin API
 * for its whole lifetime. Registration deliberately only needs an ordinary session, which is what makes this
 * self-service: the simulation logs in with its password once, registers a credential it generated itself, and
 * uses the passkey from then on.
 * <p>
 * Approval is usually automatic. {@code @EnforceAdmin} also requires {@code is-passkey-super-admin-approved},
 * and Artemis grants that on registration when the registering account holds {@code ROLE_SUPER_ADMIN}
 * (see {@code ArtemisUserCredentialRepository}). An internal admin account normally does, so nothing manual is
 * needed; for a merely {@code ROLE_ADMIN} account a super-admin has to approve the credential once.
 */
@Service
public class ArtemisPasskeyService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisPasskeyService.class);

    /** Spring Security's default endpoint for fetching assertion options. */
    private static final String AUTHENTICATION_OPTIONS_PATH = "webauthn/authenticate/options";

    /** Spring Security's default endpoint for completing an assertion. Artemis answers it with a JWT cookie. */
    private static final String LOGIN_PATH = "login/webauthn";

    private static final String REGISTRATION_OPTIONS_PATH = "webauthn/register/options";

    private static final String REGISTRATION_PATH = "webauthn/register";

    /** Artemis names its authentication cookie {@code jwt}. */
    private static final String JWT_COOKIE_PREFIX = "jwt=";

    private final PasskeyCredentialFactory credentialFactory;

    public ArtemisPasskeyService(PasskeyCredentialFactory credentialFactory) {
        this.credentialFactory = credentialFactory;
    }

    /**
     * Register a passkey for a user, doing the bootstrap password login first.
     * <p>
     * This is the one-time setup step. It works because Artemis only requires an ordinary session to register a
     * credential, not an existing passkey, which is what stops this being a chicken-and-egg problem.
     *
     * @param artemisUrl  the base URL of the Artemis server, which must be the one users reach in a browser: the
     *                        relying party checks the origin, so a node address behind a load balancer is rejected
     * @param artemisUser the user to register a credential for, updated in place
     * @param label       the label shown in the Artemis passkey settings
     */
    public void registerPasskeyWithPasswordLogin(String artemisUrl, ArtemisUser artemisUser, String label) {
        WebClient anonymousClient = WebClient.builder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        Map<String, Object> credentials = Map.of(
            "username",
            artemisUser.getUsername(),
            "password",
            artemisUser.getPassword(),
            "rememberMe",
            false
        );
        var loginResponse = anonymousClient
            .post()
            .uri("api/core/public/authenticate")
            .bodyValue(credentials)
            .retrieve()
            .toBodilessEntity()
            .block();
        if (loginResponse == null) {
            throw new IllegalStateException("Password login for " + artemisUser.getUsername() + " returned no response");
        }
        String loginCookie = jwtCookie(loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        if (loginCookie == null) {
            throw new IllegalStateException("Password login for " + artemisUser.getUsername() + " returned no JWT cookie");
        }
        AuthToken token = AuthToken.fromResponseHeaderString(loginCookie);

        // Deliberately no default Cookie header. Spring merges default headers with putIfAbsent, so the moment a
        // request sets its own Cookie (to return a ceremony cookie) the default would be dropped instead of
        // combined, and the request would arrive unauthenticated. The cookie is therefore threaded through
        // explicitly and merged by hand.
        WebClient authenticatedClient = WebClient.builder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        registerPasskey(authenticatedClient, artemisUser, label, PasskeyCredentialFactory.originOf(artemisUrl), token.jwtToken());
    }

    /**
     * Register a new passkey for the given user and store it on the user.
     * <p>
     * The web client must already be authenticated, normally by an ordinary password login. This does not save
     * the user; the caller decides when to persist.
     *
     * @param authenticatedClient a web client for this server; it carries no session of its own
     * @param artemisUser         the user to register a credential for, updated in place
     * @param label               the label shown in the Artemis passkey settings, so a human can find it later
     * @param origin              the origin a browser would report for this server
     * @param authCookie          the session cookie authenticating the caller, merged into every request of the ceremony
     */
    public void registerPasskey(WebClient authenticatedClient, ArtemisUser artemisUser, String label, String origin, String authCookie) {
        OptionsResponse optionsResponse = postForOptions(authenticatedClient, REGISTRATION_OPTIONS_PATH, Map.of(), authCookie);
        Map<String, Object> options = optionsResponse.body();
        String rpId = relyingPartyId(options);
        String challenge = stringValue(options, "challenge");
        String userHandle = userHandle(options);

        PasskeyCredentialFactory.NewPasskeyCredential credential = credentialFactory.createCredential(rpId);
        byte[] clientDataJson = credentialFactory.clientDataJson("webauthn.create", challenge, origin);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", PasskeyCredentialFactory.base64Url(clientDataJson));
        response.put("attestationObject", credential.attestationObject());
        response.put("transports", java.util.List.of("internal"));

        // Spring Security's registration filter expects the credential wrapped together with its label, which is
        // also the shape the Artemis client sends.
        Map<String, Object> body = Map.of(
            "publicKey",
            Map.of("credential", publicKeyCredential(credential.credentialId(), response), "label", label)
        );

        // Send the session cookie and the ceremony cookies together. Artemis stores the pending registration
        // challenge against an HTTP session and returns a JSESSIONID from the options call, so the second request
        // needs both: the JWT to be authenticated at all, and the JSESSIONID to find the challenge.
        authenticatedClient
            .post()
            .uri(REGISTRATION_PATH)
            .header(HttpHeaders.COOKIE, mergeCookies(authCookie, optionsResponse.cookies()))
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .block();

        artemisUser.setPasskeyCredentialId(credential.credentialId());
        artemisUser.setPasskeyCoseKey(credential.encodedCoseKey());
        artemisUser.setPasskeyUserHandle(userHandle);
        artemisUser.setPasskeySignatureCount(0L);

        // Artemis approves the credential automatically when the account holds ROLE_SUPER_ADMIN, which an internal
        // admin normally does. Say so either way rather than making the reader guess which case they are in.
        log.info(
            "Registered a passkey for {} on {}. Administrator endpoints accept it once the credential is super-admin " +
                "approved, which Artemis grants on registration for a ROLE_SUPER_ADMIN account and otherwise " +
                "requires a super admin to grant.",
            artemisUser.getUsername(),
            rpId
        );
    }

    /**
     * Log in with the user's registered passkey.
     * <p>
     * Increments the stored signature counter, because a relying party rejects an assertion whose counter fails
     * to advance: that is how a cloned authenticator is detected. The caller is responsible for persisting the
     * user so the new counter survives the next run.
     *
     * @param webClient   an unauthenticated web client pointed at the Artemis server
     * @param artemisUser the user to log in, whose signature counter is advanced
     * @param artemisUrl  the base URL of that server, from which the origin is derived
     * @return the JWT cookie Artemis issued, carrying the passkey authentication method
     */
    public AuthToken authenticateWithPasskey(WebClient webClient, ArtemisUser artemisUser, String artemisUrl) {
        if (!artemisUser.hasPasskey()) {
            throw new IllegalStateException("User " + artemisUser.getUsername() + " has no passkey credential to authenticate with");
        }

        OptionsResponse optionsResponse = postForOptions(webClient, AUTHENTICATION_OPTIONS_PATH, Map.of(), null);
        Map<String, Object> options = optionsResponse.body();
        String rpId = stringValue(options, "rpId");
        String challenge = stringValue(options, "challenge");

        long signatureCount = artemisUser.getPasskeySignatureCount() + 1;
        byte[] clientDataJson = credentialFactory.clientDataJson("webauthn.get", challenge, PasskeyCredentialFactory.originOf(artemisUrl));
        PasskeyCredentialFactory.SignedAssertion assertion = credentialFactory.signAssertion(
            rpId,
            clientDataJson,
            signatureCount,
            artemisUser.getPasskeyCoseKey()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", PasskeyCredentialFactory.base64Url(clientDataJson));
        response.put("authenticatorData", assertion.authenticatorData());
        response.put("signature", assertion.signature());
        response.put("userHandle", artemisUser.getPasskeyUserHandle());

        var entity = webClient
            .post()
            .uri(LOGIN_PATH)
            .header(HttpHeaders.COOKIE, mergeCookies(null, optionsResponse.cookies()))
            .bodyValue(publicKeyCredential(artemisUser.getPasskeyCredentialId(), response))
            .retrieve()
            .toBodilessEntity()
            .block();

        if (entity == null) {
            throw new IllegalStateException("Passkey login for " + artemisUser.getUsername() + " returned no response");
        }
        String jwtCookie = jwtCookie(entity.getHeaders().get(HttpHeaders.SET_COOKIE));
        if (jwtCookie == null) {
            throw new IllegalStateException("Passkey login for " + artemisUser.getUsername() + " returned no JWT cookie");
        }

        artemisUser.setPasskeySignatureCount(signatureCount);
        return AuthToken.fromResponseHeaderString(jwtCookie);
    }

    /**
     * Combine the cookies a request needs into one header value.
     * <p>
     * Set explicitly rather than left to a default header: Spring merges default headers with
     * {@code putIfAbsent}, so a request that sets its own {@code Cookie} loses the default instead of combining
     * with it. That silently strips the JWT from exactly the requests that also carry a ceremony cookie, and the
     * server answers 401 with nothing to indicate a cookie went missing.
     *
     * @param authCookie      the session cookie, or null when the request is unauthenticated
     * @param ceremonyCookies cookies returned by the matching options call, or null
     * @return the combined header value, empty when there is nothing to send
     */
    private String mergeCookies(@Nullable String authCookie, @Nullable String ceremonyCookies) {
        return Stream.of(authCookie, ceremonyCookies)
            .filter(cookie -> cookie != null && !cookie.isBlank())
            .collect(Collectors.joining("; "));
    }

    /**
     * Assemble the {@code PublicKeyCredential} JSON both Artemis endpoints expect. The credential id appears as
     * both {@code id} and {@code rawId}, mirroring what a browser sends.
     */
    private Map<String, Object> publicKeyCredential(String credentialId, Map<String, Object> response) {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("type", "public-key");
        credential.put("id", credentialId);
        credential.put("rawId", credentialId);
        credential.put("authenticatorAttachment", "platform");
        credential.put("clientExtensionResults", Map.of());
        credential.put("response", response);
        return credential;
    }

    /**
     * POST for a JSON object, keeping the cookies the server set alongside the body.
     * <p>
     * The cookies matter: Artemis hands out the challenge through one, so a caller that only reads the body
     * cannot complete the ceremony.
     */
    @SuppressWarnings("unchecked")
    private OptionsResponse postForOptions(WebClient webClient, String path, Object body, @Nullable String authCookie) {
        var entity = webClient
            .post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.COOKIE, mergeCookies(authCookie, null))
            .bodyValue(body)
            .retrieve()
            .toEntity(Map.class)
            .block();
        if (entity == null || entity.getBody() == null) {
            throw new IllegalStateException("No response from " + path);
        }
        List<String> setCookies = entity.getHeaders().get(HttpHeaders.SET_COOKIE);
        return new OptionsResponse((Map<String, Object>) entity.getBody(), cookieHeader(setCookies));
    }

    /**
     * Pick the JWT out of the response cookies.
     * <p>
     * A passkey login sets more than one: alongside the token, Artemis expires the challenge cookie now that the
     * ceremony is over. Taking the first header would therefore hand back the cleared challenge cookie, which
     * parses into a token that authenticates nobody, and the failure surfaces later as a puzzling access-check
     * failure rather than as a login error.
     *
     * @param setCookies the Set-Cookie headers, possibly null
     * @return the {@code jwt=...} cookie, or null if the response carried none
     */
    @Nullable
    private String jwtCookie(@Nullable List<String> setCookies) {
        if (setCookies == null) {
            return null;
        }
        return setCookies
            .stream()
            .filter(cookie -> cookie.startsWith(JWT_COOKIE_PREFIX))
            .findFirst()
            .orElse(null);
    }

    /**
     * Reduce {@code Set-Cookie} response headers to a {@code Cookie} request header.
     * <p>
     * Only the name=value pair is echoed back; attributes such as Path, Max-Age and HttpOnly are instructions to
     * a browser and are not part of what a client sends.
     *
     * @param setCookies the Set-Cookie headers, possibly null
     * @return a Cookie header value, or null when the server set none
     */
    @Nullable
    private String cookieHeader(@Nullable List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return null;
        }
        return setCookies
            .stream()
            .map(cookie -> cookie.split(";", 2)[0])
            .filter(pair -> pair.contains("="))
            .collect(Collectors.joining("; "));
    }

    /**
     * A WebAuthn options response: the challenge and its parameters, plus the cookies that identify it.
     *
     * @param body    the parsed JSON body
     * @param cookies a Cookie header to send with the request that completes the ceremony, or null
     */
    private record OptionsResponse(Map<String, Object> body, @Nullable String cookies) {}

    /**
     * Registration options nest the relying party id under {@code rp}, assertion options carry it flat as
     * {@code rpId}.
     */
    @SuppressWarnings("unchecked")
    private String relyingPartyId(Map<String, Object> registrationOptions) {
        Object rp = registrationOptions.get("rp");
        if (rp instanceof Map<?, ?> rpMap && rpMap.get("id") instanceof String id) {
            return id;
        }
        throw new IllegalStateException("Registration options contained no relying party id");
    }

    @SuppressWarnings("unchecked")
    private String userHandle(Map<String, Object> registrationOptions) {
        Object user = registrationOptions.get("user");
        if (user instanceof Map<?, ?> userMap && userMap.get("id") instanceof String id) {
            return id;
        }
        throw new IllegalStateException("Registration options contained no user handle");
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map.get(key) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new IllegalStateException("Expected a '" + key + "' in the WebAuthn options response");
    }
}
