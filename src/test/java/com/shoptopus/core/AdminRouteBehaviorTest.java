package com.shoptopus.core;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
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

class AdminRouteBehaviorTest extends IntegrationTestBase {

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
    private UserRepository userRepository;
    @Autowired
    private BannerRepository bannerRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void admin_crud_and_bulk_routes_mutate_data() {
        String token = token();

        // Products create/update/bulk archive/bulk delete
        ResponseEntity<Map> createdProduct = exchange(HttpMethod.POST, "/api/admin/product", token, Map.of(
                "name", Map.of("en", "Admin Created"),
                "slug", "admin-created-" + UUID.randomUUID(),
                "short_description", Map.of("en", "Short"),
                "description", Map.of("en", "Desc"),
                "price", 12.50,
                "stock", 15
        ));
        UUID productId = UUID.fromString(((Map<?, ?>) createdProduct.getBody().get("data")).get("id").toString());
        var product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName().get("en")).isEqualTo("Admin Created");

        exchange(HttpMethod.PATCH, "/api/admin/product/" + productId, token, Map.of("stock", 10, "status", 1));
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(10L);
        assertThat(product.getStatus()).isEqualTo(1);

        exchange(HttpMethod.POST, "/api/admin/products/bulk/archive", token, Map.of("ids", java.util.List.of(productId)));
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(2);

        exchange(HttpMethod.DELETE, "/api/admin/products/bulk", token, Map.of("ids", java.util.List.of(productId)));
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getDeletedAt()).isNotNull();

        // Categories/tags/attributes
        String categorySlug = "decor-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-category", token, Map.of(
                "name", Map.of("en", "Decor"),
                "description", Map.of("en", "Decor desc"),
                "slug", categorySlug
        ));
        UUID categoryId = categoryRepository.findAll().stream().filter(c -> categorySlug.equals(c.getSlug())).findFirst().orElseThrow().getId();
        exchange(HttpMethod.POST, "/api/admin/product-categories/bulk/availability", token, Map.of("ids", java.util.List.of(categoryId), "enabled", false));
        ProductCategory category = categoryRepository.findById(categoryId).orElseThrow();
        assertThat(category.isEnabled()).isFalse();

        String tagSlug = "featured-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-tag", token, Map.of(
                "name", Map.of("en", "Featured"),
                "description", Map.of("en", "Featured desc"),
                "slug", tagSlug
        ));
        UUID tagId = tagRepository.findAll().stream().filter(t -> tagSlug.equals(t.getSlug())).findFirst().orElseThrow().getId();
        exchange(HttpMethod.POST, "/api/admin/product-tags/bulk/availability", token, Map.of("ids", java.util.List.of(tagId), "enabled", false));
        ProductTag tag = tagRepository.findById(tagId).orElseThrow();
        assertThat(tag.isEnabled()).isFalse();

        String attributeSlug = "color-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/product-attribute", token, Map.of(
                "name", Map.of("en", "Color"),
                "slug", attributeSlug,
                "type", 0
        ));
        UUID attributeId = attributeRepository.findAll().stream().filter(a -> attributeSlug.equals(a.getSlug())).findFirst().orElseThrow().getId();
        exchange(HttpMethod.POST, "/api/admin/product-attributes/bulk/availability", token, Map.of("ids", java.util.List.of(attributeId), "enabled", false));
        ProductAttribute attribute = attributeRepository.findById(attributeId).orElseThrow();
        assertThat(attribute.isEnabled()).isFalse();

        // Users are invite-only; existing users can still be updated/deleted
        String managedEmail = "managed-" + UUID.randomUUID() + "@example.com";
        HttpHeaders createUserHeaders = new HttpHeaders();
        createUserHeaders.setBearerAuth(token);
        var createUserRes = restTemplate.exchange("/api/admin/user", HttpMethod.POST, new HttpEntity<>(Map.of(
                "first_name", "Admin",
                "last_name", "Managed",
                "prefix", "Mr",
                "email", managedEmail
        ), createUserHeaders), Map.class);
        assertThat(createUserRes.getStatusCode().is4xxClientError()).isTrue();
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Admin",
                "last_name", "Managed",
                "email", managedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        UUID createdUserId = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(managedEmail).orElseThrow().getId();
        exchange(HttpMethod.PATCH, "/api/admin/user/" + createdUserId, token, Map.of("first_name", "Updated", "last_name", "User", "prefix", "Ms"));
        var updatedUser = userRepository.findById(createdUserId).orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
        exchange(HttpMethod.DELETE, "/api/admin/user/" + createdUserId, token, null);
        updatedUser = userRepository.findById(createdUserId).orElseThrow();
        assertThat(updatedUser.getDeletedAt()).isNotNull();

        // Banners create/bulk availability/delete
        String bannerSlug = "hero-" + UUID.randomUUID();
        exchange(HttpMethod.POST, "/api/admin/banner", token, Map.of(
                "name", Map.of("en", "Hero"),
                "slug", bannerSlug
        ));
        UUID bannerId = bannerRepository.findAll().stream().filter(b -> bannerSlug.equals(b.getSlug())).findFirst().orElseThrow().getId();
        exchange(HttpMethod.POST, "/api/admin/banner/bulk/availability", token, Map.of("ids", java.util.List.of(bannerId), "enabled", false));
        Banner banner = bannerRepository.findById(bannerId).orElseThrow();
        assertThat(banner.isEnabled()).isFalse();
        exchange(HttpMethod.DELETE, "/api/admin/banner/" + bannerId, token, null);
        banner = bannerRepository.findById(bannerId).orElseThrow();
        assertThat(banner.getDeletedAt()).isNotNull();

        // Order update/delete
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(rootUserUsername).orElseThrow();
        Address address = new Address();
        address.setAddressLine1("1 Admin St");
        address.setTown("London");
        address.setPostCode("E1 1AA");
        address.setCountry("UK");
        address.setSlug("admin-address-" + UUID.randomUUID());
        address.setUser(user);
        addressRepository.save(address);
        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setSlug("admin-order-" + UUID.randomUUID());
        order.setOriginalPrice(BigDecimal.TEN);
        order.setSubtotal(BigDecimal.TEN);
        order.setTotalPrice(BigDecimal.TEN);
        orderRepository.save(order);

        exchange(HttpMethod.PATCH, "/api/admin/order/" + order.getId(), token, Map.of("status", 3));
        order = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(3);
        exchange(HttpMethod.DELETE, "/api/admin/order/" + order.getId(), token, null);
        order = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(order.getDeletedAt()).isNotNull();
    }

    private String token() {
        String email = "admin-behavior-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Admin",
                "last_name", "Behavior",
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

    private ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var res = restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as(method + " " + path + " -> " + res.getStatusCode() + " body=" + res.getBody())
                .isTrue();
        return res;
    }

    private void assignRole(String email, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?::uuid) on conflict do nothing", roleId, userId);
    }
}
