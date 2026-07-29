package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Default {@link BillingProvider} while no payment platform is integrated.
 * <p>
 * It fails loudly instead of silently: the merchant gets an explicit pt-BR 503 rather
 * than a checkout button that does nothing. Replace it (or override it with a
 * {@code @Primary} bean) when the next provider ships.
 */
@Service
public class UnavailableBillingProvider implements BillingProvider {

    private static final Logger log = LoggerFactory.getLogger(UnavailableBillingProvider.class);

    @Override
    public CheckoutResponse createCheckout(UUID merchantId, UUID planId) {
        log.warn("Checkout solicitado pelo merchant {} para o plano {}, mas nenhum provedor de "
                + "pagamento está integrado", merchantId, planId);
        throw new BillingProviderUnavailableException();
    }
}
