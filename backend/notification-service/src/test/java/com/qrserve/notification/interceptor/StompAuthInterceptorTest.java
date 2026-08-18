package com.qrserve.notification.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers STOMP destination authorization.
 *
 * <p>The interceptor previously checked only that a principal existed on the
 * frame, so any authenticated user could subscribe to
 * {@code /topic/merchant/{anyMerchantId}/branch/{n}/kitchen} and read another
 * tenant's traffic. These tests pin that shut, and pin the deny-by-default
 * behaviour for destinations that match no known shape.
 */
class StompAuthInterceptorTest {

    private final StompAuthInterceptor interceptor = new StompAuthInterceptor();

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORDER_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    /** Builds a SUBSCRIBE frame for a session with the given attributes. */
    private Message<byte[]> subscribe(String destination, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setUser(new StompAuthInterceptor.StompPrincipal(
                (String) sessionAttrs.getOrDefault(JwtHandshakeInterceptor.ATTR_USERNAME, "someone")));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> staffSession(UUID merchantId, String role) {
        Map<String, Object> a = new HashMap<>();
        a.put(JwtHandshakeInterceptor.ATTR_USERNAME, "staff@example.com");
        a.put(JwtHandshakeInterceptor.ATTR_ROLE, role);
        a.put(JwtHandshakeInterceptor.ATTR_MERCHANT_ID, merchantId.toString());
        return a;
    }

    private Map<String, Object> guestSession(UUID orderId) {
        Map<String, Object> a = new HashMap<>();
        a.put(JwtHandshakeInterceptor.ATTR_USERNAME, "order:" + orderId);
        a.put(JwtHandshakeInterceptor.ATTR_ORDER_ID, orderId.toString());
        return a;
    }

    private String kitchen(UUID merchantId, long branchId) {
        return "/topic/merchant/" + merchantId + "/branch/" + branchId + "/kitchen";
    }

    // ---------- tenant isolation ----------

    @Test
    @DisplayName("staff may subscribe to their own merchant's kitchen")
    void ownTenantAllowed() {
        assertDoesNotThrow(() -> interceptor.preSend(
                subscribe(kitchen(MERCHANT_A, 7), staffSession(MERCHANT_A, "BRANCH_MANAGER")), null));
    }

    @Test
    @DisplayName("staff may NOT subscribe to another merchant's kitchen")
    void crossTenantDenied() {
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe(kitchen(MERCHANT_B, 7), staffSession(MERCHANT_A, "BRANCH_MANAGER")), null));
    }

    @Test
    @DisplayName("staff may NOT subscribe to another merchant's waiter channel")
    void crossTenantWaitersDenied() {
        String dest = "/topic/merchant/" + MERCHANT_B + "/branch/1/waiters";
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe(dest, staffSession(MERCHANT_A, "WAITER")), null));
    }

    @Test
    @DisplayName("a super admin may observe any tenant")
    void superAdminAllowedAnywhere() {
        Map<String, Object> a = new HashMap<>();
        a.put(JwtHandshakeInterceptor.ATTR_USERNAME, "root@example.com");
        a.put(JwtHandshakeInterceptor.ATTR_ROLE, "SUPER_ADMIN");
        assertDoesNotThrow(() -> interceptor.preSend(subscribe(kitchen(MERCHANT_B, 3), a), null));
    }

    // ---------- guest order scoping ----------

    @Test
    @DisplayName("a guest may subscribe to their own order")
    void guestOwnOrderAllowed() {
        assertDoesNotThrow(() -> interceptor.preSend(
                subscribe("/topic/orders/" + ORDER_1, guestSession(ORDER_1)), null));
    }

    @Test
    @DisplayName("a guest may NOT subscribe to somebody else's order")
    void guestOtherOrderDenied() {
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe("/topic/orders/" + ORDER_2, guestSession(ORDER_1)), null));
    }

    @Test
    @DisplayName("a guest may NOT reach a kitchen channel at all")
    void guestKitchenDenied() {
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe(kitchen(MERCHANT_A, 1), guestSession(ORDER_1)), null));
    }

    // ---------- deny by default ----------

    @Test
    @DisplayName("an unrecognised destination shape is denied, not permitted")
    void unknownDestinationDenied() {
        Map<String, Object> staff = staffSession(MERCHANT_A, "BRANCH_MANAGER");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe("/topic/admin/secrets", staff), null));
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe("/topic/merchant/not-a-uuid/branch/1/kitchen", staff), null));
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe("/topic/#", staff), null));
    }

    @Test
    @DisplayName("a wildcard cannot be smuggled into the merchant segment")
    void wildcardMerchantDenied() {
        Map<String, Object> staff = staffSession(MERCHANT_A, "WAITER");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe("/topic/merchant/*/branch/1/kitchen", staff), null));
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(
                subscribe(kitchen(MERCHANT_A, 1) + "/../" + MERCHANT_B, staff), null));
    }

    @Test
    @DisplayName("staff may follow an individual order")
    void staffMayFollowAnOrder() {
        assertDoesNotThrow(() -> interceptor.preSend(
                subscribe("/topic/orders/" + ORDER_1, staffSession(MERCHANT_A, "WAITER")), null));
    }

    // ---------- authentication still enforced ----------

    @Test
    @DisplayName("a CONNECT without a handshake identity is rejected")
    void connectWithoutIdentityRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(msg, null));
    }

    @Test
    @DisplayName("a SUBSCRIBE without a principal is rejected")
    void subscribeWithoutPrincipalRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(kitchen(MERCHANT_A, 1));
        accessor.setSessionAttributes(staffSession(MERCHANT_A, "WAITER"));
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(msg, null));
    }
}
