package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateWaiterRequest;
import com.qrserve.merchant.dto.UpdateWaiterRequest;
import com.qrserve.merchant.entity.WaiterEntity;
import com.qrserve.merchant.service.WaiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/waiters")
@RequiredArgsConstructor
@Tag(name = "Waiters", description = "Waiter Management & Shift Assignment APIs")
public class WaiterController {

    private final WaiterService waiterService;

    @PostMapping
    @Operation(summary = "Create a new waiter for a merchant branch")
    public ResponseEntity<WaiterEntity> createWaiter(@Valid @RequestBody CreateWaiterRequest request) {
        WaiterEntity waiter = WaiterEntity.builder()
                .merchantId(request.getMerchantId())
                .branchId(request.getBranchId())
                .userId(request.getUserId())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(waiterService.createWaiter(waiter));
    }

    @GetMapping
    @Operation(summary = "List all waiters (filter by merchantId or branchId)")
    public ResponseEntity<List<WaiterEntity>> getWaiters(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) Long branchId) {
        if (branchId != null) {
            return ResponseEntity.ok(waiterService.getWaitersByBranch(branchId));
        }
        return ResponseEntity.ok(waiterService.getWaitersByMerchant(merchantId != null ? merchantId : UUID.fromString("00000000-0000-0000-0000-000000000000")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get waiter by ID")
    public ResponseEntity<WaiterEntity> getWaiter(@PathVariable Long id, @RequestParam(required = false) UUID merchantId) {
        return ResponseEntity.ok(waiterService.getWaiter(id, merchantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update waiter status / shift")
    public ResponseEntity<WaiterEntity> updateWaiter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWaiterRequest request,
            @RequestParam(required = false) UUID merchantId) {
        WaiterEntity updates = WaiterEntity.builder()
                .status(request.getStatus())
                .shift(request.getShift())
                .build();
        return ResponseEntity.ok(waiterService.updateWaiter(id, updates, merchantId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a waiter (soft delete)")
    public ResponseEntity<Void> deleteWaiter(@PathVariable Long id, @RequestParam(required = false) UUID merchantId) {
        waiterService.deleteWaiter(id, merchantId);
        return ResponseEntity.noContent().build();
    }
}