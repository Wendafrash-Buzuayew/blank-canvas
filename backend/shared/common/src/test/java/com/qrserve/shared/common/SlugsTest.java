package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case here is a row from the defect table in the design doc. The
 * one-line expression this replaces got all six wrong, and its output is a
 * tenant's public hostname.
 */
class SlugsTest {

    @Test
    @DisplayName("trims the leading and trailing hyphens that are illegal in a DNS label")
    void trimsEdgeHyphens() {
        // Old behaviour: "--joe-s-diner-"
        assertEquals("joe-s-diner", Slugs.toDnsLabel("Joe's Diner "));
    }

    @Test
    @DisplayName("collapses runs of hyphens")
    void collapsesHyphenRuns() {
        // Old behaviour: "sunrise-coffee---tea"
        assertEquals("sunrise-coffee-tea", Slugs.toDnsLabel("Sunrise Coffee & Tea"));
    }

    @Test
    @DisplayName("a name with no Latin characters is rejected, not silently emptied")
    void rejectsNonLatin() {
        // Old behaviour: "------". Amharic-named businesses supply a Latin slug.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Slugs.toDnsLabel("ካፈ አበባ"));
        assertTrue(e.getMessage().contains("Latin"),
                "the message must tell the owner to supply a Latin slug");
    }

    @Test
    @DisplayName("rejects a slug longer than the 40-character cap")
    void rejectsOverlongLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("a".repeat(41)));
        assertEquals("a".repeat(40), Slugs.toDnsLabel("a".repeat(40)), "the cap itself is allowed");
    }

    @Test
    @DisplayName("rejects a slug shorter than 3 characters")
    void rejectsTooShortLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("ab"));
        assertEquals("abc", Slugs.toDnsLabel("abc"));
    }

    @Test
    @DisplayName("rejects an entirely numeric label - it cannot be a hostname")
    void rejectsNumericLabel() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("12345"));
    }

    @Test
    @DisplayName("rejects reserved labels so a tenant cannot claim admin.")
    void rejectsReserved() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("Admin"));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("api"));
        assertTrue(Slugs.isReserved("www"));
        assertFalse(Slugs.isReserved("sunrise"));
    }

    @Test
    @DisplayName("rejects null and blank input")
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel(null));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toDnsLabel("   "));
    }

    @Test
    @DisplayName("an already-valid slug passes through unchanged")
    void idempotent() {
        assertEquals("sunrise-coffee", Slugs.toDnsLabel("sunrise-coffee"));
        assertEquals("sunrise-coffee", Slugs.toDnsLabel(Slugs.toDnsLabel("Sunrise Coffee")));
    }

    // ---- branch (path) slugs: three rules differ ----

    @Test
    @DisplayName("a branch slug may be entirely numeric - a branch called 2 is a valid path segment")
    void pathSlugAllowsNumeric() {
        assertEquals("2", Slugs.toPathSlug("2"));
    }

    @Test
    @DisplayName("a branch slug may use a reserved label - it is a path segment, not a hostname")
    void pathSlugAllowsReserved() {
        assertEquals("admin", Slugs.toPathSlug("Admin"));
    }

    @Test
    @DisplayName("a branch slug is capped at 60, not 40")
    void pathSlugHasLongerCap() {
        assertEquals("a".repeat(60), Slugs.toPathSlug("a".repeat(60)));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toPathSlug("a".repeat(61)));
    }

    @Test
    @DisplayName("a branch slug is still normalised and still cannot be empty")
    void pathSlugStillNormalises() {
        assertEquals("main-hall", Slugs.toPathSlug(" Main   Hall! "));
        assertThrows(IllegalArgumentException.class, () -> Slugs.toPathSlug("!!!"));
    }
}
