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
import com.shoptopus.core.payment.PaymentProvider;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontCartCheckoutApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductCategoryRepository productCategoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;
    @Autowired
    private DeliveryRuleRepository deliveryRuleRepository;
    @Autowired
    private PaymentProviderRepository paymentProviderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Test
    void storefront_cart_and_checkout_summary_shape_are_available() {
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
        user.setEmail("storefront-checkout-" + UUID.randomUUID() + "@example.com");
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

        DeliveryRule rule = new DeliveryRule();
        rule.setDeliveryType(deliveryType);
        rule.setName(Map.of("en", "UK rule"));
        rule.setSlug("uk-rule-" + UUID.randomUUID());
        rule.setCountry("UK");
        rule.setPrice(new BigDecimal("4.99"));
        rule.setEnabled(true);
        deliveryTypeRepository.save(deliveryType);
        deliveryRuleRepository.save(rule);

        paymentProviderRepository.findByNameIgnoreCase("stripe").orElseGet(() -> {
            PaymentProvider paymentProvider = new PaymentProvider();
            paymentProvider.setName("stripe");
            paymentProvider.setEnabled(true);
            return paymentProviderRepository.save(paymentProvider);
        });

        var add = exchange(HttpMethod.POST, "/api/storefront/cart/items", Map.of(
                "product_id", product.getId(),
                "quantity", 2
        ), null);
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> cart = dataObject(add);
        assertThat(cart).containsKeys("id", "items", "totals");

        String token = login(user.getEmail(), "password123");
        var resolve = exchange(HttpMethod.POST, "/api/storefront/cart/resolve", Map.of("cart_id", cart.get("id")), token);
        assertThat(resolve.getStatusCode()).isEqualTo(HttpStatus.OK);
        String resolvedCartId = String.valueOf(dataObject(resolve).get("id"));

        var summary = exchange(HttpMethod.GET, "/api/storefront/checkout/" + resolvedCartId, null, token);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> summaryData = dataObject(summary);
        assertThat(summaryData).containsKeys("cart", "addresses", "payment_providers");

        var delivery = exchange(HttpMethod.POST, "/api/storefront/checkout/delivery-options", Map.of(
                "cart_id", resolvedCartId,
                "address_id", address.getId()
        ), token);
        assertThat(delivery.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(delivery))
                .extracting(row -> row.get("id"))
                .contains(deliveryType.getId().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataList(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("data");
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
