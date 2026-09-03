package com.jetmenu.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duas coisas precisam ser verdade ao mesmo tempo nesta rota, e as duas quebram em silêncio.
 * <p>
 * Se o Spring Security a protegesse, o Cloud Scheduler receberia 401 e a reconciliação diária
 * nunca rodaria — sem erro nenhum deste lado, só pedidos que não aparecem. E se o
 * {@code permitAll} valesse sozinho, uma rota que varre a base inteira ficaria aberta na
 * internet. O contrato é: chega ao controller, e o controller recusa sem o token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig — jobs internos")
class InternalJobsSecurityTest {

    private static final String URL = "/api/internal/jobs/anotaai-reconcile";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST não autenticado chega ao controller (nunca 401/403)")
    void shouldPermitUnauthenticatedSchedulerCalls() throws Exception {
        int status = mockMvc.perform(post(URL)).andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    /**
     * O ambiente de teste não configura {@code app.internal-jobs.token}, então este caso é
     * exatamente o do deploy que esqueceu a variável: a rota tem que ficar fechada.
     */
    @Test
    @DisplayName("sem token configurado a rota recusa tudo com 404")
    void shouldStayClosedWhenTokenIsNotConfigured() throws Exception {
        mockMvc.perform(post(URL).header("X-Internal-Job-Token", "chute"))
                .andExpect(status().isNotFound());
    }

    /**
     * O Cloud Scheduler assina a chamada com um OIDC token do Google no header
     * {@code Authorization}. Ele não decodifica contra o JWKS do Supabase, e um 401 do
     * {@code JwtAuthFilter} aqui mataria o job silenciosamente.
     */
    @Test
    @DisplayName("OIDC token do Google no Authorization não vira 401")
    void shouldNotRejectGoogleOidcToken() throws Exception {
        int status = mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer token-oidc-que-nao-e-do-supabase"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
