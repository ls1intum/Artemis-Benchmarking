package de.tum.cit.aet.service.artemis.interaction;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * A STOMP-over-websocket connection from a simulated user to the real Artemis server.
 * <p>
 * Real Artemis clients keep a websocket open for the whole exam conduction: they subscribe to exam live events
 * and to programming submission/result topics, and the broker keeps the connection alive with heartbeats. The
 * REST-only simulation never exercised this, so the connection-level load (one persistent websocket per concurrent
 * student plus the broker subscriptions) was completely unmodeled. This class mirrors that behaviour so a weak
 * websocket/broker path shows up under load just like a weak REST endpoint would.
 * <p>
 * The underlying {@link WebSocketStompClient} (and its heartbeat scheduler) is shared across all simulated users to
 * avoid creating a client per student; each student owns its own {@link StompSession}. All operations are
 * best-effort: a failure to connect or subscribe is logged and never aborts the surrounding simulation.
 */
public class SimulatedArtemisWebsocket {

    private static final Logger log = LoggerFactory.getLogger(SimulatedArtemisWebsocket.class);

    private static final long[] HEARTBEAT = { 10_000, 10_000 }; // must match the server (WebsocketConfiguration)
    private static final long CONNECT_TIMEOUT_SECONDS = 20;
    private static final int MAX_MESSAGE_BUFFER = 512 * 1024;

    private static volatile WebSocketStompClient sharedClient;

    private final String websocketUrl;
    private final String jwtCookie;
    private StompSession session;

    /**
     * @param artemisUrl the base Artemis URL (e.g. {@code http://localhost:8080/}), as used for the REST web client
     * @param jwtCookie  the {@code jwt=...} cookie value of the authenticated user (sent during the websocket handshake)
     */
    public SimulatedArtemisWebsocket(String artemisUrl, String jwtCookie) {
        this.jwtCookie = jwtCookie;
        var base = artemisUrl.endsWith("/") ? artemisUrl.substring(0, artemisUrl.length() - 1) : artemisUrl;
        // http -> ws, https -> wss. Connect without SockJS via the native '/websocket/websocket' endpoint.
        this.websocketUrl = base.replaceFirst("^http", "ws") + "/websocket/websocket";
    }

    /**
     * Open the STOMP session (websocket handshake + STOMP CONNECT), authenticating via the JWT handshake cookie.
     *
     * @return {@code true} if the session was established and is connected
     * @throws Exception if the connection could not be established within the timeout
     */
    public boolean connect() throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        if (jwtCookie != null && !jwtCookie.isBlank()) {
            handshakeHeaders.add(HttpHeaders.COOKIE, jwtCookie);
        }
        StompHeaders connectHeaders = new StompHeaders();
        this.session = getSharedClient()
            .connectAsync(
                websocketUrl,
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleTransportError(StompSession stompSession, Throwable exception) {
                        log.debug("Websocket transport error: {}", exception.getMessage());
                    }
                }
            )
            .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return isConnected();
    }

    /**
     * Subscribe to the given destination. The payload is intentionally ignored (we only generate the subscription
     * load and receive broker pushes); failures are swallowed so a single bad destination cannot break the run.
     *
     * @param destination the STOMP destination to subscribe to
     */
    public void subscribe(String destination) {
        if (!isConnected()) {
            return;
        }
        try {
            session.subscribe(
                destination,
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Load test only: we do not process the (gzip-compressed) broker messages.
                    }
                }
            );
        } catch (Exception e) {
            log.debug("Websocket subscribe to {} failed: {}", destination, e.getMessage());
        }
    }

    /**
     * @return whether the STOMP session is currently connected
     */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * Close the STOMP session (and its underlying websocket). Safe to call multiple times.
     */
    public void disconnect() {
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception e) {
                log.debug("Websocket disconnect failed: {}", e.getMessage());
            }
            session = null;
        }
    }

    private static WebSocketStompClient getSharedClient() {
        if (sharedClient == null) {
            synchronized (SimulatedArtemisWebsocket.class) {
                if (sharedClient == null) {
                    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                    container.setDefaultMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER);
                    container.setDefaultMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER);
                    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient(container));
                    client.setMessageConverter(new SimpleMessageConverter());
                    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
                    scheduler.setPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
                    scheduler.setThreadNamePrefix("ws-heartbeat-");
                    scheduler.initialize();
                    client.setTaskScheduler(scheduler);
                    client.setDefaultHeartbeat(HEARTBEAT);
                    sharedClient = client;
                }
            }
        }
        return sharedClient;
    }
}
