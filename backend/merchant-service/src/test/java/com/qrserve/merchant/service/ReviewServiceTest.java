package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateReviewRequest;
import com.qrserve.merchant.dto.ReviewSummaryResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.ReviewEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.ReviewRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private ReviewRepository reviewRepository;
    private BranchRepository branchRepository;
    private ReviewService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        branchRepository = mock(BranchRepository.class);
        service = new ReviewService(reviewRepository, branchRepository);
    }

    @Test
    void createReviewDerivesMerchantIdFromTheResolvedBranchNotTheRequest() {
        BranchEntity branch = BranchEntity.builder().id(BRANCH).merchantId(MERCHANT).name("Main").build();
        when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(5);
        request.setComment("Great food");

        ReviewEntity review = service.createReview(BRANCH, request);

        assertEquals(MERCHANT, review.getMerchantId());
        assertEquals(BRANCH, review.getBranchId());
        assertEquals(5, review.getRating());
        assertEquals("Great food", review.getComment());
    }

    @Test
    void createReviewThrowsForAMissingBranch() {
        when(branchRepository.findById(BRANCH)).thenReturn(Optional.empty());
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(3);

        assertThrows(ResourceNotFoundException.class, () -> service.createReview(BRANCH, request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void summaryAveragesTheRatingsForThatBranchOnly() {
        when(reviewRepository.findByBranchIdOrderByCreatedAtDesc(BRANCH)).thenReturn(List.of(
                ReviewEntity.builder().branchId(BRANCH).rating(5).build(),
                ReviewEntity.builder().branchId(BRANCH).rating(3).build(),
                ReviewEntity.builder().branchId(BRANCH).rating(4).build()));

        ReviewSummaryResponse summary = service.getSummaryForBranch(BRANCH);

        assertEquals(4.0, summary.getAverageRating(), 0.001);
        assertEquals(3, summary.getCount());
    }

    @Test
    void summaryIsZeroForABranchWithNoReviewsYet() {
        when(reviewRepository.findByBranchIdOrderByCreatedAtDesc(BRANCH)).thenReturn(List.of());

        ReviewSummaryResponse summary = service.getSummaryForBranch(BRANCH);

        assertEquals(0, summary.getAverageRating());
        assertEquals(0, summary.getCount());
    }
}
