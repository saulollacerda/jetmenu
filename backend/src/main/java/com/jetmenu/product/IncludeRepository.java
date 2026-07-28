package com.jetmenu.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncludeRepository extends JpaRepository<Include, UUID> {

    List<Include> findByProductIdAndProductMerchantId(UUID productId, UUID merchantId);

    @Query("SELECT i FROM Include i WHERE i.product.id IN :productIds AND i.product.merchant.id = :merchantId")
    List<Include> findAllByProductIdInAndProductMerchantId(
            @Param("productIds") Collection<UUID> productIds,
            @Param("merchantId") UUID merchantId);

    List<Include> findByProductIdAndProductMerchantIdOrderBySortOrderAsc(UUID productId, UUID merchantId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(MAX(i.sortOrder), 0) FROM Include i WHERE i.product.id = :productId AND i.product.merchant.id = :merchantId"
    )
    Integer findMaxSortOrderByProductIdAndProductMerchantId(
            @org.springframework.data.repository.query.Param("productId") UUID productId,
            @org.springframework.data.repository.query.Param("merchantId") UUID merchantId);

    Optional<Include> findByIdAndProductIdAndProductMerchantId(UUID id, UUID productId, UUID merchantId);

    @Modifying
    @Transactional
    void deleteByIdAndProductIdAndProductMerchantId(UUID id, UUID productId, UUID merchantId);

    @Modifying
    @Transactional
    long deleteAllByProductIdAndProductMerchantId(UUID productId, UUID merchantId);

    /**
     * Busca todos os includes cujo {@code name} bate (case-insensitive) com o nome dado,
     * dentro do tenant do merchant. Usado para descobrir em quais produtos um ingrediente
     * cadastrado e referenciado nas fichas tecnicas (matching por nome).
     */
    List<Include> findByNameIgnoreCaseAndProductMerchantId(String name, UUID merchantId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT LOWER(i.name), COUNT(i) FROM Include i
            WHERE i.product.merchant.id = :merchantId
            AND LOWER(i.name) IN :names
            GROUP BY LOWER(i.name)
            """)
    java.util.List<Object[]> countByLowercaseNameInForMerchant(
            @org.springframework.data.repository.query.Param("merchantId") UUID merchantId,
            @org.springframework.data.repository.query.Param("names") java.util.Collection<String> names);
}
