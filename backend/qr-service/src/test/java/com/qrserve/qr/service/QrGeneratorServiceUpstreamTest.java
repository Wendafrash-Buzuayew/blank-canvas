package com.qrserve.qr.service;

import com.qrserve.shared.exceptions.ResourceNotFoundException;
import com.qrserve.shared.exceptions.UpstreamServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Only a real 404 from merchant-service may become "not found"; everything else is a 502. */
class QrGeneratorServiceUpstreamTest {

    private static RuntimeException translate(Exception e) {
        return QrGeneratorService.translateMerchantServiceFailure(
                (org.springframework.web.client.RestClientException) e, "http://merchant/x", "Table not found ID: 7");
    }

    @Test
    @DisplayName("404 from merchant-service -> ResourceNotFoundException")
    void notFound() {
        RuntimeException ex = translate(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        assertInstanceOf(ResourceNotFoundException.class, ex);
        assertEquals("Table not found ID: 7", ex.getMessage());
    }

    @Test
    @DisplayName("5xx, timeouts and other 4xx -> UpstreamServiceException naming merchant-service")
    void upstreamFailures() {
        for (Exception e : new Exception[] {
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null),
                new ResourceAccessException("Read timed out"),
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "nope", null, null, null)}) {
            RuntimeException ex = translate(e);
            assertInstanceOf(UpstreamServiceException.class, ex, e.toString());
            assertEquals("merchant-service", ((UpstreamServiceException) ex).getUpstream());
        }
    }
}
