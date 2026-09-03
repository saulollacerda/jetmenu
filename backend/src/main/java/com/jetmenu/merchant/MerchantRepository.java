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

    /**
     * Carrega o merchant já com a integração da Anota.AI inicializada. O webhook precisa do
     * segredo e do id da loja deles no mesmo passo em que resolve o merchant: a associação é
     * LAZY, e sem o fetch cada entrega faria duas idas ao banco em vez de uma.
     * <p>
     * LEFT JOIN de propósito — merchant sem linha de integração ainda é um merchant válido,
     * e quem decide o que fazer com a ausência do segredo é o webhook.
     */
    @Query("select m from Merchant m left join fetch m.anotaAiIntegration where m.id = :merchantId")
    Optional<Merchant> findByIdWithAnotaAiIntegration(@Param("merchantId") UUID merchantId);

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
