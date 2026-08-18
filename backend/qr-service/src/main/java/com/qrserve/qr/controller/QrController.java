package com.qrserve.qr.controller;

import com.qrserve.qr.dto.QrExportRequest;
import com.qrserve.qr.dto.QrMetadataResponse;
import com.qrserve.qr.service.QrGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qr")
// QR provisioning is a staff operation; SecurityConfig does not make /api/qr
// public, and these endpoints expose table tokens used to build menu URLs.
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER','BRANCH_MANAGER')")
@RequiredArgsConstructor
@Tag(name = "QR Stand & Code Generator", description = "High-Res PNG, SVG & PDF Printable Table Stand Export APIs")
public class QrController {

    private final QrGeneratorService qrGeneratorService;

    @GetMapping("/{tableId}")
    @Operation(summary = "Get QR metadata and target URL for a table")
    public ResponseEntity<QrMetadataResponse> getQr(@PathVariable Long tableId) {
        return ResponseEntity.ok(qrGeneratorService.getQrForTable(tableId));
    }

    @PostMapping("/export/png")
    @Operation(summary = "Export high-resolution PNG QR image for custom branding")
    public ResponseEntity<byte[]> exportPng(@Valid @RequestBody QrExportRequest request) {
        byte[] data = qrGeneratorService.exportPng(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=table-" + request.getTableId() + ".png")
                .contentType(MediaType.IMAGE_PNG)
                .body(data);
    }

    @PostMapping("/export/pdf")
    @Operation(summary = "Export printable PDF table stand with logo branding")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody QrExportRequest request) {
        byte[] data = qrGeneratorService.exportPdf(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=table-stand-" + request.getTableId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
