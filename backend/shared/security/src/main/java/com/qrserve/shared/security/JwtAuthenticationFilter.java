package com.qrserve.shared.security;

import com.qrserve.shared.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = parseJwt(request);

        // Only ACCESS tokens authenticate a REST request. A refresh token or an
        // anonymous order-stream token is cryptographically valid but carries no
        // authority, so it must not establish a SecurityContext — otherwise a
        // guest's order-stream token would authenticate API calls. Leaving the
        // context empty yields a normal 401/403 rather than an exception.
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            String email = jwtTokenProvider.getUsernameFromToken(token);
            UserRole role = jwtTokenProvider.getRoleFromToken(token);
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);
            UUID merchantId = jwtTokenProvider.getMerchantIdFromToken(token);

            // Reconstruct UserPrincipal
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .merchantId(merchantId)
                    .email(email)
                    .role(role)
                    .build();

            // Grant authority with "ROLE_" prefix (e.g. ROLE_SUPER_ADMIN)
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.name())
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Set context for this request thread
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
