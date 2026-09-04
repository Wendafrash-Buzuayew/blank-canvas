package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves the two phase-1 digital-menu public routes:
 * {@code /m/{merchant-slug}} (primary branch) and
 * {@code /m/{merchant-slug}/{branch-slug}} (a named branch). A sibling of
 * {@link PublicMenuResolutionService} — that class and its table-scoped
 * route are untouched by this addition.
 */
@Service
@RequiredArgsConstructor
public class PublicDigitalMenuResolutionService {

    private final MerchantService merchantService;
    private final BranchService branchService;

    public DigitalMenuResolutionResponse resolvePrimary(String merchantSlug) {
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);
        List<BranchEntity> branches = branchService.getBranchesByMerchant(merchant.getId());
        BranchEntity primary = branches.stream()
                .filter(BranchEntity::isPrimary)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant " + merchantSlug + " has no primary branch designated"));

        return toResponse(merchant, primary);
    }

    public DigitalMenuResolutionResponse resolveBranch(String merchantSlug, String branchSlug) {
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);
        BranchEntity branch = branchService.getBranchByMerchantAndSlug(merchant.getId(), branchSlug);
        return toResponse(merchant, branch);
    }

    private DigitalMenuResolutionResponse toResponse(MerchantEntity merchant, BranchEntity branch) {
        return DigitalMenuResolutionResponse.builder()
                .merchantId(merchant.getId())
                .merchantSlug(merchant.getSlug())
                .branchId(branch.getId())
                .branchSlug(branch.getSlug())
                .branchName(branch.getName())
                .build();
    }
}
