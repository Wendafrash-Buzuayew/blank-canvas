package com.qrserve.merchant.controller;

import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.service.MerchantService;
import com.qrserve.shared.common.Slugs;
import com.qrserve.shared.common.dto.TenantResolutionResponse;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/**
 * Resolves a subdomain label to a tenant.
 *
 * <p>Called by the API gateway's {@code TenantResolutionGlobalFilter} on a cache
 * miss, before any JWT has been inspected — which is why it must be public. It
 * carries no tenant context of its own.
 */
@RestController
@RequestMapping("/api/v1/public/tenants")
@RequiredArgsConstructor
@Tag(name = "Public Tenant Resolution", description = "Subdomain label to merchant id")
public class PublicTenantController {

    private final MerchantService merchantService;

    @GetMapping("/by-slug/{slug}")
    @Operation(summary = "Resolve a subdomain label to a merchant id")
    public ResponseEntity<TenantResolutionResponse> bySlug(@PathVariable String slug) {
        // Hostnames are case-insensitive; slugs are stored lowercase.
        String normalized = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);

        // A reserved label must never resolve, even if a row somehow exists with
        // that slug — for instance a merchant created before the creation-time check
        // in MerchantService. Two gates, because the consequence of one failing is
        // that a tenant answers for admin.qrserve.safaricom.et.
        if (normalized.isEmpty() || Slugs.isReserved(normalized)) {
            throw new ResourceNotFoundException("No tenant for '" + slug + "'");
        }

        MerchantEntity merchant = merchantService.getMerchantBySlug(normalized);

        return ResponseEntity.ok()
                // The gateway caches in Redis; this header lets any intermediate proxy
                // do the same. Short, because a newly created tenant should start
                // resolving quickly.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(TenantResolutionResponse.builder()
                        .merchantId(merchant.getId())
                        .slug(merchant.getSlug())
                        .name(merchant.getName())
                        .build());
    }
}
