package com.jetmenu.integration.anotaai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** O que a varredura diária encontrou. Corpo da resposta do job de reconciliação. */
@Data
@Builder
public class AnotaAIReconciliationResult {

    private int merchantsScanned;

    /**
     * Pedidos que o webhook não trouxe. <b>Zero é o resultado esperado</b> — qualquer número
     * acima disso é entrega perdida, e vale investigar antes que vire rotina.
     */
    private int ordersImported;

    /** Pedidos que já estavam no banco: a prova de que o webhook fez o trabalho dele. */
    private int ordersSkipped;

    private List<String> errors;
}
