package com.jetmenu.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsByEmail(String email);

    boolean existsByCnpj(String cnpj);

    Optional<Merchant> findByEmail(String email);

    // Queries below join the normalized 1:1 integration tables. JOIN FETCH initializes
    // the association so callers can read the convenience accessors outside a transaction.

    @Query("select m from Merchant m join fetch m.anotaAiIntegration a where a.anotaAiApiKey is not null")
    List<Merchant> findAllByAnotaAiApiKeyIsNotNull();

    @Query("select count(m) from Merchant m join m.ifoodIntegration i where i.ifoodMerchantId is not null")
    long countByIfoodMerchantIdIsNotNull();

    @Query("select m from Merchant m join fetch m.ifoodIntegration i where i.ifoodMerchantId is not null")
    List<Merchant> findAllByIfoodMerchantIdIsNotNull();

    @Query("select m from Merchant m join fetch m.ifoodIntegration i "
            + "where i.ifoodMerchantId is not null and i.ifoodOrderSyncEnabled = true")
    List<Merchant> findAllByIfoodMerchantIdIsNotNullAndIfoodOrderSyncEnabledTrue();

    @Query("select m from Merchant m join fetch m.ifoodIntegration i where i.ifoodMerchantId = :ifoodMerchantId")
    Optional<Merchant> findByIfoodMerchantId(@Param("ifoodMerchantId") String ifoodMerchantId);
}
