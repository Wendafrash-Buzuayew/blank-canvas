package com.qrserve.notification.config;

import com.qrserve.notification.interceptor.JwtHandshakeInterceptor;
import com.qrserve.notification.interceptor.StompAuthInterceptor;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

/**
 * STOMP-over-WebSocket configuration.
 *
 * <p>Replaces the legacy raw {@code OrderWebSocketHandler} with a full
 * STOMP message broker. Clients connect to {@code /ws} and subscribe to
 * topics prefixed with {@code /topic/...}. The simple in-memory broker
 * handles routing within a single pod; Redis Pub/Sub ensures messages
 * reach all pods (see {@link RedisConfig}).</p>
 *
 * <p>Authentication is handled at two levels:</p>
 * <ol>
 *   <li>{@link JwtHandshakeInterceptor} – extracts the JWT from the
 *       query-string parameter {@code token} or the HTTP
 *       {@code Authorization} header during the WebSocket handshake
 *       and stores the authenticated principal in the session
 *       attributes.</li>
 *   <li>{@link StompAuthInterceptor} – validates the principal on
 *       every STOMP frame and rejects unauthenticated sessions.</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final StompAuthInterceptor stompAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable the simple in-memory broker for /topic destinations
        registry.enableSimpleBroker("/topic")
                // .setHeartbeatValue(10000)
                .setHeartbeatValue(new long[] { 10000L, 10000L })
                .setTaskScheduler(heartBeatTaskScheduler());
        // Application destination prefix for messages sent to @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
        // User-specific queue prefix
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler heartBeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
