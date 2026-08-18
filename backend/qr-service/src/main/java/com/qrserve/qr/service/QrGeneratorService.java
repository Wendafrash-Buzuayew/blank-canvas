package com.qrserve.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrserve.qr.dto.QrExportRequest;
import com.qrserve.qr.dto.QrMetadataResponse;
import com.qrserve.shared.common.PublicMenuUrl;
import com.qrserve.shared.common.QrSignatureService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrGeneratorService {

    private static final int QR_SIZE = 300;

    private final RestTemplate restTemplate;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    public QrMetadataResponse getQrForTable(Long tableId) {
        TableInfo table = fetchTable(tableId);
        MerchantInfo merchant = fetchMerchant(table.getMerchantId());
        BranchInfo branch = fetchBranch(table.getBranchId());

        String targetUrl = targetUrlFor(table, merchant, branch, publicMenuUrl, qrSignatureService);

        // Generate real QR code using ZXing
        String base64Png = generateQrBase64(targetUrl);

        return QrMetadataResponse.builder()
                .tableId(table.getId())
                .qrUrl(targetUrl)
                .format("PNG")
                .mimeType("image/png")
                .base64Content("data:image/png;base64," + base64Png)
                .build();
    }

    public byte[] exportPng(QrExportRequest request) {
        TableInfo table = fetchTable(request.getTableId());
        MerchantInfo merchant = fetchMerchant(table.getMerchantId());
        BranchInfo branch = fetchBranch(table.getBranchId());

        return generateQrPng(targetUrlFor(table, merchant, branch, publicMenuUrl, qrSignatureService));
    }

    /**
     * The single URL contract, shared with merchant-service through
     * {@link PublicMenuUrl}. Static and package-private so the exact output can be
     * asserted without standing up HTTP: this service and merchant-service must
     * emit byte-identical URLs for the same table, and the two previously drifted
     * while each carried a comment claiming it matched the other.
     */
    static String targetUrlFor(TableInfo table, MerchantInfo merchant, BranchInfo branch,
                               PublicMenuUrl publicMenuUrl, QrSignatureService signatures) {
        String signature = signatures.generateSignature(
                merchant.getId(), branch.getId(), table.getId());
        return publicMenuUrl.menuUrl(
                merchant.getSlug(), branch.getSlug(), table.getTableNumber(), signature);
    }

    public byte[] exportPdf(QrExportRequest request) {
        // Generate QR PNG and wrap in a minimal PDF
        byte[] qrPng = exportPng(request);
        String base64Qr = Base64.getEncoder().encodeToString(qrPng);

        // Simple PDF with embedded QR image
        String pdfContent = "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Contents 4 0 R /Resources << /XObject << /Im1 5 0 R >> >> >>\nendobj\n" +
                "4 0 obj\n<< /Length 44 >>\nstream\nq\n300 0 0 300 0 0 cm\n/Im1 Do\nQ\nendstream\nendobj\n" +
                "5 0 obj\n<< /Type /XObject /Subtype /Image /Width 300 /Height 300 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + qrPng.length + " >>\nstream\n" +
                new String(qrPng, java.nio.charset.StandardCharsets.ISO_8859_1) +
                "\nendstream\nendobj\n" +
                "trailer\n<< /Root 1 0 R >>\n%%EOF";

        return pdfContent.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private TableInfo fetchTable(Long tableId) {
        try {
            String url = merchantServiceUrl + "/api/tables/" + tableId;
            // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Table not found ID: " + tableId);
            }
            
            return new TableInfo(
                    ((Number) body.get("id")).longValue(),
                    ((Number) body.get("branchId")).longValue(),
                    UUID.fromString((String) body.get("merchantId")),
                    (String) body.get("tableNumber")
            );
        } catch (Exception e) {
            log.error("Failed to fetch table {} from merchant-service", tableId, e);
            throw new ResourceNotFoundException("Table not found ID: " + tableId);
        }
    }

    private MerchantInfo fetchMerchant(UUID merchantId) {
        try {
            String url = merchantServiceUrl + "/api/merchants/" + merchantId;
             // Build headers containing the security token
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Merchant not found ID: " + merchantId);
            }
            
            return new MerchantInfo(
                    UUID.fromString((String) body.get("id")),
                    (String) body.get("slug")
            );
        } catch (Exception e) {
            log.error("Failed to fetch merchant {} from merchant-service", merchantId, e);
            throw new ResourceNotFoundException("Merchant not found ID: " + merchantId);
        }
    }


    /**
     * Fetches the branch so its SLUG is available. This lookup did not exist
     * before, because the old URL used the branch id - the id was already to hand
     * and the slug was not, which is very likely how the wrong format came to be
     * written.
     */
    private BranchInfo fetchBranch(Long branchId) {
        try {
            String url = merchantServiceUrl + "/api/branches/" + branchId;
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Branch not found ID: " + branchId);
            }
            return new BranchInfo(
                    ((Number) body.get("id")).longValue(),
                    (String) body.get("slug")
            );
        } catch (Exception e) {
            log.error("Failed to fetch branch {} from merchant-service", branchId, e);
            throw new ResourceNotFoundException("Branch not found ID: " + branchId);
        }
    }

    /**
     * Extracts Authorization Header from the current request thread
     */
    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, authToken);
            }
        }
        return headers;
    }


    private String generateQrBase64(String content) {
        return Base64.getEncoder().encodeToString(generateQrPng(content));
    }

    private byte[] generateQrPng(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /** Package-private so {@link #targetUrlFor} can be unit-tested. */
    static class TableInfo {
        private final Long id;
        private final Long branchId;
        private final UUID merchantId;
        private final String tableNumber;

        TableInfo(Long id, Long branchId, UUID merchantId, String tableNumber) {
            this.id = id;
            this.branchId = branchId;
            this.merchantId = merchantId;
            this.tableNumber = tableNumber;
        }

        public Long getId() { return id; }
        public Long getBranchId() { return branchId; }
        public UUID getMerchantId() { return merchantId; }
        public String getTableNumber() { return tableNumber; }
    }

    static class MerchantInfo {
        private final UUID id;
        private final String slug;

        MerchantInfo(UUID id, String slug) {
            this.id = id;
            this.slug = slug;
        }

        public UUID getId() { return id; }
        public String getSlug() { return slug; }
    }

    /** The branch SLUG, which is what the public route actually needs. */
    static class BranchInfo {
        private final Long id;
        private final String slug;

        BranchInfo(Long id, String slug) {
            this.id = id;
            this.slug = slug;
        }

        public Long getId() { return id; }
        public String getSlug() { return slug; }
    }
}