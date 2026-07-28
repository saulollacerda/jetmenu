package com.MenuBank.MenuBank.order;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private LocalDateTime dateTime;
    private UUID customerId;
    private String customerName;
    private OrderStatus status;
    private BigDecimal totalValue;
    private BigDecimal estimatedProfit;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalCost;
    private UUID feeId;
    private String feeName;
    private BigDecimal feeRate;
    private List<OrderItemResponse> items;
    private OrderOrigin origin;
    private BigDecimal marginPct;
    /**
     * Insumos cobrados uma vez neste pedido (ficha do pedido), como gravados na criação.
     * Vazio para pedidos sem ficha configurada e para pedidos anteriores à V17.
     */
    private List<OrderFichaIngredientResponse> orderFicha;
    /** Parcela do {@link #totalCost} que veio da ficha do pedido. Zero quando não há ficha. */
    private BigDecimal orderFichaCost;

    // ------------------------------------------------------------------------------------
    // Dados descritivos importados do marketplace (homologação do módulo Order do iFood).
    // Todos nulos/vazios em pedidos manuais e em pedidos importados antes desta feature —
    // a UI precisa tratar a ausência. Nenhum deles entra em nenhum cálculo financeiro.
    // ------------------------------------------------------------------------------------

    /** Id amigável do pedido no iFood (ex.: "3421"). */
    private String displayId;

    /** DELIVERY / TAKEOUT / DINE_IN. */
    private OrderType orderType;

    /** IMMEDIATE / SCHEDULED. */
    private OrderTiming orderTiming;

    /** CPF/CNPJ informado pelo cliente para a nota fiscal. */
    private String customerDocument;

    /** Valor já pago online (pré-pago). */
    private BigDecimal paymentPrepaidAmount;

    /** Valor a receber na entrega. */
    private BigDecimal paymentPendingAmount;

    /** Meios de pagamento do pedido — bandeira do cartão e troco. Vazio se não importado. */
    @Builder.Default
    private List<OrderPaymentMethodResponse> paymentMethods = List.of();

    /** Soma dos cupons/descontos aplicados. Apenas exibição — não deduzido de nada. */
    private BigDecimal discountTotal;

    /** Parcela do desconto bancada pelo iFood. */
    private BigDecimal discountIfoodValue;

    /** Parcela do desconto bancada pela Loja. */
    private BigDecimal discountMerchantValue;

    private String deliveryMode;

    /** IFOOD ou MERCHANT — quem faz a entrega. */
    private String deliveredBy;

    private LocalDateTime deliveryDateTime;

    /** Observações/instruções de entrega, exibidas na comanda. */
    private String deliveryObservations;

    /** Código de coleta do pedido. */
    private String pickupCode;

    private String takeoutMode;

    private LocalDateTime takeoutDateTime;
}

