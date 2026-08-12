package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaiterService {

    private final WaiterRepository waiterRepository;

    /**
     * Create a new waiter for a merchant + branch.
     * Tenant isolation: waiter must be created under the provided merchantId.
     */
    @Transactional
    public WaiterEntity createWaiter(WaiterEntity waiter) {
        // Enforce tenant: always set merchantId from the request payload
        if (waiter.getMerchantId() == null || waiter.getBranchId() == null) {
            throw new IllegalArgumentException("merchantId and branchId are required");
        }
        return waiterRepository.save(waiter);
    }

    /**
     * Get all waiters for a merchant (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<WaiterEntity> getWaitersByMerchant(UUID merchantId) {
        return waiterRepository.findByMerchantId(merchantId);
    }

    /**
     * Get all waiters for a branch (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<WaiterEntity> getWaitersByBranch(Long branchId) {
        return waiterRepository.findByBranchId(branchId);
    }

    /**
     * Get all waiters for a merchant and branch (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<WaiterEntity> getWaitersByMerchantAndBranch(
            UUID merchantId,
            Long branchId) {

        return waiterRepository.findByMerchantIdAndBranchId(
                merchantId,
                branchId);
    }

    @Transactional(readOnly = true)
    public List<WaiterEntity> getAllWaiters() {
        return waiterRepository.findAll();
    }

    /**
     * Get a specific waiter by ID, within tenant scope.
     */
    @Transactional(readOnly = true)
    public WaiterEntity getWaiter(Long id, UUID merchantId) {
        WaiterEntity waiter = waiterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found ID: " + id));
        // Tenant isolation: check merchant ownership
        if (merchantId != null && !merchantId.equals(waiter.getMerchantId())) {
            throw new ResourceNotFoundException("Waiter not found ID: " + id);
        }
        return waiter;
    }

    /**
     * Update a waiter's status / shift.
     */
    @Transactional
    public WaiterEntity updateWaiter(Long id, WaiterEntity updates, UUID merchantId) {
        WaiterEntity waiter = getWaiter(id, merchantId);
        if (updates.getStatus() != null)
            waiter.setStatus(updates.getStatus());
        if (updates.getShift() != null)
            waiter.setShift(updates.getShift());
        return waiterRepository.save(waiter);
    }

    /**
     * Delete a waiter (soft-delete by marking INACTIVE).
     */
    @Transactional
    public void deleteWaiter(Long id, UUID merchantId) {
        WaiterEntity waiter = getWaiter(id, merchantId);
        waiter.setStatus("INACTIVE");
        waiterRepository.save(waiter);
    }
}