package com.qrserve.shared.common;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle states of an order.
 *
 * <p><strong>These names are the wire contract.</strong> They must match what
 * order-service actually persists, because clients branch on them.
 *
 * <p>This enum previously declared {@code CREATED, PREPARING, READY, SERVED,
 * COMPLETED} — three of which the backend never produces — while orders were in
 * fact created {@code PENDING} and served {@code DELIVERED}. It was also unused,
 * so nothing forced the two to agree. That drift caused three separate real bugs:
 * the kitchen display's "Incoming" column keyed on {@code CREATED} and was
 * therefore always empty, its action wrote {@code SERVED} which the backend does
 * not recognise, and the waiter dashboard filtered active orders on
 * {@code CREATED} and so missed every new order. The enum is now the single
 * source of truth and is bound at the API boundary.
 */
public enum OrderStatus {

    /** Placed by the customer, not yet acknowledged by staff. */
    PENDING,

    /** Acknowledged by staff, not yet being prepared. */
    ACCEPTED,

    /** Kitchen is actively preparing the order. */
    PREPARING,

    /** Ready to be taken to the table. */
    READY,

    /** Delivered to the customer. */
    DELIVERED,

    /** Settled. Terminal. */
    PAID,

    /** Cancelled. Terminal, reachable from any non-terminal state. */
    CANCELLED;

    /** True if this state admits no further transitions. */
    public boolean isTerminal() {
        return this == PAID || this == CANCELLED;
    }

    /**
     * States reachable from this one.
     *
     * <p>Deliberately permissive about skipping forward — a small kitchen may go
     * straight from PENDING to READY — but it forbids moving backwards and
     * forbids leaving a terminal state, which is what actually corrupts reporting.
     */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACCEPTED, PREPARING, READY, CANCELLED);
            case ACCEPTED -> EnumSet.of(PREPARING, READY, CANCELLED);
            case PREPARING -> EnumSet.of(READY, DELIVERED, CANCELLED);
            case READY -> EnumSet.of(DELIVERED, PAID, CANCELLED);
            case DELIVERED -> EnumSet.of(PAID, CANCELLED);
            case PAID, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return next != null && allowedNext().contains(next);
    }
}
