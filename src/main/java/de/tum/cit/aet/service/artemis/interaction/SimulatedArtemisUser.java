package de.tum.cit.aet.service.artemis.interaction;

import static de.tum.cit.aet.domain.RequestType.AUTHENTICATION;
import static java.time.ZonedDateTime.now;

import de.tum.cit.aet.artemisModel.ArtemisAuthMechanism;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.interaction.browser.BrowserSimulationSettings;
import de.tum.cit.aet.service.artemis.passkey.ArtemisPasskeyService;
import de.tum.cit.aet.service.artemis.util.AuthToken;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpSslContextSpec;

/**
 * A simulated Artemis user that can be used to interact with the Artemis server.
 * This class is abstract
 * and should be extended by classes that represent specific types of Artemis users (e.g. students, instructors, admins).
 */
public abstract class SimulatedArtemisUser {

    protected Logger log;

    protected final String username;
    protected final String password;
    protected String privateKeyString;
    protected String publicKeyString;
    protected final String artemisUrl;
    protected WebClient webClient;
    protected AuthToken authToken;
    protected boolean authenticated = false;
    private ArtemisUser artemisUser;
    private ArtemisUserService artemisUserService;

    /**
     * Set only for users that should authenticate with a passkey. Artemis gates administrator features behind a
     * passkey on some deployments, and a password login cannot pass that gate, so an admin or instructor driving
     * such a server needs one.
     */
    private ArtemisPasskeyService passkeyService;
    private final Supplier<WebClient.Builder> webClientBuilderSupplier;

    /**
     * Create a new SimulatedArtemisUser.
     * The artemisUser and artemisUserService parameters are used to cache the JWT token.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param artemisUser the ArtemisUser entity to cache the JWT token in
     * @param artemisUserService the ArtemisUserService to use to update the ArtemisUser entity
     */
    public SimulatedArtemisUser(String artemisUrl, ArtemisUser artemisUser, ArtemisUserService artemisUserService) {
        this(artemisUrl, artemisUser, artemisUserService, null);
    }

    /**
     * Create a new SimulatedArtemisUser.
     * No JWT token caching will be performed for this user.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param username the username to use for logging in
     * @param password the password to use for logging in
     */
    public SimulatedArtemisUser(String artemisUrl, String username, String password) {
        this(artemisUrl, username, password, null);
    }

    protected SimulatedArtemisUser(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        Supplier<WebClient.Builder> webClientBuilderSupplier
    ) {
        this.username = artemisUser.getUsername();
        this.password = artemisUser.getPassword();
        this.artemisUrl = artemisUrl;
        this.artemisUser = artemisUser;
        this.artemisUserService = artemisUserService;
        this.privateKeyString = artemisUser.getPrivateKey();
        this.publicKeyString = artemisUser.getPublicKey();
        this.webClientBuilderSupplier = webClientBuilderSupplier;
    }

    protected SimulatedArtemisUser(
        String artemisUrl,
        String username,
        String password,
        Supplier<WebClient.Builder> webClientBuilderSupplier
    ) {
        this.username = username;
        this.password = password;
        this.artemisUrl = artemisUrl;
        this.webClientBuilderSupplier = webClientBuilderSupplier;
    }

    /**
     * Login to Artemis and return the request stats for the login request.
     * If an artemisUser is specified and a valid token is already cached, it will be used instead of logging in again.
     *
     * @return the request stats for the login request (empty if a cached token was used)
     */
    public List<RequestStat> login() {
        if (artemisUser != null && artemisUser.getJwtToken() != null && artemisUser.getTokenExpirationDate().isAfter(now())) {
            log.debug("Using cached token for user {}", username);
            authToken = new AuthToken(artemisUser.getJwtToken(), null, null, artemisUser.getTokenExpirationDate());
            webClient = createWebClientBuilder()
                .baseUrl(artemisUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Cookie", authToken.jwtToken())
                .build();
            checkAccess();
            if (authenticated) {
                return List.of();
            }
            log.warn(
                "Cached token invalid or insufficient for user {} (expires at {}). Re-authenticating.",
                username,
                artemisUser.getTokenExpirationDate()
            );
            // If the cached token is invalid, we will try to log in again...
        }

        if (shouldAuthenticateWithPasskey()) {
            return loginWithPasskey();
        }

        log.info("Logging in as {{}}", username);
        List<RequestStat> requestStats = new ArrayList<>();
        WebClient webClient = createWebClientBuilder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        long start = System.nanoTime();
        var payload = Map.of("username", username, "password", password, "rememberMe", true);
        var response = webClient.post().uri("api/core/public/authenticate").bodyValue(payload).retrieve().toBodilessEntity().block();

        requestStats.add(new RequestStat(now(), System.nanoTime() - start, AUTHENTICATION));

        if (response == null) {
            throw new RuntimeException("Login failed - No response received");
        }
        var header = response.getHeaders().get("Set-Cookie");
        if (header == null) {
            throw new RuntimeException("Login failed - No cookie received");
        }
        var cookieHeader = header.getFirst();
        authToken = AuthToken.fromResponseHeaderString(cookieHeader);
        if (artemisUser != null) {
            artemisUser.setJwtToken(authToken.jwtToken());
            artemisUser.setTokenExpirationDate(authToken.expireDate());
            artemisUser = artemisUserService.updateArtemisUser(artemisUser.getId(), artemisUser);
        }
        this.webClient = createWebClientBuilder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Cookie", authToken.jwtToken())
            .build();
        checkAccess();
        if (!authenticated) {
            log.warn("User {} failed access check after login.", username);
        }
        log.debug("Logged in as {}", username);
        return requestStats;
    }

    /**
     * @return true if this user has a registered passkey and a service to use it with
     */
    private boolean shouldAuthenticateWithPasskey() {
        return passkeyService != null && artemisUser != null && artemisUser.hasPasskey();
    }

    /**
     * Log in with the user's passkey rather than its password.
     * <p>
     * Only the resulting token differs, but it differs in the way that matters: it records the authentication
     * method as a passkey, which is what Artemis checks before allowing administrator features. The signature
     * counter advances on every assertion, so the user is persisted here even though the password path already
     * does that: losing the counter would make the next assertion look like a cloned authenticator.
     *
     * @return the request stats for the passkey login
     */
    private List<RequestStat> loginWithPasskey() {
        log.info("Logging in as {} with a passkey", username);
        List<RequestStat> requestStats = new ArrayList<>();
        WebClient anonymousClient = createWebClientBuilder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        long start = System.nanoTime();
        authToken = passkeyService.authenticateWithPasskey(anonymousClient, artemisUser, artemisUrl);
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, AUTHENTICATION));

        artemisUser.setJwtToken(authToken.jwtToken());
        artemisUser.setTokenExpirationDate(authToken.expireDate());
        artemisUser = artemisUserService.updateArtemisUser(artemisUser.getId(), artemisUser);

        this.webClient = createWebClientBuilder()
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Cookie", authToken.jwtToken())
            .build();
        checkAccess();
        if (!authenticated) {
            log.warn("User {} failed access check after passkey login.", username);
        }
        return requestStats;
    }

    /**
     * Enable passkey authentication for this user.
     *
     * @param passkeyService the service performing the WebAuthn assertion
     */
    public void setPasskeyService(ArtemisPasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    protected abstract void checkAccess();

    protected WebClient.Builder createWebClientBuilder() {
        WebClient.Builder builder =
            webClientBuilderSupplier != null
                ? webClientBuilderSupplier.get()
                : WebClient.builder().clientConnector(new ReactorClientHttpConnector(createHttpClient()));
        return builder.filter(logErrorResponses());
    }

    private ExchangeFilterFunction logErrorResponses() {
        return (request, next) ->
            next.exchange(request).flatMap(response -> {
                if (!response.statusCode().isError()) {
                    return reactor.core.publisher.Mono.just(response);
                }
                return response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        Logger logger = log != null ? log : org.slf4j.LoggerFactory.getLogger(SimulatedArtemisUser.class);
                        logger.error(
                            "Artemis request failed: {} {} -> {}. Response body: {}",
                            request.method(),
                            request.url(),
                            response.statusCode(),
                            body
                        );
                        return reactor.core.publisher.Mono.error(
                            new IllegalStateException(
                                "Artemis request failed: " +
                                    request.method() +
                                    " " +
                                    request.url() +
                                    " -> " +
                                    response.statusCode() +
                                    ": " +
                                    body
                            )
                        );
                    });
            });
    }

    /**
     * Get the JWT token for this user.
     * @return the JWT token for this user
     */
    public AuthToken getAuthToken() {
        return authToken;
    }

    /**
     * Create a new student.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param artemisUser the ArtemisUser entity to cache the JWT token in and to access the user's credentials
     * @param artemisUserService the ArtemisUserService to use to update the ArtemisUser entity
     * @param numberOfCommitsAndPushesFrom the minimum number of commits and pushes to simulate
     * @param numberOfCommitsAndPushesTo the maximum number of commits and pushes to simulate
     * @param authMechanism the authentication mechanism to use for the student
     * @return a new SimulatedArtemisStudent
     */
    public static SimulatedArtemisStudent createArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo,
        ArtemisAuthMechanism authMechanism
    ) {
        return createArtemisStudent(
            artemisUrl,
            artemisUser,
            artemisUserService,
            numberOfCommitsAndPushesFrom,
            numberOfCommitsAndPushesTo,
            authMechanism,
            BrowserSimulationSettings.defaults()
        );
    }

    /**
     * Create a new student from a given ArtemisUser, stating how closely it should imitate a browser.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param artemisUser the ArtemisUser entity to cache the JWT token in and to access the user's credentials
     * @param artemisUserService the ArtemisUserService to use to update the ArtemisUser entity
     * @param numberOfCommitsAndPushesFrom the lower bound of the number of commits and pushes to perform
     * @param numberOfCommitsAndPushesTo the upper bound of the number of commits and pushes to perform
     * @param authMechanism the authentication mechanism the student uses for git
     * @param browserSettings how much of a real browser's behaviour to reproduce
     * @return the created student
     */
    public static SimulatedArtemisStudent createArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo,
        ArtemisAuthMechanism authMechanism,
        BrowserSimulationSettings browserSettings
    ) {
        return new SimulatedArtemisStudent(
            artemisUrl,
            artemisUser,
            artemisUserService,
            numberOfCommitsAndPushesFrom,
            numberOfCommitsAndPushesTo,
            authMechanism,
            browserSettings
        );
    }

    /**
     * Create a new admin from a given ArtemisUser.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param artemisUser the ArtemisUser entity to cache the JWT token in and to access the user's credentials
     * @param artemisUserService the ArtemisUserService to use to update the ArtemisUser entity
     * @return a new SimulatedArtemisInstructor
     */
    public static SimulatedArtemisAdmin createArtemisAdminFromUser(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService
    ) {
        return new SimulatedArtemisAdmin(artemisUrl, artemisUser, artemisUserService);
    }

    /**
     * Create a new admin from a given username and password without persisting credentials.
     *
     * @param artemisUrl the URL of the Artemis server
     * @param username the username to use for logging in
     * @param password the password to use for logging in
     * @return a new SimulatedArtemisAdmin
     */
    public static SimulatedArtemisAdmin createArtemisAdminFromCredentials(String artemisUrl, String username, String password) {
        return new SimulatedArtemisAdmin(artemisUrl, username, password);
    }

    private static HttpClient createHttpClient() {
        return HttpClient.create()
            .doOnConnected(conn ->
                conn.addHandlerFirst(new ReadTimeoutHandler(20, TimeUnit.MINUTES)).addHandlerFirst(new WriteTimeoutHandler(30))
            )
            .responseTimeout(Duration.ofMinutes(20))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30 * 1000)
            .secure(spec -> {
                try {
                    spec.sslContext(TcpSslContextSpec.forClient().sslContext())
                        .handshakeTimeout(Duration.ofSeconds(30))
                        .closeNotifyFlushTimeout(Duration.ofSeconds(30))
                        .closeNotifyReadTimeout(Duration.ofSeconds(30));
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
