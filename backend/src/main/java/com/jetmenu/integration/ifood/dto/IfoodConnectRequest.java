package com.jetmenu.integration.ifood.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IfoodConnectRequest {
    @NotBlank
    private String authorizationCode;
}
