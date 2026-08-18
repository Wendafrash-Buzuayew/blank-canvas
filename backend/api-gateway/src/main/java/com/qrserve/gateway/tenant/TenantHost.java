package com.qrserve.gateway.tenant;

import com.qrserve.shared.common.Slugs;

import java.util.Locale;

/**
 * Extracts a tenant label from a {@code Host} header.
 *
 * <p>Pure and static on purpose. Host parsing is where the edge cases live —
 * ports, IPv6 literals, the bare apex, multi-level labels, cluster-internal
 * names, reserved labels — and none of them need Redis, WebFlux or a Spring
 * context to test.
 *
 * <p>Returns {@code null} rather than throwing for "this host has no tenant".
 * That is a normal condition, not an error: localhost during development,
 * cluster-internal service names, kubelet probes and the admin console all
 * legitimately carry no tenant, and they must pass through rather than 404.
 */
public final class TenantHost {

    private TenantHost() {
    }

    /**
     * @param hostHeader the raw {@code Host} header, possibly with a port
     * @param baseDomain the configured tenant base domain, possibly with a port
     * @return the tenant label, or {@code null} if this host names no tenant
     */
    public static String labelFrom(String hostHeader, String baseDomain) {
        if (hostHeader == null || hostHeader.isBlank() || baseDomain == null || baseDomain.isBlank()) {
            return null;
        }
        String host = stripPort(hostHeader.trim().toLowerCase(Locale.ROOT));
        String base = stripPort(baseDomain.trim().toLowerCase(Locale.ROOT));
        if (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (host.isEmpty() || base.isEmpty()) {
            return null;
        }

        String suffix = "." + base;
        if (!host.endsWith(suffix)) {
            // The apex itself, localhost, an IP, or a cluster-internal name.
            return null;
        }

        String label = host.substring(0, host.length() - suffix.length());
        if (label.isEmpty() || label.contains(".")) {
            // Empty means the host was exactly ".base". A dot means a multi-level
            // label, which a single-label wildcard certificate cannot serve — so
            // picking the first segment would be a guess, not a resolution.
            return null;
        }
        if (!isValidLabel(label) || Slugs.isReserved(label)) {
            return null;
        }
        return label;
    }

    /**
     * Strips a trailing {@code :port}. Bracketed IPv6 literals are rejected
     * outright: they cannot carry a tenant label, and treating the text inside the
     * brackets as one would be nonsense.
     */
    private static String stripPort(String value) {
        if (value.startsWith("[")) {
            return "";
        }
        int colon = value.lastIndexOf(':');
        return colon >= 0 ? value.substring(0, colon) : value;
    }

    /**
     * DNS label characters only. Notably excludes {@code _}, which is legal in a
     * Host header but not in a hostname label.
     */
    private static boolean isValidLabel(String label) {
        if (label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
