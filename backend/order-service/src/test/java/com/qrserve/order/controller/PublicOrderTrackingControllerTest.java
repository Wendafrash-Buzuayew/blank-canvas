package com.qrserve.order.controller;

import com.qrserve.order.dto.OrderTrackingResponse;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.service.OrderService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guest tracking endpoint is reachable without any account, so its whole
 * defence is the order-stream token. These tests use real signed tokens from a
 * real {@link JwtTokenProvider} rather than a mock, because the thing worth
 * testing is exactly what the provider accepts and rejects.
 */
class PublicOrderTrackingControllerTest {

    private static final UUID MERCHANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private JwtTokenProvider tokens;
    private OrderService orderService;
    private PublicOrderTrackingController controller;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        tokens = new JwtTokenProvider("a-test-secret-that-is-long-enough-for-hs256", 3_600_000L, 604_800_000L);
        orderService = mock(OrderService.class);
        controller = new PublicOrderTrackingController(orderService, tokens);
        orderId = UUID.randomUUID();
    }

    private OrderEntity order(UUID merchantId) {
        return OrderEntity.builder()
                .id(orderId)
                .orderNumber("ORD-1001")
                .merchantId(merchantId)
                .branchId(1L)
                .tableId(7L)
                .status("PREPARING")
                .totalAmount(new BigDecimal("420.00"))
                .build();
    }

    @Test
    @DisplayName("the order's own stream token reads its current status")
    void ownTokenReadsStatus() {
        when(orderService.getOrder(orderId)).thenReturn(order(MERCHANT_A));

        ResponseEntity<OrderTrackingResponse> response =
                controller.track(orderId, tokens.generateOrderStreamToken(orderId), null);

        assertNotNull(response.getBody());
        // The status the kitchen last set — the value a push-only tracker could
        // never recover after missing the event.
        assertEquals("PREPARING", response.getBody().getStatus());
        assertEquals("ORD-1001", response.getBody().getOrderNumber());
    }

    @Test
    @DisplayName("a token for a different order is rejected")
    void otherOrdersTokenRejected() {
        String otherToken = tokens.generateOrderStreamToken(UUID.randomUUID());

        assertThrows(UnauthorizedException.class, () -> controller.track(orderId, otherToken, null));
        // Rejected before the lookup: otherwise the response would say whether the
        // order exists, which is an enumeration oracle on a public endpoint.
        verify(orderService, never()).getOrder(any());
    }

    @Test
    @DisplayName("no token is rejected")
    void missingTokenRejected() {
        assertThrows(UnauthorizedException.class, () -> controller.track(orderId, null, null));
        assertThrows(UnauthorizedException.class, () -> controller.track(orderId, "  ", null));
        verify(orderService, never()).getOrder(any());
    }

    @Test
    @DisplayName("an access token is not a tracking token")
    void accessTokenRejected() {
        String access = tokens.generateAccessToken(UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .merchantId(MERCHANT_A)
                .email("waiter@example.com")
                .role(UserRole.WAITER)
                .build());

        assertThrows(UnauthorizedException.class, () -> controller.track(orderId, access, null));
    }

    @Test
    @DisplayName("garbage is rejected without throwing")
    void garbageTokenRejected() {
        assertThrows(UnauthorizedException.class, () -> controller.track(orderId, "not.a.jwt", null));
    }

    @Test
    @DisplayName("a tenant host that does not own the order is rejected")
    void foreignTenantHostRejected() {
        when(orderService.getOrder(orderId)).thenReturn(order(MERCHANT_A));
        String token = tokens.generateOrderStreamToken(orderId);

        assertThrows(UnauthorizedException.class,
                () -> controller.track(orderId, token, MERCHANT_B.toString()));
    }

    @Test
    @DisplayName("the owning tenant host is accepted")
    void owningTenantHostAccepted() {
        when(orderService.getOrder(orderId)).thenReturn(order(MERCHANT_A));
        String token = tokens.generateOrderStreamToken(orderId);

        ResponseEntity<OrderTrackingResponse> response =
                controller.track(orderId, token, MERCHANT_A.toString());

        assertEquals("PREPARING", response.getBody().getStatus());
    }

    @Test
    @DisplayName("a malformed tenant header is ignored, not fatal")
    void malformedTenantHeaderIgnored() {
        when(orderService.getOrder(orderId)).thenReturn(order(MERCHANT_A));
        String token = tokens.generateOrderStreamToken(orderId);

        // Only the gateway sets this header; a bad value is a misconfiguration and
        // must not strand a guest who holds a valid token.
        ResponseEntity<OrderTrackingResponse> response =
                controller.track(orderId, token, "not-a-uuid");

        assertEquals("PREPARING", response.getBody().getStatus());
    }
}
