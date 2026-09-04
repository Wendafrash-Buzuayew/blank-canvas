package com.qrserve.menu.config;

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
     * {@code ProtocolException: Invalid HTTP method: PATCH}. This service's
     * {@code BranchMenuBackfillRunner} calls
     * {@code PATCH /api/branches/{id}/primary}, and {@code MenuService}'s
     * branch-ownership check is a GET — the PATCH would fail before it left
     * the process. Mirrors order-service's {@code AppConfig}, which hit and
     * documented this exact bug first. JdkClientHttpRequestFactory supports
     * PATCH and needs no extra dependency on Java 17.
     *
     * <p>Timeouts are explicit: without them a hung downstream (merchant-service)
     * holds a category-create or publish request open indefinitely.
     */
    @Bean
    public RestTemplate restTemplate() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
