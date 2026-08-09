package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.CustomerRequestEntity;
import com.qrserve.merchant.repository.CustomerRequestRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRequestService {

    private final CustomerRequestRepository requestRepository;

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
     * Get all requests for a merchant (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByMerchant(UUID merchantId) {
        return requestRepository.findByMerchantId(merchantId);
    }

    /**
     * Get all requests for a branch (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByBranch(Long branchId) {
        return requestRepository.findByBranchId(branchId);
    }

    /**
     * Get all requests for a specific table.
     */
    @Transactional(readOnly = true)
    public List<CustomerRequestEntity> getRequestsByTable(Long tableId) {
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
        // Tenant isolation
        if (merchantId != null && !merchantId.equals(request.getMerchantId())) {
            throw new ResourceNotFoundException("Customer request not found ID: " + requestId);
        }
        request.setStatus(status.toUpperCase());
        if ("COMPLETED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            request.setResolvedAt(LocalDateTime.now());
        }
        return requestRepository.save(request);
    }
}