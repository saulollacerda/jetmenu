package com.MenuBank.MenuBank.notification;

import com.MenuBank.MenuBank.merchant.MerchantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MerchantRepository merchantRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               MerchantRepository merchantRepository) {
        this.notificationRepository = notificationRepository;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Creates a notification flagging that an ingredient referenced by an imported order
     * is not registered yet. Deduplicates by (merchantId, type, canonicalName): if a pending
     * (non-RESOLVED) notification already exists for the same canonical name, returns it
     * unchanged.
     */
    @Transactional
    public Notification createMissingIngredient(String rawName, String canonicalName, UUID merchantId) {
        Optional<Notification> existing = notificationRepository
                .findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                        merchantId, NotificationType.MISSING_INGREDIENT, canonicalName, NotificationStatus.RESOLVED);
        if (existing.isPresent()) {
            return existing.get();
        }

        Notification created = Notification.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .type(NotificationType.MISSING_INGREDIENT)
                .title("Ingrediente não cadastrado")
                .message("O ingrediente '" + rawName + "' apareceu em um pedido mas não está cadastrado no sistema.")
                .referenceData(canonicalName)
                .referenceDisplay(rawName)
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.save(created);
    }

    /**
     * Creates a notification flagging that a product referenced by an imported order is not
     * registered yet (no match by external code nor canonical name). Deduplicates by
     * (merchantId, type, canonicalName), like {@link #createMissingIngredient}.
     */
    @Transactional
    public Notification createMissingProduct(String rawName, String canonicalName, UUID merchantId) {
        Optional<Notification> existing = notificationRepository
                .findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                        merchantId, NotificationType.MISSING_PRODUCT, canonicalName, NotificationStatus.RESOLVED);
        if (existing.isPresent()) {
            return existing.get();
        }

        Notification created = Notification.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .type(NotificationType.MISSING_PRODUCT)
                .title("Produto não cadastrado")
                .message("O produto '" + rawName + "' apareceu em um pedido mas não está cadastrado no sistema."
                        + " O item foi ignorado na importação e o custo do pedido pode estar incompleto.")
                .referenceData(canonicalName)
                .referenceDisplay(rawName)
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.save(created);
    }

    /**
     * Creates a notification informing the merchant that an already imported order was
     * cancelled on iFood. Idempotency is handled by the caller (only invoked on the
     * transition to CANCELLED), so no dedup lookup is needed here.
     *
     * @param cancelReasonDescription iFood's cancellation reason, appended to the message
     *                                when available (may be {@code null})
     */
    @Transactional
    public Notification createOrderCancelled(String externalOrderId,
                                             String cancelReasonDescription,
                                             UUID merchantId) {
        String message = "O pedido " + externalOrderId + " foi cancelado no iFood e removido dos ganhos.";
        if (cancelReasonDescription != null && !cancelReasonDescription.isBlank()) {
            message += " Motivo: " + cancelReasonDescription;
        }

        Notification created = Notification.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .type(NotificationType.ORDER_CANCELLED)
                .title("Pedido cancelado")
                .message(message)
                .referenceData(externalOrderId)
                .referenceDisplay(externalOrderId)
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.save(created);
    }

    /**
     * Creates a notification informing the merchant that the customer (or the platform) asked
     * to cancel an order and is waiting for an answer. The order itself is untouched — only
     * accepting the request cancels it.
     *
     * <p>Deduplicates by (merchantId, type, orderId) so a redelivered iFood event does not pile
     * up alerts for the same request.
     *
     * <p>{@code referenceData} carries the <strong>local</strong> order id, not the iFood one:
     * the accept/deny endpoints are keyed by it, so the notification is directly actionable.
     * The iFood identifier goes in {@code referenceDisplay}, which is what the merchant reads.
     */
    @Transactional
    public Notification createOrderCancellationRequested(UUID orderId,
                                                         String externalOrderId,
                                                         UUID merchantId) {
        String reference = orderId.toString();
        Optional<Notification> existing = notificationRepository
                .findByMerchantIdAndTypeAndReferenceDataAndStatusNot(
                        merchantId, NotificationType.ORDER_CANCELLATION_REQUESTED,
                        reference, NotificationStatus.RESOLVED);
        if (existing.isPresent()) {
            return existing.get();
        }

        Notification created = Notification.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .type(NotificationType.ORDER_CANCELLATION_REQUESTED)
                .title("Solicitação de cancelamento")
                .message("O cliente solicitou o cancelamento do pedido " + externalOrderId
                        + ". Aceite ou recuse a solicitação no iFood.")
                .referenceData(reference)
                .referenceDisplay(externalOrderId)
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.save(created);
    }

    /**
     * Deletes every {@link NotificationType#MISSING_INGREDIENT} notification for the given
     * canonical name. Invoked when the merchant finally registers the ingredient, so the
     * alert disappears entirely instead of lingering as a resolved item.
     *
     * @return the number of notifications removed
     */
    @Transactional
    public int deleteMissingIngredient(String canonicalName, UUID merchantId) {
        return (int) notificationRepository.deleteByMerchantIdAndTypeAndReferenceData(
                merchantId, NotificationType.MISSING_INGREDIENT, canonicalName);
    }

    public Page<NotificationResponse> findAll(UUID merchantId, Pageable pageable) {
        return notificationRepository
                .findAllByMerchantIdAndStatusNotOrderByCreatedAtDesc(merchantId, NotificationStatus.RESOLVED, pageable)
                .map(this::toResponse);
    }

    public long unreadCount(UUID merchantId) {
        return notificationRepository.countByMerchantIdAndStatus(merchantId, NotificationStatus.UNREAD);
    }

    @Transactional
    public void markRead(UUID merchantId, UUID id) {
        Notification notification = notificationRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (notification.getStatus() == NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void dismiss(UUID merchantId, UUID id) {
        notificationRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notificationRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .referenceData(n.getReferenceData())
                .referenceDisplay(n.getReferenceDisplay())
                .status(n.getStatus())
                .createdAt(n.getCreatedAt())
                .resolvedAt(n.getResolvedAt())
                .build();
    }
}
