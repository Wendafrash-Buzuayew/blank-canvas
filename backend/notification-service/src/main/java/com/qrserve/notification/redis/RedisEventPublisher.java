package com.qrserve.notification.redis;

import com.qrserve.notification.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes notification envelopes to the shared Redis Pub/Sub channel.
 * Every notification-service pod subscribes to the same channel, so a
 * message published by one pod reaches WebSocket clients connected to
 * all pods.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Failures propagate on purpose: the Kafka listener calling this must fail
     * so the container's error handler retries instead of acking the offset.
     */
    public void publish(NotificationEnvelope envelope) {
        redisTemplate.convertAndSend(RedisConfig.NOTIFICATION_CHANNEL, envelope);
        log.debug("Published {} to Redis for destination {}",
                envelope.getEventType(), envelope.getDestination());
    }
}
