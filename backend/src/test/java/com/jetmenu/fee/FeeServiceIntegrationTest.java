package com.jetmenu.fee;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FeeService — integração com Postgres")
class FeeServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private FeeService feeService;
    @Autowired private FeeRepository feeRepository;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = createMerchant();
    }

    @Test
    @DisplayName("create deve persistir taxa ligada ao merchant")
    void create_shouldPersistFee() {
        FeeResponse response = feeService.create(merchant.getId(), FeeRequest.builder()
                .name("Pix").feeRate(new BigDecimal("0.0099")).build());

        Fee persisted = feeRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(persisted.getFeeRate()).isEqualByComparingTo("0.0099");
    }

    @Test
    @DisplayName("create deve rejeitar nome duplicado dentro do merchant")
    void create_shouldRejectDuplicateName() {
        feeService.create(merchant.getId(), FeeRequest.builder()
                .name("Pix").feeRate(new BigDecimal("0.01")).build());

        assertThatThrownBy(() -> feeService.create(merchant.getId(), FeeRequest.builder()
                .name("Pix").feeRate(new BigDecimal("0.02")).build()))
                .isInstanceOf(DuplicateFeeException.class);
    }

    @Test
    @DisplayName("findByNameIgnoreCaseAndMerchantId deve achar taxa case-insensitive (usado no import da Anota.AI)")
    void findByName_shouldBeCaseInsensitive() {
        feeService.create(merchant.getId(), FeeRequest.builder()
                .name("money").feeRate(new BigDecimal("0.05")).build());

        var found = feeRepository.findByNameIgnoreCaseAndMerchantId("MONEY", merchant.getId());

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("update deve modificar nome e taxa")
    void update_shouldModifyFee() {
        FeeResponse created = feeService.create(merchant.getId(), FeeRequest.builder()
                .name("Old").feeRate(new BigDecimal("0.01")).build());

        feeService.update(merchant.getId(), created.getId(), FeeRequest.builder()
                .name("New").feeRate(new BigDecimal("0.05")).build());

        Fee persisted = feeRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("New");
        assertThat(persisted.getFeeRate()).isEqualByComparingTo("0.05");
    }

    @Test
    @DisplayName("delete deve remover taxa")
    void delete_shouldRemove() {
        FeeResponse created = feeService.create(merchant.getId(), FeeRequest.builder()
                .name("X").feeRate(new BigDecimal("0.01")).build());

        feeService.delete(merchant.getId(), created.getId());

        assertThat(feeRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAll deve paginar isolando por merchant")
    void findAll_shouldIsolate() {
        feeService.create(merchant.getId(), FeeRequest.builder().name("Meu").feeRate(new BigDecimal("0.01")).build());

        Merchant outro = createMerchant("Outro");
        feeService.create(outro.getId(), FeeRequest.builder().name("Do Outro").feeRate(new BigDecimal("0.02")).build());

        var page = feeService.findAll(outro.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Do Outro");
    }
}
