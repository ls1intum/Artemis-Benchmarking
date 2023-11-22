package de.tum.cit.ase.service.artemis.interaction;

import static de.tum.cit.ase.domain.RequestType.AUTHENTICATION;
import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.domain.RequestStat;
import de.tum.cit.ase.service.artemis.util.AuthToken;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpSslContextSpec;

public abstract class SimulatedArtemisUser {

    protected Logger log;

    protected final String username;
    protected final String password;
    protected final String artemisUrl;
    protected WebClient webClient;
    protected AuthToken authToken;
    protected boolean authenticated = false;

    public SimulatedArtemisUser(String username, String password, String artemisUrl) {
        this.username = username;
        this.password = password;
        this.artemisUrl = artemisUrl;
    }

    /**
     * Login to Artemis and return the request stats for the login request.
     * @return the request stats for the login request
     */
    public List<RequestStat> login() {
        log.info("Logging in as {{}}", username);
        List<RequestStat> requestStats = new ArrayList<>();
        WebClient webClient = WebClient
            .builder()
            .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        long start = System.nanoTime();
        var payload = Map.of("username", username, "password", password, "rememberMe", true);
        var response = webClient.post().uri("api/public/authenticate").bodyValue(payload).retrieve().toBodilessEntity().block();

        requestStats.add(new RequestStat(now(), System.nanoTime() - start, AUTHENTICATION));

        if (response == null) {
            throw new RuntimeException("Login failed - No response received");
        }
        var header = response.getHeaders().get("Set-Cookie");
        if (header == null) {
            throw new RuntimeException("Login failed - No cookie received");
        }
        var cookieHeader = header.get(0);
        this.authToken = AuthToken.fromResponseHeaderString(cookieHeader);
        String cookieHeaderToken = authToken.jwtToken();
        this.webClient =
            WebClient
                .builder()
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .baseUrl(artemisUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Cookie", cookieHeaderToken)
                .build();
        checkAccess();
        log.debug("Logged in as {}", username);
        return requestStats;
    }

    protected abstract void checkAccess();

    public AuthToken getAuthToken() {
        return authToken;
    }

    private static HttpClient createHttpClient() {
        return HttpClient
            .create()
            .doOnConnected(conn ->
                conn.addHandlerFirst(new ReadTimeoutHandler(10, TimeUnit.MINUTES)).addHandlerFirst(new WriteTimeoutHandler(30))
            )
            .responseTimeout(Duration.ofMinutes(10))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30 * 1000)
            .secure(spec -> {
                try {
                    spec
                        .sslContext(TcpSslContextSpec.forClient().sslContext())
                        .handshakeTimeout(Duration.ofSeconds(30))
                        .closeNotifyFlushTimeout(Duration.ofSeconds(30))
                        .closeNotifyReadTimeout(Duration.ofSeconds(30));
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
