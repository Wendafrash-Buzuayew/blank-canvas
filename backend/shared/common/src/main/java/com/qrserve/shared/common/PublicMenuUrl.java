package com.qrserve.shared.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the public, customer-facing URLs for a tenant. This is the only place
 * either the tenant host or the menu URL is constructed.
 *
 * <p>Two copies of this format previously existed — in {@code TableService} and
 * {@code QrGeneratorService} — each with a comment asserting it matched the
 * other. Both were wrong in the same two ways: they hardcoded
 * {@code https://qrserve.com} and emitted
 * {@code /menu/{slug}/{branchId}/{tableId}} while the resolver looks the branch
 * up by <em>slug</em> and the table by <em>table number</em>. Every QR code ever
 * generated resolved to a 404. One builder means the next format change cannot
 * half-land.
 *
 * <p>{@code app.public-base-domain} has no default on purpose. A default would
 * silently emit QR codes pointing at the wrong host, and that failure mode is a
 * printed sheet of paper that does not work, discovered by a customer holding a
 * phone. Failing at startup is cheaper.
 */
@Component
public class PublicMenuUrl {

    private final String baseDomain;
    private final String scheme;

    public PublicMenuUrl(
            @Value("${app.public-base-domain}") String baseDomain,
            @Value("${app.public-url-scheme:https}") String scheme) {
        if (baseDomain == null || baseDomain.isBlank()) {
            throw new IllegalStateException(
                    "app.public-base-domain must be configured via PUBLIC_BASE_DOMAIN "
                            + "(for example qrserve.safaricom.et)");
        }
        // A leading dot is how wildcard certificates are usually written
        // (".qrserve.safaricom.et"), so accept it rather than emit "https://x..domain".
        String trimmed = baseDomain.trim();
        this.baseDomain = trimmed.startsWith(".") ? trimmed.substring(1) : trimmed;
        this.scheme = (scheme == null || scheme.isBlank()) ? "https" : scheme.trim();
    }

    /** The configured base domain, without any leading dot. */
    public String baseDomain() {
        return baseDomain;
    }

    /** {@code sunrise} -> {@code sunrise.qrserve.safaricom.et}. */
    public String tenantHost(String merchantSlug) {
        require(merchantSlug, "merchantSlug");
        return merchantSlug + "." + baseDomain;
    }

    /** The canonical public menu URL, unsigned. */
    public String menuUrl(String merchantSlug, String branchSlug, String tableNumber) {
        return menuUrl(merchantSlug, branchSlug, tableNumber, null);
    }

    /**
     * The canonical public menu URL. The signature is appended only when present,
     * so an unsigned demo URL does not carry an empty parameter that the resolver
     * would then have to treat as "supplied but invalid".
     */
    public String menuUrl(String merchantSlug, String branchSlug, String tableNumber, String signature) {
        require(merchantSlug, "merchantSlug");
        require(branchSlug, "branchSlug");
        require(tableNumber, "tableNumber");

        StringBuilder url = new StringBuilder()
                .append(scheme).append("://")
                .append(tenantHost(merchantSlug))
                .append("/menu/")
                .append(encode(branchSlug))
                .append('/')
                .append(encode(tableNumber));

        if (signature != null && !signature.isBlank()) {
            url.append("?signature=").append(encode(signature));
        }
        return url.toString();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required to build a public menu URL");
        }
    }

    /**
     * URL-encodes a path segment. {@code table_number} is a free-text column, so a
     * value like {@code "A 1"} would otherwise emit a raw space and truncate the
     * URL inside a QR code. {@link URLEncoder} is form-encoding, so the {@code +}
     * it produces for a space is converted to {@code %20} for path use.
     */
    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
