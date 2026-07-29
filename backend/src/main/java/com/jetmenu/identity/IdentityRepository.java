package com.jetmenu.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository extends JpaRepository<Identity, UUID> {

    Optional<Identity> findByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
}
