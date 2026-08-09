package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.merchant.repository.WaiterRepository;
import com.qrserve.merchant.service.TableAssignmentService;
import com.qrserve.shared.common.dto.WaiterAssignmentDto;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Versioned waiter-assignment endpoint required by the Phase 2 contract:
 * {@code POST /api/v1/tables/{tableId}/assign-waiter}.
 *
 * <p>Every lookup is scoped by {@code merchantId} and {@code branchId}
 * derived from the table record itself, so a caller cannot assign a
 * waiter across tenant boundaries.</p>
 */
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@Tag(name = "Table Assignments (v1)", description = "Assign waiters to tables")
public class TableAssignmentV1Controller {

    private final TableAssignmentService assignmentService;
    private final TableRepository tableRepository;
    private final WaiterRepository waiterRepository;

    @PostMapping("/{tableId}/assign-waiter")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Assign a waiter to a table")
    public ResponseEntity<WaiterAssignmentDto> assignWaiter(
            @PathVariable Long tableId,
            @Valid @RequestBody WaiterAssignmentDto request) {

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found ID: " + tableId));

        WaiterEntity waiter = waiterRepository.findById(request.getWaiterId())
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found ID: " + request.getWaiterId()));

        // Tenant isolation: waiter and table must belong to the same merchant + branch
        if (!waiter.getMerchantId().equals(table.getMerchantId())
                || !waiter.getBranchId().equals(table.getBranchId())) {
            throw new ResourceNotFoundException("Waiter not found ID: " + request.getWaiterId());
        }

        TableAssignmentEntity assignment = assignmentService.assignWaiterToTable(
                table.getMerchantId(),
                table.getBranchId(),
                table.getId(),
                waiter.getId(),
                request.getShift());

        return ResponseEntity.ok(WaiterAssignmentDto.builder()
                .assignmentId(assignment.getId())
                .merchantId(assignment.getMerchantId())
                .branchId(assignment.getBranchId())
                .tableId(assignment.getTableId())
                .tableNumber(table.getTableNumber())
                .waiterId(assignment.getWaiterId())
                .userId(waiter.getUserId())
                .shift(assignment.getShift())
                .status(assignment.getStatus())
                .assignedAt(assignment.getAssignedAt())
                .endedAt(assignment.getEndedAt())
                .build());
    }
}
