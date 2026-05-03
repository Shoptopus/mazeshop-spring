package com.shoptopus.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        Stripe stripe,
        Paypal paypal,
        Amazon amazon
) {
    public record Stripe(String publicKey, String secretKey) {}
    public record Paypal(String clientId, String secret) {}
    public record Amazon(String merchantId, String storeId, String publicKeyId, String region) {}
}
