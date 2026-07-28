package com.jetmenu.integration.rawpayload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalOrderRawPayloadCleanupScheduler")
class ExternalOrderRawPayloadCleanupSchedulerTest {

    @Mock private ExternalOrderRawPayloadService service;

    @InjectMocks
    private ExternalOrderRawPayloadCleanupScheduler scheduler;

    @Test
    @DisplayName("purgeExpiredPayloads deve delegar para o service")
    void shouldDelegateToService() {
        scheduler.purgeExpiredPayloads();

        verify(service).purgeExpired();
    }

    @Test
    @DisplayName("exceção do service não propaga — scheduler não pode morrer")
    void shouldSwallowServiceFailure() {
        given(service.purgeExpired()).willThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.purgeExpiredPayloads()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("roda uma vez ao dia às 04:00 no fuso de Brasília")
    void shouldBeScheduledDailyAtFourAmBrazilTime() throws NoSuchMethodException {
        Method method = ExternalOrderRawPayloadCleanupScheduler.class
                .getMethod("purgeExpiredPayloads");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("America/Sao_Paulo");
    }
}
