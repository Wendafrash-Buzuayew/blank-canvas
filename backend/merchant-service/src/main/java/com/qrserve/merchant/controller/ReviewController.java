package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.ReviewEntity;
import com.qrserve.merchant.service.ReviewService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Staff-facing view of customer feedback, for the merchant dashboard. */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Staff-facing customer feedback")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * A caller-supplied branchId is verified against the caller's own tenant
     * scope after the fact (a review's merchantId is denormalized onto it),
     * the same shape ProductController.getProducts uses — without this, any
     * staff member could pass an arbitrary branchId and read another
     * merchant's reviews.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
    @Operation(summary = "List reviews for a branch or the caller's own merchant")
    public ResponseEntity<List<ReviewEntity>> getReviews(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) UUID merchantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID scope = resolveScope(merchantId, principal);
        if (branchId != null) {
            List<ReviewEntity> reviews = reviewService.getReviewsByBranch(branchId);
            if (scope != null && reviews.stream().anyMatch(r -> !scope.equals(r.getMerchantId()))) {
                throw new UnauthorizedException("Caller has no access to branch " + branchId);
            }
            return ResponseEntity.ok(reviews);
        }
        return ResponseEntity.ok(reviewService.getReviewsByMerchant(scope));
    }

    private UUID resolveScope(UUID requested, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return requested;
        }
        UUID own = principal.getMerchantId();
        if (own == null) {
            throw new UnauthorizedException("Caller has no merchant scope");
        }
        return own;
    }
}
