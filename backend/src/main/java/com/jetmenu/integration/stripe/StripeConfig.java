package com.jetmenu.integration.stripe;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link StripeProperties}. Nothing else is registered here on purpose: the
 * {@code StripeClient} is built lazily by {@link StripeClientFactory} so the application
 * boots normally with Stripe unconfigured.
 */
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {
}
