package com.jetmenu.integration.ifood.services;

import com.jetmenu.integration.ifood.IfoodIntegrationStatus;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Estado e preferências da integração iFood no nível do merchant (checklist de
 * ativação e opt-in da sincronia de pedidos). Auth/token ficam no IfoodTokenService.
 */
@Service
public class IfoodIntegrationSettingsService {

    private final MerchantRepository merchantRepository;

    public IfoodIntegrationSettingsService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public IfoodIntegrationStatus getStatus(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(IfoodIntegrationSettingsService::toStatus)
                .orElseGet(IfoodIntegrationStatus::disconnected);
    }

    @Transactional
    public IfoodIntegrationStatus setOrderSyncEnabled(UUID merchantId, boolean enabled) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .filter(m -> m.getIfoodMerchantId() != null)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + merchantId + " is not connected to iFood"));

        merchant.setIfoodOrderSyncEnabled(enabled);
        merchantRepository.save(merchant);
        return toStatus(merchant);
    }

    private static IfoodIntegrationStatus toStatus(Merchant merchant) {
        return new IfoodIntegrationStatus(
                merchant.getIfoodMerchantId() != null,
                merchant.getIfoodCatalogImportedAt(),
                merchant.isIfoodOrderSyncEnabled());
    }
}
