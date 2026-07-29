package com.jetmenu.category;

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

@WebMvcTest(CategoryController.class)
@WithMockUser
@DisplayName("CategoryController")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID categoryId;
    private UUID merchantId;
    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        categoryResponse = CategoryResponse.builder()
                .id(categoryId)
                .name("Lanches")
                .build();
    }

    private CategoryRequest buildValidRequest() {
        return CategoryRequest.builder()
                .name("Lanches")
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/categories
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/categories")
    class CreateCategory {

        @Test
        @DisplayName("deve retornar 201 com CategoryResponse ao criar categoria válida")
        void shouldReturn201WithCategoryResponse() throws Exception {
            given(categoryService.create(any(), any(CategoryRequest.class))).willReturn(categoryResponse);

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(categoryId.toString()))
                    .andExpect(jsonPath("$.name").value("Lanches"));
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está ausente")
        void shouldReturn400WhenNameMissing() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CategoryRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está em branco")
        void shouldReturn400WhenNameIsBlank() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    CategoryRequest.builder().name("").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando nome já está em uso")
        void shouldReturn409WhenNameAlreadyInUse() throws Exception {
            given(categoryService.create(any(), any(CategoryRequest.class)))
                    .willThrow(new DuplicateCategoryException("nome"));

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/categories/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/categories/{id}")
    class GetCategoryById {

        @Test
        @DisplayName("deve retornar 200 com CategoryResponse quando categoria existe")
        void shouldReturn200WhenCategoryExists() throws Exception {
            given(categoryService.findById(any(), eq(categoryId))).willReturn(categoryResponse);

            mockMvc.perform(get("/api/categories/{id}", categoryId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(categoryId.toString()))
                    .andExpect(jsonPath("$.name").value("Lanches"));
        }

        @Test
        @DisplayName("deve retornar 404 quando categoria não encontrada")
        void shouldReturn404WhenCategoryNotFound() throws Exception {
            given(categoryService.findById(any(), eq(categoryId)))
                    .willThrow(new CategoryNotFoundException(categoryId));

            mockMvc.perform(get("/api/categories/{id}", categoryId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/categories
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/categories")
    class GetAllCategories {

        @Test
        @DisplayName("deve retornar 200 com página de categorias")
        void shouldReturn200WithCategoryPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(categoryService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(categoryResponse), pageable, 1));

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(categoryId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("Lanches"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(categoryService.findAll(any(), eq("lan"), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(categoryResponse), pageable, 1));

            mockMvc.perform(get("/api/categories").param("search", "lan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Lanches"));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/categories/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/categories/{id}")
    class UpdateCategory {

        @Test
        @DisplayName("deve retornar 200 com CategoryResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(categoryService.update(any(), eq(categoryId), any(CategoryRequest.class)))
                    .willReturn(categoryResponse);

            mockMvc.perform(put("/api/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(categoryId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando categoria não encontrada para atualização")
        void shouldReturn404WhenCategoryNotFoundForUpdate() throws Exception {
            given(categoryService.update(any(), eq(categoryId), any(CategoryRequest.class)))
                    .willThrow(new CategoryNotFoundException(categoryId));

            mockMvc.perform(put("/api/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está ausente na atualização")
        void shouldReturn400WhenNameMissingForUpdate() throws Exception {
            mockMvc.perform(put("/api/categories/{id}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CategoryRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/categories/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/categories/{id}")
    class DeleteCategory {

        @Test
        @DisplayName("deve retornar 204 ao deletar categoria existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(categoryService).delete(any(), eq(categoryId));

            mockMvc.perform(delete("/api/categories/{id}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar categoria inexistente")
        void shouldReturn404WhenCategoryNotFoundForDelete() throws Exception {
            willThrow(new CategoryNotFoundException(categoryId))
                    .given(categoryService).delete(any(), eq(categoryId));

            mockMvc.perform(delete("/api/categories/{id}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}

