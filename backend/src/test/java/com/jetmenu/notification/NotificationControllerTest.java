package com.jetmenu.notification;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@WithMockUser
@DisplayName("NotificationController")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID notificationId;

    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(UUID.randomUUID());
    }

    @Nested
    @DisplayName("GET /api/notifications")
    class GetAll {

        @Test
        @DisplayName("deve retornar 200 com página de notificações")
        void shouldReturnPagedNotifications() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            NotificationResponse response = NotificationResponse.builder()
                    .id(notificationId)
                    .type(NotificationType.MISSING_INGREDIENT)
                    .title("Ingrediente não cadastrado")
                    .message("O ingrediente 'Pistache' ...")
                    .referenceData("pistache")
                    .referenceDisplay("Pistache")
                    .status(NotificationStatus.UNREAD)
                    .createdAt(Instant.parse("2026-05-22T12:00:00Z"))
                    .build();
            given(notificationService.findAll(any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(response), pageable, 1));

            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                    .andExpect(jsonPath("$.content[0].type").value("MISSING_INGREDIENT"))
                    .andExpect(jsonPath("$.content[0].referenceDisplay").value("Pistache"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/unread-count")
    class UnreadCount {

        @Test
        @DisplayName("deve retornar 200 com count das notificações não lidas")
        void shouldReturnUnreadCount() throws Exception {
            given(notificationService.unreadCount(any())).willReturn(5L);

            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/{id}/read")
    class MarkRead {

        @Test
        @DisplayName("deve retornar 204 ao marcar como lida")
        void shouldReturn204OnSuccess() throws Exception {
            willDoNothing().given(notificationService).markRead(any(), eq(notificationId));

            mockMvc.perform(put("/api/notifications/{id}/read", notificationId).with(csrf()))
                    .andExpect(status().isNoContent());

            then(notificationService).should().markRead(any(), eq(notificationId));
        }

        @Test
        @DisplayName("deve retornar 404 quando notificação não encontrada")
        void shouldReturn404WhenNotFound() throws Exception {
            willThrow(new NotificationNotFoundException(notificationId))
                    .given(notificationService).markRead(any(), eq(notificationId));

            mockMvc.perform(put("/api/notifications/{id}/read", notificationId).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications/{id}")
    class Dismiss {

        @Test
        @DisplayName("deve retornar 204 ao deletar")
        void shouldReturn204OnSuccess() throws Exception {
            willDoNothing().given(notificationService).dismiss(any(), eq(notificationId));

            mockMvc.perform(delete("/api/notifications/{id}", notificationId).with(csrf()))
                    .andExpect(status().isNoContent());

            then(notificationService).should().dismiss(any(), eq(notificationId));
        }

        @Test
        @DisplayName("deve retornar 404 quando notificação não existe")
        void shouldReturn404WhenNotFound() throws Exception {
            willThrow(new NotificationNotFoundException(notificationId))
                    .given(notificationService).dismiss(any(), eq(notificationId));

            mockMvc.perform(delete("/api/notifications/{id}", notificationId).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
