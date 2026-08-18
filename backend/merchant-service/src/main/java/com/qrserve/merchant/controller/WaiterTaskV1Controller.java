package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.merchant.service.CustomerRequestService;
import com.qrserve.merchant.service.TableAssignmentService;
import com.qrserve.shared.common.dto.ResolveRequestDto;
import com.qrserve.shared.common.dto.WaiterAssignmentDto;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Versioned waiter task endpoints required by the Phase 2 contract:
 * <ul>
 *   <li>{@code GET /api/v1/waiters/tasks}</li>
 *   <li>{@code PATCH /api/v1/requests/{requestId}/resolve}</li>
 * </ul>
 *
 * <p>All queries are scoped by {@code merchantId} and {@code branchId}.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Waiter Tasks (v1)", description = "Active waiter tasks and customer request resolution")
public class WaiterTaskV1Controller {

    private final TableAssignmentService assignmentService;
    private final CustomerRequestService requestService;
    private final WaiterRepository waiterRepository;
    private final TableRepository tableRepository;

    @GetMapping("/waiters/tasks")
    // KITCHEN included deliberately: KitchenLivePage renders the pending customer
    // calls from this payload, so excluding the role broke the kitchen display with
    // "Access Denied". A KITCHEN user has no WaiterEntity, which resolveWaiter
    // handles by returning null — they get an empty assignment list and the
    // branch's pending requests, which is exactly what that screen shows.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN')")
    @Operation(summary = "Active tasks for a waiter: assigned tables and pending customer requests")
    public ResponseEntity<WaiterTasksResponse> getTasks(
            @RequestParam UUID merchantId,
            @RequestParam Long branchId,
            @RequestParam(required = false) Long waiterId,
            @RequestParam(required = false) UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireOwnTenant(merchantId, principal);

        WaiterEntity waiter = resolveWaiter(merchantId, branchId, waiterId, userId);

        List<WaiterAssignmentDto> assignments = new ArrayList<>();
        if (waiter != null) {
            for (TableAssignmentEntity a : assignmentService.getActiveAssignmentsForWaiter(waiter.getId())) {
                // Tenant isolation
                if (!merchantId.equals(a.getMerchantId()) || !branchId.equals(a.getBranchId())) {
                    continue;
                }
                String tableNumber = tableRepository.findById(a.getTableId())
                        .map(TableEntity::getTableNumber)
                        .orElse(null);
                assignments.add(WaiterAssignmentDto.builder()
                        .assignmentId(a.getId())
                        .merchantId(a.getMerchantId())
                        .branchId(a.getBranchId())
                        .tableId(a.getTableId())
                        .tableNumber(tableNumber)
                        .waiterId(a.getWaiterId())
                        .userId(waiter.getUserId())
                        .shift(a.getShift())
                        .status(a.getStatus())
                        .assignedAt(a.getAssignedAt())
                        .endedAt(a.getEndedAt())
                        .build());
            }
        }

        List<CustomerRequestEntity> pending = requestService.getPendingRequestsByBranch(branchId)
                .stream()
                .filter(r -> merchantId.equals(r.getMerchantId()))
                .toList();

        return ResponseEntity.ok(WaiterTasksResponse.builder()
                .waiterId(waiter != null ? waiter.getId() : null)
                .merchantId(merchantId)
                .branchId(branchId)
                .assignedTables(assignments)
                .pendingRequests(pending)
                .build());
    }

    @PatchMapping("/requests/{requestId}/resolve")
    // Same reason: the kitchen display offers a Done button on these requests.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN')")
    @Operation(summary = "Acknowledge or complete a customer service request")
    public ResponseEntity<CustomerRequestEntity> resolveRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ResolveRequestDto body,
            @RequestParam(required = false) UUID merchantId) {

        String status = body.getStatus() != null ? body.getStatus().toUpperCase() : "COMPLETED";
        if (!List.of("ACKNOWLEDGED", "COMPLETED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid resolution status: " + status);
        }
        return ResponseEntity.ok(requestService.updateRequestStatus(requestId, status, merchantId));
    }

    /**
     * Rejects a merchantId that is not the caller's own.
     *
     * <p>The role check alone was not enough: merchantId is a query parameter, so
     * any permitted role could read another merchant's pending customer requests
     * simply by supplying that merchant's id.
     */
    private void requireOwnTenant(UUID merchantId, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (!merchantId.equals(principal.getMerchantId())) {
            throw new UnauthorizedException("Not authorized for merchant " + merchantId);
        }
    }

    private WaiterEntity resolveWaiter(UUID merchantId, Long branchId, Long waiterId, UUID userId) {
        WaiterEntity waiter = null;
        if (waiterId != null) {
            waiter = waiterRepository.findById(waiterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Waiter not found ID: " + waiterId));
        } else if (userId != null) {
            waiter = waiterRepository.findByUserId(userId).orElse(null);
        }
        if (waiter != null
                && (!merchantId.equals(waiter.getMerchantId()) || !branchId.equals(waiter.getBranchId()))) {
            throw new ResourceNotFoundException("Waiter not found for this merchant/branch");
        }
        return waiter;
    }

    /** Aggregated task payload for the waiter dashboard. */
    @Data
    @Builder
    public static class WaiterTasksResponse {
        private Long waiterId;
        private UUID merchantId;
        private Long branchId;
        private List<WaiterAssignmentDto> assignedTables;
        private List<CustomerRequestEntity> pendingRequests;
    }
}
