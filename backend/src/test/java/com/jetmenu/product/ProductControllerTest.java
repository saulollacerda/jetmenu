package com.jetmenu.product;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@WithMockUser
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID productId;
    private UUID categoryId;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(UUID.randomUUID());

        productResponse = ProductResponse.builder()
                .id(productId)
                .name("X-Burguer")
                .price(new BigDecimal("25.90"))
                .status(ProductStatus.ACTIVE)
                .categoryId(categoryId)
                .categoryName("Lanches")
                .build();
    }

    private ProductRequest buildValidRequest() {
        return ProductRequest.builder()
                .name("X-Burguer")
                .price(new BigDecimal("25.90"))
                .categoryId(categoryId)
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/products
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/products")
    class CreateProduct {

        @Test
        @DisplayName("deve retornar 201 com ProductResponse ao criar produto válido")
        void shouldReturn201WithProductResponse() throws Exception {
            given(productService.create(any(), any(ProductRequest.class))).willReturn(productResponse);

            mockMvc.perform(post("/api/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("X-Burguer"))
                    .andExpect(jsonPath("$.price").value(25.90))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                    .andExpect(jsonPath("$.categoryName").value("Lanches"));
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ProductRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando categoryId está ausente")
        void shouldReturn400WhenCategoryIdMissing() throws Exception {
            ProductRequest noCategory = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .build();

            mockMvc.perform(post("/api/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(noCategory)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está em branco")
        void shouldReturn400WhenNameIsBlank() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    ProductRequest.builder().name("").price(new BigDecimal("10.00")).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando nome já está em uso")
        void shouldReturn409WhenNameAlreadyInUse() throws Exception {
            given(productService.create(any(), any(ProductRequest.class)))
                    .willThrow(new DuplicateProductException("nome"));

            mockMvc.perform(post("/api/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/products/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("deve retornar 200 com ProductResponse quando produto existe")
        void shouldReturn200WhenProductExists() throws Exception {
            given(productService.findById(any(), eq(productId))).willReturn(productResponse);

            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("X-Burguer"))
                    .andExpect(jsonPath("$.price").value(25.90));
        }

        @Test
        @DisplayName("deve retornar 404 quando produto não encontrado")
        void shouldReturn404WhenProductNotFound() throws Exception {
            given(productService.findById(any(), eq(productId)))
                    .willThrow(new ProductNotFoundException(productId));

            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/products
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/products")
    class GetAllProducts {

        @Test
        @DisplayName("deve retornar 200 com página de produtos")
        void shouldReturn200WithProductPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(productService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(productResponse), pageable, 1));

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("X-Burguer"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(productService.findAll(any(), eq("burg"), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(productResponse), pageable, 1));

            mockMvc.perform(get("/api/products").param("search", "burg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("X-Burguer"));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/products/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProduct {

        @Test
        @DisplayName("deve retornar 200 com ProductResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(productService.update(any(), eq(productId), any(ProductRequest.class)))
                    .willReturn(productResponse);

            mockMvc.perform(put("/api/products/{id}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando produto não encontrado para atualização")
        void shouldReturn404WhenProductNotFoundForUpdate() throws Exception {
            given(productService.update(any(), eq(productId), any(ProductRequest.class)))
                    .willThrow(new ProductNotFoundException(productId));

            mockMvc.perform(put("/api/products/{id}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes na atualização")
        void shouldReturn400WhenRequiredFieldsMissingForUpdate() throws Exception {
            mockMvc.perform(put("/api/products/{id}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ProductRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/products/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("deve retornar 204 ao deletar produto existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(productService).delete(any(), eq(productId));

            mockMvc.perform(delete("/api/products/{id}", productId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar produto inexistente")
        void shouldReturn404WhenProductNotFoundForDelete() throws Exception {
            willThrow(new ProductNotFoundException(productId))
                    .given(productService).delete(any(), eq(productId));

            mockMvc.perform(delete("/api/products/{id}", productId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}

