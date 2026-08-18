package com.qrserve.shared.common;

/**
 * Occupancy states of a table.
 *
 * <p>Introduced because {@code PATCH /api/tables/{id}/status} accepted a raw
 * {@code Map<String,String>} body and persisted whatever string arrived, with a
 * silent default of {@code AVAILABLE} when the key was missing entirely. Any
 * typo became a stored value that no query would ever match again.
 */
public enum TableStatus {

    /** No active party seated. */
    AVAILABLE,

    /** A party is seated, or an order is open against the table. */
    OCCUPIED,

    /** Held for a future booking. */
    RESERVED
}
