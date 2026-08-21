package com.qrserve.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The active provisioned QR for a table, carried across the wire to qr-service.
 *
 * <p>Deliberately narrower than {@link com.qrserve.merchant.entity.TableQrEntity}: it
 * omits merchantId/branchId/state/timestamps, none of which the renderer needs, and
 * none of which should be handed to a caller that only asked "what does this table's
 * sticker say".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableQrResponse {

    /** The exact payload to encode. The caller must not build its own. */
    private String payloadRaw;

    /** Null for a MENU_URL-profile row — a URL has no CRC. */
    private String payloadCrc;

    /** EMVCo tag 62-07 for this sticker. */
    private String terminalLabel;

    /** EMVCO or MENU_URL. */
    private String profile;

    private int version;
}
