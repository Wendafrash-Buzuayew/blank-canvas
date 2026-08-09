package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer request entity for real-time service requests from tables.
 * Supports: CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE.
 */
@Entity
@Table(name = "customer_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType; // CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE

    @Column(nullable = false, length = 20)
    private String status; // PENDING, ACKNOWLEDGED, COMPLETED, CANCELLED

    @Column(length = 255)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}