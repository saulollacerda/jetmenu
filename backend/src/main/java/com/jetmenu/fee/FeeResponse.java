package com.jetmenu.fee;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeResponse {

    private UUID id;
    private String name;
    private BigDecimal feeRate;
}
