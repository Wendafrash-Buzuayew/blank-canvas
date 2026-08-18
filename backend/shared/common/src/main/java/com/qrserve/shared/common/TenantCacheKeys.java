package com.qrserve.shared.common;

/**
 * The tenant cache contract, shared between the gateway (which writes) and
 * merchant-service (which invalidates).
 *
 * <p>Two hand-written copies of a Redis key prefix stop matching the moment
 * either changes, and the failure is invisible: the cache keeps working, the
 * invalidation silently stops. One constant removes that.
 */
public final class TenantCacheKeys {

    private static final String SLUG_PREFIX = "tenant:slug:";

    /**
     * Sentinel for "this slug names no tenant". Not a valid UUID, so it cannot be
     * mistaken for one.
     */
    public static final String NEGATIVE = "-";

    /**
     * A resolved slug is cached for 10 minutes. Slugs are permanent — renames are
     * rejected — so a cached hit cannot go stale.
     */
    public static final long HIT_TTL_SECONDS = 600;

    /**
     * A miss is cached for 60 seconds. Much shorter than a hit, because on a public
     * wildcard domain bots enumerate subdomains, and each un-cached probe would
     * otherwise cost a gateway hop plus a database round trip — a free
     * amplification primitive. Short enough that a newly created tenant starts
     * resolving almost immediately even if its slug happened to be probed just
     * beforehand.
     */
    public static final long MISS_TTL_SECONDS = 60;

    private TenantCacheKeys() {
    }

    public static String slugKey(String slug) {
        return SLUG_PREFIX + slug;
    }
}
