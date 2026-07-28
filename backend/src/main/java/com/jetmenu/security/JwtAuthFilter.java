package com.jetmenu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Validates the incoming Supabase JWT and places its subject (the Supabase user id)
 * as the authentication principal. Replaces Spring's built-in OAuth2 resource-server
 * bearer-token filter so the rest of the app can resolve the principal as a plain
 * {@code String} UUID.
 * <p>
 * Wired explicitly into the {@code SecurityFilterChain} (not a {@code @Component}) to
 * avoid being auto-registered in the plain servlet filter chain.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public JwtAuthFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Jwt jwt = jwtDecoder.decode(token);
            var authentication = new UsernamePasswordAuthenticationToken(
                    jwt.getSubject(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException ex) {
            // setStatus (not sendError): sendError triggers the servlet container's ERROR
            // dispatch, which drops the CORS headers added earlier by the CorsFilter. The
            // browser would then block the 401 as a CORS error and the SPA would never see
            // the status (so it couldn't redirect to login). Writing the response directly
            // keeps the CORS headers intact.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
