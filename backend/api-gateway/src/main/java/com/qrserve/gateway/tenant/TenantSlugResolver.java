package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.TenantCacheKeys;
import com.qrserve.shared.common.dto.TenantResolutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a subdomain label into a merchant id, cached in Redis.
 *
 * <h2>Misses are cached too</h2>
 * On a public wildcard domain, bots enumerate subdomains. Without negative
 * caching every {@code xyz.qrserve.safaricom.et} probe becomes a gateway hop plus
 * a database round trip, which is a free amplification primitive. A miss is
 * cached as a sentinel with a much shorter TTL, so a newly created tenant still
 * starts resolving quickly.
 *
 * <h2>Only a 404 is a miss</h2>
 * A 5xx, a timeout or a connection failure from merchant-service says nothing
 * about whether the tenant exists. Caching it as "unknown tenant" would turn a
 * merchant-service blip into minutes of 404s for every live tenant, so those
 * failures are never cached: they surface as a 503 and the next request retries.
 *
 * <h2>Why a configured URL rather than lb://</h2>
 * This lookup runs on every cache-missing request, ahead of routing. A plain
 * {@link WebClient} against a configured URL keeps the filter independent of the
 * load balancer's own state, and matches the pattern qr-service already uses for
 * its merchant-service calls.
 */
@Component
@Slf4j
public class TenantSlugResolver {

    // Key format and TTLs come from TenantCacheKeys so the gateway (which writes)
    // and merchant-service (which invalidates) cannot drift apart.
    private static final Duration HIT_TTL = Duration.ofSeconds(TenantCacheKeys.HIT_TTL_SECONDS);
    private static final Duration MISS_TTL = Duration.ofSeconds(TenantCacheKeys.MISS_TTL_SECONDS);

    private final ReactiveStringRedisTemplate redis;
    private final WebClient webClient;
    private final Duration lookupTimeout;

    public TenantSlugResolver(
            ReactiveStringRedisTemplate redis,
            WebClient.Builder webClientBuilder,
            @Value("${services.merchant-service-url:http://localhost:8085}") String merchantServiceUrl,
            @Value("${services.merchant-service-timeout:2s}") Duration lookupTimeout) {
        this.redis = redis;
        this.webClient = webClientBuilder.baseUrl(merchantServiceUrl).build();
        this.lookupTimeout = lookupTimeout;
    }

    /**
     * @return the merchant id, or an empty {@code Mono} if the slug names no tenant
     * @throws ResponseStatusException (as an error signal) with 503 when the
     *         lookup itself failed, so the caller does not report "no such tenant"
     */
    public Mono<UUID> resolve(String slug) {
        String key = TenantCacheKeys.slugKey(slug);
        return redis.opsForValue().get(key)
                .map(cached -> TenantCacheKeys.NEGATIVE.equals(cached)
                        ? Optional.<UUID>empty()
                        : Optional.of(UUID.fromString(cached)))
                // A Redis outage must not take the whole platform down: fall back to a
                // direct lookup rather than failing every request. Sits before the
                // cache-miss branch so a merchant-service failure is NOT retried here.
                .onErrorResume(e -> {
                    log.warn("Tenant cache unavailable for '{}', falling back to direct lookup: {}",
                            slug, e.getMessage());
                    return lookup(slug);
                })
                .switchIfEmpty(Mono.defer(() -> lookupAndCache(slug, key)))
                .flatMap(Mono::justOrEmpty);
    }

    private Mono<Optional<UUID>> lookupAndCache(String slug, String key) {
        return lookup(slug).flatMap(found -> {
            Mono<Boolean> write = found.isPresent()
                    ? redis.opsForValue().set(key, found.get().toString(), HIT_TTL)
                    : redis.opsForValue().set(key, TenantCacheKeys.NEGATIVE, MISS_TTL);
            return write
                    .onErrorResume(e -> {
                        log.warn("Failed to cache tenant '{}': {}", slug, e.getMessage());
                        return Mono.just(false);
                    })
                    .thenReturn(found);
        });
    }

    /**
     * @return the merchant id, {@code Optional.empty()} for a definite 404, or an
     *         error for anything else (5xx, timeout, connection failure)
     */
    private Mono<Optional<UUID>> lookup(String slug) {
        return webClient.get()
                .uri("/api/v1/public/tenants/by-slug/{slug}", slug)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(TenantResolutionResponse.class)
                                .map(body -> Optional.ofNullable(body.getMerchantId()));
                    }
                    if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        // The ordinary "no such tenant" answer, not a failure.
                        return response.releaseBody().thenReturn(Optional.<UUID>empty());
                    }
                    return response.createException().flatMap(Mono::error);
                })
                .timeout(lookupTimeout)
                .onErrorMap(e -> {
                    log.warn("Tenant lookup for '{}' failed: {}", slug, e.toString());
                    return new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE, "Tenant lookup unavailable", e);
                });
    }
}
