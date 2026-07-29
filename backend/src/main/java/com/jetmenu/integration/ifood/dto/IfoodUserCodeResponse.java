package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IfoodUserCodeResponse {
    private String userCode;
    private String authorizationCodeVerifier;
    private String verificationUrl;
    private String verificationUrlComplete;
    private int expiresIn;
}
