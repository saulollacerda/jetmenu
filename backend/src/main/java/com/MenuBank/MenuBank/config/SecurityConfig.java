package com.MenuBank.MenuBank.config;

import com.MenuBank.MenuBank.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost,http://localhost:80,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) {
        try {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(auth -> auth
                            // Dev-only local auth endpoints (the controller exists only under
                            // dev/test profiles; in prod these paths 404). Public so a user can
                            // obtain a token without a bearer token.
                            .requestMatchers("/api/auth/dev-login", "/api/auth/dev-register").permitAll()
                            .requestMatchers("/api/plans").permitAll()
                            // NOTE: the payment provider's webhook path used to be permitted
                            // here. It was removed together with the provider — the next
                            // billing integration must add its own permitAll entry, or its
                            // server-to-server callbacks will be rejected with 401.
                            .anyRequest().authenticated()
                    )
                    // Without an explicit entry point Spring Security answers an
                    // unauthenticated request to a protected resource with 403 (its default
                    // Http403ForbiddenEntryPoint). The SPA only signs out and redirects to
                    // /login on 401, so a request that arrives without a valid bearer token
                    // (e.g. an expired/absent Supabase session) got a confusing 403 and the
                    // user stayed stuck on the page. Return 401 so the frontend recovers.
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                    .addFilterBefore(new JwtAuthFilter(jwtDecoder), UsernamePasswordAuthenticationFilter.class)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error configuring security filter chain", e);
        }
    }

    /**
     * Answers unauthenticated requests with 401 and a pt-BR ProblemDetail body.
     * <p>
     * Uses {@code setStatus} (not {@code sendError}) so the container's ERROR dispatch does
     * not drop the CORS headers added by the CORS filter — same reasoning as
     * {@code JwtAuthFilter}; otherwise the browser would block the response as a CORS error
     * and the SPA would never see the 401 (so it couldn't redirect to login).
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Não autenticado\","
                            + "\"status\":401,\"detail\":\"Autenticação necessária\"}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
