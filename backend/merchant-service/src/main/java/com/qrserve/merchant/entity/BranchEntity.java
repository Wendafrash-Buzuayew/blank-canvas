package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "branches",
        // Unique PER MERCHANT, not globally. A globally unique branch slug means
        // the second tenant to name a branch "Main" collides with the first - and
        // on a restaurant platform "Main" is the most likely branch name there is.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_branches_merchant_slug",
                columnNames = {"merchant_id", "slug"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // Slug derivation deliberately removed. It lived here, in MerchantEntity and
        // in MerchantService as three copies of the same broken expression. The slug
        // is now normalised and validated once, in the service, via Slugs - an entity
        // callback is the wrong place to reject caller input, because it cannot
        // produce a useful 400.
    }
}
