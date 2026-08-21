package com.qrserve.merchant.entity;

import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merchant-configurable behaviour: which fulfilment types are on, when money is
 * taken, and where it lands.
 *
 * <p>A null {@code branchId} is the merchant-wide default; a row with a branch id
 * overrides it field by field (see {@code MerchantSettingsResolver}).
 *
 * <p>{@code credentialHandle} is a reference into the secret store, never a
 * credential. The M-PESA collection account's secrets must not sit in a table that
 * merchant admin endpoints can read.
 */
@Entity
@Table(name = "merchant_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** Null means this row is the merchant-wide default. */
    @Column(name = "branch_id")
    private Long branchId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "merchant_fulfilment_enabled",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "fulfilment_type")
    @Enumerated(EnumType.STRING)
    private Set<FulfilmentType> fulfilmentEnabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "merchant_settlement_mode",
            joinColumns = @JoinColumn(name = "settings_id"))
    @MapKeyColumn(name = "fulfilment_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "mode")
    @Enumerated(EnumType.STRING)
    private Map<FulfilmentType, SettlementMode> settlementMode;

    /** MPESA_COLLECTION, BANK_MAI or SUPER_APP_MERCHANT. */
    @Column(name = "rail")
    private String rail;

    /** The merchant's own account. Funds never land anywhere else. */
    @Column(name = "destination_ref")
    private String destinationRef;

    @Column(name = "credential_handle")
    private String credentialHandle;

    @Column(name = "currency")
    private String currency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currency == null) currency = "ETB";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
