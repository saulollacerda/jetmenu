package com.jetmenu.integration.ifood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Frontend-facing response for the start-authorization step. Deliberately excludes
 * {@code authorizationCodeVerifier}, which must never leave the backend.
 */
@Data
@AllArgsConstructor
public class IfoodStartAuthResponse {

    private String userCode;
    private String verificationUrl;
    private String verificationUrlComplete;
    private int expiresIn;

    public static IfoodStartAuthResponse from(IfoodUserCodeResponse response) {
        return new IfoodStartAuthResponse(
                response.getUserCode(),
                response.getVerificationUrl(),
                response.getVerificationUrlComplete(),
                response.getExpiresIn());
    }
}
