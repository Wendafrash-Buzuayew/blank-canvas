package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Table Management & QR URL Provisioning APIs")
public class TableController {

    private final TableService tableService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "Create a table and auto-generate QR menu URL")
    public ResponseEntity<CreateTableResponse> createTable(@Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.ok(tableService.createTable(request));
    }

    /**
     * Returns every table across every merchant, so it must never be public.
     * Note SecurityConfig's "/api/tables/*" public GET rule also matches "/all";
     * an explicit authenticated rule sits above it, and this annotation is the
     * second layer.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "List tables for a merchant (inter-service for analytics)")
    public ResponseEntity<List<TableEntity>> getAllTables(
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Only SUPER_ADMIN may list across tenants; everyone else is pinned to their
        // own merchantId regardless of what the query parameter says.
        UUID scope = principal != null && principal.getRole() == UserRole.SUPER_ADMIN
                ? merchantId
                : (principal != null ? principal.getMerchantId() : null);
        if (scope == null && (principal == null || principal.getRole() != UserRole.SUPER_ADMIN)) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return ResponseEntity.ok(tableService.getAllTables(scope));
    }

    // Intentionally NOT role-restricted: a customer scanning a QR resolves their
    // table without logging in (SecurityConfig permits GET /api/tables/{id}).
    @GetMapping("/{id}")
    @Operation(summary = "Get table details by ID")
    public ResponseEntity<TableEntity> getTable(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.getTable(id));
    }

    /**
     * Called by order-service as well as staff clients. It already required a JWT
     * before this change (it falls through to anyRequest().authenticated()), so
     * restricting the roles does not newly break anonymous order placement —
     * that path already fails and is swallowed by OrderService.updateTableStatus.
     * Proper inter-service identity is tracked as deferred work.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER','WAITER','KITCHEN','CASHIER')")
    @Operation(summary = "Update table status (inter-service)")
    public ResponseEntity<TableEntity> updateTableStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "AVAILABLE");
        return ResponseEntity.ok(tableService.updateTableStatus(id, status));
    }
}