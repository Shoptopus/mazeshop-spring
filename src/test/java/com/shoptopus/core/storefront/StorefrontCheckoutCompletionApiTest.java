package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.delivery.DeliveryRule;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.delivery.repository.DeliveryRuleRepository;
import com.shoptopus.core.delivery.repository.DeliveryTypeRepository;
import com.shoptopus.core.mail.EmailDeliveryService;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StorefrontCheckoutCompletionApiTest extends IntegrationTestBase {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository productCategoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private DeliveryTypeRepository deliveryTypeRepository;
    @Autowired private DeliveryRuleRepository deliveryRuleRepository;
    @Autowired private PaymentProviderRepository paymentProviderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private AccessTokenRepository accessTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailDeliveryService emailDeliveryService;

    @LocalServerPort
    private int port;

    @Test
    void storefront_checkout_can_submit_confirm_and_serve_confirmation() {
        ProductCategory category = new ProductCategory();
        category.setName(Map.of("en", "General"));
        category.setSlug("general-" + UUID.randomUUID());
        category.setDescription(Map.of("en", "General"));
        productCategoryRepository.save(category);

        Product product = new Product();
        product.setName(Map.of("en", "Bag"));
        product.setSlug("bag-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Desc"));
        product.setPrice(new BigDecimal("25.00"));
        product.setStock(20L);
        product.setWeight(2);
        product.setStatus(1);
        product.getCategories().add(category);
        productRepository.save(product);

        User user = new User();
        user.setFirstName("Shop");
        user.setLastName("User");
        user.setName("Shop User");
        user.setEmail("storefront-complete-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setSlug("user-" + UUID.randomUUID());
        userRepository.save(user);

        Address address = new Address();
        address.setUser(user);
        address.setSlug("address-" + UUID.randomUUID());
        address.setAddressLine1("1 Test St");
        address.setTown("Cardiff");
        address.setPostCode("CF10 1AA");
        address.setCountry("UK");
        addressRepository.save(address);

        DeliveryType deliveryType = new DeliveryType();
        deliveryType.setName(Map.of("en", "Standard"));
        deliveryType.setSlug("standard-" + UUID.randomUUID());
        deliveryType.setPrice(new BigDecimal("4.99"));
        deliveryType.setEnabled(true);
        deliveryTypeRepository.save(deliveryType);

        DeliveryRule rule = new DeliveryRule();
        rule.setDeliveryType(deliveryType);
        rule.setName(Map.of("en", "UK rule"));
        rule.setSlug("uk-rule-" + UUID.randomUUID());
        rule.setCountry("UK");
        rule.setPrice(new BigDecimal("4.99"));
        rule.setEnabled(true);
        deliveryRuleRepository.save(rule);

        String token = login(user.getEmail(), "password123");
        var add = exchange(HttpMethod.POST, "/api/storefront/cart/items", Map.of(
                "product_id", product.getId(),
                "quantity", 2
        ), token);
        String cartId = String.valueOf(dataObject(add).get("id"));

        var submit = exchange(HttpMethod.POST, "/api/storefront/checkout/submit", Map.of(
                "cart_id", cartId,
                "address_id", address.getId(),
                "delivery_type_id", deliveryType.getId(),
                "payment_provider", "paypal",
                "payment", Map.of(
                        "payer_email", user.getEmail(),
                        "provider_ref", "paypal-ref-1"
                )
        ), token);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> submitData = dataObject(submit);
        String orderId = String.valueOf(submitData.get("order_id"));
        String paymentId = String.valueOf(submitData.get("payment_id"));

        var confirm = exchange(HttpMethod.POST, "/api/storefront/checkout/confirm", Map.of(
                "order_id", orderId,
                "payment_id", paymentId,
                "payment_ref", "pay-ref-1"
        ), token);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);

        var confirmation = exchange(HttpMethod.GET, "/api/storefront/orders/" + orderId + "/confirmation", null, null);
        assertThat(confirmation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(confirmation)).containsKeys("items", "payments", "totals");

        assertThat(orderRepository.findById(UUID.fromString(orderId))).isPresent();
        assertThat(paymentRepository.findById(UUID.fromString(paymentId))).isPresent();
        var invoice = invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(UUID.fromString(orderId));
        assertThat(invoice).isPresent();
        @SuppressWarnings("unchecked")
        var invoiceItems = (java.util.List<Map<String, Object>>) invoice.orElseThrow().getProducts().get("items");
        assertThat(invoiceItems).hasSize(1);
        assertThat(invoiceItems.get(0)).containsKeys(
                "original_unit_price",
                "unit_price",
                "unit_discount",
                "line_original_amount",
                "line_discount_amount",
                "line_total_amount",
                "cover_image",
                "cover_thumbnail_base64"
        );
        assertThat(invoice.orElseThrow().getTotals()).containsKeys(
                "subtotal_amount",
                "item_discount_amount",
                "voucher_discount_amount",
                "delivery_amount",
                "total_amount"
        );
        assertThat(accessTokenRepository.findAll().stream()
                .anyMatch(tokenRow -> tokenRow.getType() == 4
                        && "Invoice".equals(tokenRow.getAccessableType())
                        && invoice.orElseThrow().getId().toString().equals(tokenRow.getAccessableId())))
                .isTrue();
        String invoiceToken = accessTokenRepository.findAll().stream()
                .filter(tokenRow -> tokenRow.getType() == 4
                        && "Invoice".equals(tokenRow.getAccessableType())
                        && invoice.orElseThrow().getId().toString().equals(tokenRow.getAccessableId()))
                .map(com.shoptopus.core.auth.AccessToken::getToken)
                .findFirst()
                .orElseThrow();
        verify(emailDeliveryService, times(1)).sendHtml(
                eq(user.getEmail()),
                eq("Receipt for " + orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getSlug()),
                contains("/api/storefront/invoice/" + invoiceToken)
        );

        ResponseEntity<byte[]> invoicePdf = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/storefront/invoice/" + invoiceToken,
                byte[].class
        );
        assertThat(invoicePdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invoicePdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(invoicePdf.getBody()).isNotNull();
        assertThat(invoicePdf.getBody().length).isGreaterThan(500);

        ResponseEntity<byte[]> invoicePdfSecondUse = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/storefront/invoice/" + invoiceToken,
                byte[].class
        );
        assertThat(invoicePdfSecondUse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invoicePdfSecondUse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(invoicePdfSecondUse.getBody()).isNotNull();
        assertThat(invoicePdfSecondUse.getBody().length).isGreaterThan(500);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("http://127.0.0.1:" + port + path, method, entity, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        var response = restTemplate.postForEntity("http://127.0.0.1:" + port + "/api/auth/login", Map.of(
                "email", email,
                "password", password
        ), Map.class);
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) response.getBody().get("data")).get("auth")).get("token"));
    }
}
