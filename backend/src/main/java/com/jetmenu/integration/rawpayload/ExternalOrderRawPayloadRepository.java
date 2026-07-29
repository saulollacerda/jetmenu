package com.jetmenu.integration.rawpayload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ExternalOrderRawPayloadRepository extends JpaRepository<ExternalOrderRawPayload, UUID> {

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
