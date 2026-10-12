package com.qrserve.notification.config;

import com.qrserve.notification.kafka.DomainEventListener;
import com.qrserve.shared.events.CustomerRequestEvent;
import com.qrserve.shared.events.OrderCreatedEvent;
import com.qrserve.shared.events.OrderStatusUpdatedEvent;
import com.qrserve.shared.events.WaiterAlertEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Four listeners with four event types share one container factory. This proves
 * each listener's parameter type is bound from the bytes the producers actually
 * write (JacksonJsonSerializer, with its own __TypeId__ headers), so no listener
 * depends on a shared default type.
 */
class KafkaConsumerConfigTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 12, 9, 30, 0);

    private final RecordMessageConverter converter = KafkaConsumerConfig.messageConverter();

    private Object roundTrip(String topic, Object event, String listenerMethod) throws Exception {
        RecordHeaders headers = new RecordHeaders();
        byte[] bytes = new JacksonJsonSerializer<>().serialize(topic, headers, event);
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(topic, 0, 0L, 0L,
                TimestampType.CREATE_TIME, -1, bytes.length, null, bytes, headers, Optional.empty());

        Method method = DomainEventListener.class.getMethod(listenerMethod, event.getClass());
        Type paramType = method.getGenericParameterTypes()[0];
        Message<?> message = converter.toMessage(record, null, null, paramType);
        return message.getPayload();
    }

    @Test
    @DisplayName("order-created binds to OrderCreatedEvent")
    void orderCreated() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder().orderId(UUID.randomUUID())
                .orderNumber("ORD-1").merchantId(MERCHANT).branchId(5L).tableId(7L)
                .totalAmount(new BigDecimal("120.50")).createdAt(AT).build();
        Object bound = roundTrip("order-created", event, "onOrderCreated");
        assertInstanceOf(OrderCreatedEvent.class, bound);
        assertEquals(event, bound);
    }

    @Test
    @DisplayName("order-status-updated binds to OrderStatusUpdatedEvent")
    void orderStatusUpdated() throws Exception {
        OrderStatusUpdatedEvent event = OrderStatusUpdatedEvent.builder().orderId(UUID.randomUUID())
                .orderNumber("ORD-2").merchantId(MERCHANT).branchId(5L)
                .previousStatus("PREPARING").newStatus("READY").build();
        Object bound = roundTrip("order-status-updated", event, "onOrderStatusUpdated");
        assertInstanceOf(OrderStatusUpdatedEvent.class, bound);
        assertEquals(event, bound);
    }

    @Test
    @DisplayName("customer-request binds to CustomerRequestEvent")
    void customerRequest() throws Exception {
        CustomerRequestEvent event = CustomerRequestEvent.builder().requestId(UUID.randomUUID())
                .merchantId(MERCHANT).branchId(5L).tableId(7L).tableNumber("12")
                .requestType("CALL_WAITER").createdAt(AT).build();
        Object bound = roundTrip("customer-request", event, "onCustomerRequest");
        assertInstanceOf(CustomerRequestEvent.class, bound);
        assertEquals(event, bound);
    }

    @Test
    @DisplayName("waiter-alert binds to WaiterAlertEvent")
    void waiterAlert() throws Exception {
        WaiterAlertEvent event = WaiterAlertEvent.builder().alertId(UUID.randomUUID())
                .merchantId(MERCHANT).branchId(5L).alertType("ORDER_READY")
                .message("ready").createdAt(AT).build();
        Object bound = roundTrip("waiter-alert", event, "onWaiterAlert");
        assertInstanceOf(WaiterAlertEvent.class, bound);
        assertEquals(event, bound);
    }
}
