package com.qrserve.order.controller;

import com.qrserve.order.dto.CreateOrderRequest;
import com.qrserve.order.dto.CreateOrderResponse;
import com.qrserve.order.dto.UpdateOrderStatusRequest;
import com.qrserve.order.entity.OrderEntity;
import com.qrserve.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "QR Menu Ordering & Status Tracking APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new customer table order")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "List orders for a merchant (inter-service for analytics)")
    public ResponseEntity<List<OrderEntity>> getAllOrders(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Only SUPER_ADMIN may list across tenants; everyone else is pinned to their
        // own merchantId regardless of what the query parameter says.
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UUID scope = principal.getRole() == UserRole.SUPER_ADMIN
                ? merchantId
                : principal.getMerchantId();
        if (scope == null && principal.getRole() != UserRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return ResponseEntity.ok(orderService.getAllOrders(scope));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderEntity> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Update order lifecycle status (ACCEPTED, PREPARING, READY, DELIVERED, PAID, CANCELLED)")
    public ResponseEntity<OrderEntity> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }
}