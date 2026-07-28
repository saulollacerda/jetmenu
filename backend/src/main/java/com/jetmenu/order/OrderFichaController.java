package com.jetmenu.order;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Configuração da ficha do pedido do lojista ("Configurar pedidos"): insumos cobrados
 * uma vez por pedido, independentemente da quantidade de itens.
 */
@RestController
@RequestMapping("/api/order-ficha")
public class OrderFichaController {

    private final OrderFichaService orderFichaService;
    private final AuthHelper authHelper;

    public OrderFichaController(OrderFichaService orderFichaService, AuthHelper authHelper) {
        this.orderFichaService = orderFichaService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<OrderFichaResponse> find(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(orderFichaService.findByMerchant(merchantId));
    }

    /** Substitui a ficha inteira. Lista vazia limpa a ficha (custo zero por pedido). */
    @PutMapping
    public ResponseEntity<OrderFichaResponse> replace(Authentication auth,
                                                      @Valid @RequestBody OrderFichaRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(orderFichaService.replace(merchantId, request));
    }
}
