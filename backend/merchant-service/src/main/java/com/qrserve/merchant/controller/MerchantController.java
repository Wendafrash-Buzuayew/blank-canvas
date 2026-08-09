package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateMerchantRequest;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant Tenant Registration & Management APIs")
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @Operation(summary = "Create a new merchant tenant account")
    public ResponseEntity<MerchantEntity> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.ok(merchantService.createMerchant(request));
    }

    @GetMapping
    @Operation(summary = "List all merchants (SUPER_ADMIN only)")
    public ResponseEntity<List<MerchantEntity>> getAllMerchants() {
        return ResponseEntity.ok(merchantService.getAllMerchants());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<MerchantEntity> getMerchant(@PathVariable UUID id) {
        return ResponseEntity.ok(merchantService.getMerchant(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update merchant business profile")
    public ResponseEntity<MerchantEntity> updateMerchant(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.ok(merchantService.updateMerchant(id, request));
    }
}
