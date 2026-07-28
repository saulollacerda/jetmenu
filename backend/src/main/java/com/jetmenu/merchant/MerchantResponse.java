package com.jetmenu.merchant;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {

    private UUID id;
    private String merchantName;
    private String cnpj;
    private String email;
    private String phone;
    private MerchantStatus status;
    private LocalDateTime createdAt;
    private String anotaAiApiKey;
    private String address;
    private String logoUrl;
    private List<OpeningHour> openingHours;
    private MerchantPreferences preferences;
}
