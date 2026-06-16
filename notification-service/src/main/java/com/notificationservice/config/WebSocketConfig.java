package com.notificationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket handshake endpoint — clients connect here
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // In production, restrict to your frontend domain
                .withSockJS();                   // SockJS fallback for browsers without WebSocket support
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix for messages FROM clients TO server
        registry.setApplicationDestinationPrefixes("/app");

        // Enable in-memory broker for topics and queues
        // In production, swap with a dedicated RabbitMQ or Kafka broker
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
