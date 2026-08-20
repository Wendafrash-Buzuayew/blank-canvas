package com.qrserve.shared.common;

/**
 * Identifies one printed sticker, not one table.
 *
 * <p>A reprint issues a new label and supersedes the old row, so a payment quoting
 * tag 62-07 tells us which physical code produced it — which is how a stale sticker
 * still in circulation gets discovered. Resolving a label to a table therefore has to
 * search superseded rows as well as the active one.
 */
public final class TerminalLabel {

    /** EMVCo caps tag 62-07 at 25 characters. */
    public static final int MAX_LENGTH = 25;

    private TerminalLabel() {
    }

    public static String of(long tableId, int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Terminal label version is one-based, got " + version);
        }
        String label = "T" + tableId + "-" + version;
        if (label.length() > MAX_LENGTH) {
            throw new IllegalStateException("Terminal label exceeds tag 62-07: " + label);
        }
        return label;
    }
}
