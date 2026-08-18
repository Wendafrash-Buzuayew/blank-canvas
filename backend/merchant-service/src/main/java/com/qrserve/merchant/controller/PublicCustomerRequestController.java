package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.service.CustomerRequestService;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TenantContext;
import com.qrserve.shared.common.dto.CustomerRequestDto;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public (no-JWT) endpoint for customers to create service requests
 * (Call Waiter, Request Water, Request Bill) from their table.
 *
 * <p>Exposed under {@code /api/v1/**} so the API gateway routes it without
 * requiring a full user JWT session. The table lookup is scoped by the
 * table's {@code merchantId} and {@code branchId} to enforce tenant isolation.
 * The QR signature is validated to prove the request originated from a
 * legitimate QR scan.</p>
 */
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@Tag(name = "Public Customer Requests", description = "Unauthenticated customer service requests from tables")
public class PublicCustomerRequestController {

    private final CustomerRequestService requestService;
    private final TableRepository tableRepository;
    private final QrSignatureService qrSignatureService;

    @PostMapping("/{tableId}/requests")
    @Operation(summary = "Create a customer service request (CALL_WAITER, REQUEST_WATER, REQUEST_BILL)")
    public ResponseEntity<CustomerRequestEntity> createRequest(
            @PathVariable Long tableId,
            @Valid @RequestBody CustomerRequestDto request,
            @RequestParam(required = false) String signature) {

        // 1. Resolve the table and scope lookups by merchantId/branchId
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + tableId));

        // 1b. A guest on one tenant's host must not be able to fire service calls
        //     at another tenant's tables. Table ids are sequential and therefore
        //     trivially enumerable, so without this the only thing between a bored
        //     guest and every kitchen on the platform is the signature check below,
        //     which is optional.
        UUID hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(table.getMerchantId())) {
            throw new AccessDeniedException("This table belongs to a different tenant");
        }

        // 2. Validate QR signature over {merchantId, branchId, tableId} IF provided.
        //    Signature is optional in demo/preview mode so customers can place
        //    service requests without a scanned QR payload. When a signature IS
        //    supplied it must be valid (tamper protection for production QR scans).
        if (signature != null && !signature.isBlank()
                && !qrSignatureService.validateSignature(signature, table.getMerchantId(), table.getBranchId(), table.getId())) {
            throw new UnauthorizedException("Invalid QR signature for the requested table");
        }

        // 3. Persist the request with tenant scoping
        CustomerRequestEntity entity = CustomerRequestEntity.builder()
                .merchantId(table.getMerchantId())
                .branchId(table.getBranchId())
                .tableId(table.getId())
                .requestType(request.getRequestType().name())
                .status("PENDING")
                .note(request.getNote())
                .build();

        return ResponseEntity.ok(requestService.createRequest(entity));
    }
}