package com.shoptopus.core;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPostmanPayloadParityTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductCategoryRepository categoryRepository;
    @Autowired
    private ProductTagRepository tagRepository;
    @Autowired
    private ProductAttributeRepository attributeRepository;
    @Autowired
    private ProductAttributeOptionRepository optionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BannerRepository bannerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postman_update_alias_routes_accept_post_and_mutate() {
        String token = token();

        // Product POST update alias
        ResponseEntity<Map> productCreate = exchange(HttpMethod.POST, "/api/admin/product", token, Map.of(
                "name", Map.of("en", "Original Product"),
                "slug", "postman-product-" + UUID.randomUUID(),
                "short_description", Map.of("en", "Short"),
                "description", Map.of("en", "Long"),
                "price", 10.00,
                "stock", 5
        ));
        UUID productId = UUID.fromString(((Map<?, ?>) productCreate.getBody().get("data")).get("id").toString());
        exchange(HttpMethod.POST, "/api/admin/product/" + productId, token, Map.of(
                "name", Map.of("en", "Updated Product", "de", "Aktualisiert Produkt"),
                "stock", 42,
                "price", 12.33
        ));
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(42L);
        assertThat(product.getPrice()).isEqualByComparingTo("12.33");
        assertThat(product.getName().get("de")).isEqualTo("Aktualisiert Produkt");

        // Category POST update alias
        String categorySlug = "postman-category-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-category", token, Map.of(
                "name", Map.of("en", "Category"),
                "description", Map.of("en", "Category desc"),
                "slug", categorySlug
        ));
        ProductCategory category = categoryRepository.findAll().stream()
                .filter(c -> categorySlug.equals(c.getSlug()))
                .findFirst()
                .orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/product-category/" + category.getId(), token, Map.of("enabled", false));
        category = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(category.isEnabled()).isFalse();

        // Tag POST update alias
        String tagSlug = "postman-tag-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-tag", token, Map.of(
                "name", Map.of("en", "Tag"),
                "description", Map.of("en", "Tag desc"),
                "slug", tagSlug
        ));
        ProductTag tag = tagRepository.findAll().stream().filter(t -> tagSlug.equals(t.getSlug())).findFirst().orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/product-tag/" + tag.getId(), token, Map.of(
                "name", Map.of("en", "Updated Tag"),
                "enabled", false
        ));
        tag = tagRepository.findById(tag.getId()).orElseThrow();
        assertThat(tag.isEnabled()).isFalse();
        assertThat(tag.getName().get("en")).isEqualTo("Updated Tag");

        // Attribute + option POST update aliases
        String attributeSlug = "postman-attribute-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-attribute", token, Map.of(
                "name", Map.of("en", "Colour"),
                "slug", attributeSlug,
                "type", 0
        ));
        ProductAttribute attribute = attributeRepository.findAll().stream()
                .filter(a -> attributeSlug.equals(a.getSlug()))
                .findFirst()
                .orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/product-attribute/" + attribute.getId(), token, Map.of(
                "name", Map.of("en", "Color"),
                "enabled", false
        ));
        attribute = attributeRepository.findById(attribute.getId()).orElseThrow();
        assertThat(attribute.isEnabled()).isFalse();
        assertThat(attribute.getName().get("en")).isEqualTo("Color");

        String optionSlug = "postman-option-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-attribute/" + attribute.getId() + "/option", token, Map.of(
                "name", Map.of("en", "Red"),
                "slug", optionSlug,
                "value", "red"
        ));
        ProductAttributeOption option = optionRepository.findAll().stream()
                .filter(o -> optionSlug.equals(o.getSlug()))
                .findFirst()
                .orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/product-attribute/" + attribute.getId() + "/option/" + option.getId(), token, Map.of(
                "name", Map.of("en", "Blue"),
                "value", "blue"
        ));
        option = optionRepository.findById(option.getId()).orElseThrow();
        assertThat(option.getValue()).isEqualTo("blue");
        assertThat(option.getName().get("en")).isEqualTo("Blue");

        // User POST update alias on an existing user
        String managedEmail = "postman-user-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Postman",
                "last_name", "User",
                "email", managedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        var managedUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(managedEmail).orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/user/" + managedUser.getId(), token, Map.of(
                "first_name", "Updated",
                "last_name", "Person",
                "prefix", "Ms"
        ));
        managedUser = userRepository.findById(managedUser.getId()).orElseThrow();
        assertThat(managedUser.getFirstName()).isEqualTo("Updated");

        // Banner POST update alias
        String bannerSlug = "postman-banner-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/banner", token, Map.of(
                "name", Map.of("en", "Hero"),
                "slug", bannerSlug
        ));
        Banner banner = bannerRepository.findAll().stream().filter(b -> bannerSlug.equals(b.getSlug())).findFirst().orElseThrow();
        exchange(HttpMethod.POST, "/api/admin/banner/" + banner.getId(), token, Map.of("enabled", false));
        banner = bannerRepository.findById(banner.getId()).orElseThrow();
        assertThat(banner.isEnabled()).isFalse();

        // Order POST update alias
        var baseUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(rootUserUsername).orElseThrow();
        Address address = new Address();
        address.setAddressLine1("1 Postman Road");
        address.setTown("London");
        address.setPostCode("E1 1AA");
        address.setCountry("UK");
        address.setSlug("postman-address-" + UUID.randomUUID());
        address.setUser(baseUser);
        addressRepository.save(address);
        Order order = new Order();
        order.setUser(baseUser);
        order.setAddress(address);
        order.setSlug("postman-order-" + UUID.randomUUID());
        order.setOriginalPrice(BigDecimal.TEN);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotalPrice(BigDecimal.TEN);
        orderRepository.save(order);
        exchange(HttpMethod.POST, "/api/admin/order/" + order.getId(), token, Map.of("status", 4));
        order = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(4);

        // Payment POST update alias
        Payment payment = new Payment();
        payment.setPayableType("order");
        payment.setPayableId(order.getId().toString());
        payment.setAmount(new BigDecimal("25.00"));
        payment.setSlug("postman-payment-" + UUID.randomUUID());
        payment.setStatus(1);
        payment.setType(0);
        paymentRepository.save(payment);
        exchange(HttpMethod.POST, "/api/admin/payment/" + payment.getId(), token, Map.of(
                "status", 2,
                "amount", "30.55"
        ));
        payment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(2);
        assertThat(payment.getAmount()).isEqualByComparingTo("30.55");

        // File route aliases from admin postman collection
        String fakeId = UUID.randomUUID().toString();
        exchange(HttpMethod.POST, "/api/admin/file/" + fakeId, token, Map.of("name", "renamed.txt"));
        exchange(HttpMethod.POST, "/api/admin/file/" + fakeId + "/download", token, null);
        exchange(HttpMethod.DELETE, "/api/admin/files/" + fakeId, token, null);
        exchange(HttpMethod.POST, "/api/admin/note/" + UUID.randomUUID(), token, Map.of("content", "updated"));
    }

    @Test
    void laravel_style_json_string_translation_payloads_are_parsed() {
        String token = token();

        ResponseEntity<Map> response = exchange(HttpMethod.POST, "/api/admin/product", token, Map.of(
                "name", "{\"en\":\"Test Product\",\"de\":\"Test Produkt\"}",
                "slug", "json-translations-" + UUID.randomUUID(),
                "short_description", "{\"en\":\"Short Description\",\"de\":\"Kurz Bezeichnung\"}",
                "description", "{\"en\":\"Long Description\",\"de\":\"Lange Beschreibung\"}",
                "price", 12.50,
                "stock", 15
        ));
        UUID productId = UUID.fromString(((Map<?, ?>) response.getBody().get("data")).get("id").toString());
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName().get("en")).isEqualTo("Test Product");
        assertThat(product.getName().get("de")).isEqualTo("Test Produkt");
        assertThat(product.getShortDescription().get("de")).isEqualTo("Kurz Bezeichnung");
        assertThat(product.getDescription().get("de")).isEqualTo("Lange Beschreibung");
    }

    private String token() {
        String email = "admin-postman-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Admin",
                "last_name", "Postman",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "super_admin");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private void assignRole(String email, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?::uuid) on conflict do nothing", roleId, userId);
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> res = restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as(method + " " + path + " -> " + res.getStatusCode() + " body=" + res.getBody())
                .isTrue();
        return res;
    }
}
