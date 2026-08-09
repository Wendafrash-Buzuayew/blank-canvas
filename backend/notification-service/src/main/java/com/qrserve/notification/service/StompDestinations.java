package com.qrserve.notification.service;

import java.util.UUID;

/**
 * Central definition of the STOMP topic naming scheme so every producer
 * uses an identical destination string.
 */
public final class StompDestinations {

    private StompDestinations() {
    }

    /** Kitchen Display System topic for a branch. */
    public static String kitchen(UUID merchantId, Long branchId) {
        return "/topic/merchant/" + merchantId + "/branch/" + branchId + "/kitchen";
    }

    /** Waiter alert topic for a branch. */
    public static String waiters(UUID merchantId, Long branchId) {
        return "/topic/merchant/" + merchantId + "/branch/" + branchId + "/waiters";
    }

    /** Customer order tracking topic. */
    public static String order(UUID orderId) {
        return "/topic/orders/" + orderId;
    }
}
