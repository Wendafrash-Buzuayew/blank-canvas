package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The exact payload that went onto one printed sticker.
 *
 * <p>A payment instrument in the physical world is a liability: "what exact bytes are
 * on table 15" has to be answerable without recomputing from current configuration
 * and hoping none of the inputs moved. So the bytes are stored, not derived.
 *
 * <p>Rows are versioned and never deleted. A superseded sticker can stay on a table
 * for weeks, and a payment quoting its terminal label must still resolve to this
 * table — otherwise a reprint silently converts real payments into unmatched money.
 */
@Entity
@Table(name = "table_qr")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableQrEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** EMVCo tag 62-07. Unique per version, so a payment names its sticker. */
    @Column(name = "terminal_label", nullable = false, unique = true)
    private String terminalLabel;

    @Column(name = "payload_raw", nullable = false, length = 1024)
    private String payloadRaw;

    /**
     * An EMVCo payload's own last four characters, so it is redundant with
     * {@link #payloadRaw} but cheap to check without reparsing it. Null for the
     * MENU_URL profile — a URL has no CRC, and a sliced substring of one would be
     * meaningless data pretending to be a checksum.
     */
    @Column(name = "payload_crc", length = 4)
    private String payloadCrc;

    /** EMVCO or MENU_URL — the two provisioning profiles. */
    @Column(name = "profile", nullable = false)
    private String profile;

    @Column(name = "version", nullable = false)
    private int version;

    /** ACTIVE, SUPERSEDED or REVOKED. */
    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    /** Set when a merchant actually exports the code for printing. */
    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @PrePersist
    public void prePersist() {
        if (provisionedAt == null) provisionedAt = LocalDateTime.now();
        if (state == null) state = "ACTIVE";
    }
}
