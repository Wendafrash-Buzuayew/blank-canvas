package com.qrserve.shared.common;

import java.util.Locale;
import java.util.Set;

/**
 * The single source of truth for slug normalisation.
 *
 * <p>A merchant slug becomes a DNS label — the tenant's public hostname — so it
 * is validated against DNS label rules rather than merely "cleaned". The
 * expression this replaces, duplicated in {@code MerchantService},
 * {@code MerchantEntity} and {@code BranchEntity},
 * {@code name.toLowerCase().replaceAll("[^a-z0-9]", "-")}, produced
 * {@code --joe-s-diner-} for {@code "Joe's Diner "}: leading and trailing
 * hyphens are illegal in a DNS label, and nothing rejected them.
 *
 * <p>Rejection is by exception rather than by silent repair. A slug that cannot
 * be derived — a purely non-Latin name, for instance — must be supplied by the
 * owner rather than guessed at. Transliteration is deliberately not implemented:
 * a mangled machine transliteration would become that business's permanent
 * public address.
 *
 * <p>{@link IllegalArgumentException} is used because {@code GlobalExceptionHandler}
 * maps it to 400 carrying this message, and {@code shared:common} cannot depend
 * on {@code shared:exceptions} — that dependency runs the other way.
 */
public final class Slugs {

    /** Minimum merchant slug length. Below this, hostnames become guessable. */
    public static final int DNS_LABEL_MIN_LENGTH = 3;

    /**
     * Maximum merchant slug length. The DNS label limit is 63; 40 leaves room for
     * future prefixes without reprinting anyone's QR stands.
     */
    public static final int DNS_LABEL_MAX_LENGTH = 40;

    /** Maximum branch slug length. A path segment has far more headroom. */
    public static final int PATH_SLUG_MAX_LENGTH = 60;

    /**
     * Labels that never resolve as a tenant. {@code admin} is where SUPER_ADMIN
     * cross-tenant work lives; the rest are conventional infrastructure names
     * that a tenant claiming them could use to intercept traffic or mislead
     * operators.
     */
    public static final Set<String> RESERVED_LABELS = Set.of(
            "admin", "api", "app", "www", "static", "assets", "ws", "mail", "status");

    private Slugs() {
    }

    /**
     * Normalises and validates a merchant slug as a DNS label.
     *
     * @throws IllegalArgumentException if the result cannot be a hostname label
     */
    public static String toDnsLabel(String raw) {
        String slug = normalize(raw);

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "slug cannot be derived from this name: it contains no usable Latin letters or digits. "
                            + "Please supply a slug explicitly (lowercase a-z, 0-9 and hyphens).");
        }
        if (slug.length() < DNS_LABEL_MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at least " + DNS_LABEL_MIN_LENGTH + " characters: '" + slug + "'");
        }
        if (slug.length() > DNS_LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at most " + DNS_LABEL_MAX_LENGTH + " characters: '" + slug + "'");
        }
        // A purely numeric hostname label is ambiguous with an IP address octet
        // and is rejected outright by some resolvers.
        if (slug.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("slug must not be entirely numeric: '" + slug + "'");
        }
        if (isReserved(slug)) {
            throw new IllegalArgumentException("slug '" + slug + "' is reserved and cannot be used");
        }
        return slug;
    }

    /**
     * Normalises and validates a branch slug as a URL path segment.
     *
     * <p>Three rules differ from {@link #toDnsLabel}: the cap is
     * {@value #PATH_SLUG_MAX_LENGTH}, a single character is enough, and an
     * entirely numeric or reserved value is allowed. A branch legitimately named
     * "2" is a perfectly good path segment, and a branch named "admin" cannot
     * collide with a subdomain because it never becomes one.
     */
    public static String toPathSlug(String raw) {
        String slug = normalize(raw);

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "slug cannot be derived from this name: it contains no usable Latin letters or digits. "
                            + "Please supply a slug explicitly (lowercase a-z, 0-9 and hyphens).");
        }
        if (slug.length() > PATH_SLUG_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be at most " + PATH_SLUG_MAX_LENGTH + " characters: '" + slug + "'");
        }
        return slug;
    }

    /** True if the label is reserved for platform use. Case-insensitive. */
    public static boolean isReserved(String label) {
        return label != null && RESERVED_LABELS.contains(label.toLowerCase(Locale.ROOT));
    }

    /**
     * Lowercases, replaces every character outside {@code [a-z0-9]} with a
     * hyphen, collapses hyphen runs, and trims the edges. Returns "" when nothing
     * usable survives — callers decide whether that is fatal.
     */
    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("slug source must not be blank");
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lowered.length());
        boolean lastWasHyphen = false;
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (allowed) {
                out.append(c);
                lastWasHyphen = false;
            } else if (!lastWasHyphen) {
                out.append('-');
                lastWasHyphen = true;
            }
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == '-') {
            start++;
        }
        while (end > start && out.charAt(end - 1) == '-') {
            end--;
        }
        return out.substring(start, end);
    }
}
