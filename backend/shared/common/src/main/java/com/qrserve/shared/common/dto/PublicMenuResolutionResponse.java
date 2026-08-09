package com.qrserve.shared.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO returned when resolving a public menu URL by slug-based path
 * {@code GET /api/v1/public/menu/{merchantSlug}/{branchSlug}/{tableNumber}}.
 *
 * <p>Carries both the human-readable slug/table identifiers from the URL
 * and the resolved internal IDs needed by downstream services (order
 * creation, customer requests, analytics, etc.).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuResolutionResponse {

    /** Merchant slug from the URL path (e.g. "le-petit-bistro"). */
    private String merchantSlug;

    /** Branch slug from the URL path (e.g. "downtown"). */
    private String branchSlug;

    /** Table number from the URL path (e.g. "12"). */
    private String tableNumber;

    /** Resolved merchant internal UUID id. */
    private UUID merchantId;

    /** Resolved merchant display name. */
    private String merchantName;

    /** Resolved branch internal Long id. */
    private Long branchId;

    /** Resolved branch display name. */
    private String branchName;

    /** Resolved table internal Long id. */
    private Long tableId;

    /** Resolved branch slug (may differ from URL if resolved via alias). */
    private String resolvedBranchSlug;
}