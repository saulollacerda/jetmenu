package com.jetmenu.integration.anotaai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AnotaAIFixtures {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String BASE_PATH = "fixtures/anotaai/";

    private AnotaAIFixtures() {}

    static <T> T load(String fileName, Class<T> type) {
        try (InputStream is = AnotaAIFixtures.class.getClassLoader()
                .getResourceAsStream(BASE_PATH + fileName)) {
            if (is == null) {
                throw new IllegalStateException("Fixture não encontrada: " + BASE_PATH + fileName);
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler fixture " + fileName, e);
        }
    }
}
