package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateCustomerRequestDto;
import com.qrserve.merchant.dto.UpdateRequestStatusDto;
import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.service.CustomerRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-requests")
@RequiredArgsConstructor
@Tag(name = "Customer Requests", description = "Real-time Customer Service Requests (Call Waiter, Water, Bill, Assistance)")
public class CustomerRequestController {

    private final CustomerRequestService requestService;

    // Intentionally public: a seated customer has no account. The signed
    // variant (PublicCustomerRequestController, /api/v1/tables/{id}/requests)
    // is the tamper-resistant path and should be preferred by clients.
    @PostMapping
    @Operation(summary = "Create a new customer request (CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE)")
    public ResponseEntity<CustomerRequestEntity> createRequest(@Valid @RequestBody CreateCustomerRequestDto request) {
        CustomerRequestEntity entity = CustomerRequestEntity.builder()
                .merchantId(request.getMerchantId())
                .branchId(request.getBranchId())
                .tableId(request.getTableId())
                .requestType(request.getRequestType().toUpperCase())
                .status("PENDING")
                .note(request.getNote())
                .build();
        return ResponseEntity.ok(requestService.createRequest(entity));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER')")
    @Operation(summary = "List customer requests (filter by merchantId, branchId, or status)")
    public ResponseEntity<List<CustomerRequestEntity>> getRequests(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Only SUPER_ADMIN may choose the tenant. Everyone else is pinned to their
        // own merchantId regardless of the query param, which previously let any
        // authenticated user read another merchant's requests.
        UUID scope = principal.getRole() == UserRole.SUPER_ADMIN
                ? merchantId
                : principal.getMerchantId();

        if (branchId != null && "PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(requestService.getPendingRequestsByBranch(branchId));
        }
        if (branchId != null) {
            return ResponseEntity.ok(requestService.getRequestsByBranch(branchId));
        }
        return ResponseEntity.ok(requestService.getRequestsByMerchant(scope));
    }

    // Public: the customer polls their own table's request status.
    @GetMapping("/table/{tableId}")
    @Operation(summary = "Get all requests for a specific table")
    public ResponseEntity<List<CustomerRequestEntity>> getByTable(@PathVariable Long tableId) {
        return ResponseEntity.ok(requestService.getRequestsByTable(tableId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER')")
    @Operation(summary = "Update request status (PENDING -> ACKNOWLEDGED -> COMPLETED/CANCELLED)")
    public ResponseEntity<CustomerRequestEntity> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestStatusDto request,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // The tenant is derived from the token. The merchantId param is retained
        // for SUPER_ADMIN only; previously any caller could omit it entirely and
        // skip the tenant check in CustomerRequestService.
        UUID scope = principal.getRole() == UserRole.SUPER_ADMIN && merchantId != null
                ? merchantId
                : principal.getMerchantId();
        return ResponseEntity.ok(requestService.updateRequestStatus(id, request.getStatus(), scope));
    }
}