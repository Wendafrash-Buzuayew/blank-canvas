package com.qrserve.merchant.controller;

import com.qrserve.merchant.dto.CreateReviewRequest;
import com.qrserve.merchant.dto.ReviewSummaryResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.ReviewEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.service.ReviewService;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TenantContext;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-JWT) endpoint for a customer to leave a rating/comment about a
 * branch visit — the phase-1 HLD "customer feedback" feature. Visit-scoped
 * (merchantId + branchId derived from the branch, not order-scoped): phase
 * 1's digital menu has no cart/order, so this cannot depend on the parked
 * ordering system. Mirrors PublicCustomerRequestController's anti-abuse
 * shape: tenant scoping is mandatory, the QR signature is optional (same
 * "dm:" 2-arg scheme the digital-menu QR code itself already encodes).
 */
@RestController
@RequestMapping("/api/v1/public/branches")
@RequiredArgsConstructor
@Tag(name = "Public Reviews", description = "Unauthenticated customer feedback about a branch visit")
public class PublicReviewController {

    private final ReviewService reviewService;
    private final BranchRepository branchRepository;
    private final QrSignatureService qrSignatureService;

    @PostMapping("/{branchId}/reviews")
    @Operation(summary = "Leave a rating/comment about a branch visit")
    public ResponseEntity<ReviewEntity> createReview(
            @PathVariable Long branchId,
            @Valid @RequestBody CreateReviewRequest request,
            @RequestParam(required = false) String signature) {

        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found ID: " + branchId));

        var hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(branch.getMerchantId())) {
            throw new AccessDeniedException("This branch belongs to a different tenant");
        }

        if (signature != null && !signature.isBlank()
                && !qrSignatureService.validateSignature(signature, branch.getMerchantId(), branch.getId())) {
            throw new UnauthorizedException("Invalid QR signature for the requested branch");
        }

        return ResponseEntity.ok(reviewService.createReview(branchId, request));
    }

    @GetMapping("/{branchId}/reviews/summary")
    @Operation(summary = "Get a branch's average rating and review count")
    public ResponseEntity<ReviewSummaryResponse> getSummary(@PathVariable Long branchId) {
        return ResponseEntity.ok(reviewService.getSummaryForBranch(branchId));
    }
}
