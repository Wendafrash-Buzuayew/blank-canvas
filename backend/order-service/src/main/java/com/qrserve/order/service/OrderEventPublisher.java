package com.qrserve.order.service;

import com.qrserve.shared.common.TraceContext;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String ORDER_STATUS_TOPIC = "order-status-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        // Populate telemetry context (traceId / spanId) for distributed tracing
        event.setTraceId(TraceContext.getTraceId());
        event.setSpanId(TraceContext.getSpanId());

        kafkaTemplate.send(ORDER_CREATED_TOPIC, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-created event for order {}", event.getOrderNumber(), ex);
                    } else {
                        log.info("Published order-created event for order {} to partition {}",
                                event.getOrderNumber(), result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        // Populate telemetry context (traceId / spanId) for distributed tracing
        event.setTraceId(TraceContext.getTraceId());
        event.setSpanId(TraceContext.getSpanId());

        kafkaTemplate.send(ORDER_STATUS_TOPIC, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-status-updated event for order {}", event.getOrderNumber(), ex);
                    } else {
                        log.info("Published order-status-updated event for order {} to partition {}",
                                event.getOrderNumber(), result.getRecordMetadata().partition());
                    }
                });
    }
}
