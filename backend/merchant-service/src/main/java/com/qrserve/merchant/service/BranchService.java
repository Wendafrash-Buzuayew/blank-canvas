package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.shared.common.Slugs;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    /**
     * Creates a branch under the caller's merchant.
     *
     * <p>The slug is taken from the request. It previously was not: the DTO has
     * carried a {@code @NotBlank slug} field all along and this method derived one
     * from the name instead, so a caller-supplied slug was silently discarded.
     *
     * <p>Branch slugs are path segments, not hostnames, so {@link Slugs#toPathSlug}
     * applies — a branch may legitimately be called "2".
     */
    @Transactional
    public BranchEntity createBranch(CreateBranchRequest request) {
        String slug = Slugs.toPathSlug(request.getSlug());

        // Checked rather than left to the database: a raw constraint violation
        // surfaces through the catch-all handler as 500 "An unexpected server
        // error occurred", which tells the owner nothing about what to change.
        if (branchRepository.findByMerchantIdAndSlug(request.getMerchantId(), slug).isPresent()) {
            throw new BusinessException(
                    "A branch with the slug '" + slug + "' already exists for this merchant");
        }

        BranchEntity branch = BranchEntity.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .address(request.getAddress() != null ? request.getAddress() : "Main Address")
                .build();
        return branchRepository.save(branch);
    }

    public BranchEntity getBranchByMerchantAndSlug(UUID merchantId, String slug) {
        return branchRepository.findByMerchantIdAndSlug(merchantId, slug)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for merchant " + merchantId + " and slug: " + slug));
    }

    public List<BranchEntity> getBranchesByMerchant(UUID merchantId) {
        return branchRepository.findByMerchantId(merchantId);
    }

    public BranchEntity getBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + id));
    }
}
