package com.qrserve.gateway.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves tenant identity from the URL PATH, for the phase-1 digital-menu
 * routes only ({@code /api/v1/public/digital-menu/**}). A sibling of
 * {@link TenantResolutionGlobalFilter} (host-based, for the parked
 * subdomain+table scheme) — that filter is untouched. Both domains
 * (menu.safaricom.et vs. the tenant-subdomain host) never collide, so there
 * is no ambiguity about which filter's output applies to a given request.
 */
@Component
@Slf4j
public class PathTenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    private static final String DIGITAL_MENU_PREFIX = "/api/v1/public/digital-menu/";

    private final TenantSlugResolver resolver;

    public PathTenantResolutionGlobalFilter(TenantSlugResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(DIGITAL_MENU_PREFIX)) {
            return chain.filter(exchange);
        }

        String remainder = path.substring(DIGITAL_MENU_PREFIX.length());
        String merchantSlug = remainder.split("/", 2)[0];
        if (merchantSlug.isBlank()) {
            return chain.filter(exchange);
        }

        return resolver.resolve(merchantSlug)
                // switchIfEmpty must sit on resolve()'s Mono<UUID> directly, not on the
                // flatMap'd result: chain.filter(...) returns Mono<Void>, which always
                // completes empty even on success. Chaining switchIfEmpty after the
                // flatMap would fire it on every request, successful or not.
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No tenant for path-derived slug '{}'", merchantSlug);
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                    return exchange.getResponse().setComplete().then(Mono.<java.util.UUID>empty());
                }))
                .flatMap(merchantId -> chain.filter(withTenant(exchange, merchantId, merchantSlug)));
    }

    private ServerWebExchange withTenant(ServerWebExchange exchange, java.util.UUID merchantId, String slug) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(TenantResolutionGlobalFilter.TENANT_ID_HEADER, merchantId.toString());
                    headers.set(TenantResolutionGlobalFilter.TENANT_SLUG_HEADER, slug);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /**
     * Deliberately NOT -100 (the same value {@link TenantResolutionGlobalFilter} uses).
     * Spring does not guarantee a tie-break order between two {@link GlobalFilter}
     * beans with equal {@link Ordered} values — it falls back to bean registration
     * order, which neither class controls. {@code TenantResolutionGlobalFilter}
     * unconditionally strips {@code X-Tenant-*} headers on any host with no tenant
     * label in it (e.g. {@code menu.safaricom.et}), which is exactly the host this
     * filter's routes are served from. Running one step after -100 guarantees that
     * unconditional strip always happens first (harmlessly, before anything has been
     * set) and this filter's header injection always happens last and survives.
     */
    @Override
    public int getOrder() {
        return -99;
    }
}
