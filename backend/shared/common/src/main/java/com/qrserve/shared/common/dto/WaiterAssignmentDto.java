package com.qrserve.shared.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a waiter-to-table assignment.
 *
 * <p>Used as both the request payload for
 * {@code POST /api/v1/tables/{tableId}/assign-waiter} and the response
 * body returned by that endpoint. Also serves as the shared contract
 * for {@code GET /api/v1/waiters/tasks} responses, allowing merchant
 * and notification services to interpret assignment data consistently.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterAssignmentDto {

    /** Internal ID of the assignment record (populated in responses). */
    private Long assignmentId;

    /** Merchant owning the table/waiter. Populated server-side. */
    private UUID merchantId;

    /** Branch where the table is located. */
    @NotNull
    private Long branchId;

    /** Table being assigned to the waiter. */
    @NotNull
    private Long tableId;

    /** Table number (human-readable) for display in responses. */
    private String tableNumber;

    /** Waiter staff record being assigned. */
    @NotNull
    private Long waiterId;

    /** Waiter user account ID for auth/notification targeting. */
    private UUID userId;

    /** Waiter display name for dashboards. */
    private String waiterName;

    /** Shift label: MORNING, AFTERNOON, EVENING, NIGHT. */
    @Size(max = 50)
    private String shift;

    /** Assignment status: ACTIVE, ENDED, CANCELLED. */
    private String status;

    /** When the assignment was created. */
    private LocalDateTime assignedAt;

    /** When the assignment ended, if applicable. */
    private LocalDateTime endedAt;
}