package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.AssignTableRequest;
import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.service.TableAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/table-assignments")
@RequiredArgsConstructor
@Tag(name = "Table Assignments", description = "Waiter-to-Table Assignment & Shift Management APIs")
public class TableAssignmentController {

    private final TableAssignmentService assignmentService;

    @PostMapping
    @Operation(summary = "Assign a waiter to a table")
    public ResponseEntity<TableAssignmentEntity> assignTable(@Valid @RequestBody AssignTableRequest request) {
        TableAssignmentEntity assignment = assignmentService.assignWaiterToTable(
                request.getMerchantId(),
                request.getBranchId(),
                request.getTableId(),
                request.getWaiterId(),
                request.getShift());
        return ResponseEntity.ok(assignment);
    }

    @GetMapping("/table/{tableId}")
    @Operation(summary = "Get assignments for a table (including current active assignment)")
    public ResponseEntity<List<TableAssignmentEntity>> getByTable(@PathVariable Long tableId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsForTable(tableId));
    }

    @GetMapping("/table/{tableId}/active")
    @Operation(summary = "Get the current active waiter assignment for a table")
    public ResponseEntity<TableAssignmentEntity> getActiveByTable(@PathVariable Long tableId) {
        TableAssignmentEntity assignment = assignmentService.getActiveAssignmentForTable(tableId);
        return assignment != null ? ResponseEntity.ok(assignment) : ResponseEntity.noContent().build();
    }

    @GetMapping("/waiter/{waiterId}/active")
    @Operation(summary = "Get all active assignments for a waiter")
    public ResponseEntity<List<TableAssignmentEntity>> getActiveByWaiter(@PathVariable Long waiterId) {
        return ResponseEntity.ok(assignmentService.getActiveAssignmentsForWaiter(waiterId));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get all assignments for a merchant (tenant-scoped)")
    public ResponseEntity<List<TableAssignmentEntity>> getByMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByMerchant(merchantId));
    }

    @PutMapping("/{id}/end")
    @Operation(summary = "End an active assignment")
    public ResponseEntity<TableAssignmentEntity> endAssignment(@PathVariable Long id, @RequestParam UUID merchantId) {
        return ResponseEntity.ok(assignmentService.endAssignment(id, merchantId));
    }
}