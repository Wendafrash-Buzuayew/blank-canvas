package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateCustomerRequestDto;
import com.qrserve.merchant.dto.UpdateRequestStatusDto;
import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.service.CustomerRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-requests")
@RequiredArgsConstructor
@Tag(name = "Customer Requests", description = "Real-time Customer Service Requests (Call Waiter, Water, Bill, Assistance)")
public class CustomerRequestController {

    private final CustomerRequestService requestService;

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
    @Operation(summary = "List customer requests (filter by merchantId, branchId, or status)")
    public ResponseEntity<List<CustomerRequestEntity>> getRequests(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status) {
        if (branchId != null && "PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(requestService.getPendingRequestsByBranch(branchId));
        }
        if (branchId != null) {
            return ResponseEntity.ok(requestService.getRequestsByBranch(branchId));
        }
        return ResponseEntity.ok(requestService.getRequestsByMerchant(merchantId));
    }

    @GetMapping("/table/{tableId}")
    @Operation(summary = "Get all requests for a specific table")
    public ResponseEntity<List<CustomerRequestEntity>> getByTable(@PathVariable Long tableId) {
        return ResponseEntity.ok(requestService.getRequestsByTable(tableId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update request status (PENDING -> ACKNOWLEDGED -> COMPLETED/CANCELLED)")
    public ResponseEntity<CustomerRequestEntity> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestStatusDto request,
            @RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(requestService.updateRequestStatus(id, request.getStatus(), merchantId));
    }
}