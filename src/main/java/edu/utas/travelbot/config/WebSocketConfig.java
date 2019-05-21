package edu.utas.travelbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.lang.invoke.MethodHandles;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/connect")
                .setHandshakeHandler(new HandshakeHanlder())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/public", "/chat");   // Enables a simple in-memory broker
        registry.setUserDestinationPrefix("/private");
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        log.info("<==> handleSubscribeEvent: username="+event.getUser().getName()+", event="+event);
    }

    @EventListener
    public void handleConnectEvent(SessionConnectEvent event) {
        log.info("===> handleConnectEvent: username="+event.getUser().getName()+", event="+event);
    }

    @EventListener
    public void handleDisconnectEvent(SessionDisconnectEvent event) {
        log.info("<=== handleDisconnectEvent: username="+event.getUser().getName()+", event="+event);
    }
}