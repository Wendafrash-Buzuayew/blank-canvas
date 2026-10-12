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
import com.qrserve.shared.common.emvco.EmvcoPayload;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UpstreamServiceException;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrGeneratorService {

    private static final int QR_SIZE = 300;

    /** The one profile whose payload is a URL rather than an EMVCo TLV string. */
    private static final String PROFILE_MENU_URL = "MENU_URL";

    /** Stable upstream label for 502 responses; never a host or URL. */
    static final String UPSTREAM_MERCHANT_SERVICE = "merchant-service";

    private final RestTemplate restTemplate;
    private final PublicMenuUrl publicMenuUrl;
    private final QrSignatureService qrSignatureService;
    private final com.qrserve.shared.common.DigitalMenuUrl digitalMenuUrl;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    public QrMetadataResponse getQrForTable(Long tableId) {
        TableInfo table = fetchTable(tableId);
        MerchantInfo merchant = fetchMerchant(table.getMerchantId());
        BranchInfo branch = fetchBranch(table.getBranchId());
        TableQrInfo qr = fetchTableQr(tableId);

        // Still built and returned for the designer to show as a human-readable
        // link, but it is no longer what gets encoded into the sticker.
        String menuUrl = targetUrlFor(table, merchant, branch, publicMenuUrl, qrSignatureService);

        String payload = payloadToRender(qr.getPayloadRaw(), qr.getProfile());
        byte[] png = renderPng(payload, QR_SIZE);

        return QrMetadataResponse.builder()
                .tableId(table.getId())
                .qrUrl(menuUrl)
                .payloadRaw(payload)
                .terminalLabel(qr.getTerminalLabel())
                .profile(qr.getProfile())
                .format("PNG")
                .mimeType("image/png")
                .base64Content("data:image/png;base64," + Base64.getEncoder().encodeToString(png))
                .build();
    }

    public byte[] exportPng(QrExportRequest request) {
        TableQrInfo qr = fetchTableQr(request.getTableId());
        return renderPng(payloadToRender(qr.getPayloadRaw(), qr.getProfile()), QR_SIZE);
    }

    /**
     * The exact string a branch's digital-menu QR encodes, signature included.
     *
     * <p>Extracted so the PNG render and the URL the merchant portal renders
     * its own SVG/high-res QR from cannot diverge. That matters more than it
     * looks: the signature is an HMAC only this backend can compute, so a
     * frontend that built this URL itself would produce a code missing the
     * signature — and would then laminate it onto a table. The portal asks for
     * this string and encodes it verbatim; it never assembles one. Same
     * principle as src/lib/qrDisplay.ts's "never construct a code".
     */
    public String branchMenuUrl(String merchantSlug, String branchSlug) {
        DigitalMenuResolution resolution = fetchDigitalMenuResolution(merchantSlug, branchSlug);
        String url = digitalMenuUrl.branchUrl(resolution.merchantSlug(), resolution.branchSlug());
        String signature = qrSignatureService.generateSignature(resolution.merchantId(), resolution.branchId());
        return url + "?signature=" + signature;
    }

    /**
     * Renders a QR for the phase-1 digital-menu branch URL. No stored QR
     * state exists for this scheme (unlike table QR's rotation-tracked
     * payload) — slugs are permanent, so the URL is fully reproducible from
     * merchant-service data on every call.
     */
    public byte[] getQrForBranch(String merchantSlug, String branchSlug) {
        return renderPng(branchMenuUrl(merchantSlug, branchSlug), QR_SIZE);
    }

    private DigitalMenuResolution fetchDigitalMenuResolution(String merchantSlug, String branchSlug) {
        Map<String, Object> body = getFromMerchantService(
                merchantServiceUrl + "/api/v1/public/digital-menu/" + merchantSlug + "/" + branchSlug,
                "No branch " + branchSlug + " for merchant " + merchantSlug);
        return new DigitalMenuResolution(
                UUID.fromString((String) body.get("merchantId")),
                (String) body.get("merchantSlug"),
                ((Number) body.get("branchId")).longValue(),
                (String) body.get("branchSlug"));
    }

    /**
     * GETs a JSON object from merchant-service. Only a real 404 (or an empty body)
     * becomes {@link ResourceNotFoundException}; any other failure — timeout,
     * connection refused, 5xx, auth errors — is an upstream failure (502), so an
     * outage is not misreported to the merchant as "your table does not exist".
     */
    private Map<String, Object> getFromMerchantService(String url, String notFoundMessage) {
        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getAuthHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            throw translateMerchantServiceFailure(e, url, notFoundMessage);
        }
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
        return body;
    }

    /** Package-private and static so the 404-vs-502 split is unit-testable without HTTP. */
    static RuntimeException translateMerchantServiceFailure(RestClientException e, String url, String notFoundMessage) {
        if (e instanceof HttpClientErrorException.NotFound) {
            return new ResourceNotFoundException(notFoundMessage);
        }
        log.error("merchant-service call failed: {}", url, e);
        return new UpstreamServiceException(UPSTREAM_MERCHANT_SERVICE, "merchant-service request failed", e);
    }

    private record DigitalMenuResolution(UUID merchantId, String merchantSlug, Long branchId, String branchSlug) {}

    /**
     * The payload to encode, validated.
     *
     * <p>Static so it can be asserted without standing up HTTP, and package-visible
     * for the same reason {@code targetUrlFor} was: this is the one decision that
     * must not diverge between services.
     *
     * <p>A {@code MENU_URL}-profile row carries no CRC — a sliced substring of a URL
     * would be meaningless data pretending to be a checksum — so only the EMVCO
     * profile is put through {@link EmvcoPayload#crcValid(String)}.
     *
     * @throws IllegalStateException when nothing is stored or an EMVCo payload's CRC
     *         does not match — both cases must stop a print run rather than produce
     *         a dead sticker
     */
    static String payloadToRender(String storedPayload, String profile) {
        if (storedPayload == null || storedPayload.isBlank()) {
            throw new IllegalStateException(
                    "No provisioned QR payload for this table; provision one before rendering");
        }
        if (!PROFILE_MENU_URL.equals(profile) && !EmvcoPayload.crcValid(storedPayload)) {
            throw new IllegalStateException(
                    "Stored QR payload fails its own CRC and would be rejected by every wallet");
        }
        return storedPayload;
    }

    /**
     * ZXing render. Separated from payload selection so each can be tested alone.
     *
     * <p>Error correction {@code H}, margin {@code 2}, and the UTF-8 charset hint
     * match the {@code generateQrPng} this replaced. This is the print path for a
     * code that gets laminated onto a physical table and cannot be reprinted on a
     * whim — it has to survive scuffing and being photographed at an angle, and a
     * merchant display name outside ASCII has to still encode correctly.
     */
    static byte[] renderPng(String payload, int size) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(renderImage(payload, size), "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render QR payload", e);
        }
    }

    static BufferedImage renderImage(String payload, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    payload, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                            EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render QR payload", e);
        }
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
        TableQrInfo qr = fetchTableQr(request.getTableId());
        return pdfFromImage(renderImage(payloadToRender(qr.getPayloadRaw(), qr.getProfile()), QR_SIZE));
    }

    /**
     * A minimal, valid single-page PDF 1.4 showing {@code image} at 1pt per pixel.
     *
     * <p>No PDF library is on the classpath, so this is written by hand — but
     * correctly: the image is raw 8-bit grayscale under {@code /FlateDecode}
     * (a filter the JDK's Deflater produces), every {@code /Length} is the real
     * byte count, and the file ends with an {@code xref} table and
     * {@code startxref} pointing at the real byte offsets. The previous version
     * declared PNG bytes as {@code /DCTDecode}, hardcoded a wrong content
     * length and had no xref, so no reader could open it.
     */
    static byte[] pdfFromImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] gray = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                gray[y * w + x] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
            }
        }
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(deflated)) {
            z.write(gray);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] imageData = deflated.toByteArray();
        byte[] content = ("q\n" + w + " 0 0 " + h + " 0 0 cm\n/Im1 Do\nQ").getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        int[] offsets = new int[6];
        writeAscii(pdf, "%PDF-1.4\n");
        offsets[1] = pdf.size();
        writeAscii(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets[2] = pdf.size();
        writeAscii(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets[3] = pdf.size();
        writeAscii(pdf, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "]"
                + " /Contents 4 0 R /Resources << /XObject << /Im1 5 0 R >> >> >>\nendobj\n");
        offsets[4] = pdf.size();
        writeAscii(pdf, "4 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        pdf.writeBytes(content);
        writeAscii(pdf, "\nendstream\nendobj\n");
        offsets[5] = pdf.size();
        writeAscii(pdf, "5 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + imageData.length + " >>\nstream\n");
        pdf.writeBytes(imageData);
        writeAscii(pdf, "\nendstream\nendobj\n");

        int xref = pdf.size();
        StringBuilder table = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            table.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        table.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        writeAscii(pdf, table.toString());
        return pdf.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    private TableInfo fetchTable(Long tableId) {
        Map<String, Object> body = getFromMerchantService(
                merchantServiceUrl + "/api/tables/" + tableId, "Table not found ID: " + tableId);
        return new TableInfo(
                ((Number) body.get("id")).longValue(),
                ((Number) body.get("branchId")).longValue(),
                UUID.fromString((String) body.get("merchantId")),
                (String) body.get("tableNumber")
        );
    }

    private MerchantInfo fetchMerchant(UUID merchantId) {
        Map<String, Object> body = getFromMerchantService(
                merchantServiceUrl + "/api/merchants/" + merchantId, "Merchant not found ID: " + merchantId);
        return new MerchantInfo(
                UUID.fromString((String) body.get("id")),
                (String) body.get("slug")
        );
    }


    /**
     * Fetches the branch so its SLUG is available. This lookup did not exist
     * before, because the old URL used the branch id - the id was already to hand
     * and the slug was not, which is very likely how the wrong format came to be
     * written.
     */
    private BranchInfo fetchBranch(Long branchId) {
        Map<String, Object> body = getFromMerchantService(
                merchantServiceUrl + "/api/branches/" + branchId, "Branch not found ID: " + branchId);
        return new BranchInfo(
                ((Number) body.get("id")).longValue(),
                (String) body.get("slug")
        );
    }

    /**
     * Fetches the provisioned {@code TableQr} for this table — the payload this
     * service must render, and the one thing it must not recompute. 404s when the
     * table has no ACTIVE row, which merchant-service's {@code GET .../qr} endpoint
     * signals with a plain 404 (never public: the payload embeds a merchant's own
     * settlement account).
     */
    private TableQrInfo fetchTableQr(Long tableId) {
        Map<String, Object> body = getFromMerchantService(
                merchantServiceUrl + "/api/tables/" + tableId + "/qr", "No provisioned QR for table ID: " + tableId);
        return new TableQrInfo(
                (String) body.get("payloadRaw"),
                (String) body.get("terminalLabel"),
                (String) body.get("profile")
        );
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

    /** The stored payload plus the fields the response and the render decision need. */
    static class TableQrInfo {
        private final String payloadRaw;
        private final String terminalLabel;
        private final String profile;

        TableQrInfo(String payloadRaw, String terminalLabel, String profile) {
            this.payloadRaw = payloadRaw;
            this.terminalLabel = terminalLabel;
            this.profile = profile;
        }

        public String getPayloadRaw() { return payloadRaw; }
        public String getTerminalLabel() { return terminalLabel; }
        public String getProfile() { return profile; }
    }
}