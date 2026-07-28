package com.jetmenu.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNameAndMerchantId(String name, UUID merchantId);

    Optional<Category> findByNameAndMerchantId(String name, UUID merchantId);

    Optional<Category> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Category> findAllByMerchantId(UUID merchantId);

    Page<Category> findAllByMerchantIdAndNameContainingIgnoreCase(UUID merchantId, String name, Pageable pageable);

    boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

    void deleteByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Category> findByExternalIdAndMerchantId(String externalId, UUID merchantId);
}
