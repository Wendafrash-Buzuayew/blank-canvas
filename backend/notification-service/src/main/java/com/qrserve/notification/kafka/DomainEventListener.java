package com.qrserve.notification.kafka;

import com.qrserve.notification.redis.NotificationEnvelope;
import com.qrserve.notification.redis.RedisEventPublisher;
import com.qrserve.notification.service.StompDestinations;
import com.qrserve.shared.events.CustomerRequestEvent;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import com.qrserve.shared.events.WaiterAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bridges domain events emitted by order-service and merchant-service
 * onto the real-time STOMP channels, fanned out across pods via Redis.
 *
 * <p>Channels:</p>
 * <ul>
 *   <li>{@code /topic/merchant/{merchantId}/branch/{branchId}/kitchen}</li>
 *   <li>{@code /topic/merchant/{merchantId}/branch/{branchId}/waiters}</li>
 *   <li>{@code /topic/orders/{orderId}}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventListener {

    private final RedisEventPublisher redisEventPublisher;

    @KafkaListener(topics = "order-created", groupId = "notification-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received ORDER_CREATED for {}", event.getOrderNumber());

        // Kitchen Display System
        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.kitchen(event.getMerchantId(), event.getBranchId()))
                .eventType("ORDER_CREATED")
                .payload(event)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());

        // Customer order tracking
        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.order(event.getOrderId()))
                .eventType("ORDER_CREATED")
                .payload(event)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());
    }

    @KafkaListener(topics = "order-status-updated", groupId = "notification-service")
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        String status = event.getNewStatus() != null ? event.getNewStatus().toUpperCase() : "";
        log.info("Received ORDER_STATUS_UPDATED {} -> {}", event.getOrderNumber(), status);

        // Customer order tracking — every transition
        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.order(event.getOrderId()))
                .eventType("ORDER_STATUS_UPDATED")
                .payload(event)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());

        // Kitchen sees cancellations and progress
        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.kitchen(event.getMerchantId(), event.getBranchId()))
                .eventType("ORDER_STATUS_UPDATED")
                .payload(event)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());

        // Waiters are alerted when an order becomes READY
        if ("READY".equals(status)) {
            WaiterAlertEvent alert = WaiterAlertEvent.builder()
                    .alertId(UUID.randomUUID())
                    .merchantId(event.getMerchantId())
                    .branchId(event.getBranchId())
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .tableId(event.getTableId())
                    .alertType("ORDER_READY")
                    .message("Order " + event.getOrderNumber() + " is ready to serve")
                    .createdAt(LocalDateTime.now())
                    .traceId(event.getTraceId())
                    .spanId(event.getSpanId())
                    .build();

            redisEventPublisher.publish(NotificationEnvelope.builder()
                    .destination(StompDestinations.waiters(event.getMerchantId(), event.getBranchId()))
                    .eventType("ORDER_READY")
                    .payload(alert)
                    .traceId(event.getTraceId())
                    .spanId(event.getSpanId())
                    .build());
        }
    }

    @KafkaListener(topics = "customer-request", groupId = "notification-service")
    public void onCustomerRequest(CustomerRequestEvent event) {
        log.info("Received CUSTOMER_REQUEST {} for table {}", event.getRequestType(), event.getTableId());

        WaiterAlertEvent alert = WaiterAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .merchantId(event.getMerchantId())
                .branchId(event.getBranchId())
                .tableId(event.getTableId())
                .tableNumber(event.getTableNumber())
                .alertType(event.getRequestType())
                .message("Table " + (event.getTableNumber() != null ? event.getTableNumber() : event.getTableId())
                        + ": " + event.getRequestType())
                .createdAt(event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now())
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build();

        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.waiters(event.getMerchantId(), event.getBranchId()))
                .eventType(event.getRequestType())
                .payload(alert)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());
    }

    @KafkaListener(topics = "waiter-alert", groupId = "notification-service")
    public void onWaiterAlert(WaiterAlertEvent event) {
        redisEventPublisher.publish(NotificationEnvelope.builder()
                .destination(StompDestinations.waiters(event.getMerchantId(), event.getBranchId()))
                .eventType(event.getAlertType())
                .payload(event)
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .build());
    }
}
