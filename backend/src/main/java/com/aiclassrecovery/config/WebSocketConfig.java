package com.aiclassrecovery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP over WebSocket for live session real-time transcription.
 *
 * Connect:  ws://localhost:8080/ws/live-session  (with SockJS fallback)
 * Subscribe: /topic/session/{sessionId}          (receive live updates)
 * Send:      /app/...                             (not used — chunks sent via REST)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/live-session")
                .setAllowedOriginPatterns(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "null"   // file:// pages opened directly from disk
                )
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Client subscribes to /topic/session/{id} to get live updates
        config.enableSimpleBroker("/topic");
        // Prefix for messages sent from client to server (not currently used)
        config.setApplicationDestinationPrefixes("/app");
    }
}
