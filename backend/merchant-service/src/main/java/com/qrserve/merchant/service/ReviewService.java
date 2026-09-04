package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateReviewRequest;
import com.qrserve.merchant.dto.ReviewSummaryResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.ReviewEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.merchant.repository.ReviewRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BranchRepository branchRepository;

    /**
     * merchantId is derived from the resolved branch, never trusted from the
     * caller — a public endpoint with no other authentication must not let
     * a review be attributed to an arbitrary merchant.
     */
    @Transactional
    public ReviewEntity createReview(Long branchId, CreateReviewRequest request) {
        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found ID: " + branchId));

        ReviewEntity review = ReviewEntity.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .customerName(request.getCustomerName())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewEntity> getReviewsByBranch(Long branchId) {
        return reviewRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
    }

    @Transactional(readOnly = true)
    public List<ReviewEntity> getReviewsByMerchant(UUID merchantId) {
        return reviewRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummaryForBranch(Long branchId) {
        List<ReviewEntity> reviews = reviewRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
        if (reviews.isEmpty()) {
            return ReviewSummaryResponse.builder().averageRating(0).count(0).build();
        }
        double average = reviews.stream().mapToInt(ReviewEntity::getRating).average().orElse(0);
        return ReviewSummaryResponse.builder().averageRating(average).count(reviews.size()).build();
    }
}
