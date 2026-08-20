package com.qrserve.shared.common;

/**
 * When a payable closes. Configured per merchant AND per fulfilment type, because one
 * restaurant commonly runs takeout prepaid and dine-in on a tab at the same time.
 */
public enum SettlementMode {

    /** Closes at order placement; the kitchen sees the order only once it is settled. */
    PREPAID,

    /** Stays open across a sitting and closes when the bill is requested. */
    TAB
}
