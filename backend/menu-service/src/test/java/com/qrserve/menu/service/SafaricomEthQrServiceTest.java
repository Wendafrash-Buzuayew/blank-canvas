package com.qrserve.menu.service;

import com.qrserve.menu.dto.EthQrResponse;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UpstreamServiceException;
import com.qrserve.shared.security.UserPrincipal;
import com.qrserve.shared.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * generate() has two remote calls before it ever reaches Safaricom: fetch
 * the branch (ownership + merchantId), then fetch the merchant (shortCode +
 * name). Both go through the same mocked RestTemplate, matched by URL
 * substring, same shape as MenuServiceBranchManagementTest.
 *
 * <p>The request-body assertions below are the point of this suite. The
 * provider answers an {@code accountNumber}-only request with a bare QR and
 * a merchantName/amount request with a completely different pre-composed
 * card, so "exactly one field in the body" is a correctness property, not a
 * tidiness preference — see SafaricomEthQrService's class comment.
 */
class SafaricomEthQrServiceTest {

    private RestTemplate restTemplate;
    private SafaricomEthQrService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final String ETH_QR_URL = "https://qr.safaricom.et/api/qr/generate";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new SafaricomEthQrService(restTemplate);
        ReflectionTestUtils.setField(service, "merchantServiceUrl", "http://merchant-service");
        ReflectionTestUtils.setField(service, "ethQrBaseUrl", ETH_QR_URL);
        ReflectionTestUtils.setField(service, "apiKey", "");
    }

    private static UserPrincipal principal(UUID merchantId, UserRole role) {
        return UserPrincipal.builder().userId(UUID.randomUUID()).merchantId(merchantId).role(role).build();
    }

    @SuppressWarnings("unchecked")
    private void stubBranch(UUID ownerMerchantId) {
        when(restTemplate.exchange(
                contains("/api/branches/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("merchantId", ownerMerchantId.toString())));
    }

    @SuppressWarnings("unchecked")
    private void stubMerchant(String name, String shortCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("shortCode", shortCode);
        when(restTemplate.exchange(
                contains("/api/merchants/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    /** The documented response for an accountNumber-only request. */
    private static Map<String, Object> providerBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("qrImageUrl", "data:image/png;base64,iVBORw0KGgo");
        body.put("merchantName", "Muzemil Erichamo Anjilo");
        body.put("accountNumber", "8319389");
        body.put("mobileNumber", "+251718788479");
        body.put("city", null);
        return body;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<HttpEntity<?>> stubProvider(Map<String, Object> body) {
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq(ETH_QR_URL), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));
        return captor;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> bodyOf(ArgumentCaptor<HttpEntity<?>> captor) {
        return (Map<String, String>) captor.getValue().getBody();
    }

    // ---- the request contract ------------------------------------------

    @Test
    @DisplayName("the request body is accountNumber and nothing else")
    void requestBodyCarriesOnlyTheAccountNumber() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        ArgumentCaptor<HttpEntity<?>> captor = stubProvider(providerBody());

        service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        Map<String, String> sent = bodyOf(captor);
        assertEquals(Map.of("accountNumber", "8319389"), sent);
        // Named explicitly: these two are what the old code added, and their
        // presence silently switches the provider to the other response shape.
        assertFalse(sent.containsKey("merchantName"));
        assertFalse(sent.containsKey("amount"));
        assertEquals(1, sent.size());
    }

    @Test
    @DisplayName("the short code is sent, not the merchant's name or id")
    void accountNumberIsTheShortCode() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "7654321");
        ArgumentCaptor<HttpEntity<?>> captor = stubProvider(providerBody());

        service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals("7654321", bodyOf(captor).get("accountNumber"));
    }

    @Test
    @DisplayName("Content-Type is application/json, and no Authorization header is sent without a key")
    void headersMatchTheSpecWhenNoApiKeyIsConfigured() {
        // A real call with no Authorization header at all was confirmed to
        // succeed, so a missing key must not block the call.
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        ArgumentCaptor<HttpEntity<?>> captor = stubProvider(providerBody());

        service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("a configured api key is sent as a bearer token")
    void aConfiguredApiKeyIsSent() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ArgumentCaptor<HttpEntity<?>> captor = stubProvider(providerBody());

        service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals("Bearer test-key", captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    // ---- the response mapping ------------------------------------------

    @Test
    @DisplayName("the documented response maps field-for-field")
    void mapsTheDocumentedResponse() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        stubProvider(providerBody());

        EthQrResponse response = service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals("data:image/png;base64,iVBORw0KGgo", response.getQrImageUrl());
        // The PROVIDER's name, not the local record's "Sunrise Cafe" — a
        // standee must only print what Safaricom confirmed for that code.
        assertEquals("Muzemil Erichamo Anjilo", response.getMerchantName());
        assertEquals("8319389", response.getAccountNumber());
        assertEquals("+251718788479", response.getMobileNumber());
        assertNull(response.getCity());
    }

    @Test
    @DisplayName("bare base64 is normalised into a data URL")
    void bareBase64IsNormalised() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        Map<String, Object> body = providerBody();
        body.put("qrImageUrl", "iVBORw0KGgoAAAA");
        stubProvider(body);

        EthQrResponse response = service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals("data:image/png;base64,iVBORw0KGgoAAAA", response.getQrImageUrl());
    }

    @Test
    @DisplayName("an http(s) image URL is passed through untouched")
    void remoteImageUrlIsPassedThrough() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        Map<String, Object> body = providerBody();
        body.put("qrImageUrl", "https://cdn.safaricom.et/qr/8319389.png");
        stubProvider(body);

        assertEquals("https://cdn.safaricom.et/qr/8319389.png",
                service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)).getQrImageUrl());
    }

    @Test
    @DisplayName("merchantName and accountNumber fall back to the local record when the provider omits them")
    void omittedProviderFieldsFallBackLocally() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("qrImageUrl", "data:image/png;base64,iVBORw0KGgo");
        stubProvider(body);

        EthQrResponse response = service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER));

        assertEquals("Sunrise Cafe", response.getMerchantName());
        assertEquals("8319389", response.getAccountNumber());
        // Nothing to fall back to, and nothing invented — EthQrCard omits
        // the digit strip entirely on a null number.
        assertNull(response.getMobileNumber());
    }

    @Test
    @DisplayName("a 200 with no qrImageUrl fails here rather than reaching the UI")
    void aResponseWithNoImageIsAServiceFailure() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        stubProvider(Map.of("merchantName", "Muzemil Erichamo Anjilo"));

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        assertTrue(ex.getMessage().contains("no QR image"));
        assertEquals("safaricom-ethqr", ex.getUpstream());
    }

    @Test
    @DisplayName("a blank qrImageUrl counts as missing, not as an empty image")
    void aBlankImageIsTreatedAsMissing() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        Map<String, Object> body = providerBody();
        body.put("qrImageUrl", "   ");
        stubProvider(body);

        assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("an empty response body is a service failure")
    void anEmptyBodyIsAServiceFailure() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        stubProvider(null);

        assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    // ---- authorization and failure modes --------------------------------

    @Test
    void rejectsABranchThatBelongsToAnotherMerchant() {
        stubBranch(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    void nullPrincipalIsDenied() {
        assertThrows(AccessDeniedException.class, () -> service.generate(BRANCH, null));
    }

    @Test
    void refusesWhenTheMerchantHasNoShortCodeOnFile() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        assertTrue(ex.getMessage().contains("short code"));
        // Never reached Safaricom — there is no account number to ask about.
        verify(restTemplate, never()).exchange(
                eq(ETH_QR_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    /**
     * The exact failure a stale container produced: the configured endpoint
     * pointed at a host that no longer existed, so every call died in
     * connect. It used to surface as a flat 503 "a required service is
     * temporarily unavailable" — true of all three remote calls behind this
     * endpoint, and therefore useless. It must now name the dependency.
     */
    @Test
    @DisplayName("an unreachable endpoint is a named 502, not an anonymous 503")
    @SuppressWarnings("unchecked")
    void anUnreachableProviderNamesTheUpstream() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        when(restTemplate.exchange(
                eq(ETH_QR_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("I/O error on POST request", new ConnectException()));

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        assertEquals("safaricom-ethqr", ex.getUpstream());
        // Points the reader at configuration, which is what was actually wrong.
        assertTrue(ex.getMessage().contains("endpoint"), ex.getMessage());
    }

    /**
     * Safaricom answering with a 4xx is the signal that the PAYLOAD is
     * wrong — the failure mode extraneous request fields produce. Its status
     * has to reach the caller instead of being flattened into "retry".
     */
    @Test
    @DisplayName("a rejection from Safaricom surfaces its real status code")
    @SuppressWarnings("unchecked")
    void aProviderRejectionSurfacesItsStatus() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        when(restTemplate.exchange(
                eq(ETH_QR_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        "{\"error\":\"unknown account\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        assertEquals("safaricom-ethqr", ex.getUpstream());
        assertTrue(ex.getMessage().contains("400"), ex.getMessage());
    }

    // ---- merchant-service failures are not "not found" -------------------

    /**
     * This method used to catch Exception wholesale and answer
     * "Branch not found", so a merchant-service outage told the merchant
     * their branch did not exist.
     */
    @Test
    @DisplayName("a merchant-service outage is an upstream failure, not a missing branch")
    @SuppressWarnings("unchecked")
    void aBranchLookupOutageIsNotAMissingBranch() {
        when(restTemplate.exchange(
                contains("/api/branches/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("connection refused", new ConnectException()));

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
        assertEquals("merchant-service", ex.getUpstream());
    }

    @Test
    @DisplayName("a genuine 404 from merchant-service is still a missing branch")
    @SuppressWarnings("unchecked")
    void aGenuine404IsStillNotFound() {
        when(restTemplate.exchange(
                contains("/api/branches/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(ResourceNotFoundException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    /**
     * Caught in live testing: merchant-service enforces tenant isolation on
     * the branch endpoint, so a cross-tenant read comes back 403. Lumping
     * that in with connection failures reported an attempt to read another
     * merchant's branch as {@code 502 "could not verify which merchant this
     * branch belongs to"} — an infrastructure story for what is actually a
     * denied read, and a 502 invites a retry where none should happen.
     */
    @Test
    @DisplayName("a 403 from merchant-service is a denial, not an upstream fault")
    @SuppressWarnings("unchecked")
    void aForbiddenBranchReadIsADenial() {
        when(restTemplate.exchange(
                contains("/api/branches/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(AccessDeniedException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    @DisplayName("a 401 on the forwarded token is also a denial, not a 502")
    @SuppressWarnings("unchecked")
    void anUnauthorizedBranchReadIsADenial() {
        when(restTemplate.exchange(
                contains("/api/branches/"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(AccessDeniedException.class,
                () -> service.generate(BRANCH, principal(MERCHANT, UserRole.MERCHANT_OWNER)));
    }

    @Test
    void superAdminCanGenerateForAnyMerchantsBranch() {
        stubBranch(MERCHANT);
        stubMerchant("Sunrise Cafe", "8319389");
        stubProvider(providerBody());

        assertDoesNotThrow(() -> service.generate(BRANCH, principal(UUID.randomUUID(), UserRole.SUPER_ADMIN)));
    }
}
