package com.qrserve.shared.common;

/**
 * Enum representing the types of service requests a customer can make
 * from their table. Centralized in {@code :shared:common} to prevent
 * cross-service string-literal drift.
 *
 * <p>Used by merchant-service (customer request creation), notification-service
 * (waiter alerts), and any other service that needs to interpret or forward
 * customer request events.</p>
 */
public enum WaiterRequestType {

    /** Customer calls a waiter to the table. */
    CALL_WAITER,

    /** Customer requests water. */
    REQUEST_WATER,

    /** Customer requests the bill. */
    REQUEST_BILL
}