package com.qrserve.shared.common;

/**
 * How an order reaches the guest. The frontend mirrors these names; import from here
 * rather than writing the strings inline, which is how {@code OrderStatus} drifted in
 * four places.
 */
public enum FulfilmentType {

    /** Eaten at a table. The only type with a table id and a signed-QR presence proof. */
    DINE_IN,

    /** Collected at the counter. No table, so prepayment is what protects the kitchen. */
    TAKEOUT,

    /** Modelled now, enabled later — see the delivery sub-project. */
    DELIVERY
}
