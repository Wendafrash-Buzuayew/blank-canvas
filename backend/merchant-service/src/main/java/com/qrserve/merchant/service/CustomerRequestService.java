package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.CustomerRequestRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TenantContext;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRequestService {

    /** Allowed next statuses. COMPLETED and CANCELLED are terminal. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("ACKNOWLEDGED", "COMPLETED", "CANCELLED"),
            "ACKNOWLEDGED", Set.of("COMPLETED", "CANCELLED"));

    private final CustomerRequestRepository requestRepository;
    private final TableRepository tableRepository;
    private final QrSignatureService qrSignatureService;

    /**
     * Create a new customer request (CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE).
     */
    @Transactional
    public CustomerRequestEntity createRequest(CustomerRequestEntity request) {
        if (request.getMerchantId() == null || request.getBranchId() == null || request.getTableId() == null) {
            throw new IllegalArgumentException("merchantId, branchId, and tableId are required");
        }
        if (request.getRequestType() == null || request.getRequestType().isBlank()) {
            throw new IllegalArgumentException("requestType is required");
        }
        return requestRepository.save(request);
    }

    /**
     * The anonymous guest write path. The table's QR signature is REQUIRED and the
     * tenant (merchant + branch) is taken from the table row, never from the caller:
     * table ids are sequential, so an unsigned or caller-scoped request let anyone
     * fire service calls into any tenant's waiter dashboard.
     */
    @Transactional
    public CustomerRequestEntity createGuestRequest(Long tableId, String signature, String requestType, String note) {
        TableEntity table = requireSignedTable(tableId, signature);
        return createRequest(CustomerRequestEntity.builder()
                .merchantId(table.getMerchantId())
                .branchId(table.getBranchId())
                .tableId(table.getId())
                .requestType(requestType == null ? null : requestType.toUpperCase())
                .status("PENDING")
                .note(note)
                .build());
    }

    /**
     * Get all requests for a merchant (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByMerchant(UUID merchantId) {
        return requestRepository.findByMerchantId(merchantId);
    }

    /**
     * Get all requests for a branch. Callers must filter by tenant.
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByBranch(Long branchId) {
        return requestRepository.findByBranchId(branchId);
    }

    /**
     * A guest polling their own table's requests. Requires the table's QR
     * signature, same as the write path, so request history is not readable by
     * walking table ids anonymously.
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByTable(Long tableId, String signature) {
        requireSignedTable(tableId, signature);
        return requestRepository.findByTableId(tableId);
    }

    /**
     * Get pending requests for a branch (used by waiter dashboard).
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getPendingRequestsByBranch(Long branchId) {
        return requestRepository.findByBranchIdAndStatus(branchId, "PENDING");
    }

    /**
     * Update the status of a customer request (PENDING -> ACKNOWLEDGED -> COMPLETED/CANCELLED).
     */
    @Transactional
    public CustomerRequestEntity updateRequestStatus(Long requestId, String status, UUID merchantId) {
        CustomerRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer request not found ID: " + requestId));

        // Tenant isolation. The merchantId is REQUIRED: the previous
        // `merchantId != null &&` guard skipped the check entirely when the caller
        // omitted the parameter, letting any authenticated user update any
        // merchant's requests. Not-found (rather than forbidden) is returned so the
        // response does not confirm that another tenant's record exists.
        if (merchantId == null) {
            throw new AccessDeniedException("merchantId is required to update a customer request");
        }
        if (!merchantId.equals(request.getMerchantId())) {
            throw new ResourceNotFoundException("Customer request not found ID: " + requestId);
        }
        String next = status.toUpperCase();
        if (!TRANSITIONS.getOrDefault(request.getStatus(), Set.of()).contains(next)) {
            throw new BusinessException(
                    "Cannot move customer request " + requestId + " from " + request.getStatus() + " to " + next);
        }
        request.setStatus(next);
        if ("COMPLETED".equals(next) || "CANCELLED".equals(next)) {
            request.setResolvedAt(LocalDateTime.now());
        }
        return requestRepository.save(request);
    }

    private TableEntity requireSignedTable(Long tableId, String signature) {
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + tableId));

        // A guest on one tenant's host must not reach another tenant's tables.
        UUID hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(table.getMerchantId())) {
            throw new AccessDeniedException("This table belongs to a different tenant");
        }
        if (signature == null || signature.isBlank()
                || !qrSignatureService.validateSignature(
                        signature, table.getMerchantId(), table.getBranchId(), table.getId())) {
            throw new AccessDeniedException("A valid QR signature is required for this table");
        }
        return table;
    }
}
