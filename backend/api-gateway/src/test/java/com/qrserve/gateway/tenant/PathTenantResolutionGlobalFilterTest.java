package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class PathTenantResolutionGlobalFilterTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @Test
    void resolvesTheMerchantSlugFromThePathAndInjectsHeaders() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve("sunrise")).thenReturn(Mono.just(MERCHANT_ID));
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/public/digital-menu/sunrise/main").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest mutated = captor.getValue().getRequest();
        assertEquals(MERCHANT_ID.toString(), mutated.getHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_ID_HEADER));
        assertEquals("sunrise", mutated.getHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER));
        // Regression guard: a successful resolution must not also trip the
        // not-found branch (chain.filter(...) returns an always-empty Mono<Void>,
        // which switchIfEmpty must not mistake for "resolution failed").
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void unknownSlugIs404NotAFallback() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        when(resolver.resolve("ghost")).thenReturn(Mono.empty());
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/public/digital-menu/ghost").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void pathsOutsideTheDigitalMenuPrefixAreIgnored() {
        TenantSlugResolver resolver = mock(TenantSlugResolver.class);
        PathTenantResolutionGlobalFilter filter = new PathTenantResolutionGlobalFilter(resolver);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(resolver);
    }
}
