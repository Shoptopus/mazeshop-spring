package com.shoptopus.core.payment;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void get_settings_returns_public_provider_data() {
        var res = restTemplate.getForEntity("/api/payment/stripe/settings", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var data = (Map<?, ?>) res.getBody().get("data");
        assertThat(data.get("provider")).isEqualTo("stripe");
        assertThat(data.get("enabled")).isEqualTo(true);
        assertThat(data.get("settings")).asString().contains("public_key");
    }

    @Test
    void paypal_and_amazon_settings_are_reachable() {
        var paypal = restTemplate.getForEntity("/api/payment/paypal/settings", Map.class);
        assertThat(paypal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) paypal.getBody().get("data")).get("provider")).isEqualTo("paypal");

        var amazon = restTemplate.getForEntity("/api/payment/amazon/settings", Map.class);
        assertThat(amazon.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) amazon.getBody().get("data")).get("provider")).isEqualTo("amazon");
    }

    @Test
    void get_settings_rejects_unsupported_provider() {
        var res = restTemplate.getForEntity("/api/payment/unsupported/settings", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("user_message")).isEqualTo("Validation failed");
        assertThat(res.getBody().get("developer_message")).isEqualTo("Unsupported payment provider");
    }

    @Test
    void execute_validates_provider_and_returns_shape() {
        var ok = restTemplate.postForEntity("/api/payment/execute?provider=stripe&paymentRef=pay-1", null, Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<?, ?>) ok.getBody().get("data");
        assertThat(data.get("provider")).isEqualTo("stripe");
        assertThat(data.get("status")).isEqualTo("accepted");

        var unsupported = restTemplate.postForEntity("/api/payment/execute?provider=nope&paymentRef=pay-1", null, Map.class);
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unsupported.getBody().get("developer_message")).isEqualTo("Unsupported payment provider");

        var missingProvider = restTemplate.postForEntity("/api/payment/execute?paymentRef=pay-1", null, Map.class);
        assertThat(missingProvider.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
