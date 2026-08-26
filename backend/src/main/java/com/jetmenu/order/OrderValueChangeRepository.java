package com.jetmenu.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderValueChangeRepository extends JpaRepository<OrderValueChange, UUID> {

    /** Tudo que aconteceu com um pedido, em ordem — a consulta de debug. */
    List<OrderValueChange> findByOrderIdAndMerchantIdOrderByChangedAtAsc(UUID orderId, UUID merchantId);
}
