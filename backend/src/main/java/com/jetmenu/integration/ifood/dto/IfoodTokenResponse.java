package com.jetmenu.integration.ifood.dto;

import lombok.Data;

@Data
public class IfoodTokenResponse {
    private String accessToken;
    private String type;
    private int expiresIn;
    private String refreshToken;
}
