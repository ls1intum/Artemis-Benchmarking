package de.tum.cit.ase.web.websocket;

import jakarta.websocket.*;

@ClientEndpoint
public class WebsocketClient extends Endpoint {

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Websocket opened.");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        System.out.println("Websocket opened 2.");
    }
}
