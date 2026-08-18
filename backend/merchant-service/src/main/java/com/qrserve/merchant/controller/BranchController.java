package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Restaurant & Hotel Branch Management APIs")
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    // The merchantId is caller-supplied in the body, so the tenant must be
    // checked here — otherwise an owner can create a branch under any merchant.
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('MERCHANT_OWNER') "
            + "and #request.merchantId == authentication.principal.merchantId)")
    @Operation(summary = "Create a new branch for a merchant")
    public ResponseEntity<BranchEntity> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.ok(branchService.createBranch(request));
    }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or #merchantId == authentication.principal.merchantId")
    @Operation(summary = "Get all branches for a merchant")
    public ResponseEntity<List<BranchEntity>> getBranchesByMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(branchService.getBranchesByMerchant(merchantId));
    }
}
