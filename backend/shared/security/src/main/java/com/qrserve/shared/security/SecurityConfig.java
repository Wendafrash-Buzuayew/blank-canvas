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

    // Explicit constructor injection instead of Lombok @RequiredArgsConstructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
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
            .authorizeHttpRequests(auth -> auth
                // 1. ALWAYS allow CORS preflight requests from browsers
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. Permit WebSocket / SockJS endpoints
                .requestMatchers("/ws/**", "/ws/info/**", "/ws/info").permitAll()

                // 3. System & Swagger endpoints
                .requestMatchers(
                    "/actuator/**",
                    "/error",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // 4. Public API endpoints (Including both versioned and unversioned)
                .requestMatchers(
                    "/api/auth/**",
                    "/api/v1/auth/**",
                    "/api/menu/**",
                    "/api/v1/menu/**",
                    "/api/v1/public/**"
                ).permitAll()

                // Narrow rules must come BEFORE the broad permitAll
                .requestMatchers(HttpMethod.POST, "/api/customer-requests", "/api/v1/customer-requests").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/customer-requests/table/**", "/api/v1/customer-requests/table/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/customer-requests", "/api/v1/customer-requests").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/customer-requests/**", "/api/v1/customer-requests/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/orders", "/api/v1/orders").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tables/*", "/api/v1/tables/*").permitAll()

                // 6. Explicit Waiter & Orders requirements (Ensure authenticated JWT required)
                .requestMatchers("/api/v1/waiters/**", "/api/v1/orders/**").authenticated()

                // All other requests require JWT authentication
                .anyRequest().authenticated()
            )
            // Add JWT Filter before Spring's default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

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