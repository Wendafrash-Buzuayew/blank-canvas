package com.qrserve.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.List;


@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;

    // Explicit constructor injection instead of Lombok @RequiredArgsConstructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, TenantContextFilter tenantContextFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Rules are evaluated FIRST-MATCH-WINS. Every narrow rule must therefore
            // appear above any broader pattern that would also match it. The previous
            // version declared a blanket permitAll on "/api/menu/**" and "/api/auth/**"
            // above the narrow rules, which made every menu write endpoint and
            // /api/auth/users publicly reachable.
            .authorizeHttpRequests(auth -> auth
                // 1. ALWAYS allow CORS preflight requests from browsers
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. WebSocket / SockJS handshake. Frame-level authorization is
                //    enforced by StompAuthInterceptor, not here.
                .requestMatchers("/ws/**", "/ws/info/**", "/ws/info").permitAll()

                // 3. System & docs. Deliberately NOT "/actuator/**" — that also
                //    exposes /actuator/env, /configprops and /beans, which leak
                //    configuration (including secret property names).
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/error",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // 4. Public authentication endpoints, enumerated. "/api/auth/**"
                //    would also expose POST /api/auth/users (user creation) and
                //    GET /api/auth/me (which NPEs on a null principal).
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/v1/auth/logout").permitAll()

                // 5. Public customer-facing reads/writes, narrowly scoped.
                //    GET /api/menu/{merchantId} is the only public menu endpoint;
                //    the wildcard is a single segment so it cannot match writes.
                .requestMatchers(HttpMethod.GET, "/api/menu/*").permitAll()
                // "/api/tables/all" MUST be declared above "/api/tables/*" — the
                // single-segment wildcard also matches "all", which would otherwise
                // expose every table of every merchant to anonymous callers.
                .requestMatchers(HttpMethod.GET, "/api/tables/all", "/api/v1/tables/all").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/tables/*", "/api/v1/tables/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/customer-requests", "/api/v1/customer-requests").permitAll()
                // Signed customer service calls (call waiter / water / bill). The
                // frontend uses this versioned endpoint rather than the unversioned
                // one above, and it was reachable by nobody: a seated guest has no
                // token, so it fell through to anyRequest().authenticated(). Single
                // segment wildcard on the table id so this cannot widen.
                //
                // NOTE: PublicCustomerRequestController validates the QR signature
                // only when one is supplied, so this endpoint is currently
                // spam-able by anyone who knows a table id. Making the signature
                // mandatory is a separate decision — it would break guests who
                // reach the menu by direct navigation rather than by scanning.
                .requestMatchers(HttpMethod.POST, "/api/v1/tables/*/requests").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/customer-requests/table/**", "/api/v1/customer-requests/table/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders", "/api/v1/orders").permitAll()
                // Public QR-resolved menu (merchant-service PublicMenuResolution).
                .requestMatchers("/api/v1/public/**").permitAll()

                // 6. Explicitly authenticated. Role checks live on the controllers
                //    via @PreAuthorize; this only guarantees a valid JWT.
                .requestMatchers(HttpMethod.GET, "/api/customer-requests", "/api/v1/customer-requests").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/customer-requests/**", "/api/v1/customer-requests/**").authenticated()
                .requestMatchers("/api/v1/waiters/**", "/api/v1/orders/**").authenticated()

                // 7. Everything else requires a JWT.
                .anyRequest().authenticated()
            )
            // Add JWT Filter before Spring's default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // AFTER the JWT filter: the tenant precedence check compares the
            // gateway's host-derived tenant against the authenticated principal's,
            // so the principal has to exist by the time it runs.
            .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}