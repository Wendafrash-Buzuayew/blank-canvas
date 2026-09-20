package com.qrserve.menu.service;

import com.qrserve.menu.dto.EthQrResponse;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UpstreamServiceException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Proxies a merchant's Safaricom ETHQR payment-QR generation. A Pro-tier
 * feature offered from the standee studio's payment QR toggle — distinct
 * from the digital-menu QR the rest of qr-service/qrRender.ts handle, which
 * carries no payment payload.
 *
 * <p>THE REQUEST BODY IS EXACTLY ONE FIELD:
 *
 * <pre>
 * POST https://qr.safaricom.et/api/qr/generate
 * Content-Type: application/json
 *
 * {"accountNumber": "8319389"}
 * </pre>
 *
 * <p>This is the whole contract, and sending anything else breaks it. An
 * earlier version added {@code merchantName} and {@code amount} whenever a
 * fixed amount was requested, which made the provider answer with a
 * completely different payload — a pre-composed branded invoice card under
 * {@code qrCodeBase64} instead of the bare QR under {@code qrImageUrl} — so
 * the endpoint effectively had two response schemas depending on the
 * request, and the frontend carried a branch for each. The fixed-amount
 * feature is gone with it: the standee QR is a reusable open-amount code by
 * definition (a guest enters what they owe), so there was never a printed
 * artefact that wanted an amount baked in.
 *
 * <p>SAFARICOM_QR_API_KEY remains optional and is UNSET in every environment
 * today, so the request carries exactly the Content-Type header above. A
 * real call with no Authorization header was confirmed to succeed. The
 * header is only added when a key IS configured, rather than refusing the
 * call without one — kept solely so an account that later requires a key
 * needs a config change and not a code change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SafaricomEthQrService {

    private final RestTemplate restTemplate;

    @Value("${services.merchant-service-url:http://localhost:8085}")
    private String merchantServiceUrl;

    @Value("${safaricom.ethqr.base-url}")
    private String ethQrBaseUrl;

    @Value("${safaricom.ethqr.api-key:}")
    private String apiKey;

    /** Stable client-facing identifier for this dependency — see UpstreamServiceException. */
    private static final String UPSTREAM_ETHQR = "safaricom-ethqr";
    private static final String UPSTREAM_MERCHANT = "merchant-service";

    private static final String EXPECTED_ETHQR_HOST = "qr.safaricom.et";

    /**
     * Logs the endpoint this service will actually call, once, at boot.
     *
     * <p>Worth the two lines: a stale container was left pointing at
     * {@code http://mock-ethqr:5679/api/qr/generate} (a host that no longer
     * existed), and because the URL appeared nowhere in the logs until a
     * request failed deep in a stack trace, every ETHQR call returned a bare
     * 503 with no hint that the target was wrong rather than down. The
     * mismatch warning makes that state obvious on startup instead.
     */
    @PostConstruct
    void logConfiguredEndpoint() {
        log.info("Safaricom ETHQR endpoint configured as {} (api key {})",
                ethQrBaseUrl, (apiKey == null || apiKey.isBlank()) ? "unset" : "set");
        if (ethQrBaseUrl == null || !ethQrBaseUrl.contains(EXPECTED_ETHQR_HOST)) {
            log.warn("Safaricom ETHQR endpoint does NOT point at {} — payment QR generation will not reach the real "
                    + "provider. Check SAFARICOM_QR_BASE_URL on this instance.", EXPECTED_ETHQR_HOST);
        }
    }

    public EthQrResponse generate(Long branchId, UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }

        UUID merchantId = fetchBranchMerchantId(branchId);
        if (principal.getRole() != UserRole.SUPER_ADMIN && !merchantId.equals(principal.getMerchantId())) {
            throw new AccessDeniedException("Branch " + branchId + " does not belong to your merchant");
        }

        MerchantSummary merchant = fetchMerchant(merchantId);
        if (merchant.shortCode() == null || merchant.shortCode().isBlank()) {
            throw new BusinessException(
                    "This merchant has no Safaricom short code on file. "
                            + "Set one before generating a payment QR.");
        }
        // The entire request body. Exactly one field — see the class comment
        // for what adding merchantName/amount here did to the response.
        Map<String, String> payload = Map.of("accountNumber", merchant.shortCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    ethQrBaseUrl, HttpMethod.POST, new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpStatusCodeException e) {
            // Something answered with an error status. WHAT answered decides
            // what to tell the merchant — see describeUpstreamFailure. The
            // body is logged in full because it is the only thing that
            // distinguishes the three cases.
            String responseBody = e.getResponseBodyAsString();
            log.error("Safaricom ETHQR call for merchant {} failed with {} — target {}, body: {}",
                    merchantId, e.getStatusCode(), ethQrBaseUrl, responseBody, e);
            throw new UpstreamServiceException(UPSTREAM_ETHQR,
                    describeUpstreamFailure(e.getStatusCode().value(), responseBody), e);
        } catch (RestClientException e) {
            // Never reached Safaricom at all: DNS, TCP, TLS or timeout —
            // which is also what a MISCONFIGURED endpoint looks like, hence
            // logging the target URL rather than only the exception.
            log.error("Safaricom ETHQR request failed for merchant {} — target {}", merchantId, ethQrBaseUrl, e);
            throw new UpstreamServiceException(UPSTREAM_ETHQR,
                    "Could not reach the Safaricom ETHQR service. If this persists, check the configured "
                            + "ETHQR endpoint for this environment.", e);
        }

        // A 200 with no usable image is a contract break, not a merchant
        // error, and it fails HERE rather than shipping a half-populated
        // response the UI would have to re-validate. The old passthrough
        // sent whatever came back and left the frontend to hunt for the
        // image across eight candidate field names.
        String qrImageUrl = normalizeImage(body == null ? null : str(body, "qrImageUrl"));
        if (qrImageUrl == null) {
            log.error("Safaricom ETHQR response for merchant {} carried no qrImageUrl. Target {}, body: {}",
                    merchantId, ethQrBaseUrl, body);
            throw new UpstreamServiceException(UPSTREAM_ETHQR,
                    "Safaricom returned no QR image for this short code.");
        }

        // The provider's own merchant profile for that short code is
        // authoritative for anything PRINTED — a standee must not claim a
        // name Safaricom did not confirm. The local record is only a
        // fallback for a response that omits the field.
        return EthQrResponse.builder()
                .qrImageUrl(qrImageUrl)
                .merchantName(orDefault(str(body, "merchantName"), merchant.name()))
                .accountNumber(orDefault(str(body, "accountNumber"), merchant.shortCode()))
                .mobileNumber(str(body, "mobileNumber"))
                .city(str(body, "city"))
                .build();
    }

    /**
     * Turns an error status plus response body into something a merchant can
     * act on. Three genuinely different failures reach here, and conflating
     * them costs real debugging time:
     *
     * <ol>
     *   <li><b>A network security appliance blocked the request.</b> Safaricom
     *       never saw it. The appliance returns its own interstitial —
     *       {@code {"page_title":"Web Page Blocked!", ..., "attack_ID":...}}
     *       — under whatever status it likes, in practice 500. Nothing about
     *       the merchant, the short code or Safaricom is wrong; the egress
     *       path from THIS deployment is being filtered.</li>
     *   <li><b>Safaricom refused the request (4xx).</b> Here an unregistered
     *       or mistyped short code really is the likely cause.</li>
     *   <li><b>Safaricom's own service failed (5xx).</b> Their side, and
     *       retryable. This is NOT evidence of anything about the short
     *       code.</li>
     * </ol>
     *
     * <p>This method exists because all three used to produce "Safaricom
     * rejected the payment QR request. The short code may not be registered
     * for ETHQR." A blocked-egress 500 therefore sent an operator hunting
     * through merchant records for a short-code fault while the short code
     * was valid the whole time — the request had not reached Safaricom at
     * all. A 500 is a server error by definition and can never imply the
     * client's short code is unregistered.
     */
    static String describeUpstreamFailure(int status, String responseBody) {
        if (looksLikeSecurityApplianceBlock(responseBody)) {
            return "The payment QR request was blocked by a network security appliance before it reached "
                    + "Safaricom, so this is not a problem with the merchant's short code. Allow outbound "
                    + "HTTPS to qr.safaricom.et from this deployment's egress address, then retry.";
        }
        if (status >= 400 && status < 500) {
            return "Safaricom rejected the payment QR request (HTTP " + status
                    + "). The short code may not be registered for ETHQR.";
        }
        return "Safaricom's QR service failed to generate a code (HTTP " + status
                + "). This is an error on their side, not a problem with the short code — please retry.";
    }

    /**
     * Recognises a blocking proxy/WAF interstitial rather than a real API
     * response.
     *
     * <p>Matched on the two fields such pages carry that a JSON payment API
     * has no reason to emit — a {@code page_title} announcing a block, and an
     * {@code attack_ID}. Deliberately not matched on the vendor name or the
     * exact wording: the appliance in front of a given deployment is not
     * something this service can know, and every one of them phrases its page
     * differently.
     */
    private static boolean looksLikeSecurityApplianceBlock(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String lower = responseBody.toLowerCase();
        return lower.contains("page blocked")
                || lower.contains("attack_id")
                || (lower.contains("access denied") && lower.contains("firewall"));
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        return s;
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }

    /**
     * The documented schema already delivers {@code qrImageUrl} as a
     * {@code data:} URL, so this is normally a passthrough. Bare base64 is
     * still wrapped rather than rejected: it costs one branch here and
     * spares every consumer — the live preview, the print path and the PDF
     * export — from each having to decide what an unprefixed string is.
     */
    private static String normalizeImage(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("data:") || raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        return "data:image/png;base64," + raw;
    }

    private record MerchantSummary(String name, String shortCode) {}

    /**
     * Mirrors MenuService.fetchBranchMerchantId's RestTemplate +
     * forwarded-Authorization-header pattern.
     *
     * <p>A 404 from merchant-service means the branch really is absent. Any
     * other failure — connection refused, a 500, a timeout — is reported as
     * an upstream failure, NOT as "Branch not found": this method used to
     * catch {@code Exception} wholesale and answer 404 for all of them, so a
     * merchant-service outage told the merchant their branch did not exist.
     * That is precisely the misdirection ServiceUnavailableException was
     * introduced to end elsewhere in this codebase.
     *
     * <p>A 403 is handled separately and deliberately. merchant-service
     * enforces tenant isolation on this endpoint, so it answers 403 when the
     * branch belongs to someone else — which is the answer this method
     * wants, not an upstream fault. Folding it into the upstream branch
     * reported a cross-tenant attempt as {@code 502 "could not verify"},
     * dressing up a denied read as broken infrastructure. It becomes the
     * same AccessDeniedException the explicit ownership check below raises.
     */
    private UUID fetchBranchMerchantId(Long branchId) {
        Map<String, Object> body;
        try {
            String url = merchantServiceUrl + "/api/branches/" + branchId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            throw new AccessDeniedException("Branch " + branchId + " does not belong to your merchant");
        } catch (RestClientException e) {
            log.error("Failed to verify branch {} ownership via merchant-service at {}", branchId, merchantServiceUrl, e);
            throw new UpstreamServiceException(UPSTREAM_MERCHANT,
                    "Could not verify which merchant this branch belongs to. Please retry.", e);
        }
        if (body == null || body.get("merchantId") == null) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
        try {
            return UUID.fromString((String) body.get("merchantId"));
        } catch (IllegalArgumentException | ClassCastException e) {
            // A 200 carrying an unusable merchantId is merchant-service
            // breaking its own contract, not a missing branch.
            log.error("merchant-service returned an unusable merchantId for branch {}: {}", branchId, body.get("merchantId"));
            throw new UpstreamServiceException(UPSTREAM_MERCHANT,
                    "Received an invalid merchant reference for this branch.", e);
        }
    }

    /** Same 404 / 403 / outage split as {@link #fetchBranchMerchantId}. */
    private MerchantSummary fetchMerchant(UUID merchantId) {
        Map<String, Object> body;
        try {
            String url = merchantServiceUrl + "/api/merchants/" + merchantId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            body = response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Merchant not found: " + merchantId);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            throw new AccessDeniedException("Merchant " + merchantId + " is not accessible to you");
        } catch (RestClientException e) {
            log.error("Failed to fetch merchant {} from merchant-service at {}", merchantId, merchantServiceUrl, e);
            throw new UpstreamServiceException(UPSTREAM_MERCHANT,
                    "Could not load this merchant's payment details. Please retry.", e);
        }
        if (body == null) {
            throw new ResourceNotFoundException("Merchant not found: " + merchantId);
        }
        return new MerchantSummary((String) body.get("name"), (String) body.get("shortCode"));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authToken != null && !authToken.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, authToken);
            }
        }
        return headers;
    }
}
