package com.jetmenu.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByMerchantId(UUID merchantId);

    List<Subscription> findByStatusAndTrialEndsAtBefore(SubscriptionStatus status, LocalDateTime dateTime);
}
