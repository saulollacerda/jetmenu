package com.jetmenu.notification;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura end-to-end de {@link NotificationService} contra Postgres real.
 *
 * <p>Validações cruciais:
 * <ul>
 *   <li>persistência do {@code Notification} com FK para {@code Merchant} (catch do bug
 *       do {@code owner_id} velho — schema vs entity);</li>
 *   <li>dedupe quando uma notificação pendente já existe para o mesmo canonical name;</li>
 *   <li>resolução automática quando o ingrediente é cadastrado depois;</li>
 *   <li>paginação ordenada por createdAt DESC;</li>
 *   <li>isolamento por merchant.</li>
 * </ul>
 */
@DisplayName("NotificationService — integração com Postgres")
class NotificationServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("createMissingIngredient deve persistir notificação ligada ao merchant")
    void createMissingIngredient_shouldPersistNotificationLinkedToMerchant() {
        Merchant merchant = createMerchant();

        Notification created = notificationService.createMissingIngredient(
                "Leite Ninho", "leite ninho", merchant.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(created.getType()).isEqualTo(NotificationType.MISSING_INGREDIENT);
        assertThat(created.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(created.getReferenceData()).isEqualTo("leite ninho");
        assertThat(created.getReferenceDisplay()).isEqualTo("Leite Ninho");
        assertThat(created.getCreatedAt()).isNotNull();

        // Confirma round-trip do banco (não só do retorno do save)
        Notification reloaded = notificationRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(reloaded.getMessage()).contains("Leite Ninho");
    }

    @Test
    @DisplayName("createMissingIngredient deve deduplicar quando já existe notificação pendente para o mesmo canonical name")
    void createMissingIngredient_shouldDedupeWhenPendingExistsForSameCanonical() {
        Merchant merchant = createMerchant();

        Notification first = notificationService.createMissingIngredient(
                "Leite Ninho", "leite ninho", merchant.getId());
        Notification second = notificationService.createMissingIngredient(
                "leite ninho", "leite ninho", merchant.getId());

        // Mesmo registro retornado — não persistiu de novo
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("createMissingIngredient deve criar nova notificação se a anterior foi apagada")
    void createMissingIngredient_shouldCreateNewWhenPreviousWasDeleted() {
        Merchant merchant = createMerchant();

        Notification first = notificationService.createMissingIngredient(
                "Leite Ninho", "leite ninho", merchant.getId());
        notificationService.deleteMissingIngredient("leite ninho", merchant.getId());

        Notification second = notificationService.createMissingIngredient(
                "leite ninho", "leite ninho", merchant.getId());

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("createMissingIngredient deve isolar entre merchants — cada um cria sua própria notificação")
    void createMissingIngredient_shouldIsolateBetweenMerchants() {
        Merchant merchantA = createMerchant("A");
        Merchant merchantB = createMerchant("B");

        Notification a = notificationService.createMissingIngredient(
                "Leite Ninho", "leite ninho", merchantA.getId());
        Notification b = notificationService.createMissingIngredient(
                "Leite Ninho", "leite ninho", merchantB.getId());

        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteMissingIngredient deve apagar todas as notificações do mesmo canonical name")
    void deleteMissingIngredient_shouldDeleteAllForCanonicalName() {
        Merchant merchant = createMerchant();
        notificationService.createMissingIngredient("Leite Ninho", "leite ninho", merchant.getId());

        int deletedCount = notificationService.deleteMissingIngredient("leite ninho", merchant.getId());

        assertThat(deletedCount).isEqualTo(1);
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteMissingIngredient deve retornar 0 quando não há notificação para o canonical name")
    void deleteMissingIngredient_shouldReturnZeroWhenNoneExists() {
        Merchant merchant = createMerchant();

        int deletedCount = notificationService.deleteMissingIngredient("inexistente", merchant.getId());

        assertThat(deletedCount).isZero();
    }

    @Test
    @DisplayName("unreadCount deve contar apenas notificações UNREAD do merchant autenticado")
    void unreadCount_shouldCountOnlyUnreadForMerchant() {
        Merchant merchant = createMerchantAndAuthenticate();
        Notification a = notificationService.createMissingIngredient("A", "a", merchant.getId());
        notificationService.createMissingIngredient("B", "b", merchant.getId());
        notificationService.markRead(merchant.getId(), a.getId());

        long count = notificationService.unreadCount(merchant.getId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("markRead deve mudar status UNREAD → READ")
    void markRead_shouldFlipStatus() {
        Merchant merchant = createMerchantAndAuthenticate();
        Notification n = notificationService.createMissingIngredient("X", "x", merchant.getId());

        notificationService.markRead(merchant.getId(), n.getId());

        Notification reloaded = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("dismiss deve remover a notificação do banco")
    void dismiss_shouldDeleteFromDatabase() {
        Merchant merchant = createMerchantAndAuthenticate();
        Notification n = notificationService.createMissingIngredient("X", "x", merchant.getId());

        notificationService.dismiss(merchant.getId(), n.getId());

        assertThat(notificationRepository.findById(n.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByMerchantIdOrderByCreatedAtDesc deve listar notificações do merchant, mais recente primeiro")
    void listing_shouldReturnNotificationsOrderedByCreatedAtDesc() {
        Merchant merchant = createMerchant();
        notificationService.createMissingIngredient("Primeiro", "primeiro", merchant.getId());
        // Garante diferença de instant
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        notificationService.createMissingIngredient("Segundo", "segundo", merchant.getId());

        var page = notificationRepository.findAllByMerchantIdOrderByCreatedAtDesc(
                merchant.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getReferenceData()).isEqualTo("segundo");
        assertThat(page.getContent().get(1).getReferenceData()).isEqualTo("primeiro");
    }
}
