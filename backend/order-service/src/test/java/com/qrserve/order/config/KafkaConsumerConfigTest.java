package com.qrserve.order.config;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TerminalMapListener is order-service's first {@code @KafkaListener}. Without a
 * consumer configuration binding {@link TableQrProvisionedEvent}, Boot's default
 * String deserializer cannot deserialize the event this listener exists to
 * consume, and the terminal-map projection would silently never receive anything.
 *
 * <p>No broker involved: this constructs the exact deserializer
 * {@link KafkaConsumerConfig#consumerFactory()} configures — same trusted
 * packages, same type-info-headers setting, same default value type — and
 * round-trips a real event through it.
 */
class KafkaConsumerConfigTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** The exact value-deserializer configuration {@code consumerFactory()} builds. */
    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.qrserve.shared.events");
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, TableQrProvisionedEvent.class.getName());
        return props;
    }

    @Test
    @DisplayName("the configured deserializer binds TableQrProvisionedEvent from JSON with no type headers")
    void deserializerBindsProvisionedEventFromJson() {
        TableQrProvisionedEvent event = TableQrProvisionedEvent.builder()
                .terminalLabel("T42-1")
                .tableId(42L)
                .merchantId(MERCHANT)
                .branchId(5L)
                .tableNumber("15")
                .version(1)
                .provisionedAt(LocalDateTime.of(2026, 8, 21, 10, 30, 0))
                .build();

        // The producer side, so the bytes fed to the deserializer are exactly what
        // merchant-service would actually put on the topic — not a hand-built string.
        JacksonJsonSerializer<TableQrProvisionedEvent> serializer = new JacksonJsonSerializer<>();
        byte[] bytes = serializer.serialize("table-qr-provisioned", event);

        JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>();
        deserializer.configure(consumerProps(), false);

        // No type-info header at all, matching USE_TYPE_INFO_HEADERS=false in production:
        // if binding depended on a header instead of the configured default type, this
        // would throw "No type information in headers and no default type provided"
        // rather than come back as the wrong type.
        Object result = deserializer.deserialize("table-qr-provisioned", new RecordHeaders(), bytes);

        assertNotNull(result);
        assertEquals(TableQrProvisionedEvent.class, result.getClass());
        TableQrProvisionedEvent bound = (TableQrProvisionedEvent) result;
        assertEquals("T42-1", bound.getTerminalLabel());
        assertEquals(42L, bound.getTableId());
        assertEquals(MERCHANT, bound.getMerchantId());
        assertEquals(5L, bound.getBranchId());
        assertEquals("15", bound.getTableNumber());
        assertEquals(1, bound.getVersion());
        assertEquals(LocalDateTime.of(2026, 8, 21, 10, 30, 0), bound.getProvisionedAt());
    }

    @Test
    @DisplayName("trusted packages does not block com.qrserve.shared.events")
    void trustedPackagesIncludesSharedEvents() {
        // A wrong or missing TRUSTED_PACKAGES value would make Jackson refuse to bind
        // the type even with a default type configured; deserializing successfully here
        // is itself the assertion that com.qrserve.shared.events is trusted.
        TableQrProvisionedEvent event = TableQrProvisionedEvent.builder()
                .terminalLabel("T7-1").tableId(7L).merchantId(MERCHANT).branchId(1L)
                .tableNumber("3").version(1).provisionedAt(LocalDateTime.now())
                .build();
        JacksonJsonSerializer<TableQrProvisionedEvent> serializer = new JacksonJsonSerializer<>();
        byte[] bytes = serializer.serialize("table-qr-provisioned", event);

        JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>();
        deserializer.configure(consumerProps(), false);

        Object result = deserializer.deserialize("table-qr-provisioned", new RecordHeaders(), bytes);
        assertEquals(TableQrProvisionedEvent.class, result.getClass());
    }
}
