package com.qrserve.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * One factory serves four listeners, each consuming a different event type
 * ({@code OrderCreatedEvent}, {@code OrderStatusUpdatedEvent},
 * {@code CustomerRequestEvent}, {@code WaiterAlertEvent}). A single
 * deserializer-level default type cannot serve them all, so values are read
 * as raw bytes and the JSON is bound by a message converter to the type
 * inferred from each {@code @KafkaListener} method's parameter.
 *
 * <p>Raw bytes cannot fail to deserialize, so a malformed payload surfaces as
 * a conversion exception, which {@link DefaultErrorHandler} treats as
 * non-retryable: logged and skipped, never blocking the partition. Other
 * listener failures (e.g. Redis down) are retried with a bounded backoff and
 * then logged and skipped.
 *
 * <p>{@code auto-offset-reset} is {@code latest}: these are live alerts, and
 * replaying history on a new consumer group would ring every kitchen and
 * waiter screen with stale orders.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.setRecordMessageConverter(messageConverter());
        // 3 retries, 1s apart, then the record is logged and skipped.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }

    /** Binds JSON bytes to the listener parameter's type (type headers are ignored). */
    static RecordMessageConverter messageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }
}
