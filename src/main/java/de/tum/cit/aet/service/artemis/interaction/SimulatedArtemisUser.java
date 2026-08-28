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
import io.netty.handler.ssl.SslContext;
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
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpSslContextSpec;

/**
 * A simulated Artemis user that can be used to interact with the Artemis server.
 * This class is abstract
 * and should be extended by classes that represent specific types of Artemis users (e.g. students, instructors, admins).
 */
public abstract class SimulatedArtemisUser {

    /**
     * The HTTP protocol simulated users speak, set from {@code benchmarking.simulation.http-protocol}.
     * <p>
     * {@code auto} negotiates HTTP/2 with a HTTP/1.1 fallback over TLS and stays on HTTP/1.1 in plaintext, which is
     * what a browser does. {@code h1} forces the old behaviour, {@code h3} forces HTTP/3.
     * <p>
     * Static because it describes the transport of a whole run rather than of one user, and because the users are
     * constructed directly rather than by Spring. {@link de.tum.cit.aet.config.SimulationHttpProtocolConfiguration}
     * sets it once at startup.
     */
    private static volatile String httpProtocol = "auto";

    /** Built once and shared; see {@link #sharedSslContext()}. */
    private static volatile SslContext sslContext;

    /**
     * Sets the protocol every simulated user's client will use.
     *
     * @param protocol one of {@code auto}, {@code h1} or {@code h3}; anything else behaves as {@code auto}
     */
    public static void setHttpProtocol(String protocol) {
        httpProtocol = protocol == null || protocol.isBlank() ? "auto" : protocol;
    }

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

        // Artemis asks for the identifier first and only then for a secret, so the client checks which methods the
        // account may use before it posts a password. A simulation that jumps straight to the password misses one
        // request per login, and on an exam morning every student logs in within the same few minutes.
        long optionsStart = System.nanoTime();
        try {
            webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("api/core/public/login-options").queryParam("usernameOrEmail", username).build())
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.debug("Could not fetch the login options for {}: {}", username, e.getMessage());
        }
        requestStats.add(new RequestStat(now(), System.nanoTime() - optionsStart, AUTHENTICATION));

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
                : WebClient.builder().clientConnector(new ReactorClientHttpConnector(createHttpClient(artemisUrl, true)));
        return builder.filter(logErrorResponses());
    }

    /**
     * A client for the files a student downloads and never looks at.
     * <p>
     * Identical to the ordinary one except that it does not inflate what it receives. A browser has to decompress the
     * bundle because it runs it; this tool discards it, and inflating megabytes on the way to
     * {@code toBodilessEntity()} is work with no measurement behind it. The asymmetry matters at cohort scale: two
     * thousand real browsers decompress on two thousand machines, and the load generator would be doing all of it on
     * one, turning a network-bound phase into a CPU-bound one and reporting the tool's own limits as the server's.
     * <p>
     * The request still looks exactly like a browser's — {@link BrowserHeaders#forDiscardedAsset} sends the same
     * {@code Accept-Encoding}, so the server compresses and the wire carries the same bytes it would for a real
     * client. Only the inflating is skipped.
     * <p>
     * A test that supplies its own builder gets that builder, unchanged.
     *
     * @return a client for downloads whose body is thrown away
     */
    protected WebClient.Builder createDiscardingWebClientBuilder() {
        WebClient.Builder builder =
            webClientBuilderSupplier != null
                ? webClientBuilderSupplier.get()
                : WebClient.builder().clientConnector(new ReactorClientHttpConnector(createHttpClient(artemisUrl, false)));
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

    /**
     * The client every simulated user talks through.
     * <p>
     * The protocol matters for what a run measures. Artemis is served over TLS behind nginx, which offers h2, so a
     * browser multiplexes a page's requests over one connection; a client left on HTTP/1.1 opens a separate connection
     * per parallel request instead and measures a connection pattern no user produces. HTTP/2 is therefore negotiated
     * where the server offers it, with HTTP/1.1 as the ALPN fallback, so a plaintext or h2-less server still works.
     * <p>
     * HTTP/3 is deliberately opt-in. It has no negotiation: reactor-netty either speaks QUIC to the server or fails,
     * so pointing it at a server without h3 breaks every request rather than falling back. staging1 advertises
     * {@code alt-svc: h3=":443"} and works; a plaintext server such as a local Artemis cannot work at all.
     *
     * @param artemisUrl the server this client will talk to, which decides whether TLS is in play
     * @return the configured client
     */
    private static HttpClient createHttpClient(String artemisUrl, boolean inflateResponses) {
        boolean secure = artemisUrl != null && artemisUrl.startsWith("https");
        HttpClient client = HttpClient.create()
            .doOnConnected(conn ->
                conn.addHandlerFirst(new ReadTimeoutHandler(20, TimeUnit.MINUTES)).addHandlerFirst(new WriteTimeoutHandler(30))
            )
            .responseTimeout(Duration.ofMinutes(20))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30 * 1000);

        // Ask for compressed responses, which every browser does and this client did not. Without it nginx served the
        // client bundle uncompressed: measured against staging1 the same fourteen bundle files were 2,097,134 bytes
        // plain and 590,017 gzipped, and /api/core/courses/for-dashboard is 431,544 against 21,353. A run moved
        // several times the bytes a real cohort moves, spent the difference saturating its own network, and measured
        // a code path no real user takes while missing the compression cost every real user does cause.
        //
        // compress(true) both sends the header and inflates what comes back. Callers that discard the body want only
        // the first half, and set the header themselves — see BrowserHeaders#forDiscardedAsset.
        if (inflateResponses) {
            client = client.compress(true);
        }

        if ("h3".equalsIgnoreCase(httpProtocol) && secure) {
            // HTTP/3 carries its own TLS inside QUIC, so the TCP-oriented secure() settings below do not apply.
            //
            // The flow-control limits are not tuning, they are required. Reactor-netty defaults every QUIC limit to
            // zero, and a peer may not send a byte of stream data beyond what the limits allow: without these the
            // handshake completes, a stream opens, and the response never arrives — the request dies on the read
            // timeout with nothing to say why. Verified against nginx and against Cloudflare's HTTP/3 endpoint.
            return client.protocol(HttpProtocol.HTTP3).http3Settings(spec ->
                spec
                    // Generous, because the students download a 20 MB bundle over these connections.
                    .maxData(64 * 1024 * 1024)
                    .maxStreamDataBidirectionalLocal(8 * 1024 * 1024)
                    .maxStreamDataBidirectionalRemote(8 * 1024 * 1024)
                    // A browser opens far fewer than this at once; the ceiling only has to not be the bottleneck.
                    .maxStreamsBidirectional(256)
                    // QUIC closes an idle connection itself, and a student sits idle for the whole think time.
                    .idleTimeout(Duration.ofMinutes(5))
            );
        }

        if (secure && !"h1".equalsIgnoreCase(httpProtocol)) {
            client = client.protocol(HttpProtocol.H2, HttpProtocol.HTTP11);
        }

        return client.secure(spec ->
            spec
                .sslContext(sharedSslContext())
                .handshakeTimeout(Duration.ofSeconds(30))
                .closeNotifyFlushTimeout(Duration.ofSeconds(30))
                .closeNotifyReadTimeout(Duration.ofSeconds(30))
        );
    }

    /**
     * The one TLS context every simulated user's client uses.
     * <p>
     * Building it per user meant parsing the JDK trust store per user: a heap dump from a 2000-student run held
     * 496,836 {@link java.security.cert.TrustAnchor} instances, roughly 248 certificates times 2000 students, with
     * every certificate's encoded bytes behind them. A real browser parses its trust store once for the whole
     * process, and netty's {@code SslContext} is explicitly built to be shared, so this is both cheaper and closer to
     * what a client does. Each user still gets its own {@code HttpClient}, so per-student connection behaviour is
     * unchanged.
     *
     * @return the shared client TLS context
     */
    static SslContext sharedSslContext() {
        SslContext context = sslContext;
        if (context == null) {
            synchronized (SimulatedArtemisUser.class) {
                context = sslContext;
                if (context == null) {
                    try {
                        context = TcpSslContextSpec.forClient().sslContext();
                    } catch (SSLException e) {
                        throw new IllegalStateException("Could not build the TLS context for the simulated users", e);
                    }
                    sslContext = context;
                }
            }
        }
        return context;
    }
}
