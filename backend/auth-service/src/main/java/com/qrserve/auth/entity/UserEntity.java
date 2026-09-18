package com.qrserve.auth.entity;

import com.qrserve.shared.security.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * The M-PESA Super App's merchant till/short code, so a repeat entry from
     * the Super App finds the existing account instead of re-provisioning.
     * Null for every user created through the ordinary email/password path.
     */
    @Column(name = "super_app_merchant_ref", unique = true)
    private String superAppMerchantRef;

    @Column(nullable = false)
    private boolean enabled;

    /**
     * Whether this merchant has filled in the business profile (name, city,
     * address, category) that a Super App claim never carries - it only ever
     * hands over a merchant short code and an MSISDN. True for every account
     * created through the ordinary email/password path, which collects that
     * profile up front. False the moment a Super App login auto-provisions a
     * new merchant; the frontend gates every other route behind an onboarding
     * form until {@code AuthController#completeOnboarding} flips it.
     */
    @Column(name = "onboarding_complete", nullable = false)
    @Builder.Default
    private boolean onboardingComplete = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
