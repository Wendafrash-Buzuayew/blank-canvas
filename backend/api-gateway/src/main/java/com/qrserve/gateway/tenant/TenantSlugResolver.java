package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.TenantCacheKeys;
import com.qrserve.shared.common.dto.TenantResolutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

    public TenantSlugResolver(
            ReactiveStringRedisTemplate redis,
            WebClient.Builder webClientBuilder,
            @Value("${services.merchant-service-url:http://localhost:8085}") String merchantServiceUrl) {
        this.redis = redis;
        this.webClient = webClientBuilder.baseUrl(merchantServiceUrl).build();
    }

    /** @return the merchant id, or an empty {@code Mono} if the slug names no tenant */
    public Mono<UUID> resolve(String slug) {
        String key = TenantCacheKeys.slugKey(slug);
        return redis.opsForValue().get(key)
                .flatMap(cached -> TenantCacheKeys.NEGATIVE.equals(cached)
                        ? Mono.empty()
                        : Mono.just(UUID.fromString(cached)))
                .switchIfEmpty(Mono.defer(() -> lookupAndCache(slug, key)))
                // A Redis outage must not take the whole platform down: fall back to a
                // direct lookup rather than failing every request.
                .onErrorResume(e -> {
                    log.warn("Tenant cache unavailable for '{}', falling back to direct lookup: {}",
                            slug, e.getMessage());
                    return lookup(slug);
                });
    }

    private Mono<UUID> lookupAndCache(String slug, String key) {
        return lookup(slug)
                .flatMap(id -> redis.opsForValue()
                        .set(key, id.toString(), HIT_TTL)
                        .thenReturn(id))
                .switchIfEmpty(Mono.defer(() -> redis.opsForValue()
                        .set(key, TenantCacheKeys.NEGATIVE, MISS_TTL)
                        .then(Mono.empty())))
                .onErrorResume(e -> {
                    log.warn("Failed to cache tenant '{}': {}", slug, e.getMessage());
                    return lookup(slug);
                });
    }

    private Mono<UUID> lookup(String slug) {
        return webClient.get()
                .uri("/api/v1/public/tenants/by-slug/{slug}", slug)
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.bodyToMono(TenantResolutionResponse.class)
                        : response.releaseBody().then(Mono.empty()))
                .map(TenantResolutionResponse::getMerchantId)
                .onErrorResume(e -> {
                    // A 404 is the ordinary "no such tenant" answer, not a failure.
                    log.debug("Tenant lookup for '{}' returned no result: {}", slug, e.getMessage());
                    return Mono.empty();
                });
    }
}
