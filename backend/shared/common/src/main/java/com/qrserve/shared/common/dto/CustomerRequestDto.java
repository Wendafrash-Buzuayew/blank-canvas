package com.qrserve.shared.common.dto;

import com.qrserve.shared.common.WaiterRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a customer service request from a table.
 * Used by {@code POST /api/v1/tables/{tableId}/requests}.
 *
 * <p>This shared contract prevents cross-service drift between
 * merchant-service (which persists the request) and notification-service
 * (which forwards waiter alerts to connected WebSocket clients).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestDto {

    /** Merchant owning the request. Resolved server-side when not supplied. */
    private UUID merchantId;

    /** Branch where the request originates. Resolved server-side when not supplied. */
    private Long branchId;

    /** Table where the customer is seated. Resolved from path when not supplied. */
    private Long tableId;

    /** Type of request: CALL_WAITER, REQUEST_WATER, or REQUEST_BILL. */
    @NotNull
    private WaiterRequestType requestType;

    /** Optional note/description from the customer. */
    @Size(max = 255)
    private String note;

    /** Optional customer name for personalization. */
    @Size(max = 100)
    private String customerName;
}