package com.qrserve.shared.common;

/**
 * Enum representing the lifecycle states of an order from creation
 * through completion. Centralized in {@code :shared:common} to ensure
 * all microservices use the same status contract.
 *
 * <p>Valid flow: {@code CREATED → PREPARING → READY → SERVED → COMPLETED}.
 * A {@code CANCELLED} status is permitted as a terminal state from any
 * non-terminal status.</p>
 */
public enum OrderStatus {

    /** Order has been placed but not yet accepted by the kitchen. */
    CREATED,

    /** Kitchen is actively preparing the order. */
    PREPARING,

    /** Order is ready to be served to the customer. */
    READY,

    /** Order has been delivered/served to the customer. */
    SERVED,

    /** Order is fully completed and paid. */
    COMPLETED
}