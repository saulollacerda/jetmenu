package com.jetmenu.internal;

import com.jetmenu.integration.anotaai.AnotaAIReconciliationResult;
import com.jetmenu.integration.anotaai.AnotaAIReconciliationService;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.integration.stripe.StripeCatalogSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * O que antes eram schedulers agora são jobs chamados de fora, pelo Cloud Scheduler.
 *
 * <p>A troca é o que permite o serviço escalar a zero: um {@code @Scheduled} precisa de uma
 * instância viva 24h por dia esperando o relógio, enquanto um POST acorda o serviço, faz o
 * trabalho e o deixa dormir de novo. Dois jobs × 30 execuções/mês é custo irrisório.
 *
 * <p><b>Tudo síncrono, dentro do request.</b> No Cloud Run a CPU é estrangulada fora do ciclo
 * do request — {@code @Async} aqui seria trabalho que nunca termina.
 *
 * <p>Autorização: {@code X-Internal-Job-Token} (ver {@link InternalJobAuthorizer}), somado ao
 * IAM do Cloud Run quando a migração estiver feita. Recusa é <b>404</b>, e não 401: quem não
 * tem o token não precisa nem saber que a rota existe.
 */
@RestController
@RequestMapping("/api/internal/jobs")
public class InternalJobsController {

    private static final Logger log = LoggerFactory.getLogger(InternalJobsController.class);

    private final InternalJobAuthorizer authorizer;
    private final AnotaAIReconciliationService reconciliationService;
    private final ExternalOrderRawPayloadService rawPayloadService;
    private final StripeCatalogSync catalogSync;

    public InternalJobsController(InternalJobAuthorizer authorizer,
                                  AnotaAIReconciliationService reconciliationService,
                                  ExternalOrderRawPayloadService rawPayloadService,
                                  StripeCatalogSync catalogSync) {
        this.authorizer = authorizer;
        this.reconciliationService = reconciliationService;
        this.rawPayloadService = rawPayloadService;
        this.catalogSync = catalogSync;
    }

    /**
     * Varredura diária que pega os pedidos que o webhook não trouxe. Idempotente: o sync já
     * pula o que existe, então rodar de novo (ou em paralelo com o polling antigo, durante o
     * corte) não duplica pedido.
     */
    @PostMapping("/anotaai-reconcile")
    public ResponseEntity<AnotaAIReconciliationResult> reconcileAnotaAiOrders(
            @RequestHeader(value = "X-Internal-Job-Token", required = false) String token) {

        if (!authorizer.isAuthorized(token)) {
            return notFound();
        }
        return ResponseEntity.ok(reconciliationService.reconcileAll());
    }

    /**
     * Sincroniza o catálogo da Stripe. Saiu do boot ({@code StripeCatalogSyncRunner}) porque
     * era uma chamada de rede externa em cada instância nova; o catálogo muda raramente e uma
     * vez por dia basta.
     */
    @PostMapping("/stripe-catalog-sync")
    public ResponseEntity<StripeCatalogSync.SyncResult> syncStripeCatalog(
            @RequestHeader(value = "X-Internal-Job-Token", required = false) String token) {

        if (!authorizer.isAuthorized(token)) {
            return notFound();
        }
        return ResponseEntity.ok(catalogSync.sync());
    }

    /** Limpeza dos payloads brutos fora da janela de retenção — antes o cron das 4h. */
    @PostMapping("/raw-payload-cleanup")
    public ResponseEntity<Map<String, Long>> cleanUpRawPayloads(
            @RequestHeader(value = "X-Internal-Job-Token", required = false) String token) {

        if (!authorizer.isAuthorized(token)) {
            return notFound();
        }
        return ResponseEntity.ok(Map.of("removed", rawPayloadService.purgeExpired()));
    }

    private <T> ResponseEntity<T> notFound() {
        log.warn("[Jobs] chamada recusada — token do job ausente ou inválido");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
