package com.jetmenu.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Optional<Customer> findByIdAndMerchantId(UUID id, UUID merchantId);

	List<Customer> findAllByMerchantId(UUID merchantId);

	Page<Customer> findAllByMerchantIdAndNameContainingIgnoreCase(UUID merchantId, String name, Pageable pageable);

	boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

	void deleteByIdAndMerchantId(UUID id, UUID merchantId);

	Optional<Customer> findByPhoneAndMerchantId(String phone, UUID merchantId);

	Optional<Customer> findByExternalIdAndMerchantId(String externalId, UUID merchantId);
}
