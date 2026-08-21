package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminal label is what a webhook quotes back at us, so it has to identify one
 * physical sticker for as long as that sticker might still be on a table.
 */
class TerminalLabelTest {

    @Test
    @DisplayName("label is derived from the table and the version")
    void derivedFromTableAndVersion() {
        assertEquals("T42-1", TerminalLabel.of(42L, 1));
        assertEquals("T7-3", TerminalLabel.of(7L, 3));
    }

    @Test
    @DisplayName("a reprint gets a different label, so a payment names the sticker that made it")
    void reprintChangesTheLabel() {
        assertNotEquals(TerminalLabel.of(42L, 1), TerminalLabel.of(42L, 2));
    }

    @Test
    @DisplayName("different tables never collide")
    void differentTablesDiffer() {
        assertNotEquals(TerminalLabel.of(4L, 21), TerminalLabel.of(42L, 1));
    }

    @Test
    @DisplayName("fits EMVCo tag 62-07")
    void fitsTheTag() {
        assertTrue(TerminalLabel.of(Long.MAX_VALUE, 9999).length() <= TerminalLabel.MAX_LENGTH,
                "tag 62-07 is capped at " + TerminalLabel.MAX_LENGTH + " characters");
    }

    @Test
    @DisplayName("version zero is rejected: versions are one-based, and v0 would alias no sticker")
    void versionMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> TerminalLabel.of(42L, 0));
        assertThrows(IllegalArgumentException.class, () -> TerminalLabel.of(42L, -1));
    }

    @Test
    @DisplayName("overflowing the 25-character cap is rejected: a truncated tag 62-07 cannot be traced back to its sticker")
    void overflowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TerminalLabel.of(Long.MAX_VALUE, 100000));
    }
}
