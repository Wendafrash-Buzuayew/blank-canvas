package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.repository.BranchRepository;
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
        String slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
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
