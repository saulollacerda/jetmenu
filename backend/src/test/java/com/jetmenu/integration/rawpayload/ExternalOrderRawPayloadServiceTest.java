package com.jetmenu.integration.rawpayload;

import com.jetmenu.order.OrderOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalOrderRawPayloadService")
class ExternalOrderRawPayloadServiceTest {

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock private ExternalOrderRawPayloadRepository repository;

    @InjectMocks
    private ExternalOrderRawPayloadService service;

    @Test
    @DisplayName("save deve persistir payload bruto com merchant, origin, externalOrderId e createdAt")
    void save_shouldPersistPayloadWithAllFields() {
        UUID merchantId = UUID.randomUUID();

        service.save(merchantId, OrderOrigin.IFOOD, "ord-1", "{\"id\":\"ord-1\"}");

        ArgumentCaptor<ExternalOrderRawPayload> captor =
                ArgumentCaptor.forClass(ExternalOrderRawPayload.class);
        verify(repository).save(captor.capture());
        ExternalOrderRawPayload saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getOrigin()).isEqualTo(OrderOrigin.IFOOD);
        assertThat(saved.getExternalOrderId()).isEqualTo("ord-1");
        assertThat(saved.getPayload()).isEqualTo("{\"id\":\"ord-1\"}");
        assertThat(saved.getCreatedAt())
                .isCloseTo(LocalDateTime.now(BRAZIL_ZONE), within(1, MINUTES));
    }

    @Test
    @DisplayName("save deve ignorar payload nulo ou em branco")
    void save_shouldSkipNullOrBlankPayload() {
        UUID merchantId = UUID.randomUUID();

        service.save(merchantId, OrderOrigin.ANOTA_AI, "ord-1", null);
        service.save(merchantId, OrderOrigin.ANOTA_AI, "ord-1", "   ");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("save NÃO deve propagar exceção — falha de auditoria não pode quebrar o import")
    void save_shouldSwallowRepositoryFailure() {
        given(repository.save(any())).willThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.save(
                UUID.randomUUID(), OrderOrigin.IFOOD, "ord-1", "{\"id\":\"ord-1\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("purgeExpired deve apagar payloads com mais de 3 dias e retornar o total removido")
    void purgeExpired_shouldDeletePayloadsOlderThanThreeDays() {
        given(repository.deleteByCreatedAtBefore(any(LocalDateTime.class))).willReturn(5L);

        long removed = service.purgeExpired();

        assertThat(removed).isEqualTo(5);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isCloseTo(LocalDateTime.now(BRAZIL_ZONE).minusDays(3), within(1, MINUTES));
    }
}
