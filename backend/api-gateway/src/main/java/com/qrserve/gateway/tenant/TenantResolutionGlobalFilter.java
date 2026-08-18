package com.qrserve.gateway.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes tenant identity once, at the edge.
 *
 * <p>Four steps, in this order, for every request:
 * <ol>
 *   <li><b>Strip</b> any inbound {@code X-Tenant-*} header. Unconditionally —
 *       including on hosts that carry no tenant, where nothing would otherwise
 *       overwrite a forgery. Downstream services trust this header; that trust is
 *       earned by this step alone.</li>
 *   <li><b>Extract</b> the first DNS label from {@code Host}.</li>
 *   <li><b>Resolve</b> it to a merchant id through Redis, or 404.</li>
 *   <li><b>Inject</b> {@code X-Tenant-Id} and {@code X-Tenant-Slug}.</li>
 * </ol>
 *
 * <p>A host with no tenant label is not an error: localhost, cluster-internal
 * names, kubelet probes and {@code admin.} all pass through carrying no tenant.
 * What <em>is</em> an error is a label that looks like a tenant and is not — that
 * is a 404, never a fallback to some default tenant, because on a wildcard domain
 * a fallback would turn every mistyped subdomain into a cross-tenant read.
 */
@Component
@Slf4j
public class TenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

    private final TenantSlugResolver resolver;
    private final String baseDomain;

    public TenantResolutionGlobalFilter(
            TenantSlugResolver resolver,
            @Value("${app.public-base-domain}") String baseDomain) {
        this.resolver = resolver;
        this.baseDomain = baseDomain;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String hostHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
        if (hostHeader == null) {
            hostHeader = exchange.getRequest().getURI().getHost();
        }

        String label = TenantHost.labelFrom(hostHeader, baseDomain);

        if (label == null) {
            // No tenant in this host. Strip anyway — this is the branch where a
            // forged header would otherwise survive untouched.
            return chain.filter(withoutTenantHeaders(exchange));
        }

        return resolver.resolve(label)
                .flatMap(merchantId -> chain.filter(withTenant(exchange, merchantId, label)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No tenant for host label '{}'", label);
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                    return exchange.getResponse().setComplete();
                }));
    }

    private ServerWebExchange withoutTenantHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(TENANT_ID_HEADER);
                    headers.remove(TENANT_SLUG_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withTenant(ServerWebExchange exchange, UUID merchantId, String slug) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // set(), not add(): a forged value must be replaced, not joined.
                    headers.set(TENANT_ID_HEADER, merchantId.toString());
                    headers.set(TENANT_SLUG_HEADER, slug);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /**
     * Ahead of the routing filter. A header injected after routing has already
     * copied the request would never reach the service.
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
