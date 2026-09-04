package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A customer's rating/comment about a branch visit — the phase-1 HLD
 * "customer feedback" feature. Deliberately visit-scoped (merchantId +
 * branchId), not order-scoped: phase 1's digital menu is read-only (no
 * cart/order), so a review cannot depend on the parked ordering system's
 * OrderEntity. Distinct from CustomerRequestEntity, which is an operational
 * in-visit request (call waiter), not post-visit feedback.
 */
@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
