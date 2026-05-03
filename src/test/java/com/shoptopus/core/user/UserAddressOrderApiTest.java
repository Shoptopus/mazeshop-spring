package com.shoptopus.core.user;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAddressOrderApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void addresses_support_snake_case_request_and_response_shapes() {
        String email = "address-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var created = exchange(
                HttpMethod.POST,
                "/api/addresses",
                token,
                Map.of(
                        "address_line_1", "42 Test St",
                        "town", "Cardiff",
                        "post_code", "CF10 1AA",
                        "country", "UK"
                )
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        var data = (Map<?, ?>) created.getBody().get("data");
        assertThat(data.containsKey("address_line_1")).isTrue();
        assertThat(data.containsKey("post_code")).isTrue();
        String addressId = data.get("id").toString();

        var listed = exchange(HttpMethod.GET, "/api/addresses", token, null);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) listed.getBody().get("records")).intValue()).isGreaterThanOrEqualTo(1);

        var one = exchange(HttpMethod.GET, "/api/address/" + addressId, token, null);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) one.getBody().get("data")).get("post_code")).isEqualTo("CF10 1AA");
    }

    @Test
    void favorite_toggle_updates_favorites_list() {
        String email = "fav-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var p = new Product();
        p.setName(Map.of("en", "Fav Product"));
        p.setSlug("fav-product-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.TEN);
        p.setStock(10L);
        p.setVirtual(false);
        productRepository.save(p);

        var add = exchange(HttpMethod.POST, "/api/product/" + p.getId() + "/favorite", token, Map.of());
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.OK);
        var addData = (Map<?, ?>) add.getBody().get("data");
        assertThat(addData.get("product_id")).isEqualTo(p.getId().toString());
        assertThat(addData.get("favorited")).isEqualTo(true);

        var favorites = exchange(HttpMethod.GET, "/api/user/favorites", token, null);
        assertThat(favorites.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) favorites.getBody().get("data")).hasSize(1);

        var remove = exchange(HttpMethod.POST, "/api/product/" + p.getId() + "/favorite", token, Map.of());
        assertThat(remove.getStatusCode()).isEqualTo(HttpStatus.OK);
        var removeData = (Map<?, ?>) remove.getBody().get("data");
        assertThat(removeData.get("product_id")).isEqualTo(p.getId().toString());
        assertThat(removeData.get("favorited")).isEqualTo(false);
        var favoritesAfter = exchange(HttpMethod.GET, "/api/user/favorites", token, null);
        assertThat((java.util.List<?>) favoritesAfter.getBody().get("data")).isEmpty();
    }

    @Test
    void users_cannot_access_other_users_orders() {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        var owner = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(ownerEmail).orElseThrow();

        var address = new Address();
        address.setAddressLine1("1 Owner St");
        address.setTown("London");
        address.setPostCode("E1 1AA");
        address.setCountry("UK");
        address.setSlug("addr-" + UUID.randomUUID());
        address.setUser(owner);
        addressRepository.save(address);

        var order = new Order();
        order.setUser(owner);
        order.setAddress(address);
        order.setOriginalPrice(BigDecimal.TEN);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotalPrice(BigDecimal.TEN);
        order.setSlug("order-" + UUID.randomUUID());
        orderRepository.save(order);

        var ownerRead = exchange(HttpMethod.GET, "/api/order/" + order.getId(), ownerToken, null);
        assertThat(ownerRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ownerData = (Map<?, ?>) ownerRead.getBody().get("data");
        assertThat(ownerData.containsKey("user_id")).isTrue();
        assertThat(ownerData.containsKey("address_id")).isTrue();
        assertThat(ownerData.containsKey("total_price")).isTrue();
        assertThat(ownerData.containsKey("currency_code")).isTrue();

        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        String otherToken = registerAndLogin(otherEmail);
        var denied = exchange(HttpMethod.GET, "/api/order/" + order.getId(), otherToken, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    private String registerAndLogin(String email) {
        var register = restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Test",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private org.springframework.http.ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        var entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, method, entity, Map.class);
    }
}
