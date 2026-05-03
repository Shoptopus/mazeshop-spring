package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontMessagingApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Test
    void customer_can_open_message_thread_for_own_order_and_post_message() {
        User user = new User();
        user.setFirstName("Store");
        user.setLastName("Customer");
        user.setName("Store Customer");
        user.setEmail("storefront-message-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setSlug("user-" + UUID.randomUUID());
        userRepository.save(user);

        Address address = new Address();
        address.setUser(user);
        address.setSlug("address-" + UUID.randomUUID());
        address.setName("Store Customer");
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
        orderRepository.save(order);

        String token = login(user.getEmail(), "password123");

        var ensure = exchange(HttpMethod.POST, "/api/storefront/account/orders/" + order.getId() + "/message-thread", null, token);
        assertThat(ensure.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> thread = dataObject(ensure);
        assertThat(thread).containsKey("id");
        String threadId = String.valueOf(thread.get("id"));

        var send = exchange(HttpMethod.POST, "/api/storefront/account/messages/" + threadId + "/messages", Map.of(
                "body", "<p>Hello from customer</p>"
        ), token);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(send)).containsEntry("sender_role", "customer");

        var list = exchange(HttpMethod.GET, "/api/storefront/account/messages?page=1&per_page=10", null, token);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> payload = dataObject(list);
        List<Map<String, Object>> items = castList(payload.get("items"));
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("order_id", order.getId().toString());
        assertThat(String.valueOf(items.get(0).get("last_message"))).contains("Hello from customer");

        var detail = exchange(HttpMethod.GET, "/api/storefront/account/messages/" + threadId, null, token);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detailData = dataObject(detail);
        assertThat(castList(detailData.get("messages"))).hasSize(1);
        assertThat(String.valueOf(castMap(castList(detailData.get("messages")).get(0)).get("sender_role"))).isEqualTo("customer");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
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
