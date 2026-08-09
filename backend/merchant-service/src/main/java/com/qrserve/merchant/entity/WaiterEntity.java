package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Waiter entity representing staff assigned to tables at a branch.
 * Each waiter belongs to a merchant and a branch.
 */
@Entity
@Table(name = "waiters", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"branch_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status; // ACTIVE, INACTIVE, ON_SHIFT, OFF_SHIFT

    @Column(name = "shift", length = 50)
    private String shift; // MORNING, AFTERNOON, EVENING, NIGHT

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}