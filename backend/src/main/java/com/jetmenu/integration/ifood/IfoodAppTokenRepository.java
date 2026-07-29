package com.jetmenu.integration.ifood;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IfoodAppTokenRepository extends JpaRepository<IfoodAppToken, UUID> {
    Optional<IfoodAppToken> findTopByOrderByUpdatedAtDesc();
}
