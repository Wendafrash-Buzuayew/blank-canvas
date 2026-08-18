package com.qrserve.shared.security;

import com.qrserve.shared.common.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextFilterTest {

    private static final UUID MERCHANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MERCHANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final TenantContextFilter filter = new TenantContextFilter();
    private final AtomicBoolean chainRan = new AtomicBoolean(false);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(UUID merchantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID()).merchantId(merchantId)
                .email("staff@test").role(role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MockHttpServletRequest request(UUID tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (tenantHeader != null) {
            request.addHeader(TenantContextFilter.TENANT_ID_HEADER, tenantHeader.toString());
        }
        return request;
    }

    /** Captures the tenant as it was DURING the chain, since the filter clears it after. */
    private UUID runFilter(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<UUID> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            chainRan.set(true);
            seen.set(TenantContext.getCurrentTenant());
        };
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    @DisplayName("an anonymous request takes its tenant from the host header")
    void anonymousTakesTenantFromHost() throws Exception {
        assertEquals(MERCHANT_A, runFilter(request(MERCHANT_A), new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("a matching host and JWT proceed normally")
    void matchingHostAndJwtProceed() throws Exception {
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertEquals(MERCHANT_A, runFilter(request(MERCHANT_A), res));
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("a host that disagrees with the JWT is 403 and the chain does not run")
    void mismatchIsForbidden() throws Exception {
        // A waiter at merchant A pointing a browser at merchant-b.qrserve.safaricom.et.
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        MockHttpServletResponse res = new MockHttpServletResponse();

        runFilter(request(MERCHANT_B), res);

        assertEquals(403, res.getStatus());
        assertFalse(chainRan.get(), "the request must not reach the controller");
        assertTrue(res.getContentAsString().contains("403"),
                "the body must be the standard error shape, not empty");
    }

    @Test
    @DisplayName("the JWT wins: staff on a non-tenant host still carry their own tenant")
    void jwtIsAuthoritativeWhenNoHostTenant() throws Exception {
        // Direct service access, or localhost during development.
        authenticateAs(MERCHANT_A, UserRole.WAITER);
        assertEquals(MERCHANT_A, runFilter(request(null), new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("SUPER_ADMIN asserts no tenant even on a tenant host")
    void superAdminAssertsNoTenant() throws Exception {
        authenticateAs(null, UserRole.SUPER_ADMIN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertNull(runFilter(request(MERCHANT_A), res),
                "cross-tenant work must not be silently pinned to whichever host was used");
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("an unparseable tenant header is ignored rather than crashing the request")
    void malformedHeaderIsIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader(TenantContextFilter.TENANT_ID_HEADER, "not-a-uuid");

        assertNull(runFilter(req, new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("the ThreadLocal is cleared after the request, even when the chain throws")
    void clearsThreadLocalOnException() {
        // Servlet containers pool request threads. A leaked value is served to the
        // NEXT request on that thread, which in a shared multi-tenant deployment
        // means one tenant's context applied to another tenant's request.
        FilterChain boom = (request, response) -> {
            throw new java.io.IOException("downstream failure");
        };

        assertThrows(java.io.IOException.class,
                () -> filter.doFilter(request(MERCHANT_A), new MockHttpServletResponse(), boom));
        assertNull(TenantContext.getCurrentTenant(), "the ThreadLocal leaked");
    }

    @Test
    @DisplayName("the ThreadLocal is cleared after a normal request too")
    void clearsThreadLocalOnSuccess() throws Exception {
        runFilter(request(MERCHANT_A), new MockHttpServletResponse());
        assertNull(TenantContext.getCurrentTenant());
    }
}
