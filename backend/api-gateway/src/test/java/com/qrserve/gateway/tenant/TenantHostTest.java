package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantHostTest {

    private static final String BASE = "qrserve.safaricom.et";

    @Test
    @DisplayName("extracts the first label under the base domain")
    void extractsLabel() {
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("ignores the port")
    void ignoresPort() {
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.qrserve.safaricom.et:8081", BASE));
    }

    @Test
    @DisplayName("is case-insensitive, because hostnames are")
    void lowercases() {
        assertEquals("sunrise", TenantHost.labelFrom("SunRise.QRServe.Safaricom.ET", BASE));
    }

    @Test
    @DisplayName("the bare apex carries no tenant")
    void apexHasNoTenant() {
        assertNull(TenantHost.labelFrom("qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("a host outside the base domain carries no tenant")
    void foreignHostHasNoTenant() {
        // localhost, cluster-internal service names and kubelet probes all land here.
        // They must pass through untouched rather than 404, or local development and
        // health checks break.
        assertNull(TenantHost.labelFrom("localhost:8081", BASE));
        assertNull(TenantHost.labelFrom("10.0.0.5", BASE));
        assertNull(TenantHost.labelFrom("api-gateway-service", BASE));
        assertNull(TenantHost.labelFrom("evil.example.com", BASE));
    }

    @Test
    @DisplayName("a multi-level label carries no tenant")
    void multiLevelHasNoTenant() {
        // A single-label wildcard certificate does not cover *.*.domain, so this is
        // not a shape we can serve; treating it as tenant "a" would be a guess.
        assertNull(TenantHost.labelFrom("a.b.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("reserved labels carry no tenant")
    void reservedHasNoTenant() {
        assertNull(TenantHost.labelFrom("admin.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("api.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("www.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("null, blank and malformed hosts carry no tenant")
    void malformedHasNoTenant() {
        assertNull(TenantHost.labelFrom(null, BASE));
        assertNull(TenantHost.labelFrom("", BASE));
        assertNull(TenantHost.labelFrom(".qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("-bad.qrserve.safaricom.et", BASE));
        assertNull(TenantHost.labelFrom("has_underscore.qrserve.safaricom.et", BASE));
    }

    @Test
    @DisplayName("an IPv6 literal carries no tenant")
    void ipv6HasNoTenant() {
        assertNull(TenantHost.labelFrom("[::1]:8081", BASE));
    }

    @Test
    @DisplayName("a base domain with a port is handled, for local development")
    void devBaseDomainWithPort() {
        // PUBLIC_BASE_DOMAIN=localtest.me:3000 in the dev profile.
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.localtest.me:3000", "localtest.me:3000"));
        assertEquals("sunrise", TenantHost.labelFrom("sunrise.localtest.me", "localtest.me:3000"));
    }

    @Test
    @DisplayName("an unconfigured base domain yields no tenant rather than throwing")
    void unconfiguredBaseDomain() {
        assertNull(TenantHost.labelFrom("sunrise.qrserve.safaricom.et", null));
        assertNull(TenantHost.labelFrom("sunrise.qrserve.safaricom.et", ""));
    }
}
