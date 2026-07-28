package com.jetmenu.merchant;

import com.jetmenu.integration.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que os dados das integrações vivem em tabelas normalizadas (ifood_integration
 * e anotaai_integration) e não mais em colunas achatadas na tabela merchants — os
 * acessores de conveniência em {@link Merchant} continuam funcionando, mas por baixo
 * gravam/leem das tabelas 1:1.
 */
@DisplayName("Normalização das integrações do merchant — Postgres")
class MerchantIntegrationNormalizationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a chave do Anota.AI é gravada na tabela anotaai_integration")
    void anotaAiKey_isStoredInSeparateTable() {
        Merchant merchant = createMerchant();
        merchant.setAnotaAiApiKey("anota-key-123");
        merchantRepository.saveAndFlush(merchant);

        String storedKey = jdbcTemplate.queryForObject(
                "select anota_ai_api_key from anotaai_integration where merchant_id = ?",
                String.class, merchant.getId());
        assertThat(storedKey).isEqualTo("anota-key-123");

        Merchant reloaded = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(reloaded.getAnotaAiApiKey()).isEqualTo("anota-key-123");
    }

    @Test
    @DisplayName("os campos do iFood são gravados na tabela ifood_integration")
    void ifoodFields_areStoredInSeparateTable() {
        Merchant merchant = createMerchant();
        merchant.setIfoodMerchantId("ifood-xyz");
        merchant.setIfoodAuthorizedAt(LocalDateTime.now());
        merchant.setIfoodOrderSyncEnabled(true);
        merchant.setIfoodCatalogImportedAt(LocalDateTime.now());
        merchantRepository.saveAndFlush(merchant);

        Long rows = jdbcTemplate.queryForObject(
                "select count(*) from ifood_integration "
                        + "where merchant_id = ? and ifood_merchant_id = ? and ifood_order_sync_enabled = true",
                Long.class, merchant.getId(), "ifood-xyz");
        assertThat(rows).isEqualTo(1L);

        Merchant reloaded = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(reloaded.getIfoodMerchantId()).isEqualTo("ifood-xyz");
        assertThat(reloaded.isIfoodOrderSyncEnabled()).isTrue();
        assertThat(reloaded.getIfoodAuthorizedAt()).isNotNull();
        assertThat(reloaded.getIfoodCatalogImportedAt()).isNotNull();
    }

    @Test
    @DisplayName("a tabela merchants não tem mais as colunas movidas")
    void merchantsTable_noLongerHasMovedColumns() {
        assertThat(columnExists("merchants", "anota_ai_api_key")).isZero();
        assertThat(columnExists("merchants", "ifood_merchant_id")).isZero();
        assertThat(columnExists("merchants", "ifood_authorized_at")).isZero();
        assertThat(columnExists("merchants", "ifood_order_sync_enabled")).isZero();
        assertThat(columnExists("merchants", "ifood_catalog_imported_at")).isZero();
    }

    @Test
    @DisplayName("um merchant sem integrações não cria linhas nas tabelas 1:1")
    void merchantWithoutIntegrations_hasNoIntegrationRows() {
        Merchant merchant = createMerchant();
        merchantRepository.saveAndFlush(merchant);

        Long ifoodRows = jdbcTemplate.queryForObject(
                "select count(*) from ifood_integration where merchant_id = ?", Long.class, merchant.getId());
        Long anotaRows = jdbcTemplate.queryForObject(
                "select count(*) from anotaai_integration where merchant_id = ?", Long.class, merchant.getId());
        assertThat(ifoodRows).isZero();
        assertThat(anotaRows).isZero();
    }

    private Integer columnExists(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = ? and column_name = ?",
                Integer.class, table, column);
    }
}
