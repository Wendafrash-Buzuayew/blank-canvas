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
    private String qrUrl;
    private String format;
    private String mimeType;
    private String base64Content;
}
