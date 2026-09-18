package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String address;

    @Column(name = "logo_url")
    private String logoUrl;

    /**
     * Wide banner shown at the top of the customer digital menu when the
     * active template has showCoverImage on.
     *
     * <p>Nullable, and expected to stay null for a long while: no merchant has
     * uploaded one yet. The digital menu falls back to the branch's first dish
     * photo rather than rendering an empty header, so this being unset degrades
     * to a good default instead of a blank banner. See TemplatedMenu's
     * firstDishImage.
     *
     * <p>Merchant-level rather than branch-level, matching logoUrl: a cover is
     * brand artwork, and every branch of one restaurant shares it.
     */
    @Column(name = "cover_image_url")
    private String coverImageUrl;

    /** One line under the menu title, e.g. "Wood-fired Italian since 1998". Nullable. */
    @Column(name = "tagline", length = 120)
    private String tagline;

    @Column(nullable = false)
    private String category;

    /**
     * The merchant's Safaricom till/short code — the same value the Super App
     * handshake calls {@code merchantShortCode} (see SuperAppMerchantClaim in
     * auth-service) and binds a login to. Denormalised here, not looked up
     * from auth-service at request time, because this is what menu-service's
     * ETHQR proxy (GET /api/payment/ethqr) needs as {@code accountNumber},
     * and menu-service has no reason to depend on auth-service for it.
     *
     * <p>Nullable: a merchant created the ordinary way (not via Super App)
     * has no short code until one is set directly, and the ETHQR endpoint
     * degrades to a clear error rather than a blank/broken QR when it is null.
     */
    @Column(name = "short_code")
    private String shortCode;

    /**
     * Defaulted at the DB level too (see db/manual/005-merchant-tier.sql) —
     * ddl-auto=update would otherwise generate a bare NOT NULL ALTER TABLE,
     * which fails against any already-existing merchant row.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 10, columnDefinition = "varchar(10) default 'FREE'")
    @Builder.Default
    private MerchantTier tier = MerchantTier.FREE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Slug derivation deliberately removed - see BranchEntity for the same
        // change. A callback cannot reject bad input with a useful 400, and the
        // derived value here was the tenant's public hostname.
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
