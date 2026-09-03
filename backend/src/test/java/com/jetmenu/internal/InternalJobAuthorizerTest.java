package com.jetmenu.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InternalJobAuthorizer")
class InternalJobAuthorizerTest {

    @Test
    @DisplayName("aceita apenas o token exato")
    void shouldAcceptOnlyTheExactToken() {
        InternalJobAuthorizer authorizer = new InternalJobAuthorizer("segredo-do-job");

        assertThat(authorizer.isAuthorized("segredo-do-job")).isTrue();
        assertThat(authorizer.isAuthorized("segredo-do-job ")).isTrue();  // header com espaço sobrando
        assertThat(authorizer.isAuthorized("segredo-do-jo")).isFalse();
        assertThat(authorizer.isAuthorized("outro")).isFalse();
        assertThat(authorizer.isAuthorized(null)).isFalse();
        assertThat(authorizer.isAuthorized("")).isFalse();
    }

    /**
     * <b>Fechado por padrão.</b> Sem token configurado o endpoint recusa tudo, em vez de
     * liberar tudo: um deploy que esqueça a variável tem que deixar o job parado — e visível
     * no log do Cloud Scheduler —, nunca abrir uma rota que varre a base inteira.
     */
    @Test
    @DisplayName("sem token configurado nada é autorizado")
    void shouldRejectEverythingWhenNotConfigured() {
        assertThat(new InternalJobAuthorizer("").isAuthorized("qualquer")).isFalse();
        assertThat(new InternalJobAuthorizer(null).isAuthorized("qualquer")).isFalse();
        assertThat(new InternalJobAuthorizer("   ").isAuthorized("   ")).isFalse();
    }
}
