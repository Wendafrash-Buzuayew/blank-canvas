package com.qrserve.merchant.service;

import com.qrserve.shared.common.TraceContext;
import com.qrserve.shared.events.CustomerRequestEvent;
import com.qrserve.shared.events.WaiterAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes merchant-domain events to Kafka so notification-service can
 * bridge them to the real-time STOMP channels. Telemetry context
 * (traceId / spanId) is attached to every payload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantEventPublisher {

    private static final String CUSTOMER_REQUEST_TOPIC = "customer-request";
    private static final String WAITER_ALERT_TOPIC = "waiter-alert";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCustomerRequest(CustomerRequestEvent event) {
        event.setTraceId(TraceContext.getTraceId());
        event.setSpanId(TraceContext.getSpanId());
        send(CUSTOMER_REQUEST_TOPIC, String.valueOf(event.getTableId()), event);
    }

    public void publishWaiterAlert(WaiterAlertEvent event) {
        event.setTraceId(TraceContext.getTraceId());
        event.setSpanId(TraceContext.getSpanId());
        send(WAITER_ALERT_TOPIC, String.valueOf(event.getBranchId()), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} event: {}", topic, ex.getMessage());
                        } else {
                            log.info("Published {} event (key={})", topic, key);
                        }
                    });
        } catch (Exception e) {
            log.error("Kafka publish error on topic {}: {}", topic, e.getMessage());
        }
    }
}
