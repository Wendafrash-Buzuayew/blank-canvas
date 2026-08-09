package com.qrserve.shared.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for resolving (acknowledging/completing) a customer
 * service request. Used by {@code PATCH /api/v1/requests/{requestId}/resolve}.
 *
 * <p>The resolution status supports a small, well-defined set of values:
 * {@code ACKNOWLEDGED} (waiter has seen the request) and
 * {@code COMPLETED} (request has been fulfilled).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveRequestDto {

    /** New status for the request: ACKNOWLEDGED or COMPLETED. */
    @NotNull
    @Size(min = 3, max = 20)
    private String status;

    /** Optional note from the resolving waiter. */
    @Size(max = 255)
    private String resolutionNote;
}