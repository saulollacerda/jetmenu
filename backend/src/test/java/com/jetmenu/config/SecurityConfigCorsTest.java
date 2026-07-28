package com.jetmenu.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private CorsConfiguration corsConfiguration;

    @BeforeEach
    void setUp() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", "http://localhost:5173");

        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/ingredients/some-id/position");
        corsConfiguration = securityConfig.corsConfigurationSource().getCorsConfiguration(request);
    }

    @Test
    @DisplayName("allows every HTTP method the API exposes, including PATCH")
    void allowsAllApiMethods() {
        // PATCH was missing from the CORS allow-list: browser requests (which carry an
        // Origin header) were rejected with 403 "Invalid CORS request" while curl-style
        // requests without Origin passed — e.g. the ingredient reorder endpoint.
        assertThat(corsConfiguration.getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("applies to /api/** requests from the configured origins")
    void appliesToApiRequests() {
        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.getAllowedOrigins()).contains("http://localhost:5173");
    }
}
