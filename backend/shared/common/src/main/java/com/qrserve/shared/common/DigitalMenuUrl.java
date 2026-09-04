package com.qrserve.shared.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL builder for the phase-1 Digital Menu HLD's path-based public URL
 * family — {@code /m/{merchant-slug}[/{branch-slug}]}. Deliberately a
 * sibling of {@link PublicMenuUrl}, not a change to it: this scheme lives on
 * its own domain ({@code PUBLIC_MENU_DOMAIN}), scales to ~30,000 merchants
 * on one shared host (no per-tenant subdomain/wildcard-cert concern), and
 * never encodes a table — see
 * docs/superpowers/specs/2026-09-04-menu-url-access-redesign-design.md.
 */
@Component
public class DigitalMenuUrl {

    private final String publicMenuDomain;
    private final String scheme;

    public DigitalMenuUrl(
            @Value("${app.public-menu-domain}") String publicMenuDomain,
            @Value("${app.public-url-scheme:https}") String scheme) {
        require(publicMenuDomain, "app.public-menu-domain");
        this.publicMenuDomain = publicMenuDomain;
        this.scheme = scheme;
    }

    public String merchantUrl(String merchantSlug) {
        require(merchantSlug, "merchantSlug");
        return scheme + "://" + publicMenuDomain + "/m/" + encode(merchantSlug);
    }

    public String branchUrl(String merchantSlug, String branchSlug) {
        require(merchantSlug, "merchantSlug");
        require(branchSlug, "branchSlug");
        return scheme + "://" + publicMenuDomain + "/m/" + encode(merchantSlug) + "/" + encode(branchSlug);
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
