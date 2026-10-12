package com.qrserve.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    /**
     * RestTemplate backed by the JDK HttpClient.
     *
     * <p>The default {@code SimpleClientHttpRequestFactory} wraps
     * {@code HttpURLConnection}, which rejects PATCH outright with
     * {@code ProtocolException: Invalid HTTP method: PATCH}. Every
     * {@code PATCH /api/tables/{id}/status} call therefore failed before it left
     * the process — and because the caller only logged a warning, table occupancy
     * silently never updated. JdkClientHttpRequestFactory supports PATCH and needs
     * no extra dependency on Java 17.
     *
     * <p>Timeouts are explicit: without them a hung downstream holds an order
     * request open indefinitely. The connect timeout matters as much as the read
     * timeout — an unreachable host otherwise blocks for the OS default.
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /**
     * Lets OrderService commit only the DB writes, then do REST calls and Kafka
     * publishing after the commit — outside the transaction and off the pool.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
