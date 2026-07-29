package com.jetmenu.customer;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private OrderRepository orderRepository;


    @InjectMocks
    private CustomerService customerService;

    private UUID merchantId;
    private UUID customerId;
    private Customer customer;
    private CustomerRequest customerRequest;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        customerId = UUID.randomUUID();

        customerRequest = CustomerRequest.builder()
                .name("João Silva")
                .phone("11999999999")
                .email("joao@email.com")
                .build();

        customer = Customer.builder()
                .id(customerId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("João Silva")
                .phone("11999999999")
                .email("joao@email.com")
                .build();

        lenient().when(orderRepository.aggregatesByCustomerForMerchant(any(), any()))
                .thenReturn(java.util.List.of());
        lenient().when(orderRepository.originBreakdownByCustomerForMerchant(any(), any()))
                .thenReturn(java.util.List.of());
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar cliente com dados válidos e retornar CustomerResponse")
        void shouldCreateCustomerAndReturnResponse() {
            given(customerRepository.save(any(Customer.class))).willReturn(customer);

            CustomerResponse result = customerService.create(merchantId, customerRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(customerId);
            assertThat(result.getName()).isEqualTo(customerRequest.getName());
            assertThat(result.getPhone()).isEqualTo(customerRequest.getPhone());
            assertThat(result.getEmail()).isEqualTo(customerRequest.getEmail());
            then(customerRepository).should().save(argThat(c -> merchantId.equals(c.getMerchant().getId())));
        }

        @Test
        @DisplayName("deve criar cliente sem telefone e email (campos opcionais)")
        void shouldCreateCustomerWithoutOptionalFields() {
            CustomerRequest minimalRequest = CustomerRequest.builder()
                    .name("Maria Souza")
                    .build();

            Customer minimalCustomer = Customer.builder()
                    .id(customerId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Maria Souza")
                    .build();

            given(customerRepository.save(any(Customer.class))).willReturn(minimalCustomer);

            CustomerResponse result = customerService.create(merchantId, minimalRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Maria Souza");
            assertThat(result.getPhone()).isNull();
            assertThat(result.getEmail()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar CustomerResponse quando cliente existe")
        void shouldReturnResponseWhenExists() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));

            CustomerResponse result = customerService.findById(merchantId, customerId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(customerId);
            assertThat(result.getName()).isEqualTo(customer.getName());
            assertThat(result.getPhone()).isEqualTo(customer.getPhone());
            assertThat(result.getEmail()).isEqualTo(customer.getEmail());
        }

        @Test
        @DisplayName("deve lançar CustomerNotFoundException quando cliente não existe")
        void shouldThrowWhenCustomerNotFound() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.findById(merchantId, customerId))
                    .isInstanceOf(CustomerNotFoundException.class);
        }

        @Test
        @DisplayName("deve popular orderCount, lifetimeValue e lastOrderAt do aggregate")
        void shouldPopulateAggregates() {
            java.time.LocalDateTime when = java.time.LocalDateTime.of(2026, 5, 20, 12, 0);
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(orderRepository.aggregatesByCustomerForMerchant(eq(merchantId), any()))
                    .willReturn(java.util.List.<Object[]>of(
                            new Object[]{customerId, 5L, new java.math.BigDecimal("250.00"), when}));
            given(orderRepository.originBreakdownByCustomerForMerchant(eq(merchantId), any()))
                    .willReturn(java.util.List.<Object[]>of(
                            new Object[]{customerId, OrderOrigin.ANOTA_AI, 3L},
                            new Object[]{customerId, OrderOrigin.JETMENU, 2L}));

            CustomerResponse result = customerService.findById(merchantId, customerId);

            assertThat(result.getOrderCount()).isEqualTo(5L);
            assertThat(result.getLifetimeValue()).isEqualByComparingTo("250.00");
            assertThat(result.getLastOrderAt()).isEqualTo(when);
            assertThat(result.getPreferredOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
        }
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de clientes filtrada por nome (contains, case-insensitive)")
        void shouldReturnPagedCustomersFilteredByName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(customerRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "joão", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(customer), pageable, 1));

            org.springframework.data.domain.Page<CustomerResponse> result =
                    customerService.findAll(merchantId, "joão", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("João Silva");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(customerRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

            org.springframework.data.domain.Page<CustomerResponse> result =
                    customerService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar cliente existente e retornar CustomerResponse atualizado")
        void shouldUpdateAndReturnUpdatedResponse() {
            CustomerRequest updateRequest = CustomerRequest.builder()
                    .name("João Santos")
                    .phone("11988888888")
                    .email("joao.santos@email.com")
                    .build();

            Customer updatedCustomer = Customer.builder()
                    .id(customerId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("João Santos")
                    .phone("11988888888")
                    .email("joao.santos@email.com")
                    .build();

            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(customerRepository.save(any(Customer.class))).willReturn(updatedCustomer);

            CustomerResponse result = customerService.update(merchantId, customerId, updateRequest);

            assertThat(result.getName()).isEqualTo("João Santos");
            assertThat(result.getPhone()).isEqualTo("11988888888");
            assertThat(result.getEmail()).isEqualTo("joao.santos@email.com");
        }

        @Test
        @DisplayName("deve lançar CustomerNotFoundException ao atualizar cliente inexistente")
        void shouldThrowWhenCustomerNotFoundForUpdate() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.update(merchantId, customerId, customerRequest))
                    .isInstanceOf(CustomerNotFoundException.class);

            then(customerRepository).should(never()).save(any(Customer.class));
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar cliente existente sem lançar exceção")
        void shouldDeleteExistingCustomer() {
            given(customerRepository.existsByIdAndMerchantId(customerId, merchantId)).willReturn(true);
            willDoNothing().given(customerRepository).deleteByIdAndMerchantId(customerId, merchantId);

            assertThatNoException().isThrownBy(() -> customerService.delete(merchantId, customerId));

            then(customerRepository).should().deleteByIdAndMerchantId(customerId, merchantId);
        }

        @Test
        @DisplayName("deve lançar CustomerNotFoundException ao deletar cliente inexistente")
        void shouldThrowWhenCustomerNotFoundForDelete() {
            given(customerRepository.existsByIdAndMerchantId(customerId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> customerService.delete(merchantId, customerId))
                    .isInstanceOf(CustomerNotFoundException.class);

            then(customerRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }
}

