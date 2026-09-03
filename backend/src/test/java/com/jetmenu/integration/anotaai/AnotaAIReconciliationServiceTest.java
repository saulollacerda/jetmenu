package com.jetmenu.integration.anotaai;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.OpeningHour;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * A rede de segurança do webhook: uma varredura diária que reexecuta o sync para pegar o que
 * uma entrega perdida deixou passar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnotaAIReconciliationService")
class AnotaAIReconciliationServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private AnotaAISyncService syncService;

    @InjectMocks private AnotaAIReconciliationService reconciliationService;

    private Merchant merchantWithKey(UUID id, List<OpeningHour> hours) {
        Merchant merchant = Merchant.builder().id(id).openingHours(hours).build();
        merchant.setAnotaAiApiKey("key-" + id);
        return merchant;
    }

    private AnotaAISyncResult resultOf(int imported, int skipped) {
        return AnotaAISyncResult.builder()
                .ordersImported(imported)
                .ordersSkipped(skipped)
                .errors(List.of())
                .build();
    }

    /**
     * A diferença de comportamento que justifica não reusar o {@code OrderSyncScheduler}: ele
     * só olhava quem estava aberto <i>naquele minuto</i>. Uma varredura diária tem que olhar
     * todo mundo — senão a loja fechada às 4h da manhã, que é justamente quando o job roda,
     * nunca é reconciliada.
     */
    @Test
    @DisplayName("varre todos os lojistas com chave, inclusive os fechados no momento")
    void shouldSweepEveryMerchantWithKeyRegardlessOfOpeningHours() {
        UUID closed = UUID.randomUUID();
        UUID noHours = UUID.randomUUID();
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(
                merchantWithKey(closed, List.of(OpeningHour.builder()
                        .dayOfWeek(java.time.DayOfWeek.MONDAY)
                        .openTime("11:00").closeTime("12:00").closed(true).build())),
                merchantWithKey(noHours, null)));
        given(syncService.syncOrders(closed)).willReturn(resultOf(1, 0));
        given(syncService.syncOrders(noHours)).willReturn(resultOf(0, 3));

        AnotaAIReconciliationResult result = reconciliationService.reconcileAll();

        then(syncService).should().syncOrders(closed);
        then(syncService).should().syncOrders(noHours);
        assertThat(result.getMerchantsScanned()).isEqualTo(2);
        assertThat(result.getOrdersImported()).isEqualTo(1);
        assertThat(result.getOrdersSkipped()).isEqualTo(3);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("no-op quando não há lojista com chave")
    void shouldBeNoOpWithoutMerchants() {
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of());

        AnotaAIReconciliationResult result = reconciliationService.reconcileAll();

        assertThat(result.getMerchantsScanned()).isZero();
        assertThat(result.getOrdersImported()).isZero();
        assertThat(result.getErrors()).isEmpty();
    }

    /**
     * Uma loja com a chave errada não pode custar a reconciliação de todas as outras — o job
     * roda uma vez por dia, e abortar no primeiro erro deixaria o resto da base sem varredura
     * até o dia seguinte.
     */
    @Test
    @DisplayName("falha de um lojista não interrompe a varredura dos demais")
    void shouldKeepSweepingAfterOneMerchantFails() {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull())
                .willReturn(List.of(merchantWithKey(broken, null), merchantWithKey(healthy, null)));
        willThrow(new AnotaAIIntegrationException("chave inválida"))
                .given(syncService).syncOrders(broken);
        given(syncService.syncOrders(healthy)).willReturn(resultOf(2, 0));

        AnotaAIReconciliationResult result = reconciliationService.reconcileAll();

        then(syncService).should().syncOrders(healthy);
        assertThat(result.getMerchantsScanned()).isEqualTo(2);
        assertThat(result.getOrdersImported()).isEqualTo(2);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains(broken.toString()).contains("chave inválida");
    }
}
