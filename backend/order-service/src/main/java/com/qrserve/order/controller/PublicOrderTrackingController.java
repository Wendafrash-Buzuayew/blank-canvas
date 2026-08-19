package com.qrserve.order.controller;

import com.qrserve.order.dto.OrderTrackingResponse;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.service.OrderService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lets a guest read the current status of their own order.
 *
 * <h2>Why this exists</h2>
 * The customer tracker was push-only: status arrived on
 * {@code /topic/orders/{id}} and nowhere else. The broker does not replay, so any
 * transition published before the guest's subscription was live — or during a
 * signal blip on a phone — was lost for good and the tracker sat on "Received"
 * while the kitchen had moved on. A browser refresh was worse: there was no
 * endpoint a guest could read, so the order became unfollowable.
 *
 * <h2>Why not GET /api/orders/{id}</h2>
 * That endpoint is staff-scoped ({@code @PreAuthorize}) and must stay that way: it
 * returns the whole entity. A guest has no account, so the only credential they
 * hold is the order-stream token from {@code POST /api/orders}.
 *
 * <h2>Why the token is not an Authorization header</h2>
 * {@code JwtAuthenticationFilter} deliberately refuses to authenticate an
 * ORDER_STREAM token — one guest token must never establish a SecurityContext for
 * the rest of the API. So it travels in its own header and is checked here, for
 * this one order, and grants nothing else.
 */
@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
@Tag(name = "Guest Order Tracking", description = "Status of one order, readable with that order's stream token")
public class PublicOrderTrackingController {

    /** Carries the order-stream token. Not {@code Authorization} — see the class note. */
    public static final String ORDER_TOKEN_HEADER = "X-Order-Token";

    /**
     * Duplicated from {@code TenantContextFilter.TENANT_ID_HEADER} to read it
     * directly. The filter cannot enforce this comparison: it knows the host's
     * tenant but not which merchant the requested order belongs to.
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final OrderService orderService;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/{orderId}")
    @Operation(summary = "Read the current status of one order using its stream token")
    public ResponseEntity<OrderTrackingResponse> track(
            @PathVariable UUID orderId,
            @RequestHeader(value = ORDER_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String hostTenant) {

        // Authorize BEFORE touching the database, so a caller without a matching
        // token cannot tell an existing order from a missing one.
        requireTokenFor(orderId, token);

        OrderEntity order = orderService.getOrder(orderId);
        requireTenantMatch(order, hostTenant);

        return ResponseEntity.ok(OrderTrackingResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build());
    }

    /**
     * The token must be a valid ORDER_STREAM token naming exactly this order.
     *
     * <p>Every failure is the same 401 with the same message: which of the three
     * checks failed is not the caller's business, and distinguishing them would
     * turn this into an order-existence oracle.
     */
    private void requireTokenFor(UUID orderId, String token) {
        if (token == null || token.isBlank()
                || !tokenProvider.validateToken(token)
                || !tokenProvider.isOrderStreamToken(token)
                || !orderId.equals(tokenProvider.getOrderIdFromToken(token))) {
            throw new UnauthorizedException("A valid order token is required to track this order");
        }
    }

    /**
     * When the request arrived through a tenant host, that tenant must own the
     * order. Without this, a token for an order at merchant A would still read
     * through {@code merchant-b.qrserve.safaricom.et} — harmless for the data
     * returned, but it would put one tenant's order number on another tenant's
     * page. Absence of the header (localhost, direct service access) is not an
     * error; the token alone is then the authority.
     */
    private void requireTenantMatch(OrderEntity order, String hostTenant) {
        if (hostTenant == null || hostTenant.isBlank()) {
            return;
        }
        UUID asserted;
        try {
            asserted = UUID.fromString(hostTenant.trim());
        } catch (IllegalArgumentException e) {
            // Only the gateway sets this header, so a malformed value is a
            // misconfiguration. Matching TenantContextFilter, treat it as absent
            // rather than failing a guest's request.
            return;
        }
        if (!asserted.equals(order.getMerchantId())) {
            throw new UnauthorizedException("A valid order token is required to track this order");
        }
    }
}
