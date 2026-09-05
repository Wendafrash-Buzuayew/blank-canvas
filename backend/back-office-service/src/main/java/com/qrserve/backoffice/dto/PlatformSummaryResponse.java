package com.qrserve.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cross-tenant, SUPER_ADMIN-only view of platform scale and activity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSummaryResponse {
    private long merchantCount;
    private long branchCount;
    private long auditEventCount;
}
