package com.qrserve.order.payment.terminal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local, append-only projection of terminal label to table identity.
 *
 * <p>Owned by merchant-service, read here. It is a projection of immutable identity
 * data — never of a derived money value — which is what makes duplicating it safe:
 * a label's table never changes, so this copy cannot drift from its source.
 *
 * <p>It exists so that resolving a payment webhook needs no synchronous call to
 * merchant-service. That call would place a second service in the money path, able
 * to fail while a guest's payment is already in flight.
 */
@Entity
@Table(name = "terminal_map")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalMapEntity {

    /** EMVCo tag 62-07. The natural key: unique per printed sticker. */
    @Id
    @Column(name = "terminal_label", nullable = false)
    private String terminalLabel;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_number")
    private String tableNumber;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
    }
}
