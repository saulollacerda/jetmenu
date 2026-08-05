package com.jetmenu.integration.ifood.dto;

/**
 * Resposta do iFood repassada sem interpretação, para a tela de diagnóstico da homologação.
 *
 * <p>O analista do iFood pede para ver a chamada e o retorno crus, item a item do checklist —
 * por isso guardamos a URL efetivamente chamada, o status HTTP e o corpo exatamente como veio,
 * inclusive quando é um erro. Nada aqui é desserializado ou normalizado.</p>
 *
 * @param endpoint URL completa chamada no iFood
 * @param status   status HTTP devolvido pelo iFood
 * @param body     corpo da resposta como texto; vazio quando o iFood não devolve conteúdo
 */
public record IfoodRawResponse(String endpoint, int status, String body) {
}
