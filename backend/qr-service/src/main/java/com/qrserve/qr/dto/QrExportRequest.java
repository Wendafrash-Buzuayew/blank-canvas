package com.qrserve.qr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QrExportRequest {
    @NotNull
    private Long tableId;
    private String format; // PDF, PNG, SVG
    private String logoUrl;
    private String brandColor; // e.g. #E60028
    private String titleText;
}
