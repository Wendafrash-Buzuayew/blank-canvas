package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table assignment entity linking a waiter to a table at a branch.
 * Manages shift assignments and the current waiter per table.
 */
@Entity
@Table(name = "table_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "waiter_id", nullable = false)
    private Long waiterId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(nullable = false)
    private String status; // ACTIVE, ENDED, CANCELLED

    @Column(name = "shift", length = 50)
    private String shift; // MORNING, AFTERNOON, EVENING, NIGHT

    @PrePersist
    public void prePersist() {
        if (assignedAt == null) assignedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}