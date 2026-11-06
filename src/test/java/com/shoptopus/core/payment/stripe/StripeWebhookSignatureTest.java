package com.shoptopus.core.payment.stripe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.config.PaymentProperties;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class StripeWebhookSignatureTest {
    private final String secret = "whsec_fixture_only";
    private final String payload =
            "{\"id\":\"evt_fixture\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_fixture\",\"object\":\"payment_intent\"}}}";

    @Test
    void genuine_signature_is_accepted() throws Exception {
        assertThatCode(
                        () ->
                                gateway()
                                        .verifyWebhook(
                                                payload,
                                                signature(payload, Instant.now().getEpochSecond())))
                .doesNotThrowAnyException();
    }

    @Test
    void altered_payload_or_signature_is_rejected() throws Exception {
        String signature = signature(payload, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> gateway().verifyWebhook(payload + " ", signature))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> gateway().verifyWebhook(payload, "t=1,v1=wrong"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void expired_delivery_signature_is_rejected() throws Exception {
        assertThatThrownBy(
                        () ->
                                gateway()
                                        .verifyWebhook(
                                                payload,
                                                signature(
                                                        payload,
                                                        Instant.now()
                                                                .minusSeconds(600)
                                                                .getEpochSecond())))
                .isInstanceOf(ApiException.class);
    }

    private StripePaymentGateway gateway() {
        var gateway =
                new StripePaymentGateway(
                        mock(PaymentProviderRepository.class), mock(PaymentProperties.class));
        ReflectionTestUtils.setField(gateway, "webhookSecret", secret);
        return gateway;
    }

    private String signature(String payload, long timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t="
                + timestamp
                + ",v1="
                + HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + payload)
                                                .getBytes(StandardCharsets.UTF_8)));
    }
}
