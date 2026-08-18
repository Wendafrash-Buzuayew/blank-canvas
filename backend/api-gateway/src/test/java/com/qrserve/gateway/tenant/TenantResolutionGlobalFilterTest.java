package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolutionGlobalFilterTest {

    private static final UUID MERCHANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private TenantResolutionGlobalFilter filter;
    /** Captures the exchange the chain actually received, i.e. post-mutation. */
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve(anyString())).thenReturn(Mono.empty());
        when(resolver.resolve("sunrise")).thenReturn(Mono.just(MERCHANT_ID));

        filter = new TenantResolutionGlobalFilter(resolver, "qrserve.safaricom.et");

        forwarded = new AtomicReference<>();
        chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchange(String host, HttpHeaders extra) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
                .get("/api/v1/public/menu/sunrise/main/1")
                .header(HttpHeaders.HOST, host);
        extra.forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
        return MockServerWebExchange.from(builder.build());
    }

    private HttpHeaders forwardedHeaders() {
        assertNotNull(forwarded.get(), "the chain was not invoked");
        return forwarded.get().getRequest().getHeaders();
    }

    @Test
    @DisplayName("a known tenant host injects X-Tenant-Id and X-Tenant-Slug")
    void injectsTenantHeaders() {
        filter.filter(exchange("sunrise.qrserve.safaricom.et", new HttpHeaders()), chain).block();

        assertEquals(MERCHANT_ID.toString(),
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
        assertEquals("sunrise",
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER));
    }

    @Test
    @DisplayName("an INBOUND X-Tenant-Id is stripped, never trusted")
    void stripsInboundTenantHeader() {
        // Without this the header is decorative: any caller sets it and every
        // downstream service believes it. This is the single most important
        // assertion in the tenancy work.
        HttpHeaders forged = new HttpHeaders();
        forged.add(TenantResolutionGlobalFilter.TENANT_ID_HEADER, UUID.randomUUID().toString());
        forged.add(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER, "victim");

        filter.filter(exchange("sunrise.qrserve.safaricom.et", forged), chain).block();

        assertEquals(MERCHANT_ID.toString(),
                forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER),
                "the forged value must be replaced by the host-resolved one");
        assertEquals(1, forwardedHeaders().get(TenantResolutionGlobalFilter.TENANT_ID_HEADER).size(),
                "the forged value must not survive alongside the real one");
    }

    @Test
    @DisplayName("a forged header on a NON-tenant host is stripped and not replaced")
    void stripsForgedHeaderOnNonTenantHost() {
        // The dangerous case: with no host label, nothing overwrites the forgery, so
        // removal has to happen unconditionally rather than as a side effect of
        // injection.
        HttpHeaders forged = new HttpHeaders();
        forged.add(TenantResolutionGlobalFilter.TENANT_ID_HEADER, UUID.randomUUID().toString());

        filter.filter(exchange("localhost:8081", forged), chain).block();

        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
    }

    @Test
    @DisplayName("an unknown tenant label is 404 and the chain is never invoked")
    void unknownTenantIsNotFound() {
        MockServerWebExchange ex = exchange("no-such-tenant.qrserve.safaricom.et", new HttpHeaders());

        filter.filter(ex, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
        assertNull(forwarded.get(), "an unresolvable tenant must not reach a service");
    }

    @Test
    @DisplayName("a non-tenant host passes through with no tenant header")
    void nonTenantHostPassesThrough() {
        filter.filter(exchange("localhost:8081", new HttpHeaders()), chain).block();

        assertNotNull(forwarded.get(), "localhost must keep working for development and probes");
        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
    }

    @Test
    @DisplayName("the admin host asserts no tenant")
    void adminHostAssertsNoTenant() {
        filter.filter(exchange("admin.qrserve.safaricom.et", new HttpHeaders()), chain).block();

        assertNotNull(forwarded.get());
        assertNull(forwardedHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER),
                "SUPER_ADMIN cross-tenant work must not be pinned to a tenant");
    }

    @Test
    @DisplayName("the filter runs before routing")
    void runsBeforeRouting() {
        // A tenant header injected after the routing filter has already copied the
        // request would never reach the service.
        assertTrue(filter.getOrder() < 0, "must be ordered ahead of the routing filter");
    }
}
