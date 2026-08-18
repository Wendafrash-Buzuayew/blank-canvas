package com.qrserve.merchant.service;

import com.qrserve.shared.common.TenantCacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the gateway's cached answer for one slug.
 *
 * <p>The case this exists for is narrow but real: a negative cache entry created
 * by a bot probing {@code kaffa.qrserve.safaricom.et} outlives the creation of a
 * tenant with that slug, and the new owner gets a 404 on their first visit to
 * their own site.
 *
 * <p>Best-effort by design. Redis being unreachable must not fail a merchant
 * registration — the worst consequence of a missed invalidation is up to
 * {@link TenantCacheKeys#MISS_TTL_SECONDS} seconds of 404, which is not worth
 * rolling back a registration for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantCacheInvalidator {

    private final StringRedisTemplate redis;

    public void invalidate(String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        try {
            redis.delete(TenantCacheKeys.slugKey(slug));
        } catch (Exception e) {
            log.warn("Could not invalidate tenant cache for '{}': {}. It will expire within {}s.",
                    slug, e.getMessage(), TenantCacheKeys.MISS_TTL_SECONDS);
        }
    }
}
