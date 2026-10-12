package com.qrserve.shared.exceptions;

import com.qrserve.shared.exceptions.GlobalExceptionHandler.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertStatus(HttpStatus expected, ResponseEntity<ErrorResponse> response) {
        assertEquals(expected.value(), response.getStatusCode().value());
        assertEquals(expected.value(), response.getBody().getStatus());
    }

    @Test
    @DisplayName("malformed JSON body is a 400, not a 500")
    void malformedBody() {
        ResponseEntity<ErrorResponse> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("JSON parse error", (HttpInputMessage) null));
        assertStatus(HttpStatus.BAD_REQUEST, response);
        assertEquals("Malformed request body", response.getBody().getMessage());
    }

    @Test
    @DisplayName("missing request parameter is a 400")
    void missingParameter() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new MissingServletRequestParameterException("branchId", "Long"));
        assertStatus(HttpStatus.BAD_REQUEST, response);
        assertTrue(response.getBody().getMessage().contains("branchId"));
    }

    @Test
    @DisplayName("mismatched parameter type is a 400")
    void typeMismatch() {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null));
        assertStatus(HttpStatus.BAD_REQUEST, response);
        assertTrue(response.getBody().getMessage().contains("'id'"));
    }

    @Test
    @DisplayName("unsupported method is a 405 with an Allow header")
    void methodNotAllowed() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")));
        assertStatus(HttpStatus.METHOD_NOT_ALLOWED, response);
        assertEquals("GET", response.getHeaders().getFirst(HttpHeaders.ALLOW));
    }

    @Test
    @DisplayName("unsupported media type is a 415")
    void unsupportedMediaType() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response);
    }

    @Test
    @DisplayName("a 4xx ResponseStatusException keeps its status")
    void responseStatus4xx() {
        assertStatus(HttpStatus.NOT_FOUND,
                handler.handleGeneralException(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Test
    @DisplayName("a 5xx ResponseStatusException stays a generic 500")
    void responseStatus5xx() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "http://internal-host failed"));
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, response);
        assertEquals("An unexpected server error occurred", response.getBody().getMessage());
    }

    @Test
    @DisplayName("oversized upload is a 413")
    void maxUploadSize() {
        assertStatus(HttpStatus.CONTENT_TOO_LARGE,
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1024)));
    }

    @Test
    @DisplayName("an unknown exception is still a generic 500")
    void unknownException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new IllegalStateException("SELECT * FROM secrets"));
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, response);
        assertEquals("An unexpected server error occurred", response.getBody().getMessage());
    }

    @Test
    @DisplayName("persistence conflicts are matched by class name up the hierarchy")
    void hasTypeWalksSuperclasses() {
        // spring-tx is not on this module's classpath, so the hierarchy walk is
        // exercised with JDK types; the real names are pinned below.
        assertTrue(GlobalExceptionHandler.hasType(new FileNotFoundException(), "java.io.IOException"));
        assertFalse(GlobalExceptionHandler.hasType(new RuntimeException(),
                GlobalExceptionHandler.DATA_INTEGRITY_VIOLATION));
        assertEquals("org.springframework.dao.DataIntegrityViolationException",
                GlobalExceptionHandler.DATA_INTEGRITY_VIOLATION);
        assertEquals("org.springframework.dao.OptimisticLockingFailureException",
                GlobalExceptionHandler.OPTIMISTIC_LOCKING_FAILURE);
    }

    @Test
    @DisplayName("DataIntegrityViolationException and ObjectOptimisticLockingFailureException are 409 (when on the classpath)")
    void persistenceConflictsAre409() throws Exception {
        assertStatus(HttpStatus.CONFLICT, handler.handleGeneralException(
                instantiate("org.springframework.dao.DataIntegrityViolationException")));
        assertStatus(HttpStatus.CONFLICT, handler.handleGeneralException(
                instantiate("org.springframework.orm.ObjectOptimisticLockingFailureException")));
    }

    private static Exception instantiate(String className) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            assumeTrue(false, className + " not on this module's test classpath");
            throw e;
        }
        return (Exception) type.getConstructor(String.class, Throwable.class).newInstance("conflict", null);
    }
}
