package com.jetmenu.integration.anotaai;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Varredura diária que reexecuta o sync do Anota.AI para todo lojista com chave, pegando o que
 * uma entrega de webhook perdida deixou passar.
 *
 * <p>É a rede de segurança do webhook, e não um segundo caminho de importação: o sync já é
 * idempotente por {@code existsByExternalOrderId} (coberto em
 * {@code AnotaAISyncServiceIntegrationTest.syncOrders_shouldSkipAlreadyImportedOrder}), então
 * rodar os dois em paralelo não duplica pedido. É essa idempotência que permite manter o
 * Railway ligado durante o corte para o Cloud Run.
 *
 * <p><b>Sem o filtro de horário de funcionamento</b> que o {@code OrderSyncScheduler} usava.
 * Aquele filtro fazia sentido para um tick a cada 10 minutos; numa varredura diária ele
 * significaria nunca reconciliar a loja que está fechada no minuto em que o job roda — que é o
 * caso da maioria delas, já que o job roda de madrugada.
 *
 * <p><b>Síncrono, dentro do request.</b> No Cloud Run a CPU é estrangulada fora do ciclo do
 * request: {@code @Async} aqui seria trabalho que nunca termina. O volume é baixo e cabe no
 * timeout; se crescer, o próximo passo é Cloud Tasks, não uma thread de fundo.
 */
@Service
public class AnotaAIReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIReconciliationService.class);

    private final MerchantRepository merchantRepository;
    private final AnotaAISyncService syncService;

    public AnotaAIReconciliationService(MerchantRepository merchantRepository,
                                        AnotaAISyncService syncService) {
        this.merchantRepository = merchantRepository;
        this.syncService = syncService;
    }

    public AnotaAIReconciliationResult reconcileAll() {
        List<Merchant> merchants = merchantRepository.findAllByAnotaAiApiKeyIsNotNull();
        log.info("[Anota.AI][reconcile] varredura iniciada — {} lojista(s) com chave", merchants.size());

        int ordersImported = 0;
        int ordersSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (Merchant merchant : merchants) {
            try {
                AnotaAISyncResult result = syncService.syncOrders(merchant.getId());
                ordersImported += result.getOrdersImported();
                ordersSkipped += result.getOrdersSkipped();
                if (result.getErrors() != null) {
                    result.getErrors().forEach(e -> errors.add(merchant.getId() + ": " + e));
                }
            } catch (RuntimeException e) {
                // Uma loja com chave inválida não pode custar a varredura das outras: o job
                // roda uma vez por dia, e abortar aqui deixaria o resto da base sem
                // reconciliação até amanhã.
                log.error("[Anota.AI][reconcile] falhou — merchant={}: {}",
                        merchant.getId(), e.getMessage(), e);
                errors.add(merchant.getId() + ": " + e.getMessage());
            }
        }

        log.info("[Anota.AI][reconcile] varredura concluída — lojistas={} importados={} já existentes={} erros={}",
                merchants.size(), ordersImported, ordersSkipped, errors.size());

        return AnotaAIReconciliationResult.builder()
                .merchantsScanned(merchants.size())
                .ordersImported(ordersImported)
                .ordersSkipped(ordersSkipped)
                .errors(errors)
                .build();
    }
}
