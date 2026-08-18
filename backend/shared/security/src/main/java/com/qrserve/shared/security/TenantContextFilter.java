package com.qrserve.shared.security;

import com.qrserve.shared.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Populates {@link TenantContext} from the gateway-injected {@code X-Tenant-Id}
 * and enforces the precedence rule between the two sources of tenant identity.
 *
 * <h2>Precedence</h2>
 * <table>
 *   <tr><th>Request kind</th><th>Tenant source</th><th>On mismatch</th></tr>
 *   <tr><td>Anonymous</td><td>the host</td><td>n/a — the host is the only signal</td></tr>
 *   <tr><td>Authenticated staff</td><td>the <b>JWT</b></td><td><b>403</b></td></tr>
 *   <tr><td>SUPER_ADMIN</td><td>none asserted</td><td>n/a</td></tr>
 * </table>
 *
 * <p>The host never grants authority the JWT does not already carry. A waiter at
 * merchant A who points a browser at {@code merchant-b.qrserve.safaricom.et}
 * receives 403 rather than merchant B's data. Enforcing that here, once, is the
 * point: as a per-controller convention it would be forgotten on the next
 * endpoint someone adds.
 *
 * <p>Absence of the header is <b>not</b> an error. It means the request did not
 * arrive through a tenant host — direct service access, a kubelet probe, or
 * localhost during development — and the JWT then supplies the tenant. What is
 * never allowed is inventing one.
 *
 * <p>The header itself is only trustworthy because
 * {@code TenantResolutionGlobalFilter} strips any inbound copy at the gateway
 * before injecting the resolved value.
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * Duplicated from the gateway's constant rather than imported:
     * {@code shared:security} is consumed by every servlet service and must not
     * depend on {@code api-gateway}. The two must stay in step; the filter test and
     * the gateway test both name the literal.
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        UUID hostTenant = parseTenantHeader(request);
        UUID jwtTenant = authenticatedMerchantId();
        boolean superAdmin = isSuperAdmin();

        if (!superAdmin && hostTenant != null && jwtTenant != null && !hostTenant.equals(jwtTenant)) {
            log.warn("Tenant mismatch: host asserts {}, token carries {} on {} {}",
                    hostTenant, jwtTenant, request.getMethod(), request.getRequestURI());
            writeForbidden(response);
            return;
        }

        // SUPER_ADMIN asserts no tenant: cross-tenant work must not be silently
        // pinned to whichever hostname happened to be used to reach it.
        UUID effective = superAdmin ? null : (jwtTenant != null ? jwtTenant : hostTenant);

        try {
            if (effective != null) {
                TenantContext.setCurrentTenant(effective);
            }
            filterChain.doFilter(request, response);
        } finally {
            // MUST be in a finally block. Servlet containers pool request threads, so
            // a value left behind here is served to the next request on this thread —
            // one tenant's context applied to another tenant's request.
            TenantContext.clear();
        }
    }

    private UUID parseTenantHeader(HttpServletRequest request) {
        String raw = request.getHeader(TENANT_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            // Only the gateway sets this header, so a malformed value means a
            // misconfiguration rather than an attack — and treating it as "no tenant"
            // is safer than failing the request, because the JWT check is what
            // actually protects the data.
            log.warn("Ignoring malformed {} header", TENANT_ID_HEADER);
            return null;
        }
    }

    private UUID authenticatedMerchantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getMerchantId();
        }
        return null;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal
                && principal.getRole() == UserRole.SUPER_ADMIN;
    }

    /**
     * Written directly rather than thrown as {@code AccessDeniedException}: an
     * exception raised in a filter never reaches {@code GlobalExceptionHandler},
     * which is a {@code @RestControllerAdvice} and only covers dispatched requests.
     * The body matches {@code GlobalExceptionHandler.ErrorResponse} so clients see
     * one error shape.
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,"
                        + "\"message\":\"This account does not belong to the tenant in the address bar.\","
                        + "\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }
}
