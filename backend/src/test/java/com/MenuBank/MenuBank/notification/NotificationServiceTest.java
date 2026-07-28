package com.MenuBank.MenuBank.notification;

import com.MenuBank.MenuBank.merchant.Merchant;
import com.MenuBank.MenuBank.merchant.MerchantRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MerchantRepository merchantRepository;


    @InjectMocks
    private NotificationService notificationService;

    private UUID merchantId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        notificationId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("createMissingIngredient()")
    class CreateMissingIngredient {

        @Test
        @DisplayName("deve criar nova notificação quando não existir uma pendente para o canonical name")
        void shouldCreateNewWhenNoPendingExists() {
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.MISSING_INGREDIENT, "pistache", NotificationStatus.RESOLVED))
                    .willReturn(Optional.empty());
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.createMissingIngredient("Pistache", "pistache", merchantId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getMerchant().getId()).isEqualTo(merchantId);
            assertThat(saved.getType()).isEqualTo(NotificationType.MISSING_INGREDIENT);
            assertThat(saved.getReferenceData()).isEqualTo("pistache");
            assertThat(saved.getReferenceDisplay()).isEqualTo("Pistache");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getTitle()).contains("Ingrediente");
            assertThat(saved.getMessage()).contains("Pistache");
            assertThat(result).isSameAs(saved);
        }

        @Test
        @DisplayName("deve reutilizar notificação existente (não duplicar) quando já existe uma não-resolvida para o mesmo canonical name")
        void shouldNotDuplicateWhenPendingExists() {
            Notification existing = Notification.builder()
                    .id(notificationId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .type(NotificationType.MISSING_INGREDIENT)
                    .referenceData("pistache")
                    .referenceDisplay("Pistache")
                    .status(NotificationStatus.UNREAD)
                    .createdAt(Instant.now())
                    .title("Ingrediente não cadastrado")
                    .message("...")
                    .build();
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.MISSING_INGREDIENT, "pistache", NotificationStatus.RESOLVED))
                    .willReturn(Optional.of(existing));

            Notification result = notificationService.createMissingIngredient("Pistache", "pistache", merchantId);

            then(notificationRepository).should(never()).save(any(Notification.class));
            assertThat(result).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("createMissingProduct()")
    class CreateMissingProduct {

        @Test
        @DisplayName("deve criar nova notificação MISSING_PRODUCT quando não existir uma pendente para o canonical name")
        void shouldCreateNewWhenNoPendingExists() {
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.MISSING_PRODUCT, "produto fantasma", NotificationStatus.RESOLVED))
                    .willReturn(Optional.empty());
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.createMissingProduct("Produto Fantasma", "produto fantasma", merchantId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getMerchant().getId()).isEqualTo(merchantId);
            assertThat(saved.getType()).isEqualTo(NotificationType.MISSING_PRODUCT);
            assertThat(saved.getReferenceData()).isEqualTo("produto fantasma");
            assertThat(saved.getReferenceDisplay()).isEqualTo("Produto Fantasma");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(saved.getTitle()).contains("Produto");
            assertThat(saved.getMessage()).contains("Produto Fantasma");
            assertThat(result).isSameAs(saved);
        }

        @Test
        @DisplayName("deve reutilizar notificação existente (não duplicar) quando já existe uma não-resolvida para o mesmo canonical name")
        void shouldNotDuplicateWhenPendingExists() {
            Notification existing = Notification.builder()
                    .id(notificationId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .type(NotificationType.MISSING_PRODUCT)
                    .referenceData("produto fantasma")
                    .referenceDisplay("Produto Fantasma")
                    .status(NotificationStatus.UNREAD)
                    .createdAt(Instant.now())
                    .title("Produto não cadastrado")
                    .message("...")
                    .build();
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.MISSING_PRODUCT, "produto fantasma", NotificationStatus.RESOLVED))
                    .willReturn(Optional.of(existing));

            Notification result = notificationService.createMissingProduct("Produto Fantasma", "produto fantasma", merchantId);

            then(notificationRepository).should(never()).save(any(Notification.class));
            assertThat(result).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("createOrderCancelled()")
    class CreateOrderCancelled {

        @Test
        @DisplayName("deve criar notificação ORDER_CANCELLED com o motivo quando disponível")
        void shouldCreateWithCancelReason() {
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.createOrderCancelled(
                    "ord-1", "Loja fechada", merchantId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getMerchant().getId()).isEqualTo(merchantId);
            assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
            assertThat(saved.getReferenceData()).isEqualTo("ord-1");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getTitle()).contains("cancelado");
            assertThat(saved.getMessage()).contains("ord-1").contains("Loja fechada");
            assertThat(result).isSameAs(saved);
        }

        @Test
        @DisplayName("deve criar notificação sem trecho de motivo quando cancelReasonDescription é nulo")
        void shouldCreateWithoutCancelReason() {
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            notificationService.createOrderCancelled("ord-1", null, merchantId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
            assertThat(saved.getMessage()).contains("ord-1").doesNotContain("Motivo");
        }
    }

    @Nested
    @DisplayName("createOrderCancellationRequested()")
    class CreateOrderCancellationRequested {

        private final UUID orderId = UUID.randomUUID();

        @Test
        @DisplayName("deve criar notificação ORDER_CANCELLATION_REQUESTED referenciando o id local do pedido")
        void shouldCreateReferencingLocalOrderId() {
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.ORDER_CANCELLATION_REQUESTED,
                    orderId.toString(), NotificationStatus.RESOLVED))
                    .willReturn(Optional.empty());
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Notification result = notificationService.createOrderCancellationRequested(
                    orderId, "ord-1", merchantId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getMerchant().getId()).isEqualTo(merchantId);
            assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CANCELLATION_REQUESTED);
            assertThat(saved.getReferenceData()).isEqualTo(orderId.toString());
            assertThat(saved.getReferenceDisplay()).isEqualTo("ord-1");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getTitle()).contains("cancelamento");
            assertThat(saved.getMessage()).contains("ord-1");
            assertThat(result).isSameAs(saved);
        }

        @Test
        @DisplayName("deve reaproveitar a notificação pendente do mesmo pedido em vez de duplicar")
        void shouldReusePendingNotification() {
            Notification existing = Notification.builder().id(notificationId).build();
            given(notificationRepository.findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                    merchantId, NotificationType.ORDER_CANCELLATION_REQUESTED,
                    orderId.toString(), NotificationStatus.RESOLVED))
                    .willReturn(Optional.of(existing));

            Notification result = notificationService.createOrderCancellationRequested(
                    orderId, "ord-1", merchantId);

            assertThat(result).isSameAs(existing);
            then(notificationRepository).should(never()).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("deleteMissingIngredient()")
    class DeleteMissingIngredient {

        @Test
        @DisplayName("deve apagar as notificações MISSING_INGREDIENT do canonical name e retornar a quantidade removida")
        void shouldDeleteAllForCanonicalName() {
            given(notificationRepository.deleteByMerchantIdAndTypeAndReferenceData(
                    merchantId, NotificationType.MISSING_INGREDIENT, "pistache"))
                    .willReturn(2L);

            int deleted = notificationService.deleteMissingIngredient("pistache", merchantId);

            assertThat(deleted).isEqualTo(2);
            then(notificationRepository).should().deleteByMerchantIdAndTypeAndReferenceData(
                    merchantId, NotificationType.MISSING_INGREDIENT, "pistache");
        }

        @Test
        @DisplayName("deve retornar 0 quando não há notificação para o canonical name")
        void shouldReturnZeroWhenNone() {
            given(notificationRepository.deleteByMerchantIdAndTypeAndReferenceData(
                    merchantId, NotificationType.MISSING_INGREDIENT, "pistache"))
                    .willReturn(0L);

            int deleted = notificationService.deleteMissingIngredient("pistache", merchantId);

            assertThat(deleted).isZero();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de notificações ordenadas por data desc do owner autenticado")
        void shouldReturnPagedNotificationsForCurrentUser() {
            Pageable pageable = PageRequest.of(0, 10);
            Notification n = Notification.builder()
                    .id(notificationId).merchant(Merchant.builder().id(merchantId).build())
                    .type(NotificationType.MISSING_INGREDIENT)
                    .referenceData("pistache").referenceDisplay("Pistache")
                    .status(NotificationStatus.UNREAD)
                    .title("t").message("m").createdAt(Instant.now()).build();
            given(notificationRepository.findAllByMerchantIdAndStatusNotOrderByCreatedAtDesc(
                    merchantId, NotificationStatus.RESOLVED, pageable))
                    .willReturn(new PageImpl<>(List.of(n), pageable, 1));

            var page = notificationService.findAll(merchantId, pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getId()).isEqualTo(notificationId);
            assertThat(page.getContent().get(0).getReferenceDisplay()).isEqualTo("Pistache");
        }
    }

    @Nested
    @DisplayName("unreadCount()")
    class UnreadCount {

        @Test
        @DisplayName("deve retornar contagem de notificações UNREAD do owner autenticado")
        void shouldReturnUnreadCount() {
            given(notificationRepository.countByMerchantIdAndStatus(merchantId, NotificationStatus.UNREAD))
                    .willReturn(3L);

            assertThat(notificationService.unreadCount(merchantId)).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("markRead()")
    class MarkRead {

        @Test
        @DisplayName("deve marcar UNREAD como READ")
        void shouldMarkUnreadAsRead() {
            Notification n = Notification.builder()
                    .id(notificationId).merchant(Merchant.builder().id(merchantId).build())
                    .type(NotificationType.MISSING_INGREDIENT)
                    .status(NotificationStatus.UNREAD)
                    .title("t").message("m").createdAt(Instant.now()).build();
            given(notificationRepository.findByIdAndMerchantId(notificationId, merchantId))
                    .willReturn(Optional.of(n));
            given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

            notificationService.markRead(merchantId, notificationId);

            assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
            then(notificationRepository).should().save(n);
        }

        @Test
        @DisplayName("não deve alterar status quando notificação já está RESOLVED")
        void shouldNotChangeStatusWhenAlreadyResolved() {
            Notification n = Notification.builder()
                    .id(notificationId).merchant(Merchant.builder().id(merchantId).build())
                    .type(NotificationType.MISSING_INGREDIENT)
                    .status(NotificationStatus.RESOLVED)
                    .title("t").message("m").createdAt(Instant.now()).build();
            given(notificationRepository.findByIdAndMerchantId(notificationId, merchantId))
                    .willReturn(Optional.of(n));

            notificationService.markRead(merchantId, notificationId);

            assertThat(n.getStatus()).isEqualTo(NotificationStatus.RESOLVED);
            then(notificationRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("deve lançar NotificationNotFoundException quando notificação não existe para o owner")
        void shouldThrowWhenNotFound() {
            given(notificationRepository.findByIdAndMerchantId(notificationId, merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markRead(merchantId, notificationId))
                    .isInstanceOf(NotificationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("dismiss()")
    class Dismiss {

        @Test
        @DisplayName("deve deletar notificação existente do owner autenticado")
        void shouldDeleteWhenExists() {
            given(notificationRepository.findByIdAndMerchantId(notificationId, merchantId))
                    .willReturn(Optional.of(Notification.builder().id(notificationId).merchant(Merchant.builder().id(merchantId).build()).build()));

            notificationService.dismiss(merchantId, notificationId);

            then(notificationRepository).should().deleteByIdAndMerchantId(notificationId, merchantId);
        }

        @Test
        @DisplayName("deve lançar NotificationNotFoundException quando não existe")
        void shouldThrowWhenNotFound() {
            given(notificationRepository.findByIdAndMerchantId(notificationId, merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.dismiss(merchantId, notificationId))
                    .isInstanceOf(NotificationNotFoundException.class);
        }
    }
}
