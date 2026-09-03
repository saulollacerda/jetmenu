package com.jetmenu.integration.anotaai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe os pedidos que a Anota.AI entrega por webhook.
 *
 * <p>O contrato foi estabelecido por captura em produção, não pela documentação deles (que
 * descreve outra coisa): a credencial chega no header {@code authorization} com o valor
 * <b>cru</b>, sem {@code Bearer}; <b>não há assinatura</b> nenhuma; e o pedido vem na
 * <b>raiz</b> do corpo, sem o envelope {@code {success, info}} do {@code /ping/get/{id}}.
 *
 * <p>Esta rota é {@code permitAll()} no {@code SecurityConfig} porque a Anota.AI não manda
 * bearer token. Quem autentica é o segredo compartilhado, conferido em
 * {@link AnotaAIWebhookService} — e é a única credencial que existe aqui.
 *
 * <p><b>Os códigos de resposta são o protocolo com eles:</b> {@code 200} significa
 * "entregue, pare de reenviar" (inclusive para duplicata e para canal que não importamos),
 * {@code 404} esconde de quem não tem o segredo até se o merchant existe, {@code 400} recusa
 * corpo que não é um pedido de verdade, e {@code 5xx} pede o reenvio.
 */
@RestController
@RequestMapping("/api/webhooks/anotaai")
public class AnotaAIWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIWebhookController.class);

    private final AnotaAIWebhookService webhookService;

    public AnotaAIWebhookController(AnotaAIWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * O {@code merchantId} é vinculado como {@code String}, não como {@code UUID}: um valor
     * que o Spring não converte viraria 400 antes de chegar ao serviço, e o que queremos para
     * ele é o mesmo 404 de qualquer outra entrega que não reconhecemos.
     * <p>
     * O corpo vem como {@code byte[]} porque a validação precisa distinguir "corpo ausente"
     * de "corpo que não é um pedido" — os dois viram 400, mas por caminhos diferentes, e
     * deixar o Spring desserializar apagaria a diferença respondendo 400 sem passar pelo
     * serviço.
     * <p>
     * O painel da Anota.AI oferece POST ou PUT por evento; a captura mostrou POST, e PUT
     * segue aceito para não quebrar um lojista já cadastrado com ele.
     */
    @RequestMapping(value = "/{merchantId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Void> handle(
            @PathVariable String merchantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) byte[] rawPayload) {

        AnotaAIWebhookService.Outcome outcome = webhookService.handle(merchantId, authorization, rawPayload);
        log.debug("[Anota.AI][webhook] entrega processada — merchant={} resultado={}", merchantId, outcome);
        return ResponseEntity.ok().build();
    }

    /**
     * 404, e não 401/403: quem manda um segredo errado não pode nem descobrir que o merchant
     * daquela URL existe. O corpo vai vazio de propósito — não há nada que a Anota.AI possa
     * fazer com uma explicação, e há muito que um atacante faria.
     */
    @ExceptionHandler(AnotaAIWebhookService.UnknownDeliveryException.class)
    public ResponseEntity<Void> handleUnknownDelivery(AnotaAIWebhookService.UnknownDeliveryException e) {
        log.warn("[Anota.AI][webhook] entrega recusada: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 400 para corpo que não vira pedido. Responder 200 aqui é o que reabriria a falha
     * silenciosa que a captura evitou: entrega aceita, pedido nenhum importado, ninguém sabe.
     */
    @ExceptionHandler(AnotaAIWebhookService.InvalidPayloadException.class)
    public ResponseEntity<Void> handleInvalidPayload(AnotaAIWebhookService.InvalidPayloadException e) {
        log.warn("[Anota.AI][webhook] corpo recusado: {}", e.getMessage());
        return ResponseEntity.badRequest().build();
    }
}
