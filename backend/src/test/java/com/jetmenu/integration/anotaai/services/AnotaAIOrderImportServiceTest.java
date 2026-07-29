package com.jetmenu.integration.anotaai.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnotaAIOrderImportService.parseCreatedAt")
class AnotaAIOrderImportServiceTest {

    private final AnotaAIOrderImportService service =
            new AnotaAIOrderImportService(null, null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("deve converter instante UTC para horário de Brasília mesmo com timezone do servidor em UTC (ex.: Railway)")
    void parseCreatedAt_shouldConvertToBrazilZoneRegardlessOfServerTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            LocalDateTime result = service.parseCreatedAt("2026-05-20T21:31:50.786Z");

            // 21:31 UTC = 18:31 em America/Sao_Paulo (UTC-3)
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 5, 20, 18, 31, 50, 786_000_000));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("deve preservar o horário quando o offset já é -03:00")
    void parseCreatedAt_shouldKeepTimeWhenOffsetIsBrazil() {
        LocalDateTime result = service.parseCreatedAt("2026-05-20T18:31:50.786-03:00");

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 5, 20, 18, 31, 50, 786_000_000));
    }
}
