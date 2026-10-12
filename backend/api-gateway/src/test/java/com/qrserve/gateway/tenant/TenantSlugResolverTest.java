package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.TenantCacheKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantSlugResolverTest {

    private static final UUID MERCHANT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String KEY = TenantCacheKeys.slugKey("sunrise");

    private ReactiveValueOperations<String, String> ops;
    private ReactiveStringRedisTemplate redis;
    private final AtomicInteger lookups = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ops = mock(ReactiveValueOperations.class);
        redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(Mono.empty());
        when(ops.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    }

    private TenantSlugResolver resolverReturning(Mono<ClientResponse> response) {
        // Generous: the first JSON decode in a cold JVM can take a while.
        return resolverReturning(response, Duration.ofSeconds(10));
    }

    private TenantSlugResolver resolverReturning(Mono<ClientResponse> response, Duration timeout) {
        ExchangeFunction exchange = request -> {
            lookups.incrementAndGet();
            return response;
        };
        return new TenantSlugResolver(redis, WebClient.builder().exchangeFunction(exchange),
                "http://merchant-service", timeout);
    }

    @Test
    @DisplayName("a merchant-service 5xx is an error, never cached as an unknown tenant")
    void serverErrorIsNotCached() {
        TenantSlugResolver resolver = resolverReturning(
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolver.resolve("sunrise").block());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatusCode().value());
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a lookup timeout is an error, never cached")
    void timeoutIsNotCached() {
        TenantSlugResolver resolver = resolverReturning(Mono.never(), Duration.ofMillis(200));

        assertThrows(ResponseStatusException.class, () -> resolver.resolve("sunrise").block());
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a 404 is cached as a negative entry")
    void notFoundIsCachedAsNegative() {
        TenantSlugResolver resolver = resolverReturning(
                Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        assertNull(resolver.resolve("sunrise").block());
        verify(ops).set(eq(KEY), eq(TenantCacheKeys.NEGATIVE), any(Duration.class));
    }

    @Test
    @DisplayName("a 200 is cached as a hit")
    void foundIsCached() {
        TenantSlugResolver resolver = resolverReturning(Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"merchantId\":\"" + MERCHANT_ID + "\"}")
                        .build()));

        assertEquals(MERCHANT_ID, resolver.resolve("sunrise").block());
        verify(ops).set(eq(KEY), eq(MERCHANT_ID.toString()), any(Duration.class));
    }

    @Test
    @DisplayName("a cached negative answers without calling merchant-service")
    void cachedNegativeShortCircuits() {
        when(ops.get(KEY)).thenReturn(Mono.just(TenantCacheKeys.NEGATIVE));
        TenantSlugResolver resolver = resolverReturning(
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        assertNull(resolver.resolve("sunrise").block());
        assertEquals(0, lookups.get());
    }
}
