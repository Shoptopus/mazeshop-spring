package com.shoptopus.core.payment.stripe;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.config.PaymentProperties;
import com.shoptopus.core.payment.PaymentProvider;
import com.shoptopus.core.payment.PaymentProviderConfig;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.RefundCreateParams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StripePaymentGateway {

    private final PaymentProviderRepository paymentProviderRepository;
    private final PaymentProperties paymentProperties;

    @Value("${app.payment.stripe.webhook-secret:}")
    private String webhookSecret;

    public StripePaymentGateway(
            PaymentProviderRepository paymentProviderRepository,
            PaymentProperties paymentProperties) {
        this.paymentProviderRepository = paymentProviderRepository;
        this.paymentProperties = paymentProperties;
    }

    public Map<String, Object> publicConfig() {
        StripeConfig config = config();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("public", config.publiclyExposed());
        row.put("test_mode", config.testMode());
        row.put("publishable_key", config.publiclyExposed() ? config.publishableKey() : "");
        return row;
    }

    public Map<String, Object> createPaymentIntent(
            BigDecimal amount, String currency, Map<String, String> metadata) {
        StripeConfig config = config();
        try {
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(toMinorUnits(amount))
                            .setCurrency(currency.toLowerCase())
                            .setAutomaticPaymentMethods(
                                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                            .setEnabled(true)
                                            .build())
                            .putAllMetadata(metadata)
                            .build();
            PaymentIntent intent =
                    new StripeClient(config.secretKey())
                            .paymentIntents()
                            .create(
                                    params,
                                    RequestOptions.builder()
                                            .setIdempotencyKey(metadata.get("checkout_attempt_id"))
                                            .build());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", "stripe");
            row.put("publishable_key", config.publishableKey());
            row.put("client_secret", intent.getClientSecret());
            row.put("payment_intent_id", intent.getId());
            row.put("status", intent.getStatus());
            return row;
        } catch (StripeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe error",
                    "Stripe payment intent creation failed: " + ex.getMessage());
        }
    }

    public StripePaymentIntentSnapshot paymentIntent(String paymentIntentId) {
        StripeConfig config = config();
        try {
            PaymentIntent intent =
                    new StripeClient(config.secretKey())
                            .paymentIntents()
                            .retrieve(
                                    paymentIntentId,
                                    PaymentIntentRetrieveParams.builder()
                                            .addExpand("latest_charge")
                                            .build(),
                                    null);
            return new StripePaymentIntentSnapshot(
                    intent.getId(),
                    intent.getStatus(),
                    intent.getPaymentMethod(),
                    intent.getLatestCharge(),
                    fromMinorUnits(intent.getAmount()),
                    intent.getCurrency(),
                    intent.getMetadata() == null ? Map.of() : Map.copyOf(intent.getMetadata()));
        } catch (StripeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe error",
                    "Stripe payment lookup failed: " + ex.getMessage());
        }
    }

    public StripePaymentIntentSnapshot cancelPaymentIntent(String id) {
        var current = paymentIntent(id);
        if ("succeeded".equals(current.status()) || "canceled".equals(current.status()))
            return current;
        try {
            new StripeClient(config().secretKey())
                    .paymentIntents()
                    .cancel(id, PaymentIntentCancelParams.builder().build());
        } catch (StripeException ex) {
            current = paymentIntent(id);
            if (!"succeeded".equals(current.status()) && !"canceled".equals(current.status()))
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "We could not update this payment. Please retry shortly.",
                        "Stripe intent cancellation failed");
            return current;
        }
        return paymentIntent(id);
    }

    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            transactionManager = "mainTransactionManager")
    public void verifyWebhook(String payload, String signature) {
        String secret = webhookSecret;
        if (isBlank(secret)) {
            var provider = paymentProviderRepository.findByNameIgnoreCase("stripe").orElse(null);
            if (provider != null) secret = configValue(provider, List.of("webhook_secret"));
        }
        if (isBlank(secret))
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook is not configured.",
                    "Stripe webhook signing secret missing");
        try {
            Webhook.constructEvent(payload, signature, secret);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment notification.",
                    "Stripe webhook signature validation failed");
        }
    }

    public Map<String, Object> refundPaymentIntent(String paymentIntentId, BigDecimal amount) {
        StripeConfig config = config();
        try {
            RefundCreateParams.Builder builder =
                    RefundCreateParams.builder().setPaymentIntent(paymentIntentId);
            if (amount != null) {
                builder.setAmount(toMinorUnits(amount));
            }
            Refund refund = new StripeClient(config.secretKey()).refunds().create(builder.build());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", refund.getId());
            row.put("status", refund.getStatus());
            row.put("amount", fromMinorUnits(refund.getAmount()));
            row.put("currency", refund.getCurrency());
            row.put("payment_intent", refund.getPaymentIntent());
            row.put("charge", refund.getCharge());
            return row;
        } catch (StripeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe error",
                    "Stripe refund failed: " + ex.getMessage());
        }
    }

    private StripeConfig config() {
        PaymentProvider provider =
                paymentProviderRepository
                        .findByNameIgnoreCase("stripe")
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Validation failed",
                                                "Stripe payment provider is not configured."));

        String publishableKey = configValue(provider, List.of("publishable_key", "public_key"));
        String secretKey = configValue(provider, List.of("secret_key"));

        if (isBlank(publishableKey)) {
            publishableKey =
                    paymentProperties.stripe() == null
                            ? null
                            : paymentProperties.stripe().publicKey();
        }
        if (isBlank(secretKey)) {
            secretKey =
                    paymentProperties.stripe() == null
                            ? null
                            : paymentProperties.stripe().secretKey();
        }
        if (isBlank(publishableKey) || isBlank(secretKey)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed",
                    "Stripe publishable and secret keys must be configured.");
        }
        boolean testKey = secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_");
        if (testKey != publishableKey.startsWith("pk_test_") || (provider.isTestMode() && !testKey))
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Stripe keys do not match the selected environment. Use matching test keys for"
                        + " testing.",
                    "Stripe key environment mismatch");
        boolean publiclyExposed = publicFlag(provider);
        return new StripeConfig(publishableKey, secretKey, testKey, publiclyExposed);
    }

    private String configValue(PaymentProvider provider, List<String> settings) {
        for (String setting : settings) {
            for (PaymentProviderConfig config : provider.getConfigs()) {
                if (setting.equalsIgnoreCase(config.getSetting())) {
                    String value =
                            provider.isTestMode() && !isBlank(config.getTestValue())
                                    ? config.getTestValue()
                                    : config.getValue();
                    if (!isBlank(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private boolean publicFlag(PaymentProvider provider) {
        String setting = provider.isTestMode() ? "test_public" : "live_public";
        for (PaymentProviderConfig config : provider.getConfigs()) {
            if (setting.equalsIgnoreCase(config.getSetting())) {
                String value =
                        provider.isTestMode() && !isBlank(config.getTestValue())
                                ? config.getTestValue()
                                : config.getValue();
                return value == null || value.isBlank() || Boolean.parseBoolean(value);
            }
        }
        return provider.getConfigs().stream()
                .filter(
                        config ->
                                "publishable_key".equalsIgnoreCase(config.getSetting())
                                        || "public_key".equalsIgnoreCase(config.getSetting()))
                .findFirst()
                .map(PaymentProviderConfig::isPublicValue)
                .orElse(true);
    }

    private BigDecimal fromMinorUnits(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StripeConfig(
            String publishableKey, String secretKey, boolean testMode, boolean publiclyExposed) {}

    public record StripePaymentIntentSnapshot(
            String id,
            String status,
            String paymentMethodId,
            String chargeId,
            BigDecimal amount,
            String currency,
            Map<String, String> metadata) {
        public StripePaymentIntentSnapshot(
                String id,
                String status,
                String paymentMethodId,
                String chargeId,
                BigDecimal amount,
                String currency) {
            this(id, status, paymentMethodId, chargeId, amount, currency, Map.of());
        }
    }
}
