package com.qrserve.qr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrMetadataResponse {
    private Long tableId;

    /** The human-readable menu link, kept for display. It is no longer what is encoded. */
    private String qrUrl;

    private String format;
    private String mimeType;
    private String base64Content;

    /** The exact provisioned payload. Clients render this and never build their own. */
    private String payloadRaw;

    /** EMVCo tag 62-07 for the sticker this image represents. */
    private String terminalLabel;

    /** EMVCO or MENU_URL. */
    private String profile;
}
