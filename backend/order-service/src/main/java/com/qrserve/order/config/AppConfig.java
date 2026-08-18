package com.qrserve.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
     * request open indefinitely.
     */
    @Bean
    public RestTemplate restTemplate() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
