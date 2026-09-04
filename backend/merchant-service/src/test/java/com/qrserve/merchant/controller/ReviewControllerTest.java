package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.ReviewEntity;
import com.qrserve.merchant.service.ReviewService;
import com.qrserve.shared.exceptions.UnauthorizedException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * getReviews(branchId=...) previously trusted a caller-supplied branchId
 * outright — the same tenant-leak class ProductController.getProducts had.
 * A review's merchantId is denormalized onto it, so the fix verifies every
 * returned review actually belongs to the caller's own tenant.
 */
class ReviewControllerTest {

    private static final Long BRANCH_ID = 42L;
    private static final UUID OWN_MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private ReviewService reviewService;
    private ReviewController controller;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        controller = new ReviewController(reviewService);
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    @Test
    void ownerCanReadTheirOwnBranchsReviews() {
        when(reviewService.getReviewsByBranch(BRANCH_ID)).thenReturn(List.of(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(OWN_MERCHANT).rating(5).build()));

        var response = controller.getReviews(BRANCH_ID, null, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(1, response.getBody().size());
    }

    @Test
    void ownerCannotReadAnotherMerchantsBranchReviewsByGuessingItsId() {
        when(reviewService.getReviewsByBranch(BRANCH_ID)).thenReturn(List.of(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(OTHER_MERCHANT).rating(2).build()));

        assertThrows(UnauthorizedException.class,
                () -> controller.getReviews(BRANCH_ID, null, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    void superAdminCanReadAnyBranchsReviews() {
        when(reviewService.getReviewsByBranch(BRANCH_ID)).thenReturn(List.of(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(OTHER_MERCHANT).rating(2).build()));

        var response = controller.getReviews(BRANCH_ID, null, principal(null, UserRole.SUPER_ADMIN));

        assertEquals(1, response.getBody().size());
    }

    @Test
    void ownerWithNoBranchIdGetsTheirOwnMerchantsReviews() {
        when(reviewService.getReviewsByMerchant(OWN_MERCHANT)).thenReturn(List.of(
                ReviewEntity.builder().merchantId(OWN_MERCHANT).rating(4).build()));

        var response = controller.getReviews(null, null, principal(OWN_MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals(1, response.getBody().size());
        verify(reviewService, never()).getReviewsByBranch(any());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() {
        assertThrows(UnauthorizedException.class, () -> controller.getReviews(null, null, null));
    }
}
