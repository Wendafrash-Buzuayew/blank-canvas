package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "Create a new branch for a merchant")
    public ResponseEntity<BranchEntity> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.ok(branchService.createBranch(request));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get all branches for a merchant")
    public ResponseEntity<List<BranchEntity>> getBranchesByMerchant(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(branchService.getBranchesByMerchant(merchantId));
    }
}
