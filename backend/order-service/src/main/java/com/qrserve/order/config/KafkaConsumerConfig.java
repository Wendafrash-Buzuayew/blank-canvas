package com.qrserve.order.config;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures the consumer side of Kafka for order-service, mirroring
 * {@code com.qrserve.notification.config.KafkaConsumerConfig}.
 *
 * <p>Without this, {@code TerminalMapListener} — this service's first
 * {@code @KafkaListener} — falls back to Boot's defaults: a {@code String}
 * deserializer that cannot bind {@link TableQrProvisionedEvent},
 * so the default error handler retries and then drops every message. The
 * terminal-map projection this listener exists to build would never receive
 * anything, and every future bank payment against a printed sticker would
 * become unreconcilable.
 *
 * <p>{@code auto-offset-reset} is deliberately {@code earliest}, not Boot's
 * default {@code latest}: this consumer builds a projection that must see the
 * entire history of provisioning events. {@code latest} would silently skip
 * every table provisioned before this consumer's first boot.
 *
 * <p>One deviation from the notification-service config this mirrors:
 * {@code VALUE_DEFAULT_TYPE} is set here. With {@code USE_TYPE_INFO_HEADERS}
 * false and no default type, {@link JacksonJsonDeserializer#deserialize} has
 * no type to bind to at all and throws {@code IllegalStateException("No type
 * information in headers and no default type provided")} for every record —
 * verified directly against this exact configuration, not assumed. Setting a
 * default type is safe here because this factory currently serves exactly one
 * listener on exactly one topic; if order-service later gains a second event
 * type on this factory, this default type must become per-listener (e.g. a
 * dedicated {@code ConsumerFactory} for the new topic) rather than shared.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.qrserve.shared.events");
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, TableQrProvisionedEvent.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
