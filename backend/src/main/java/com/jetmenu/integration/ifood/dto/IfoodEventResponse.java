package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IfoodEventResponse {
    private String id;
    private String code;
    private String fullCode;
    private String orderId;
    private String merchantId;
    private String createdAt;
}
