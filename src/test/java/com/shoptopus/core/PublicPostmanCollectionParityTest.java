package com.shoptopus.core;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPostmanCollectionParityTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void public_postman_endpoints_are_mapped_and_reachable() {
        String email = "postman-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        assert2xx(HttpMethod.POST, "/api/public/signup", null, Map.of(
                "first_name", "Postman",
                "last_name", "Tester",
                "email", email,
                "password", password,
                "password_confirmation", password
        ));
        assert2xx(HttpMethod.POST, "/api/public/login", null, Map.of("email", email, "password", password));
        assert2xx(HttpMethod.POST, "/api/public/password-recovery", null, Map.of("email", email));
        assert2xx(HttpMethod.POST, "/api/public/password-reset", null, Map.of("email", email, "token", "x", "password", password));

        String token = authToken(email, password);
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();

        var product = new Product();
        product.setName(Map.of("en", "Postman Lamp"));
        product.setSlug("postman-lamp-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Desc"));
        product.setPrice(BigDecimal.TEN);
        product.setStock(20L);
        product.setVirtual(false);
        productRepository.save(product);

        var address = new Address();
        address.setAddressLine1("1 Test Street");
        address.setTown("Cardiff");
        address.setPostCode("CF10 1AA");
        address.setCountry("UK");
        address.setSlug("address-" + UUID.randomUUID());
        address.setUser(user);
        addressRepository.save(address);

        var order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setOriginalPrice(BigDecimal.TEN);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotalPrice(BigDecimal.TEN);
        order.setSlug("order-" + UUID.randomUUID());
        orderRepository.save(order);

        assert2xx(HttpMethod.GET, "/api/public/products", null, null);
        assert2xx(HttpMethod.GET, "/api/public/product/" + product.getId(), null, null);

        assert2xx(HttpMethod.GET, "/api/public/account/addresses", token, null);
        var createdAddress = assert2xx(HttpMethod.POST, "/api/public/account/address", token, Map.of(
                "address_line_1", "2 New St",
                "town", "Cardiff",
                "post_code", "CF10 1AB",
                "country", "UK"
        ));
        String createdAddressId = ((Map<?, ?>) createdAddress.getBody().get("data")).get("id").toString();

        HttpHeaders formHeaders = headers(token);
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("address_line_1", "2 Updated St");
        form.add("town", "Cardiff");
        form.add("post_code", "CF10 1AC");
        form.add("country", "UK");
        var updateAddress = restTemplate.exchange(
                "/api/public/account/address/" + createdAddressId,
                HttpMethod.POST,
                new HttpEntity<>(form, formHeaders),
                Map.class
        );
        assertThat(updateAddress.getStatusCode().is2xxSuccessful()).isTrue();

        assert2xx(HttpMethod.DELETE, "/api/public/account/address/" + createdAddressId, token, null);

        assert2xx(HttpMethod.GET, "/api/public/account/orders", token, null);
        assert2xx(HttpMethod.GET, "/api/public/account/order/" + order.getId(), token, null);

        UUID sourceId = UUID.randomUUID();
        assert2xx(HttpMethod.GET, "/api/public/account/payment-sources", token, null);
        assert2xx(HttpMethod.POST, "/api/public/account/payment-source/" + sourceId, token, Map.of("provider", "stripe", "last4", "4242"));
        assert2xx(HttpMethod.POST, "/api/public/account/payment-source/" + sourceId, token, Map.of("brand", "visa"));
        assert2xx(HttpMethod.DELETE, "/api/public/account/payment-source/" + sourceId, token, null);

        var userCart = assert2xx(HttpMethod.GET, "/api/public/account/cart", token, null);
        assertThat(((Map<?, ?>) userCart.getBody().get("data")).containsKey("id")).isTrue();

        var addToCart = assert2xx(HttpMethod.POST, "/api/public/account/cart/product/" + product.getId(), token, Map.of("quantity", 2));
        String cartItemId = ((Map<?, ?>) ((java.util.List<?>) ((Map<?, ?>) addToCart.getBody().get("data")).get("products")).get(0)).get("id").toString();

        assert2xx(HttpMethod.POST, "/api/public/account/cart/product/" + cartItemId, token, Map.of("quantity", 3));
        assert2xx(HttpMethod.DELETE, "/api/public/account/cart/product/" + cartItemId, token, null);

        assert2xx(HttpMethod.GET, "/api/public/account/dashboard", token, null);
        assert2xx(HttpMethod.GET, "/api/public/account/checkout", token, null);
        assert2xx(HttpMethod.POST, "/api/public/account/cart/checkout/" + order.getId(), token, Map.of("provider", "stripe", "payment_ref", "pm-test"));
    }

    private String authToken(String email, String password) {
        var login = restTemplate.postForEntity("/api/public/login", Map.of("email", email, "password", password), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private ResponseEntity<Map> assert2xx(HttpMethod method, String path, String token, Object body) {
        var res = restTemplate.exchange(path, method, new HttpEntity<>(body, headers(token)), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as(method + " " + path + " -> " + res.getStatusCode() + " body=" + res.getBody())
                .isTrue();
        return res;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
