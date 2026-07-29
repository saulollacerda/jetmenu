package com.jetmenu.customer;

import com.jetmenu.order.OrderOrigin;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private UUID id;
    private String name;
    private String phone;
    private String email;
    private String neighborhood;
    private String notes;
    private Long orderCount;
    private BigDecimal lifetimeValue;
    private LocalDateTime lastOrderAt;
    private OrderOrigin preferredOrigin;
}

