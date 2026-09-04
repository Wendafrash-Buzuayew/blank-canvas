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

    @Transactional
    public BranchEntity createBranch(CreateBranchRequest request) {
        String slug = Slugs.toPathSlug(request.getSlug());

        if (branchRepository.findByMerchantIdAndSlug(request.getMerchantId(), slug).isPresent()) {
            throw new BusinessException(
                    "A branch with the slug '" + slug + "' already exists for this merchant");
        }

        // The first branch a merchant creates has nothing to be secondary to,
        // so it becomes primary automatically. See setPrimaryBranch for how a
        // merchant reassigns it later.
        boolean isFirstBranch = branchRepository.findByMerchantId(request.getMerchantId()).isEmpty();

        BranchEntity branch = BranchEntity.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .address(request.getAddress() != null ? request.getAddress() : "Main Address")
                .isPrimary(isFirstBranch)
                .build();
        return branchRepository.save(branch);
    }

    /**
     * Reassigns which branch is primary for a merchant — the branch
     * {@code /m/{merchant-slug}} redirects to. Exactly one primary branch per
     * merchant at all times; a database partial unique index
     * (db/manual/003-branch-primary-flag.sql) backs this up against a race
     * between two concurrent calls.
     */
    @Transactional
    public BranchEntity setPrimaryBranch(UUID merchantId, Long branchId) {
        BranchEntity target = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId));
        if (!merchantId.equals(target.getMerchantId())) {
            throw new ResourceNotFoundException("Branch not found with ID: " + branchId);
        }

        for (BranchEntity branch : branchRepository.findByMerchantId(merchantId)) {
            if (branch.isPrimary() && !branch.getId().equals(branchId)) {
                branch.setPrimary(false);
                branchRepository.save(branch);
            }
        }
        target.setPrimary(true);
        return branchRepository.save(target);
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
