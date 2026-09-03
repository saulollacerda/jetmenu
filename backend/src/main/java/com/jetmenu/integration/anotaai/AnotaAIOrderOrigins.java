package com.jetmenu.integration.anotaai;

import com.jetmenu.order.OrderOrigin;

/**
 * Traduz o {@code salesChannel} da Anota.AI na origem com que o pedido é gravado.
 * <p>
 * Compartilhado entre o sync por polling e o webhook: os dois recebem os mesmos canais e
 * precisam decidir igual, senão o mesmo pedido entra com origem diferente conforme o caminho
 * por onde chegou.
 */
final class AnotaAIOrderOrigins {

    private AnotaAIOrderOrigins() {}

    /**
     * @return a origem com que gravar o pedido, ou {@code null} para canal que não deve
     *         ser importado.
     */
    static OrderOrigin resolve(String salesChannel, boolean ifoodOrdersImportEnabled) {
        if ("anotaai".equalsIgnoreCase(salesChannel)) return OrderOrigin.ANOTA_AI;
        if (ifoodOrdersImportEnabled && "ifood".equalsIgnoreCase(salesChannel)) return OrderOrigin.IFOOD;
        return null;
    }
}
