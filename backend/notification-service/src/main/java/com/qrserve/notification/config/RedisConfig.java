package com.qrserve.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Pub/Sub messaging.
 *
 * <p>Each notification-service instance subscribes to the same Redis Pub/Sub
 * channel. When any instance receives a Kafka domain event, it publishes a
 * Redis message to the channel. All instances (including the publisher)
 * receive the message and forward it to their locally connected WebSocket
 * clients via STOMP. This achieves cross-pod broadcast.</p>
 */
@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    /**
     * Redis Pub/Sub channel name for notification events.
     */
    public static final String NOTIFICATION_CHANNEL = "notification:events";

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(NOTIFICATION_CHANNEL));
        container.setErrorHandler(t -> {
            // Log but don't crash the container on Redis errors
            System.err.println("Redis message listener error: " + t.getMessage());
        });
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(
            com.qrserve.notification.redis.RedisEventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }
}
