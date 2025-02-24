package de.tum.cit.aet.config;

import de.tum.cit.aet.security.AuthoritiesConstants;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    public static final String IP_ADDRESS = "IP_ADDRESS";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = defaultHandshakeHandler();
        WebSocketTransportHandler webSocketTransportHandler = new WebSocketTransportHandler(handshakeHandler);
        // @formatter:off
        registry
            // NOTE: clients can connect using sockjs via 'ws://{artemis-url}/websocket' or without sockjs using 'ws://{artemis-url}/websocket/websocket'
            .addEndpoint("/websocket")
            .setAllowedOriginPatterns("*")
            // TODO: in the future, we should deactivate the option to connect with sockjs, because this is not needed any more
            .withSockJS()
            .setTransportHandlers(webSocketTransportHandler)
            .setInterceptors(httpSessionHandshakeInterceptor());
        // @formatter:on
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new TopicSubscriptionInterceptor());
    }

    /**
     * This interceptor is used to add the IP address of the client to the websocket session attributes.
     * @return the interceptor for the http handshake
     */
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
            ) {
                if (request instanceof ServletServerHttpRequest) {
                    ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                    attributes.put(IP_ADDRESS, servletRequest.getRemoteAddress());
                }
                return true;
            }

            @Override
            public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
            ) {}
        };
    }

    private DefaultHandshakeHandler defaultHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                Principal principal = request.getPrincipal();
                if (principal == null) {
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS));
                    principal = new AnonymousAuthenticationToken("WebsocketConfiguration", "anonymous", authorities);
                }
                return principal;
            }
        };
    }

    public static class TopicSubscriptionInterceptor implements ChannelInterceptor {

        /**
         * Method is called before the user's message is sent to the controller
         *
         * @param message Message that the websocket client is sending (e.g. SUBSCRIBE, MESSAGE, UNSUBSCRIBE)
         * @param channel Current message channel
         * @return message that gets sent along further
         */
        @Override
        public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
            log.debug("preSend: {}, channel: {}", message, channel);
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
            Principal principal = headerAccessor.getUser();
            String destination = headerAccessor.getDestination();

            if (StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
                try {
                    if (!allowSubscription(principal, destination)) {
                        logUnauthorizedDestinationAccess(principal, destination);
                        return null; // erase the forbidden SUBSCRIBE command the user was trying to send
                    }
                } catch (Exception e) {
                    // If the user is not found (e.g. because he is not logged in), he should not be able to subscribe to these topics
                    log.warn(
                        "An error occurred while subscribing user {} to destination {}: {}",
                        principal != null ? principal.getName() : "null",
                        destination,
                        e.getMessage()
                    );
                    return null;
                }
            }

            return message;
        }

        /**
         * Returns whether the subscription of the given principal to the given destination is permitted
         * Database calls should be avoided as much as possible in this method.
         * Only for very specific topics, database calls are allowed.
         *
         * @param principal   User principal of the user who wants to subscribe
         * @param destination Destination topic to which the user wants to subscribe
         * @return flag whether subscription is allowed
         */
        private boolean allowSubscription(@Nullable Principal principal, String destination) {
            log.info("{} wants to subscribe to {}", principal != null ? principal.getName() : "Anonymous", destination);
            if (principal == null) {
                log.warn("Anonymous user tried to access the protected topic: {}", destination);
                return false;
            }
            return true;
        }

        private void logUnauthorizedDestinationAccess(Principal principal, String destination) {
            if (principal == null) {
                log.warn("Anonymous user tried to access the protected topic: {}", destination);
            } else {
                log.warn("User with login '{}' tried to access the protected topic: {}", principal.getName(), destination);
            }
        }
    }
}
