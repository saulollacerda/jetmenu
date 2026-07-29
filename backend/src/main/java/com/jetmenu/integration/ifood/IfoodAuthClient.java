package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodTokenResponse;
import com.jetmenu.integration.ifood.dto.IfoodUserCodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class IfoodAuthClient {

    private final RestClient restClient;

    public IfoodAuthClient(
            RestClient.Builder builder,
            @Value("${ifood.auth-base-url}") String authBaseUrl) {
        this.restClient = builder.baseUrl(authBaseUrl).build();
    }

    public IfoodUserCodeResponse requestUserCode(String clientId) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("clientId", clientId);

        return restClient.post()
                .uri("/oauth/userCode")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(IfoodUserCodeResponse.class);
    }

    public IfoodTokenResponse exchangeCode(String clientId, String clientSecret,
                                           String authorizationCode, String authorizationCodeVerifier) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grantType", "authorization_code");
        body.add("clientId", clientId);
        body.add("clientSecret", clientSecret);
        body.add("authorizationCode", authorizationCode);
        body.add("authorizationCodeVerifier", authorizationCodeVerifier);

        return restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(IfoodTokenResponse.class);
    }

    public IfoodTokenResponse refreshToken(String clientId, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grantType", "refresh_token");
        body.add("clientId", clientId);
        body.add("clientSecret", clientSecret);
        body.add("refreshToken", refreshToken);

        return restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(IfoodTokenResponse.class);
    }
}
