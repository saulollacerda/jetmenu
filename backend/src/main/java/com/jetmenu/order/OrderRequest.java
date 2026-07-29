package com.jetmenu.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CustomerReferenceRequired
public class OrderRequest {

    /** Cliente existente. Opcional quando {@link #customerName} é informado. */
    private UUID customerId;

    /**
     * Nome do cliente para o fluxo rápido: o backend reutiliza um cliente existente
     * com o mesmo nome (match canônico) ou cria um novo. Ignorado quando
     * {@link #customerId} está presente.
     */
    private String customerName;

    @NotEmpty(message = "Pedido deve ter pelo menos um item")
    @Valid
    private List<OrderItemRequest> items;

    private OrderStatus status;

    private UUID feeId;

    /** Canal de origem do pedido. Quando ausente, o backend assume JETMENU. */
    private OrderOrigin origin;
}

