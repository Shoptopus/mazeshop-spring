package com.shoptopus.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations")
public record IntegrationProperties(
        String googleMapsApiKey,
        String chatgptApiKey
) {
}
