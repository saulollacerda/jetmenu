package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodTokenResponse;
import com.jetmenu.integration.ifood.dto.IfoodUserCodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodAuthClient")
class IfoodAuthClientTest {

    private MockRestServiceServer server;
    private IfoodAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodAuthClient(builder, "https://merchant-api.ifood.com.br/authentication/v1.0");
    }

    @Test
    @DisplayName("requestUserCode envia clientId e retorna userCode + verifier")
    void requestUserCode_shouldSendClientIdAndReturnResponse() {
        server.expect(requestTo("https://merchant-api.ifood.com.br/authentication/v1.0/oauth/userCode"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("clientId=test-client")))
              .andRespond(withSuccess("""
                      {"userCode":"HJLX-LPSQ","authorizationCodeVerifier":"verifier123",
                       "verificationUrl":"https://portal.ifood.com.br/apps/code",
                       "verificationUrlComplete":"https://portal.ifood.com.br/apps/code?c=HJLX-LPSQ",
                       "expiresIn":600}
                      """, MediaType.APPLICATION_JSON));

        IfoodUserCodeResponse response = client.requestUserCode("test-client");

        assertThat(response.getUserCode()).isEqualTo("HJLX-LPSQ");
        assertThat(response.getAuthorizationCodeVerifier()).isEqualTo("verifier123");
        assertThat(response.getExpiresIn()).isEqualTo(600);
        server.verify();
    }

    @Test
    @DisplayName("exchangeCode envia authorization_code grant e retorna tokens")
    void exchangeCode_shouldSendCorrectGrantAndReturnTokens() {
        server.expect(requestTo("https://merchant-api.ifood.com.br/authentication/v1.0/oauth/token"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
              .andExpect(content().string(org.hamcrest.Matchers.allOf(
                      org.hamcrest.Matchers.containsString("grantType=authorization_code"),
                      org.hamcrest.Matchers.containsString("clientId=test-client"),
                      org.hamcrest.Matchers.containsString("authorizationCode=auth-code"),
                      org.hamcrest.Matchers.containsString("authorizationCodeVerifier=verifier123")
              )))
              .andRespond(withSuccess("""
                      {"accessToken":"access.jwt","type":"bearer","expiresIn":10800,
                       "refreshToken":"refresh.jwt"}
                      """, MediaType.APPLICATION_JSON));

        IfoodTokenResponse response = client.exchangeCode("test-client", "secret", "auth-code", "verifier123");

        assertThat(response.getAccessToken()).isEqualTo("access.jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh.jwt");
        assertThat(response.getExpiresIn()).isEqualTo(10800);
        server.verify();
    }

    @Test
    @DisplayName("refreshToken envia refresh_token grant e retorna novo accessToken")
    void refreshToken_shouldSendRefreshGrantAndReturnNewToken() {
        server.expect(requestTo("https://merchant-api.ifood.com.br/authentication/v1.0/oauth/token"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
              .andExpect(content().string(org.hamcrest.Matchers.allOf(
                      org.hamcrest.Matchers.containsString("grantType=refresh_token"),
                      org.hamcrest.Matchers.containsString("refreshToken=old-refresh")
              )))
              .andRespond(withSuccess("""
                      {"accessToken":"new.access.jwt","type":"bearer","expiresIn":10800,
                       "refreshToken":"new.refresh.jwt"}
                      """, MediaType.APPLICATION_JSON));

        IfoodTokenResponse response = client.refreshToken("test-client", "secret", "old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new.access.jwt");
        server.verify();
    }
}
