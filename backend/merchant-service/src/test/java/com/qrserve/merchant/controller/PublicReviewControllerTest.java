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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicReviewControllerTest {

    private static final Long BRANCH_ID = 42L;
    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MERCHANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private ReviewService reviewService;
    private BranchRepository branchRepository;
    private QrSignatureService qrSignatureService;
    private PublicReviewController controller;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        branchRepository = mock(BranchRepository.class);
        qrSignatureService = mock(QrSignatureService.class);
        controller = new PublicReviewController(reviewService, branchRepository, qrSignatureService);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static CreateReviewRequest request(int rating) {
        CreateReviewRequest r = new CreateReviewRequest();
        r.setRating(rating);
        return r;
    }

    @Test
    void createsAReviewWhenNoHostTenantIsSetAndNoSignatureIsGiven() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                BranchEntity.builder().id(BRANCH_ID).merchantId(MERCHANT).build()));
        when(reviewService.createReview(eq(BRANCH_ID), any())).thenReturn(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(MERCHANT).rating(5).build());

        var response = controller.createReview(BRANCH_ID, request(5), null);

        assertEquals(5, response.getBody().getRating());
    }

    @Test
    void rejectsAHostTenantThatDoesNotOwnTheBranch() {
        TenantContext.setCurrentTenant(OTHER_MERCHANT);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                BranchEntity.builder().id(BRANCH_ID).merchantId(MERCHANT).build()));

        assertThrows(AccessDeniedException.class, () -> controller.createReview(BRANCH_ID, request(5), null));
        verify(reviewService, never()).createReview(any(), any());
    }

    @Test
    void acceptsAMatchingHostTenant() {
        TenantContext.setCurrentTenant(MERCHANT);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                BranchEntity.builder().id(BRANCH_ID).merchantId(MERCHANT).build()));
        when(reviewService.createReview(eq(BRANCH_ID), any())).thenReturn(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(MERCHANT).rating(4).build());

        var response = controller.createReview(BRANCH_ID, request(4), null);

        assertEquals(4, response.getBody().getRating());
    }

    @Test
    void rejectsAnInvalidSignatureWhenOneIsSupplied() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                BranchEntity.builder().id(BRANCH_ID).merchantId(MERCHANT).build()));
        when(qrSignatureService.validateSignature("bad-sig", MERCHANT, BRANCH_ID)).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> controller.createReview(BRANCH_ID, request(5), "bad-sig"));
        verify(reviewService, never()).createReview(any(), any());
    }

    @Test
    void acceptsAValidSignatureWhenOneIsSupplied() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                BranchEntity.builder().id(BRANCH_ID).merchantId(MERCHANT).build()));
        when(qrSignatureService.validateSignature("good-sig", MERCHANT, BRANCH_ID)).thenReturn(true);
        when(reviewService.createReview(eq(BRANCH_ID), any())).thenReturn(
                ReviewEntity.builder().branchId(BRANCH_ID).merchantId(MERCHANT).rating(5).build());

        var response = controller.createReview(BRANCH_ID, request(5), "good-sig");

        assertEquals(5, response.getBody().getRating());
    }

    @Test
    void throwsResourceNotFoundForAMissingBranch() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.createReview(BRANCH_ID, request(5), null));
    }

    @Test
    void summaryDelegatesToTheService() {
        when(reviewService.getSummaryForBranch(BRANCH_ID)).thenReturn(
                ReviewSummaryResponse.builder().averageRating(4.5).count(10).build());

        var response = controller.getSummary(BRANCH_ID);

        assertEquals(4.5, response.getBody().getAverageRating(), 0.001);
        assertEquals(10, response.getBody().getCount());
    }
}
