package com.qrserve.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableResponse {
    private Long id;
    private String tableNumber;
    private Integer capacity;
    private String qrUrl;
    private String qrToken;

    /** EMVCo tag 62-07 for this table's current sticker. */
    private String terminalLabel;

    /** The exact EMVCo payload to render. The caller must not build its own. */
    private String qrPayload;
}
