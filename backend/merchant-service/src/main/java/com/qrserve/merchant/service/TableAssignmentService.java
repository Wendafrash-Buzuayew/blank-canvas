package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.TableAssignmentRepository;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableAssignmentService {

    private final TableAssignmentRepository assignmentRepository;
    private final WaiterRepository waiterRepository;
    private final TableRepository tableRepository;

    /**
     * Assign a waiter to a table.
     * Ends any existing active assignment for the table.
     */
    @Transactional
    public TableAssignmentEntity assignWaiterToTable(
            UUID merchantId, Long branchId, Long tableId, Long waiterId, String shift) {

        // Validate waiter belongs to the merchant/branch (tenant isolation)
        WaiterEntity waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found ID: " + waiterId));
        if (!waiter.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("Waiter not found ID: " + waiterId);
        }

        // Validate table belongs to the merchant
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + tableId));
        if (!table.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("Table not found ID: " + tableId);
        }

        // End any existing active assignment for this table
        assignmentRepository.findByTableIdAndStatus(tableId, "ACTIVE")
                .ifPresent(existing -> {
                    existing.setStatus("ENDED");
                    existing.setEndedAt(LocalDateTime.now());
                    assignmentRepository.save(existing);
                });

        TableAssignmentEntity assignment = TableAssignmentEntity.builder()
                .merchantId(merchantId)
                .branchId(branchId)
                .tableId(tableId)
                .waiterId(waiterId)
                .assignedAt(LocalDateTime.now())
                .status("ACTIVE")
                .shift(shift)
                .build();

        return assignmentRepository.save(assignment);
    }

    /**
     * Get the current active assignment for a table.
     */
    @Transactional(readOnly = true)
    public TableAssignmentEntity getActiveAssignmentForTable(Long tableId) {
        return assignmentRepository.findByTableIdAndStatus(tableId, "ACTIVE")
                .orElse(null);
    }

    /**
     * Get all assignments for a table (history).
     */
    @Transactional(readOnly = true)
    public List<TableAssignmentEntity> getAssignmentsForTable(Long tableId) {
        return assignmentRepository.findByTableId(tableId);
    }

    /**
     * Get all active assignments for a waiter.
     */
    @Transactional(readOnly = true)
    public List<TableAssignmentEntity> getActiveAssignmentsForWaiter(Long waiterId) {
        return assignmentRepository.findByWaiterIdAndStatus(waiterId, "ACTIVE");
    }

    /**
     * Get all assignments for a merchant (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<TableAssignmentEntity> getAssignmentsByMerchant(UUID merchantId) {
        return assignmentRepository.findByMerchantId(merchantId);
    }

    /**
     * End an assignment (e.g., shift change or reassignment).
     */
    @Transactional
    public TableAssignmentEntity endAssignment(Long assignmentId, UUID merchantId) {
        TableAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found ID: " + assignmentId));
        // Tenant isolation
        if (!assignment.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("Assignment not found ID: " + assignmentId);
        }
        assignment.setStatus("ENDED");
        assignment.setEndedAt(LocalDateTime.now());
        return assignmentRepository.save(assignment);
    }
}