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

    /**
     * @deprecated DO NOT USE — this endpoint returns bytes that are not a valid
     *     PDF. {@link QrGeneratorService#exportPdf} hand-assembles the file as a
     *     string and gets three things wrong that each independently make it
     *     unparseable: it declares {@code /Filter /DCTDecode} (JPEG) while
     *     embedding PNG bytes, hardcodes {@code /Length 44} for a content stream
     *     that is not 44 bytes, and emits no {@code xref} table or
     *     {@code startxref} — both mandatory in PDF 1.4. No conforming reader
     *     can open the result.
     *
     *     <p>The merchant-facing standee is now produced in the browser instead
     *     (src/components/qr/StandeeStudio.tsx), which gives real vector text, a
     *     vector SVG QR and exact millimetre page sizing via {@code @page} — all
     *     of which a hand-rolled PDF writer here would have to reimplement.
     *     Delete this endpoint and {@code exportPdf} once nothing calls them; it
     *     is left in place only so removing it is a separate, reviewable change.
     */
    @Deprecated
    @PostMapping("/export/pdf")
    @Operation(summary = "DEPRECATED — emits an invalid PDF; use the browser standee studio")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody QrExportRequest request) {
        byte[] data = qrGeneratorService.exportPdf(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=table-stand-" + request.getTableId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    /**
     * The canonical string a branch's digital-menu QR must encode.
     *
     * <p>Staff-only by inheriting the class-level gate: SecurityConfig's public
     * rule covers {@code GET /api/qr/digital-menu/*&#47;*} exactly, and this path
     * has one more segment, so it does not match and is not public. That is the
     * right outcome — the standee studio is a merchant tool — but it is a
     * consequence of the path shape rather than an explicit rule, so it is
     * written down here.
     */
    @GetMapping("/digital-menu/{merchantSlug}/{branchSlug}/url")
    @Operation(summary = "The signed digital-menu URL for a branch — the exact string a QR should encode")
    public ResponseEntity<BranchMenuUrlResponse> getDigitalMenuUrl(
            @PathVariable String merchantSlug, @PathVariable String branchSlug) {
        return ResponseEntity.ok(new BranchMenuUrlResponse(
                qrGeneratorService.branchMenuUrl(merchantSlug, branchSlug), merchantSlug, branchSlug));
    }

    /** Deliberately a record: this is a value, and it must not grow fields. */
    public record BranchMenuUrlResponse(String url, String merchantSlug, String branchSlug) {}

    // Overrides the class-level @PreAuthorize: this is the one endpoint on this
    // controller SecurityConfig makes public (GET /api/qr/digital-menu/*/*), so
    // it must not inherit the staff-only role gate above, or that permitAll rule
    // would be silently defeated by method security 403ing every anonymous call.
    @GetMapping("/digital-menu/{merchantSlug}/{branchSlug}")
    @PreAuthorize("permitAll")
    @Operation(summary = "Render a QR PNG for a branch's phase-1 digital menu URL")
    public ResponseEntity<byte[]> getDigitalMenuQr(
            @PathVariable String merchantSlug, @PathVariable String branchSlug) {
        byte[] png = qrGeneratorService.getQrForBranch(merchantSlug, branchSlug);
        return ResponseEntity.ok().header("Content-Type", "image/png").body(png);
    }
}
