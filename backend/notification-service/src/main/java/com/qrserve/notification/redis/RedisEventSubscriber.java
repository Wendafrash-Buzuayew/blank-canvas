package com.qrserve.notification.redis;

import com.qrserve.shared.common.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives envelopes from the Redis Pub/Sub channel and forwards them to
 * the locally connected STOMP clients through the in-memory broker.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisEventSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Invoked by the Spring {@code MessageListenerAdapter} configured in
     * {@code RedisConfig}.
     */
    public void onMessage(NotificationEnvelope envelope) {
        if (envelope == null || envelope.getDestination() == null) {
            return;
        }
        try {
            if (envelope.getTraceId() != null) {
                TraceContext.setTraceId(envelope.getTraceId());
            }
            messagingTemplate.convertAndSend(envelope.getDestination(), envelope);
            log.debug("Forwarded {} to STOMP destination {}",
                    envelope.getEventType(), envelope.getDestination());
        } catch (Exception e) {
            log.error("Failed to forward Redis message to STOMP: {}", e.getMessage());
        } finally {
            TraceContext.clear();
        }
    }
}
