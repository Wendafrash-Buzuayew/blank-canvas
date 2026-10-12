package com.qrserve.order.controller;

import com.qrserve.order.dto.KitchenOrderResponse;
import com.qrserve.order.service.KitchenService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@Tag(name = "Kitchen Operations", description = "Kitchen Display System (KDS) & Order Board APIs")
public class KitchenController {

    private final KitchenService kitchenService;

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','KITCHEN')")
    @Operation(summary = "Get active kitchen orders with filters for pending, preparing, ready, branch, and table")
    public ResponseEntity<List<KitchenOrderResponse>> getKitchenOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Only SUPER_ADMIN may read across tenants; everyone else is pinned to
        // their own merchantId, and branchId/tableId only narrow within it.
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UUID scope = principal.getRole() == UserRole.SUPER_ADMIN
                ? merchantId
                : principal.getMerchantId();
        if (scope == null && principal.getRole() != UserRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return ResponseEntity.ok(kitchenService.getKitchenOrders(status, branchId, tableId, scope));
    }
}
