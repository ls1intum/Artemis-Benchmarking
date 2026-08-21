package de.tum.cit.aet.service.artemis.passkey;

import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.service.artemis.util.AuthToken;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * One step stays manual. A freshly registered credential is not super-admin approved, and
 * {@code @EnforceAdmin} also requires {@code is-passkey-super-admin-approved}, so a human super-admin has to
 * approve the credential once before admin calls succeed. {@link #registerPasskey} says so in its log output.
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

        Map<String, Object> credentials = Map.of("username", artemisUser.getUsername(), "password", artemisUser.getPassword(), "rememberMe", false);
        var loginResponse = anonymousClient.post().uri("api/core/public/authenticate").bodyValue(credentials).retrieve().toBodilessEntity().block();
        if (loginResponse == null) {
            throw new IllegalStateException("Password login for " + artemisUser.getUsername() + " returned no response");
        }
        var setCookie = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookie == null || setCookie.isEmpty()) {
            throw new IllegalStateException("Password login for " + artemisUser.getUsername() + " returned no JWT cookie");
        }
        AuthToken token = AuthToken.fromResponseHeaderString(setCookie.getFirst());

        WebClient authenticatedClient = WebClient.builder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.COOKIE, token.jwtToken())
            .build();

        registerPasskey(authenticatedClient, artemisUser, label);
    }

    /**
     * Register a new passkey for the given user and store it on the user.
     * <p>
     * The web client must already be authenticated, normally by an ordinary password login. This does not save
     * the user; the caller decides when to persist.
     *
     * @param authenticatedClient a web client carrying a valid session for this user
     * @param artemisUser         the user to register a credential for, updated in place
     * @param label               the label shown in the Artemis passkey settings, so a human can find it later
     */
    public void registerPasskey(WebClient authenticatedClient, ArtemisUser artemisUser, String label) {
        Map<String, Object> options = postForMap(authenticatedClient, REGISTRATION_OPTIONS_PATH, Map.of());
        String rpId = relyingPartyId(options);
        String challenge = stringValue(options, "challenge");
        String userHandle = userHandle(options);

        PasskeyCredentialFactory.NewPasskeyCredential credential = credentialFactory.createCredential(rpId);
        byte[] clientDataJson = credentialFactory.clientDataJson("webauthn.create", challenge, rpId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", PasskeyCredentialFactory.base64Url(clientDataJson));
        response.put("attestationObject", credential.attestationObject());
        response.put("transports", java.util.List.of("internal"));

        // Spring Security's registration filter expects the credential wrapped together with its label, which is
        // also the shape the Artemis client sends.
        Map<String, Object> body = Map.of("publicKey", Map.of("credential", publicKeyCredential(credential.credentialId(), response), "label", label));

        authenticatedClient.post().uri(REGISTRATION_PATH).bodyValue(body).retrieve().toBodilessEntity().block();

        artemisUser.setPasskeyCredentialId(credential.credentialId());
        artemisUser.setPasskeyCoseKey(credential.encodedCoseKey());
        artemisUser.setPasskeyUserHandle(userHandle);
        artemisUser.setPasskeySignatureCount(0L);

        log.info(
            "Registered a passkey for {} on {}. A super admin must still approve it before administrator endpoints accept it.",
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
     * @return the JWT cookie Artemis issued, carrying the passkey authentication method
     */
    public AuthToken authenticateWithPasskey(WebClient webClient, ArtemisUser artemisUser) {
        if (!artemisUser.hasPasskey()) {
            throw new IllegalStateException("User " + artemisUser.getUsername() + " has no passkey credential to authenticate with");
        }

        Map<String, Object> options = postForMap(webClient, AUTHENTICATION_OPTIONS_PATH, Map.of());
        String rpId = stringValue(options, "rpId");
        String challenge = stringValue(options, "challenge");

        long signatureCount = artemisUser.getPasskeySignatureCount() + 1;
        byte[] clientDataJson = credentialFactory.clientDataJson("webauthn.get", challenge, rpId);
        PasskeyCredentialFactory.SignedAssertion assertion = credentialFactory.signAssertion(rpId, clientDataJson, signatureCount, artemisUser.getPasskeyCoseKey());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", PasskeyCredentialFactory.base64Url(clientDataJson));
        response.put("authenticatorData", assertion.authenticatorData());
        response.put("signature", assertion.signature());
        response.put("userHandle", artemisUser.getPasskeyUserHandle());

        var entity = webClient
            .post()
            .uri(LOGIN_PATH)
            .bodyValue(publicKeyCredential(artemisUser.getPasskeyCredentialId(), response))
            .retrieve()
            .toBodilessEntity()
            .block();

        if (entity == null) {
            throw new IllegalStateException("Passkey login for " + artemisUser.getUsername() + " returned no response");
        }
        var setCookie = entity.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookie == null || setCookie.isEmpty()) {
            throw new IllegalStateException("Passkey login for " + artemisUser.getUsername() + " returned no JWT cookie");
        }

        artemisUser.setPasskeySignatureCount(signatureCount);
        return AuthToken.fromResponseHeaderString(setCookie.getFirst());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(WebClient webClient, String path, Object body) {
        Map<String, Object> result = webClient
            .post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        if (result == null) {
            throw new IllegalStateException("No response from " + path);
        }
        return result;
    }

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
