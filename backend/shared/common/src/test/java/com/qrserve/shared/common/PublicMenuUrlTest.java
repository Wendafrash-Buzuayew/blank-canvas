package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicMenuUrlTest {

    private final PublicMenuUrl urls = new PublicMenuUrl("qrserve.safaricom.et", "https");

    @Test
    @DisplayName("the merchant is the host label, the branch and table are the path")
    void buildsTheCanonicalUrl() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("the tenant host is derived from configuration, never hardcoded")
    void buildsTenantHost() {
        assertEquals("sunrise.qrserve.safaricom.et", urls.tenantHost("sunrise"));
        assertEquals("qrserve.safaricom.et", urls.baseDomain());
    }

    @Test
    @DisplayName("appends the signature as a query parameter when supplied")
    void appendsSignature() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12?signature=abc-123_x",
                urls.menuUrl("sunrise", "main", "12", "abc-123_x"));
    }

    @Test
    @DisplayName("a blank signature is omitted rather than emitted empty")
    void omitsBlankSignature() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12", "  "));
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                urls.menuUrl("sunrise", "main", "12", null));
    }

    @Test
    @DisplayName("path segments are URL-encoded so an odd table number cannot break the URL")
    void encodesPathSegments() {
        // table_number is a free-text column; "A 1" must not emit a raw space.
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/A%201",
                urls.menuUrl("sunrise", "main", "A 1"));
    }

    @Test
    @DisplayName("the scheme is configurable so local development can use http")
    void honoursScheme() {
        PublicMenuUrl dev = new PublicMenuUrl("localtest.me:3000", "http");
        assertEquals("http://sunrise.localtest.me:3000/menu/main/12",
                dev.menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("a missing base domain fails fast at construction, not at scan time")
    void requiresBaseDomain() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new PublicMenuUrl("  ", "https"));
        assertTrue(e.getMessage().contains("PUBLIC_BASE_DOMAIN"),
                "the message must name the environment variable an operator has to set");
    }

    @Test
    @DisplayName("a leading dot on the configured domain is tolerated")
    void tolerantOfLeadingDot() {
        assertEquals("https://sunrise.qrserve.safaricom.et/menu/main/12",
                new PublicMenuUrl(".qrserve.safaricom.et", "https").menuUrl("sunrise", "main", "12"));
    }

    @Test
    @DisplayName("a null or blank slug is rejected rather than producing https://.domain")
    void rejectsBlankSlug() {
        assertThrows(IllegalArgumentException.class, () -> urls.tenantHost(null));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("", "main", "12"));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("sunrise", "", "12"));
        assertThrows(IllegalArgumentException.class, () -> urls.menuUrl("sunrise", "main", ""));
    }
}
