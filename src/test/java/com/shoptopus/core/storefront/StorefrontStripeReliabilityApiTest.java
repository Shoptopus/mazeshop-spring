package com.shoptopus.core.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.delivery.*;
import com.shoptopus.core.delivery.repository.*;
import com.shoptopus.core.mail.*;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.payment.stripe.StripePaymentGateway;
import com.shoptopus.core.payment.stripe.StripePaymentGateway.StripePaymentIntentSnapshot;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

class StorefrontStripeReliabilityApiTest extends IntegrationTestBase {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired DeliveryTypeRepository deliveries;
    @Autowired DeliveryRuleRepository rules;
    @Autowired PaymentProviderRepository providers;
    @Autowired StorefrontCheckoutService checkout;
    @Autowired SystemMailOutbox outbox;
    @Autowired PasswordEncoder encoder;
    @MockBean StripePaymentGateway stripe;
    @MockBean EmailDeliveryService mail;
    @LocalServerPort int port;
    Product product;
    DeliveryType delivery;
    final Map<String, StripePaymentIntentSnapshot> intents = new ConcurrentHashMap<>();

    @BeforeEach
    void fixtures() {
        var provider = providers.findByNameIgnoreCase("stripe").orElseThrow();
        provider.setEnabled(true);
        provider.setTestMode(true);
        providers.save(provider);
        product = new Product();
        product.setName(Map.of("en", "QA Binder"));
        product.setSlug("qa-binder-" + UUID.randomUUID());
        product.setDescription(Map.of("en", "QA"));
        product.setShortDescription(Map.of("en", "QA"));
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(2L);
        product.setStatus(1);
        product.setWeight(100);
        products.save(product);
        delivery = new DeliveryType();
        delivery.setName(Map.of("en", "QA UK delivery"));
        delivery.setSlug("qa-delivery-" + UUID.randomUUID());
        delivery.setPrice(new BigDecimal("3.95"));
        delivery.setEnabled(true);
        deliveries.save(delivery);
        var rule = new DeliveryRule();
        rule.setDeliveryType(delivery);
        rule.setName(Map.of("en", "UK"));
        rule.setSlug("qa-rule-" + UUID.randomUUID());
        rule.setCountry("UK");
        rule.setEnabled(true);
        rules.save(rule);
        when(stripe.publicConfig())
                .thenReturn(Map.of("publishable_key", "pk_test_fixture", "public", true));
        when(stripe.createPaymentIntent(any(), anyString(), anyMap()))
                .thenAnswer(
                        call -> {
                            String id = "pi_qa_" + UUID.randomUUID().toString().replace("-", "");
                            intents.put(
                                    id,
                                    new StripePaymentIntentSnapshot(
                                            id,
                                            "requires_payment_method",
                                            null,
                                            null,
                                            call.getArgument(0),
                                            "gbp",
                                            Map.copyOf(call.getArgument(2))));
                            return Map.of(
                                    "provider",
                                    "stripe",
                                    "payment_intent_id",
                                    id,
                                    "client_secret",
                                    id + "_secret_fixture",
                                    "publishable_key",
                                    "pk_test_fixture",
                                    "status",
                                    "requires_payment_method");
                        });
        when(stripe.paymentIntent(anyString()))
                .thenAnswer(call -> Objects.requireNonNull(intents.get(call.getArgument(0))));
        when(stripe.cancelPaymentIntent(anyString()))
                .thenAnswer(
                        call -> {
                            String id = call.getArgument(0);
                            if (!"succeeded".equals(intents.get(id).status()))
                                setStatus(id, "canceled");
                            return intents.get(id);
                        });
    }

    @Test
    void pending_order_exists_before_payment_and_unchanged_checkout_reuses_one_intent() {
        var body = guest(cart(), "buyer-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        assertThat(orderStatus(prepared)).isZero();
        assertThat(stock()).isEqualTo(1);
        assertThat(prepare(body).get("order_id")).isEqualTo(prepared.get("order_id"));
        verify(stripe, times(1)).createPaymentIntent(any(), anyString(), anyMap());
    }

    @Test
    void paid_submit_is_idempotent_after_the_basket_has_been_cleared() {
        var body = guest(cart(), "paid-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        paid(prepared);
        var first = submit(body, prepared);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        var second = submit(body, prepared);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(second).get("order_id")).isEqualTo(prepared.get("order_id"));
        assertThat(orderStatus(prepared)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from payments where payment_ref=?",
                                Integer.class,
                                prepared.get("payment_intent_id")))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from cart_product where cart_id=?::uuid",
                                Integer.class,
                                body.get("cart_id")))
                .isZero();
        assertThat(stock()).isEqualTo(1);
    }

    @Test
    void guest_with_an_existing_account_email_does_not_gain_account_or_address_access() {
        String email = "existing-" + UUID.randomUUID() + "@example.invalid";
        var user = new User();
        user.setEmail(email);
        user.setName("Existing Customer");
        user.setFirstName("Existing");
        user.setLastName("Customer");
        user.setPassword(encoder.encode("password123"));
        user.setSlug("qa-" + UUID.randomUUID());
        users.save(user);
        var body = guest(cart(), email);
        var prepared = prepare(body);
        paid(prepared);
        assertThat(submit(body, prepared).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        jdbc.queryForObject(
                                "select user_id is null from orders where id=?::uuid",
                                Boolean.class,
                                prepared.get("order_id")))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select a.user_id is null from addresses a join orders o on"
                                        + " o.address_id=a.id where o.id=?::uuid",
                                Boolean.class,
                                prepared.get("order_id")))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from users where lower(email)=lower(?)",
                                Integer.class,
                                email))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from system_mail_outbox where recipient=? and"
                                        + " subject like 'Receipt for %'",
                                Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void successful_payment_is_recovered_without_a_browser_submit_and_without_duplicate_mail() {
        var body = guest(cart(), "recovery-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        paid(prepared);
        checkout.reconcileOrder(UUID.fromString(String.valueOf(prepared.get("order_id"))));
        assertThat(orderStatus(prepared)).isEqualTo(1);
        int count =
                jdbc.queryForObject(
                        "select count(*) from system_mail_outbox where recipient=?",
                        Integer.class,
                        map(body.get("contact")).get("email"));
        checkout.reconcileOrder(UUID.fromString(String.valueOf(prepared.get("order_id"))));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from system_mail_outbox where recipient=?",
                                Integer.class,
                                map(body.get("contact")).get("email")))
                .isEqualTo(count);
    }

    @Test
    void receipt_delivery_failure_does_not_undo_a_paid_order_and_is_retried() {
        var body = guest(cart(), "mail-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        paid(prepared);
        doThrow(new IllegalStateException("simulated mail outage"))
                .when(mail)
                .sendHtml(anyString(), anyString(), anyString());
        assertThat(submit(body, prepared).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Shared fixtures can create hundreds of admin notices during submit. Prioritize this
        // test's receipt so the batch-size boundary cannot hide its delivery attempt.
        jdbc.update(
                "update system_mail_outbox set created_at=timestamp with time zone '2000-01-01"
                    + " 00:00:00+00' where recipient=?",
                map(body.get("contact")).get("email"));
        outbox.processOnce();
        assertThat(orderStatus(prepared)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from system_mail_outbox where recipient=? and"
                                        + " status='failed'",
                                Integer.class,
                                map(body.get("contact")).get("email")))
                .isGreaterThan(0);
        doNothing().when(mail).sendHtml(anyString(), anyString(), anyString());
        jdbc.update(
                "update system_mail_outbox set next_attempt_at=now() where recipient=?",
                map(body.get("contact")).get("email"));
        while (outbox.processOnce() > 0) {}
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from system_mail_outbox where recipient=? and"
                                        + " status='sent' and attempts=2",
                                Integer.class,
                                map(body.get("contact")).get("email")))
                .isEqualTo(1);
    }

    @Test
    void declined_payment_keeps_the_basket_and_cancel_releases_reservation_exactly_once() {
        var body = guest(cart(), "decline-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        assertThat(submit(body, prepared).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderStatus(prepared)).isZero();
        var cancel =
                Map.of(
                        "order_id",
                        prepared.get("order_id"),
                        "order_access_token",
                        prepared.get("order_access_token"));
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/cancel",
                                        cancel,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/cancel",
                                        cancel,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stock()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from cart_product where cart_id=?::uuid",
                                Integer.class,
                                body.get("cart_id")))
                .isEqualTo(1);
    }

    @Test
    void changed_currency_or_cart_binding_is_rejected_without_losing_the_pending_order() {
        var body = guest(cart(), "currency-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        String id = (String) prepared.get("payment_intent_id");
        var v = intents.get(id);
        intents.put(
                id,
                new StripePaymentIntentSnapshot(
                        id, "succeeded", "pm_test", "ch_test", v.amount(), "usd", v.metadata()));
        assertThat(submit(body, prepared).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderStatus(prepared)).isZero();
        intents.put(
                id,
                new StripePaymentIntentSnapshot(
                        id, "succeeded", "pm_test", "ch_test", v.amount(), "gbp", Map.of()));
        assertThat(submit(body, prepared).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderStatus(prepared)).isZero();
    }

    @Test
    void unknown_payment_references_and_fake_confirmation_cannot_mark_an_order_paid() {
        var body = guest(cart(), "fake-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/confirm",
                                        Map.of(
                                                "order_id",
                                                prepared.get("order_id"),
                                                "payment_id",
                                                prepared.get("payment_id"),
                                                "order_access_token",
                                                prepared.get("order_access_token"),
                                                "payment_ref",
                                                "fake-paid"),
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderStatus(prepared)).isZero();
    }

    @Test
    void guest_order_details_require_the_order_specific_access_token() {
        var prepared = prepare(guest(cart(), "access-" + UUID.randomUUID() + "@example.invalid"));
        String path = "/api/storefront/orders/" + prepared.get("order_id") + "/confirmation";
        assertThat(request(HttpMethod.GET, path, null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.GET, path, null, null, "wrong-token").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        request(
                                        HttpMethod.GET,
                                        path,
                                        null,
                                        null,
                                        (String) prepared.get("order_access_token"))
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void missing_stock_or_invalid_email_is_rejected_before_a_payment_intent_can_be_created() {
        var body = guest(cart(), "invalid-email");
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/payment-intent",
                                        body,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        map(body.get("contact")).put("email", "valid@example.invalid");
        jdbc.update("update products set stock=0 where id=?", product.getId());
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/payment-intent",
                                        body,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stripe, never()).createPaymentIntent(any(), anyString(), anyMap());
    }

    @Test
    void concurrent_customers_cannot_reserve_the_same_last_item() throws Exception {
        var a = guest(cart(), "race-a-" + UUID.randomUUID() + "@example.invalid");
        var b = guest(cart(), "race-b-" + UUID.randomUUID() + "@example.invalid");
        jdbc.update("update products set stock=1 where id=?", product.getId());
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                return request(
                                                HttpMethod.POST,
                                                "/api/storefront/checkout/payment-intent",
                                                a,
                                                null,
                                                null)
                                        .getStatusCode()
                                        .value();
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                return request(
                                                HttpMethod.POST,
                                                "/api/storefront/checkout/payment-intent",
                                                b,
                                                null,
                                                null)
                                        .getStatusCode()
                                        .value();
                            });
            gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
            assertThat(stock()).isZero();
            verify(stripe, times(1)).createPaymentIntent(any(), anyString(), anyMap());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void registering_from_a_reserved_guest_basket_releases_the_draft_and_preserves_items() {
        String source = cart();
        var body = guest(source, "signup-basket-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        var signup =
                request(
                        HttpMethod.POST,
                        "/api/storefront/auth/register",
                        Map.of(
                                "first_name",
                                "QA",
                                "last_name",
                                "Basket",
                                "email",
                                map(body.get("contact")).get("email"),
                                "password",
                                "TestOnly-password-2026!",
                                "password_confirmation",
                                "TestOnly-password-2026!"),
                        null,
                        null);
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = String.valueOf(map(data(signup).get("auth")).get("token"));
        var resolved =
                request(
                        HttpMethod.POST,
                        "/api/storefront/cart/resolve",
                        Map.of("cart_id", source),
                        token,
                        null);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(resolved).get("items")).hasSize(1);
        assertThat(stock()).isEqualTo(2);
        assertThat(orderStatus(prepared)).isEqualTo(3);
        String owned = String.valueOf(data(resolved).get("id"));
        assertThat(
                        request(HttpMethod.GET, "/api/storefront/cart/" + owned, null, null, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/cart/resolve",
                                        Map.of("cart_id", owned),
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicate_webhook_delivery_is_idempotent_and_unsigned_requests_are_rejected() {
        var body = guest(cart(), "webhook-" + UUID.randomUUID() + "@example.invalid");
        var prepared = prepare(body);
        paid(prepared);
        var event =
                Map.of(
                        "id",
                        "evt_fixture",
                        "type",
                        "payment_intent.succeeded",
                        "data",
                        Map.of("object", Map.of("id", prepared.get("payment_intent_id"))));
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/storefront/checkout/stripe/webhook",
                                        event,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "fixture-verified-by-mock");
        for (int i = 0; i < 2; i++)
            assertThat(
                            http.exchange(
                                            "http://localhost:"
                                                    + port
                                                    + "/api/storefront/checkout/stripe/webhook",
                                            HttpMethod.POST,
                                            new HttpEntity<>(event, headers),
                                            Map.class)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        assertThat(orderStatus(prepared)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from system_mail_outbox where recipient=? and"
                                        + " subject like 'Receipt for %'",
                                Integer.class, map(body.get("contact")).get("email")))
                .isEqualTo(1);
    }

    private String cart() {
        return String.valueOf(
                data(request(
                                HttpMethod.POST,
                                "/api/storefront/cart/items",
                                Map.of("product_id", product.getId(), "quantity", 1),
                                null,
                                null))
                        .get("id"));
    }

    private Map<String, Object> guest(String cart, String email) {
        var body = new LinkedHashMap<String, Object>();
        body.put("cart_id", cart);
        body.put("delivery_type_id", delivery.getId());
        body.put(
                "contact",
                new LinkedHashMap<>(
                        Map.of("email", email, "first_name", "QA", "last_name", "Customer")));
        body.put(
                "address",
                Map.of(
                        "name",
                        "QA Customer",
                        "address_line_1",
                        "1 Test Street",
                        "town",
                        "Chepstow",
                        "post_code",
                        "NP16 5NW",
                        "country",
                        "GB"));
        return body;
    }

    private Map<String, Object> prepare(Map<String, Object> body) {
        var response =
                request(
                        HttpMethod.POST,
                        "/api/storefront/checkout/payment-intent",
                        body,
                        null,
                        null);
        assertThat(response.getStatusCode())
                .withFailMessage("prepare returned %s", response.getBody().get("user_message"))
                .isEqualTo(HttpStatus.OK);
        return data(response);
    }

    private void paid(Map<String, Object> prepared) {
        setStatus((String) prepared.get("payment_intent_id"), "succeeded");
    }

    private void setStatus(String id, String status) {
        var v = intents.get(id);
        intents.put(
                id,
                new StripePaymentIntentSnapshot(
                        id, status, "pm_test", "ch_test", v.amount(), v.currency(), v.metadata()));
    }

    private ResponseEntity<Map> submit(Map<String, Object> original, Map<String, Object> prepared) {
        var body = new LinkedHashMap<>(original);
        body.put("payment_provider", "stripe");
        body.put("payment_intent_id", prepared.get("payment_intent_id"));
        body.put("order_access_token", prepared.get("order_access_token"));
        return request(HttpMethod.POST, "/api/storefront/checkout/submit", body, null, null);
    }

    private int stock() {
        return jdbc.queryForObject(
                "select stock from products where id=?", Integer.class, product.getId());
    }

    private int orderStatus(Map<String, Object> p) {
        return jdbc.queryForObject(
                "select status from orders where id=?::uuid", Integer.class, p.get("order_id"));
    }

    private ResponseEntity<Map> request(
            HttpMethod method, String path, Object body, String token, String access) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (access != null) headers.set("X-Order-Access", access);
        return http.exchange(
                "http://localhost:" + port + path,
                method,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private Map<String, Object> data(ResponseEntity<Map> r) {
        return map(r.getBody().get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object v) {
        return (Map<String, Object>) v;
    }
}
