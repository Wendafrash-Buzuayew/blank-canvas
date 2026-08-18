package com.qrserve.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Target of the CircuitBreaker filter's {@code fallbackUri: forward:/fallback}.
 *
 * <p>Without this endpoint the breaker would forward to a non-existent path and
 * surface a 404 instead of a 503, hiding the fact that an upstream is down.
 *
 * <p>Annotation-based controllers are used rather than a functional
 * RouterFunction because {@code @RestController} / {@code @RequestMapping} live
 * in spring-web, which is on the WebFlux classpath — spring-webmvc is
 * deliberately excluded for this module (see build.gradle).
 */
@RestController
public class FallbackController {

    @RequestMapping(value = "/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> fallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Upstream service is temporarily unavailable. Please retry.")));
    }
}
