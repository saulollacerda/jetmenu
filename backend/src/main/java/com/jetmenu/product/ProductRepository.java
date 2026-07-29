package com.jetmenu.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByNameAndMerchantId(String name, UUID merchantId);

    Optional<Product> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Product> findAllByMerchantId(UUID merchantId);

    @Query(value = """
            SELECT p FROM Product p LEFT JOIN FETCH p.category
            WHERE p.merchant.id = :merchantId
            AND UPPER(p.name) LIKE UPPER(CONCAT('%', :name, '%'))
            """,
           countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.merchant.id = :merchantId
            AND UPPER(p.name) LIKE UPPER(CONCAT('%', :name, '%'))
            """)
    Page<Product> findAllByMerchantIdAndNameContainingIgnoreCase(
            @Param("merchantId") UUID merchantId,
            @Param("name") String name,
            Pageable pageable);

    boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

    void deleteByIdAndMerchantId(UUID id, UUID merchantId);

    List<Product> findAllByCategoryIsNull();

    Optional<Product> findByExternalIdAndMerchantId(String externalId, UUID merchantId);

    Optional<Product> findByCanonicalNameAndMerchantId(String canonicalName, UUID merchantId);

    long countByCategoryIdAndMerchantId(UUID categoryId, UUID merchantId);

    @Query("""
            SELECT p.category.id, COUNT(p) FROM Product p
            WHERE p.category.id IN :ids AND p.merchant.id = :merchantId
            GROUP BY p.category.id
            """)
    List<Object[]> countByCategoryIdsAndMerchantId(
            @Param("ids") Collection<UUID> ids,
            @Param("merchantId") UUID merchantId);
}
