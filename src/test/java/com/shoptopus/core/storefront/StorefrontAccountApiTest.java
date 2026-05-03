package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontAccountApiTest extends IntegrationTestBase {

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
    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Test
    void storefront_account_profile_addresses_and_orders_are_available() {
        User user = new User();
        user.setFirstName("Store");
        user.setLastName("Customer");
        user.setName("Store Customer");
        user.setEmail("storefront-account-" + UUID.randomUUID() + "@example.com");
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

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setSlug("order-" + UUID.randomUUID());
        order.setOriginalPrice(new BigDecimal("20.00"));
        order.setSubtotal(new BigDecimal("20.00"));
        order.setTotalPrice(new BigDecimal("20.00"));
        order.setStatus(1);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setName("Test item");
        item.setAmount(1);
        item.setOriginalUnitPrice(new BigDecimal("20.00"));
        item.setUnitPrice(new BigDecimal("20.00"));
        item.setFullPrice(new BigDecimal("20.00"));
        item.setFinalPrice(new BigDecimal("20.00"));
        item.setSlug("order-item-" + UUID.randomUUID());
        order.getItems().add(item);
        orderRepository.save(order);

        String token = login(user.getEmail(), "password123");

        var profile = exchange(HttpMethod.GET, "/api/storefront/account/profile", null, token);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(profile)).containsKeys("id", "first_name", "last_name", "email");

        var addresses = exchange(HttpMethod.GET, "/api/storefront/account/addresses", null, token);
        assertThat(addresses.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(addresses)).hasSize(1);

        var orders = exchange(HttpMethod.GET, "/api/storefront/account/orders", null, token);
        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(orders)).hasSize(1);

        var orderDetail = exchange(HttpMethod.GET, "/api/storefront/account/orders/" + order.getId(), null, token);
        assertThat(orderDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(orderDetail)).containsKeys("items", "address", "total");
    }

    @Test
    void storefront_account_can_review_order_and_product_and_product_rating_is_recalculated() {
        User user = new User();
        user.setFirstName("Review");
        user.setLastName("Customer");
        user.setName("Review Customer");
        user.setEmail("storefront-review-" + UUID.randomUUID() + "@example.com");
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

        Product product = new Product();
        product.setSlug("review-product-" + UUID.randomUUID());
        product.setName(new HashMap<>(Map.of("en", "Review product")));
        product.setShortDescription(new HashMap<>(Map.of("en", "Short")));
        product.setDescription(new HashMap<>(Map.of("en", "Description")));
        product.setCustomizationText(new HashMap<>());
        product.setHeadline(new HashMap<>());
        product.setSubtitle(new HashMap<>());
        product.setMetaTags(List.of());
        product.setPrice(new BigDecimal("25.00"));
        product.setStatus(1);
        product.setVirtual(false);
        product.setStock(5L);
        product.setRating(0d);
        productRepository.save(product);

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setSlug("order-" + UUID.randomUUID());
        order.setOriginalPrice(new BigDecimal("25.00"));
        order.setSubtotal(new BigDecimal("25.00"));
        order.setTotalPrice(new BigDecimal("25.00"));
        order.setStatus(1);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setName("Review product");
        item.setAmount(1);
        item.setOriginalUnitPrice(new BigDecimal("25.00"));
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setFullPrice(new BigDecimal("25.00"));
        item.setFinalPrice(new BigDecimal("25.00"));
        item.setSlug("order-item-" + UUID.randomUUID());
        order.getItems().add(item);
        orderRepository.save(order);

        String token = login(user.getEmail(), "password123");

        var orderReview = exchange(HttpMethod.POST, "/api/storefront/account/orders/" + order.getId() + "/ratings/order", Map.of(
                "rating", 4,
                "content", "Solid order"
        ), token);
        assertThat(orderReview.getStatusCode()).isEqualTo(HttpStatus.OK);

        var productReview = exchange(HttpMethod.POST, "/api/storefront/account/orders/" + order.getId() + "/ratings/products/" + product.getId(), Map.of(
                "rating", 5,
                "content", "Excellent product"
        ), token);
        assertThat(productReview.getStatusCode()).isEqualTo(HttpStatus.OK);

        Double recalculated = productRepository.findById(product.getId()).orElseThrow().getRating();
        assertThat(recalculated).isEqualTo(5.0d);

        var ratings = exchange(HttpMethod.GET, "/api/storefront/ratings?ratable_type=product&ratable_id=" + product.getId(), null, null);
        assertThat(ratings.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> ratingsData = dataObject(ratings);
        assertThat((Map<String, Object>) ratingsData.get("summary")).containsEntry("average_rating", 5.0d);
        assertThat((List<?>) ratingsData.get("items")).hasSize(1);

        var orderDetail = exchange(HttpMethod.GET, "/api/storefront/account/orders/" + order.getId(), null, token);
        assertThat(orderDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detail = dataObject(orderDetail);
        assertThat(detail.get("order_review")).isNotNull();
        assertThat(((List<?>) detail.get("items"))).hasSize(1);
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
