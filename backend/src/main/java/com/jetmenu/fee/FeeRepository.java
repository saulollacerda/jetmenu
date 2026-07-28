package com.jetmenu.fee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

    boolean existsByNameAndMerchantId(String name, UUID merchantId);

    Optional<Fee> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Fee> findAllByMerchantId(UUID merchantId);

    Page<Fee> findAllByMerchantIdAndNameContainingIgnoreCase(UUID merchantId, String name, Pageable pageable);

    boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

    void deleteByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Fee> findByNameIgnoreCaseAndMerchantId(String name, UUID merchantId);
}
