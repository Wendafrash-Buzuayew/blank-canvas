package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateWaiterRequest;
import com.qrserve.merchant.dto.UpdateWaiterRequest;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.service.WaiterService;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/waiters")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
@Tag(name = "Waiters", description = "Waiter Management & Shift Assignment APIs")
public class WaiterController {

    private final WaiterService waiterService;

    /**
     * Resolves the tenant for a request.
     *
     * <p>Only SUPER_ADMIN may target another merchant. Every other role is pinned
     * to the merchantId in its own token. Previously these endpoints accepted an
     * optional {@code merchantId} query parameter and passed it straight to the
     * service, so omitting it skipped the tenant check entirely.
     */
    private UUID resolveScope(UUID requested, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return requested;
        }
        UUID own = principal.getMerchantId();
        if (own == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant context is missing");
        }
        return own;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasAnyRole('MERCHANT_OWNER','BRANCH_MANAGER') "
            + "and #request.merchantId == authentication.principal.merchantId)")
    @Operation(summary = "Create a new waiter for a merchant branch")
    public ResponseEntity<WaiterEntity> createWaiter(@Valid @RequestBody CreateWaiterRequest request) {
        WaiterEntity waiter = WaiterEntity.builder()
                .merchantId(request.getMerchantId())
                .branchId(request.getBranchId())
                .userId(request.getUserId())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(waiterService.createWaiter(waiter));
    }

    /**
     * Lists waiters visible to the caller. Uses the authenticated principal rather
     * than re-parsing the Authorization header — the JWT has already been validated
     * by JwtAuthenticationFilter, so parsing it again here duplicated that logic
     * and bypassed the SecurityContext.
     */
    @GetMapping
    @Operation(summary = "List waiters for the authenticated user")
    public ResponseEntity<List<WaiterEntity>> getWaiters(
            @RequestParam(required = false) Long branchId,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return ResponseEntity.ok(branchId != null
                    ? waiterService.getWaitersByBranch(branchId)
                    : waiterService.getAllWaiters());
        }

        UUID merchantId = principal.getMerchantId();
        if (merchantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant context is missing");
        }

        return ResponseEntity.ok(branchId != null
                ? waiterService.getWaitersByMerchantAndBranch(merchantId, branchId)
                : waiterService.getWaitersByMerchant(merchantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get waiter by ID")
    public ResponseEntity<WaiterEntity> getWaiter(
            @PathVariable Long id,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(waiterService.getWaiter(id, resolveScope(merchantId, principal)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update waiter status / shift")
    public ResponseEntity<WaiterEntity> updateWaiter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWaiterRequest request,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        WaiterEntity updates = WaiterEntity.builder()
                .status(request.getStatus())
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(
                waiterService.updateWaiter(id, updates, resolveScope(merchantId, principal)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")
    @Operation(summary = "Deactivate a waiter (soft delete)")
    public ResponseEntity<Void> deleteWaiter(
            @PathVariable Long id,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        waiterService.deleteWaiter(id, resolveScope(merchantId, principal));
        return ResponseEntity.noContent().build();
    }
}
