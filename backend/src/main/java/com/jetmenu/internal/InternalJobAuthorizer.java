package com.jetmenu.internal;

import com.jetmenu.common.SharedSecrets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Autoriza as chamadas aos jobs internos por segredo compartilhado no header
 * {@code X-Internal-Job-Token}.
 *
 * <p><b>Por que existe, se o Cloud Run já autentica por IAM.</b> Duas razões. A primeira é
 * cronológica: estes endpoints entram em produção no Railway antes da migração para o GCP, e
 * lá não há IAM nenhum na frente — sem este token, uma rota que varre a base inteira ficaria
 * aberta. A segunda é que, mesmo depois da migração, uma configuração de IAM afrouxada por
 * engano não deveria ser a única coisa entre a internet e este endpoint.
 *
 * <p><b>Fechado por padrão:</b> sem token configurado, nada é autorizado. Um deploy que
 * esqueça a variável deixa o job parado e visível no log do Cloud Scheduler, em vez de abrir a
 * rota para qualquer um.
 */
@Component
public class InternalJobAuthorizer {

    private final String expectedToken;

    public InternalJobAuthorizer(@Value("${app.internal-jobs.token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public boolean isAuthorized(String providedToken) {
        return SharedSecrets.matches(expectedToken, providedToken == null ? null : providedToken.trim());
    }
}
