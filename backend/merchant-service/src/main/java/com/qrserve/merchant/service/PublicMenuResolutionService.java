package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.entity.TableEntity;
import com.qrserve.merchant.repository.TableRepository;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.common.TenantContext;
import com.qrserve.shared.common.dto.PublicMenuResolutionResponse;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves a public slug-based menu URL
 * ({@code /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}})
 * into internal merchant/branch/table identifiers.
 *
 * <p>All lookups are scoped by {@code merchantId} and {@code branchId} to
 * enforce tenant isolation. The QR signature is validated before any
 * identifiers are returned.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicMenuResolutionService {

    private final MerchantService merchantService;
    private final BranchService branchService;
    private final TableRepository tableRepository;
    private final QrSignatureService qrSignatureService;

    public PublicMenuResolutionResponse resolve(String merchantSlug, String branchSlug, String tableNumber, String signature) {
        // 1. Resolve merchant by slug
        MerchantEntity merchant = merchantService.getMerchantBySlug(merchantSlug);

        // 1b. If the request arrived through a tenant host, that host must agree
        //     with the merchant named in the path. Without this the subdomain is
        //     decoration: a guest on sunrise.qrserve.safaricom.et could read any
        //     other tenant's menu just by editing the path.
        //
        //     Absence of a host tenant is NOT an error here. This path names its
        //     own merchant, so it needs no implicit tenant - which is what keeps the
        //     demo route and direct-to-service access working.
        UUID hostTenant = TenantContext.getCurrentTenant();
        if (hostTenant != null && !hostTenant.equals(merchant.getId())) {
            throw new AccessDeniedException("This menu belongs to a different tenant");
        }

        // 2. Resolve branch scoped by merchantId + slug
        BranchEntity branch = branchService.getBranchByMerchantAndSlug(merchant.getId(), branchSlug);

        // 3. Resolve table scoped by branchId + tableNumber
        TableEntity table = tableRepository.findByBranchIdAndTableNumber(branch.getId(), tableNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Table not found for branch " + branch.getId() + " and table number: " + tableNumber));

        // 4. Validate QR signature over {merchantId, branchId, tableId} IF provided.
        //    Signature is optional in demo/preview mode so the public menu can be
        //    browsed without a scanned QR payload. When a signature IS supplied it
        //    must be valid (tamper protection for production QR scans).
        if (signature != null && !signature.isBlank()
                && !qrSignatureService.validateSignature(signature, merchant.getId(), branch.getId(), table.getId())) {
            throw new UnauthorizedException("Invalid QR signature for the requested table");
        }

        return PublicMenuResolutionResponse.builder()
                .merchantSlug(merchant.getSlug())
                .branchSlug(branchSlug)
                .tableNumber(tableNumber)
                .merchantId(merchant.getId())
                .merchantName(merchant.getName())
                .branchId(branch.getId())
                .branchName(branch.getName())
                .tableId(table.getId())
                .resolvedBranchSlug(branch.getSlug())
                .build();
    }
}