package com.prince.agentic.security;

import com.prince.agentic.auth.ratelimit.AuthRateLimiterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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

import java.util.List;

/**
 * Central security policy: stateless JWT authentication, deny-by-default authorization,
 * BCrypt password hashing, method security, restricted CORS, and JSON error rendering.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Stateless:</b> no server sessions; every request is authenticated from its bearer
 *       token by {@link JwtAuthenticationFilter}.</li>
 *   <li><b>CSRF disabled — deliberately and safely:</b> the API is stateless and token-based
 *       with no cookie session, so classic CSRF does not apply. Documented in docs/SECURITY.md;
 *       if any cookie-based flow is ever added, CSRF protection must be reintroduced.</li>
 *   <li><b>Deny by default:</b> only an explicit whitelist is public; every other route
 *       requires authentication. Admin authorization is enforced with method security
 *       ({@code @PreAuthorize}) — see {@code AdminController}.</li>
 *   <li><b>Actuator:</b> only {@code health} and {@code info} are exposed (unchanged from M1)
 *       and made public here; all other actuator endpoints stay both unexposed and non-public.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/api/v1/health",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // M10 (ADR-0030): Prometheus scrape endpoint is public at the app layer.
            // H-02: under the docker profile, management.server.port=9090 moves ALL actuator
            // endpoints to a separate port; this permitAll never matches on the API port (8080).
            // In non-docker environments, restrict scrape access at the network/reverse-proxy layer
            // (docs/SECURITY.md). Spring Security is not the enforcement point in either case.
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimiterFilter authRateLimiterFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          AuthRateLimiterFilter authRateLimiterFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${security.cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimiterFilter = authRateLimiterFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // H-01: IP-based auth rate limiter fires before any auth filter (pre-auth paths).
                .addFilterBefore(authRateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
