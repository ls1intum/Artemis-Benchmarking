package de.tum.cit.ase.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

public class ArtemisWebsocketSessionHandler extends StompSessionHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(ArtemisWebsocketSessionHandler.class);

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("New session established : {}", session.getSessionId());
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        log.info("Received : {}", payload);
    }
}
