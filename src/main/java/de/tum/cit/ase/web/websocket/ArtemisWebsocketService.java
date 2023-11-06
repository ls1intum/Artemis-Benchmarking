package de.tum.cit.ase.web.websocket;

import de.tum.cit.ase.service.artemis.ArtemisConfiguration;
import de.tum.cit.ase.service.artemis.ArtemisServer;
import de.tum.cit.ase.service.artemis.util.AuthToken;
import jakarta.websocket.ClientEndpointConfig;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Service
public class ArtemisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisWebsocketService.class);

    private final ArtemisConfiguration artemisConfiguration;

    public ArtemisWebsocketService(ArtemisConfiguration artemisConfiguration) {
        this.artemisConfiguration = artemisConfiguration;
    }

    public StompSession initializeConnection(ArtemisServer server, AuthToken token, StompSessionHandler sessionHandler) {
        /*ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();

        StandardWebSocketClient standardWebSocketClient = new StandardWebSocketClient();
        List<Transport> transports = List.of(new WebSocketTransport(standardWebSocketClient));
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(taskScheduler);
        //stompClient.setDefaultHeartbeat(new long[]{10000L, 10000L});

        String url = artemisConfiguration.getWebsocketUrl(server);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", token.jwtToken());

        StompSession session = null;
        try {
            log.debug("Connecting websocket.");
            session = stompClient.connectAsync(url, headers, sessionHandler).get();
            log.debug("Websocket connection established.");
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error while setting up websocket: {{}}", e.getMessage());
        }
        return session;*/
        ClientManager client;
        try {
            client = ClientManager.createClient();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        try {
            URI uri = new URI(artemisConfiguration.getWebsocketUrl(server));
            Map<String, List<String>> headers = Collections.singletonMap("Cookie", Collections.singletonList(token.jwtToken()));

            ClientEndpointConfig.Configurator configurator = new ClientEndpointConfig.Configurator() {
                public void beforeRequest(Map<String, List<String>> headers) {
                    super.beforeRequest(headers);
                    headers.putAll(headers); // Add all other necessary headers here
                }
            };

            ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create().configurator(configurator).build();

            client.connectToServer(WebsocketClient.class, clientConfig, uri);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
