package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.AssignTableRequest;
import com.qrserve.merchant.entity.TableAssignmentEntity;
import com.qrserve.merchant.service.TableAssignmentService;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/table-assignments")
@RequiredArgsConstructor
@Tag(name = "Table Assignments", description = "Waiter-to-Table Assignment & Shift Management APIs")
public class TableAssignmentController {

    private final TableAssignmentService assignmentService;

    @PostMapping
    // merchantId comes from the request body, so the tenant is checked here.
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasAnyRole('MERCHANT_OWNER','BRANCH_MANAGER') "
            + "and #request.merchantId == authentication.principal.merchantId)")
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

    // The lookups below are keyed by sequential table/waiter ids, so results are
    // filtered to the caller's tenant; previously any manager could read every
    // merchant's assignments by walking ids.

    @GetMapping("/table/{tableId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Get assignments for a table (including current active assignment)")
    public ResponseEntity<List<TableAssignmentEntity>> getByTable(
            @PathVariable Long tableId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(inScope(assignmentService.getAssignmentsForTable(tableId), principal));
    }

    @GetMapping("/table/{tableId}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Get the current active waiter assignment for a table")
    public ResponseEntity<TableAssignmentEntity> getActiveByTable(
            @PathVariable Long tableId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TableAssignmentEntity assignment = assignmentService.getActiveAssignmentForTable(tableId);
        return assignment != null && !inScope(List.of(assignment), principal).isEmpty()
                ? ResponseEntity.ok(assignment)
                : ResponseEntity.noContent().build();
    }

    @GetMapping("/waiter/{waiterId}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Get all active assignments for a waiter")
    public ResponseEntity<List<TableAssignmentEntity>> getActiveByWaiter(
            @PathVariable Long waiterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(inScope(assignmentService.getActiveAssignmentsForWaiter(waiterId), principal));
    }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or #merchantId == authentication.principal.merchantId")
    @Operation(summary = "Get all assignments for a merchant (tenant-scoped)")
    public ResponseEntity<List<TableAssignmentEntity>> getByMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByMerchant(merchantId));
    }

    @PutMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "End an active assignment")
    public ResponseEntity<TableAssignmentEntity> endAssignment(
            @PathVariable Long id,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Tenant from the token, as in CustomerRequestController.updateStatus; the
        // param is honoured for SUPER_ADMIN only.
        UUID scope = principal.getRole() == UserRole.SUPER_ADMIN && merchantId != null
                ? merchantId
                : principal.getMerchantId();
        if (scope == null) {
            throw new AccessDeniedException("Caller has no merchant scope");
        }
        return ResponseEntity.ok(assignmentService.endAssignment(id, scope));
    }

    private static List<TableAssignmentEntity> inScope(List<TableAssignmentEntity> rows, UserPrincipal principal) {
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return rows;
        }
        UUID own = principal.getMerchantId();
        return rows.stream().filter(a -> own != null && own.equals(a.getMerchantId())).toList();
    }
}
