package com.jetmenu.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.id = :id AND o.merchant.id = :merchantId
            """)
    Optional<Order> findByIdAndMerchantId(@Param("id") UUID id, @Param("merchantId") UUID merchantId);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.merchant.id = :merchantId
            """)
    List<Order> findAllByMerchantId(@Param("merchantId") UUID merchantId);

    @Query(
            value = """
                    SELECT o FROM Order o
                    LEFT JOIN FETCH o.customer c
                    LEFT JOIN FETCH o.fee
                    WHERE o.merchant.id = :merchantId
                    AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    """,
            countQuery = """
                    SELECT COUNT(o) FROM Order o
                    JOIN o.customer c
                    WHERE o.merchant.id = :merchantId
                    AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    """
    )
    Page<Order> findPageByMerchantIdAndCustomerNameContaining(
            @Param("merchantId") UUID merchantId,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

    void deleteByIdAndMerchantId(UUID id, UUID merchantId);

    List<Order> findByMerchantIdAndDateTimeBetween(UUID merchantId, LocalDateTime start, LocalDateTime end);

    List<Order> findByMerchantIdAndDateTimeBetweenAndStatus(UUID merchantId, LocalDateTime start, LocalDateTime end, OrderStatus status);

    List<Order> findByMerchantIdAndOriginAndDateTimeBetween(UUID merchantId, OrderOrigin origin, LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH o.fee
            LEFT JOIN FETCH o.customer
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            AND o.status = :status
            """)
    List<Order> findAllForReportByMerchantAndPeriodAndStatus(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") OrderStatus status);

    boolean existsByExternalOrderIdAndMerchantId(String externalOrderId, UUID merchantId);

    Optional<Order> findByExternalOrderIdAndMerchantId(String externalOrderId, UUID merchantId);

    @Query(
            value = """
                SELECT COUNT(*) FROM orders o
                WHERE o.merchant_id = :merchantId
                AND o.date_time BETWEEN :start AND :end
                """,
            nativeQuery = true
    )
    Integer countByMerchantIdAndDateTimeBetween(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(
            value = """
                    SELECT o FROM Order o
                    LEFT JOIN FETCH o.customer c
                    LEFT JOIN FETCH o.fee
                    WHERE o.merchant.id = :merchantId
                    AND o.status = :status
                    AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    """,
            countQuery = """
                    SELECT COUNT(o) FROM Order o
                    JOIN o.customer c
                    WHERE o.merchant.id = :merchantId
                    AND o.status = :status
                    AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    """
    )
    Page<Order> findPageByMerchantIdAndStatusAndCustomerNameContaining(
            @Param("merchantId") UUID merchantId,
            @Param("status") OrderStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT o.status, COUNT(o) FROM Order o
            JOIN o.customer c
            WHERE o.merchant.id = :merchantId
            AND (:start IS NULL OR o.dateTime >= :start)
            AND (:end   IS NULL OR o.dateTime <= :end)
            AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
            GROUP BY o.status
            """)
    List<Object[]> countByStatusForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search);

    @Query("""
            SELECT COUNT(DISTINCT o.customer.id) FROM Order o
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            """)
    Long countDistinctCustomersByMerchantIdAndDateTimeBetween(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(
            value = """
                SELECT EXTRACT(HOUR FROM o.date_time) AS hour, COUNT(*) AS cnt
                FROM orders o
                WHERE o.merchant_id = :merchantId
                AND o.date_time BETWEEN :start AND :end
                GROUP BY EXTRACT(HOUR FROM o.date_time)
                ORDER BY hour ASC
                """,
            nativeQuery = true
    )
    List<Object[]> peakHoursByMerchantIdAndDateTimeBetween(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT o.origin, COUNT(o) FROM Order o
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            GROUP BY o.origin
            """)
    List<Object[]> countByOriginForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT p.category.id, COALESCE(SUM(i.unitPrice * i.quantity), 0)
            FROM Order o JOIN o.items i JOIN i.product p
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            AND p.category.id IS NOT NULL
            GROUP BY p.category.id
            """)
    List<Object[]> sumRevenueByCategoryForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT o.customer.id,
                   COUNT(o),
                   COALESCE(SUM(o.totalValue), 0),
                   MAX(o.dateTime)
            FROM Order o
            WHERE o.merchant.id = :merchantId
            AND o.customer.id IN :customerIds
            GROUP BY o.customer.id
            """)
    List<Object[]> aggregatesByCustomerForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("customerIds") java.util.Collection<UUID> customerIds);

    @Query("""
            SELECT o.customer.id, o.origin, COUNT(o)
            FROM Order o
            WHERE o.merchant.id = :merchantId
            AND o.customer.id IN :customerIds
            AND o.origin IS NOT NULL
            GROUP BY o.customer.id, o.origin
            """)
    List<Object[]> originBreakdownByCustomerForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("customerIds") java.util.Collection<UUID> customerIds);

    /**
     * Ficha-snapshot ingredient consumption per merchant/period/status. Ficha ingredients
     * are consumed once per order, so quantity and cost are summed directly. Each row:
     * {ingredientId, ingredientName, ingredientUnit, totalQuantity, totalCost}.
     */
    @Query("""
            SELECT fi.ingredient.id, fi.ingredientName, fi.ingredientUnit,
                   SUM(fi.quantity),
                   SUM(fi.quantity * fi.costPerUnit)
            FROM Order o JOIN o.orderFicha fi
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            AND o.status = :status
            GROUP BY fi.ingredient.id, fi.ingredientName, fi.ingredientUnit
            """)
    List<Object[]> sumFichaIngredientConsumptionForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") OrderStatus status);

    /**
     * Extra-ingredient consumption per merchant/period/status. Extras are consumed per unit
     * of the parent item, so quantity and cost are multiplied by the item quantity. Each row:
     * {ingredientId, ingredientName, ingredientUnit, totalQuantity, totalCost}.
     */
    @Query("""
            SELECT ei.ingredient.id, ei.ingredientName, ei.ingredientUnit,
                   SUM(ei.quantity * it.quantity),
                   SUM(ei.quantity * ei.costPerUnit * it.quantity)
            FROM Order o JOIN o.items it JOIN it.extraIngredients ei
            WHERE o.merchant.id = :merchantId
            AND o.dateTime BETWEEN :start AND :end
            AND o.status = :status
            GROUP BY ei.ingredient.id, ei.ingredientName, ei.ingredientUnit
            """)
    List<Object[]> sumExtraIngredientConsumptionForMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") OrderStatus status);
}
