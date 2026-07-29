package com.jetmenu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // Prototype scope: RestClient.Builder is mutable (baseUrl() mutates and returns
    // the same instance), so each injection point must get a fresh, unconfigured builder.
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
