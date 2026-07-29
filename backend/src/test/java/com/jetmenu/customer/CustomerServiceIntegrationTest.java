package com.jetmenu.customer;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerService — integração com Postgres")
class CustomerServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private CustomerService customerService;
    @Autowired private CustomerRepository customerRepository;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = createMerchant();
    }

    @Test
    @DisplayName("create deve persistir cliente ligado ao merchant")
    void create_shouldPersistCustomer() {
        CustomerResponse response = customerService.create(merchant.getId(), CustomerRequest.builder()
                .name("João").phone("11999990000").email("joao@example.com").build());

        Customer persisted = customerRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(persisted.getPhone()).isEqualTo("11999990000");
    }

    @Test
    @DisplayName("update deve modificar dados do cliente")
    void update_shouldModifyCustomer() {
        CustomerResponse created = customerService.create(merchant.getId(), CustomerRequest.builder()
                .name("Velho").phone("11000000000").build());

        customerService.update(merchant.getId(), created.getId(), CustomerRequest.builder()
                .name("Novo").phone("11999990000").email("novo@example.com").build());

        Customer persisted = customerRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Novo");
        assertThat(persisted.getEmail()).isEqualTo("novo@example.com");
    }

    @Test
    @DisplayName("delete deve remover cliente")
    void delete_shouldRemove() {
        CustomerResponse created = customerService.create(merchant.getId(), CustomerRequest.builder()
                .name("X").phone("11000000000").build());

        customerService.delete(merchant.getId(), created.getId());

        assertThat(customerRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByPhoneAndMerchantId deve achar cliente por telefone (usado no import da Anota.AI)")
    void findByPhone_shouldFindCustomer() {
        customerService.create(merchant.getId(), CustomerRequest.builder()
                .name("Maria").phone("43123456789").build());

        var found = customerRepository.findByPhoneAndMerchantId("43123456789", merchant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("findAll deve paginar isolando por merchant")
    void findAll_shouldIsolate() {
        customerService.create(merchant.getId(), CustomerRequest.builder().name("Meu").phone("1").build());

        Merchant outro = createMerchant("Outro");
        customerService.create(outro.getId(), CustomerRequest.builder().name("Do Outro").phone("2").build());

        var page = customerService.findAll(outro.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Do Outro");
    }
}
