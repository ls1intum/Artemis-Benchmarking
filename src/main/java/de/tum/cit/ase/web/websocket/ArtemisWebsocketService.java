package de.tum.cit.ase.web.websocket;

import de.tum.cit.ase.config.ArtemisConfiguration;
import de.tum.cit.ase.service.util.ArtemisServer;
import de.tum.cit.ase.service.util.AuthToken;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
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
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

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
        return session;
    }
}
