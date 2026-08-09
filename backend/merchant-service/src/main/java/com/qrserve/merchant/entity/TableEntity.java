package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "table_number", nullable = false)
    private String tableNumber;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private String status; // AVAILABLE, OCCUPIED, RESERVED

    @Column(name = "qr_token", nullable = false, unique = true)
    private String qrToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "AVAILABLE";
    }
}
