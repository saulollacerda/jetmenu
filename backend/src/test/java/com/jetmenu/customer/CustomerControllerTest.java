package com.jetmenu.customer;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@WithMockUser
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID customerId;
    private UUID merchantId;
    private CustomerResponse customerResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        customerResponse = CustomerResponse.builder()
                .id(customerId)
                .name("João Silva")
                .phone("11999999999")
                .email("joao@email.com")
                .build();
    }

    private CustomerRequest buildValidRequest() {
        return CustomerRequest.builder()
                .name("João Silva")
                .phone("11999999999")
                .email("joao@email.com")
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/customers
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/customers")
    class CreateCustomer {

        @Test
        @DisplayName("deve retornar 201 com CustomerResponse ao criar cliente válido")
        void shouldReturn201WithCustomerResponse() throws Exception {
            given(customerService.create(any(), any(CustomerRequest.class))).willReturn(customerResponse);

            mockMvc.perform(post("/api/customers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(customerId.toString()))
                    .andExpect(jsonPath("$.name").value("João Silva"))
                    .andExpect(jsonPath("$.phone").value("11999999999"))
                    .andExpect(jsonPath("$.email").value("joao@email.com"));
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está ausente")
        void shouldReturn400WhenNameMissing() throws Exception {
            mockMvc.perform(post("/api/customers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CustomerRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está em branco")
        void shouldReturn400WhenNameIsBlank() throws Exception {
            mockMvc.perform(post("/api/customers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CustomerRequest.builder().name("").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando email é inválido")
        void shouldReturn400WhenEmailIsInvalid() throws Exception {
            mockMvc.perform(post("/api/customers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CustomerRequest.builder()
                                            .name("João Silva")
                                            .email("email-invalido")
                                            .build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 201 ao criar cliente apenas com nome (campos opcionais vazios)")
        void shouldReturn201WithOnlyName() throws Exception {
            CustomerResponse minimalResponse = CustomerResponse.builder()
                    .id(customerId)
                    .name("Maria Souza")
                    .build();

            given(customerService.create(any(), any(CustomerRequest.class))).willReturn(minimalResponse);

            mockMvc.perform(post("/api/customers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CustomerRequest.builder().name("Maria Souza").build())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Maria Souza"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/customers/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/customers/{id}")
    class GetCustomerById {

        @Test
        @DisplayName("deve retornar 200 com CustomerResponse quando cliente existe")
        void shouldReturn200WhenCustomerExists() throws Exception {
            given(customerService.findById(any(), eq(customerId))).willReturn(customerResponse);

            mockMvc.perform(get("/api/customers/{id}", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(customerId.toString()))
                    .andExpect(jsonPath("$.name").value("João Silva"));
        }

        @Test
        @DisplayName("deve retornar 404 quando cliente não encontrado")
        void shouldReturn404WhenCustomerNotFound() throws Exception {
            given(customerService.findById(any(), eq(customerId)))
                    .willThrow(new CustomerNotFoundException(customerId));

            mockMvc.perform(get("/api/customers/{id}", customerId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/customers
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/customers")
    class GetAllCustomers {

        @Test
        @DisplayName("deve retornar 200 com página de clientes")
        void shouldReturn200WithCustomerPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(customerService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(customerResponse), pageable, 1));

            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(customerId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("João Silva"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(customerService.findAll(any(), eq("joão"), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(customerResponse), pageable, 1));

            mockMvc.perform(get("/api/customers").param("search", "joão"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("João Silva"));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/customers/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/customers/{id}")
    class UpdateCustomer {

        @Test
        @DisplayName("deve retornar 200 com CustomerResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(customerService.update(any(), eq(customerId), any(CustomerRequest.class)))
                    .willReturn(customerResponse);

            mockMvc.perform(put("/api/customers/{id}", customerId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(customerId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando cliente não encontrado para atualização")
        void shouldReturn404WhenCustomerNotFoundForUpdate() throws Exception {
            given(customerService.update(any(), eq(customerId), any(CustomerRequest.class)))
                    .willThrow(new CustomerNotFoundException(customerId));

            mockMvc.perform(put("/api/customers/{id}", customerId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está ausente na atualização")
        void shouldReturn400WhenNameMissingForUpdate() throws Exception {
            mockMvc.perform(put("/api/customers/{id}", customerId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CustomerRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/customers/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/customers/{id}")
    class DeleteCustomer {

        @Test
        @DisplayName("deve retornar 204 ao deletar cliente existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(customerService).delete(any(), eq(customerId));

            mockMvc.perform(delete("/api/customers/{id}", customerId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar cliente inexistente")
        void shouldReturn404WhenCustomerNotFoundForDelete() throws Exception {
            willThrow(new CustomerNotFoundException(customerId))
                    .given(customerService).delete(any(), eq(customerId));

            mockMvc.perform(delete("/api/customers/{id}", customerId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}

