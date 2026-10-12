package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.service.CustomerRequestService;
import com.qrserve.shared.common.dto.CustomerRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-JWT) endpoint for customers to create service requests
 * (Call Waiter, Request Water, Request Bill) from their table.
 *
 * <p>Exposed under {@code /api/v1/**} so the API gateway routes it without
 * requiring a full user JWT session. The QR signature over
 * {merchantId, branchId, tableId} is REQUIRED and the tenant is taken from the
 * table row (see {@link CustomerRequestService#createGuestRequest}).</p>
 */
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@Tag(name = "Public Customer Requests", description = "Unauthenticated customer service requests from tables")
public class PublicCustomerRequestController {

    private final CustomerRequestService requestService;

    @PostMapping("/{tableId}/requests")
    @Operation(summary = "Create a customer service request (CALL_WAITER, REQUEST_WATER, REQUEST_BILL)")
    public ResponseEntity<CustomerRequestEntity> createRequest(
            @PathVariable Long tableId,
            @Valid @RequestBody CustomerRequestDto request,
            @RequestParam(required = false) String signature) {
        // required = false so a missing signature reaches the service and gets the
        // same 403 as a wrong one, rather than a framework 400.
        return ResponseEntity.ok(requestService.createGuestRequest(
                tableId, signature, request.getRequestType().name(), request.getNote()));
    }
}
