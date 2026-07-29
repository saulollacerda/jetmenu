package com.jetmenu.integration.anotaai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnotaAICatalogResponse Jackson deserialization")
class AnotaAICatalogResponseDeserializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("deve desserializar is_additional=true corretamente")
    void shouldDeserializeIsAdditionalTrue() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": [
                    {
                      "title": "Adicionais",
                      "id": "cat-extra",
                      "is_additional": true,
                      "itens": []
                    }
                  ]
                }
                """;

        AnotaAICatalogResponse response = objectMapper.readValue(json, AnotaAICatalogResponse.class);

        assertThat(response.getCategories()).hasSize(1);
        assertThat(response.getCategories().get(0).isAdditional()).isTrue();
    }

    @Test
    @DisplayName("deve desserializar is_additional=false corretamente")
    void shouldDeserializeIsAdditionalFalse() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": [
                    {
                      "title": "Bebidas",
                      "id": "cat-1",
                      "is_additional": false,
                      "itens": []
                    }
                  ]
                }
                """;

        AnotaAICatalogResponse response = objectMapper.readValue(json, AnotaAICatalogResponse.class);

        assertThat(response.getCategories().get(0).isAdditional()).isFalse();
    }

    @Test
    @DisplayName("deve desserializar next_steps do item")
    void shouldDeserializeNextSteps() throws Exception {
        String json = """
                {
                  "data": [
                    {
                      "title": "Lanches",
                      "id": "cat-1",
                      "is_additional": false,
                      "itens": [
                        {
                          "id": "item-1",
                          "title": "Sanduíche",
                          "next_steps": [
                            { "category_id": "cat-extra", "category_title": "Adicionais", "min": 0, "max": 3 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        AnotaAICatalogResponse response = objectMapper.readValue(json, AnotaAICatalogResponse.class);
        AnotaAICatalogResponse.AnotaAIItem item = response.getCategories().get(0).getItens().get(0);

        assertThat(item.getNextSteps()).hasSize(1);
        assertThat(item.getNextSteps().get(0).getCategoryId()).isEqualTo("cat-extra");
        assertThat(item.getNextSteps().get(0).getMax()).isEqualTo(3);
    }
}
