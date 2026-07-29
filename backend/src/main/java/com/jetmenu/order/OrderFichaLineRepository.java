package com.jetmenu.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderFichaLineRepository extends JpaRepository<OrderFichaLine, UUID> {

    List<OrderFichaLine> findAllByMerchantIdOrderBySortOrderAsc(UUID merchantId);

    void deleteAllByMerchantId(UUID merchantId);
}
