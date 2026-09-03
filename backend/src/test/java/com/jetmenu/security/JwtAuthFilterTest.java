package com.jetmenu.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtDecoder);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("token válido deve autenticar com o sub como principal e seguir a cadeia")
    void validToken_shouldAuthenticateWithSubAsPrincipal() throws Exception {
        String sub = "11111111-2222-3333-4444-555555555555";
        given(jwtDecoder.decode("valid-token")).willReturn(jwtWithSubject(sub));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(sub);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("sem header Authorization deve seguir a cadeia sem autenticar")
    void noHeader_shouldContinueWithoutAuthenticating() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    /**
     * The Anota.AI webhook depends on this: it carries the merchant's shared secret in the
     * very same {@code Authorization} header, as a raw value with no {@code Bearer} prefix
     * (confirmed by the delivery captured in production). Making this filter try to decode
     * such a value — or answer 401 for it — silently breaks every order import.
     */
    @Test
    @DisplayName("Authorization sem prefixo Bearer deve seguir a cadeia sem autenticar e sem 401")
    void nonBearerHeader_shouldPassThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "segredo-cru-do-webhook-da-anota");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtDecoder, never()).decode(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("token inválido deve responder 401 e não seguir a cadeia")
    void invalidToken_shouldRespond401AndNotContinue() throws Exception {
        given(jwtDecoder.decode("bad-token")).willThrow(new JwtException("invalid"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("token inválido deve usar setStatus (não sendError) para preservar headers de CORS")
    void invalidToken_shouldNotUseSendError_soCorsHeadersSurvive() throws Exception {
        given(jwtDecoder.decode("bad-token")).willThrow(new JwtException("invalid"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        // sendError triggers the container ERROR dispatch, which drops the CORS headers
        // added earlier by the CorsFilter; the browser then blocks the 401 as a CORS error
        // and the SPA never sees the status. setStatus keeps the response untouched.
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private Jwt jwtWithSubject(String sub) {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
