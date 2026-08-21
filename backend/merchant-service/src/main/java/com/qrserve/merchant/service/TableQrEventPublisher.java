package com.qrserve.merchant.service;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the terminal-label-to-table mapping so order-service can reconcile
 * inbound EMVCo payments without a synchronous call back into this service.
 *
 * <p>A failed publish must not fail table creation — the sticker is real
 * either way — but it is logged loudly here, because a lost event means a
 * payment against that sticker lands unreconciled until the projection is
 * rebuilt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TableQrEventPublisher {

    private static final String TOPIC = "table-qr-provisioned";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TableQrProvisionedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getTerminalLabel(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish terminal mapping for {}: {}",
                                    event.getTerminalLabel(), error.getMessage(), error);
                        }
                    });
        } catch (Exception e) {
            log.error("Kafka publish error on topic {} for terminal {}: {}",
                    TOPIC, event.getTerminalLabel(), e.getMessage(), e);
        }
    }
}
