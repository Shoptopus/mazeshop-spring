package com.shoptopus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.catalog.*;
import com.shoptopus.core.catalog.repository.*;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.Notification;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.engagement.repository.NotificationRepository;
import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicLaravelRouteParityTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductCategoryRepository productCategoryRepository;
    @Autowired
    private ProductTagRepository productTagRepository;
    @Autowired
    private ProductAttributeRepository productAttributeRepository;
    @Autowired
    private ProductAttributeOptionRepository productAttributeOptionRepository;
    @Autowired
    private BannerRepository bannerRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;

    @LocalServerPort
    private int port;

    @Test
    void non_admin_laravel_routes_are_mapped_and_reachable() {
        String email = "parity-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        register(email, password);
        String token = login(email, password);

        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();
        var fixture = createFixtureData(user.getId());

        // Auth (api/auth/auth.php)
        assert2xx(HttpMethod.POST, "/api/auth/verify/resend", token, Map.of());
        assert2xx(HttpMethod.GET, "/api/auth/verify/123", null, null);
        assert2xx(HttpMethod.GET, "/api/auth/login/google", null, null);
        assert2xx(HttpMethod.POST, "/api/auth/login/google/callback", null, Map.of("code", "abc"));
        assert2xx(HttpMethod.POST, "/api/auth/forgot-password", null, Map.of("email", email));
        assertReachable(HttpMethod.POST, "/api/auth/reset-password", null, Map.of("email", email, "password", "password123", "token", "x"));
        assert2xx(HttpMethod.POST, "/api/auth/me", token, Map.of());
        var authDetails = assert2xx(HttpMethod.GET, "/api/auth/details", token, null);
        var authDetailsData = assertDataObject(authDetails);
        var authUser = (Map<?, ?>) ((Map<?, ?>) authDetailsData.get("auth")).get("user");
        assertThat(authUser.get("id")).isEqualTo(user.getId().toString());
        assertReachable(HttpMethod.POST, "/api/auth/register-by-invite/test-token", null, Map.of("email", "invited@example.com"));

        // Product (routes/api/product/product.php)
        var products = assert2xx(HttpMethod.GET, "/api/products", null, null);
        assertThat(idsOf(assertDataList(products))).contains(fixture.productId.toString());

        var searchProducts = assert2xx(HttpMethod.GET, "/api/products/search/Lamp", null, null);
        assertThat(idsOf(assertDataList(searchProducts))).contains(fixture.productId.toString());

        var productById = assert2xx(HttpMethod.GET, "/api/product/" + fixture.productId, null, null);
        var productByIdData = assertDataObject(productById);
        assertThat(productByIdData.get("id")).isEqualTo(fixture.productId.toString());
        assertThat(productByIdData.get("slug")).isEqualTo(fixture.productSlug);

        var productBySlug = assert2xx(HttpMethod.GET, "/api/product/slug/" + fixture.productSlug, null, null);
        var productBySlugData = assertDataObject(productBySlug);
        assertThat(productBySlugData.get("id")).isEqualTo(fixture.productId.toString());

        var availableAttributeOptions = assert2xx(HttpMethod.GET, "/api/product/" + fixture.productId + "/available-attribute-options", null, null);
        var availableAttributeOptionsData = assertDataObject(availableAttributeOptions);
        assertThat(availableAttributeOptionsData.get("product_id")).isEqualTo(fixture.productId.toString());

        assert2xx(HttpMethod.POST, "/api/product/" + fixture.productId + "/favorite", token, Map.of());
        var ratingRes = assert2xx(HttpMethod.POST, "/api/product/" + fixture.productId + "/rating", token, Map.of(
                "rating", 5,
                "title", "Great",
                "description", "Excellent product",
                "languagePrefix", "en"
        ));
        String ratingId = String.valueOf(assertDataObject(ratingRes).get("id"));

        // Product category/tag/attribute/rating/banner/public meta routes
        var categories = assert2xx(HttpMethod.GET, "/api/product_categories", null, null);
        assertThat(idsOf(assertDataList(categories))).contains(fixture.categoryId.toString());

        var categoryById = assert2xx(HttpMethod.GET, "/api/product_category/" + fixture.categoryId, null, null);
        assertThat(assertDataObject(categoryById).get("id")).isEqualTo(fixture.categoryId.toString());

        var categoryBySlug = assert2xx(HttpMethod.GET, "/api/product_category/slug/" + fixture.categorySlug, null, null);
        assertThat(assertDataObject(categoryBySlug).get("id")).isEqualTo(fixture.categoryId.toString());

        var tags = assert2xx(HttpMethod.GET, "/api/product_tags", null, null);
        assertThat(idsOf(assertDataList(tags))).contains(fixture.tagId.toString());

        var tagById = assert2xx(HttpMethod.GET, "/api/product_tag/" + fixture.tagId, null, null);
        assertThat(assertDataObject(tagById).get("id")).isEqualTo(fixture.tagId.toString());

        var attributes = assert2xx(HttpMethod.GET, "/api/product_attributes", null, null);
        assertThat(idsOf(assertDataList(attributes))).contains(fixture.attributeId.toString());

        var attributesByCategory = assert2xx(HttpMethod.GET, "/api/product_attributes/product_category/" + fixture.categoryId, null, null);
        assertThat(idsOf(assertDataList(attributesByCategory))).contains(fixture.attributeId.toString());

        var attributeById = assert2xx(HttpMethod.GET, "/api/product_attribute/" + fixture.attributeId, null, null);
        assertThat(assertDataObject(attributeById).get("id")).isEqualTo(fixture.attributeId.toString());

        var ratings = assert2xx(HttpMethod.GET, "/api/ratings/" + fixture.productId, null, null);
        assertThat(idsOf(assertDataList(ratings))).contains(ratingId);

        var banners = assert2xx(HttpMethod.GET, "/api/banners", null, null);
        assertThat(idsOf(assertDataList(banners))).contains(fixture.bannerId.toString());

        assert2xx(HttpMethod.POST, "/api/banners/" + fixture.bannerId + "/click", null, Map.of());
        var home = assert2xx(HttpMethod.GET, "/api/home", null, null);
        assertThat(assertDataObject(home)).containsKeys("featured_products", "featured_categories");

        var meta = assert2xx(HttpMethod.GET, "/api/meta", null, null);
        assertThat(assertDataObject(meta)).containsKeys("currency", "locale", "version");

        var sharedOptions = assert2xx(HttpMethod.GET, "/api/shared-options", null, null);
        assertThat(assertDataObject(sharedOptions)).containsKeys("countries", "languages");

        var paidFileDownload = assert2xx(HttpMethod.GET, "/api/download-paid-file/" + UUID.randomUUID(), null, null);
        assertThat(assertDataObject(paidFileDownload)).containsKeys("paid_file_content_id", "download_url");

        // Checkout (routes/api/checkout/checkout.php)
        assert2xx(HttpMethod.POST, "/api/checkout/get-available-delivery-types", null, Map.of("cartId", fixture.cartSeedId));
        assert2xx(HttpMethod.GET, "/api/checkout/check-availabilities/" + fixture.cartSeedId, null, null);
        assert2xx(HttpMethod.POST, "/api/checkout/create-pending-order", null, Map.of("cartId", fixture.cartSeedId));
        assert2xx(HttpMethod.POST, "/api/checkout/revert-order", null, Map.of("orderId", fixture.orderId));
        assert2xx(HttpMethod.POST, "/api/checkout/apply-voucher-code", null, Map.of("code", "NOPE"));

        // Payment (routes/api/payment/payment.php)
        assert2xx(HttpMethod.GET, "/api/payment/stripe/settings", null, null);
        assert2xx(HttpMethod.POST, "/api/payment/execute?provider=stripe&paymentRef=pay-1", null, null);

        // Cart (routes/api/cart/cart.php)
        var addItemRes = assert2xx(HttpMethod.POST, "/api/cart/addItem", null, Map.of(
                "productId", fixture.productId,
                "quantity", 2
        ));
        var data = (List<?>) addItemRes.getBody().get("data");
        var firstCart = (Map<?, ?>) data.get(0);
        String cartId = firstCart.get("id").toString();
        var getCart = assert2xx(HttpMethod.GET, "/api/cart/" + cartId, null, null);
        assertThat(String.valueOf(assertDataObject(getCart).get("id"))).isEqualTo(cartId);
        assert2xx(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", null, Map.of(
                "productId", fixture.productId,
                "quantity", 1
        ));
        assert2xx(HttpMethod.DELETE, "/api/cart/removeItem?cartId=" + cartId + "&productId=" + fixture.productId, null, null);
        assert2xx(HttpMethod.DELETE, "/api/cart/all?cartId=" + cartId, null, null);
        assert2xx(HttpMethod.PATCH, "/api/cart/" + cartId, token, Map.of());

        // User + Address (routes/api/user/user.php + routes/api/address/address.php)
        var favorites = assert2xx(HttpMethod.GET, "/api/user/favorites", token, null);
        var favoriteIds = assertDataArray(favorites).stream().map(String::valueOf).toList();
        assertThat(favoriteIds).contains(fixture.productId.toString());

        var userAccount = assert2xx(HttpMethod.GET, "/api/user/account", token, null);
        assertThat(assertDataObject(userAccount).get("id")).isEqualTo(user.getId().toString());

        assert2xx(HttpMethod.POST, "/api/user/account", token, Map.of(
                "firstName", "Route",
                "lastName", "Parity",
                "email", email,
                "phone", "+441234"
        ));

        var createAddressRes = assert2xx(HttpMethod.POST, "/api/addresses", token, Map.of(
                "addressLine1", "42 Test St",
                "town", "Cardiff",
                "postCode", "CF10 1AA",
                "country", "UK"
        ));
        String addressId = ((Map<?, ?>) createAddressRes.getBody().get("data")).get("id").toString();
        var addresses = assert2xx(HttpMethod.GET, "/api/addresses", token, null);
        assertThat(idsOf(assertDataList(addresses))).contains(addressId);

        var address = assert2xx(HttpMethod.GET, "/api/address/" + addressId, token, null);
        assertThat(assertDataObject(address).get("id")).isEqualTo(addressId);

        assert2xx(HttpMethod.PATCH, "/api/address/" + addressId, token, Map.of(
                "addressLine1", "43 Test St",
                "town", "Cardiff",
                "postCode", "CF10 1AB",
                "country", "UK"
        ));
        assert2xx(HttpMethod.DELETE, "/api/address/" + addressId, token, null);

        // Orders (routes/api/order/order.php)
        var orders = assert2xx(HttpMethod.GET, "/api/orders", token, null);
        assertThat(idsOf(assertDataList(orders))).contains(fixture.orderId.toString());

        var order = assert2xx(HttpMethod.GET, "/api/order/" + fixture.orderId, token, null);
        assertThat(assertDataObject(order).get("id")).isEqualTo(fixture.orderId.toString());

        // Notifications (routes/api/public.php auth section)
        var notifications = assert2xx(HttpMethod.GET, "/api/notifications", token, null);
        assertThat(idsOf(assertDataList(notifications))).contains(fixture.notificationId.toString());

        var latestNotification = assert2xx(HttpMethod.GET, "/api/notifications/latest", token, null);
        assertThat(assertDataObject(latestNotification).get("id")).isEqualTo(fixture.notificationId.toString());

        assert2xx(HttpMethod.PATCH, "/api/notifications/clear", token, Map.of());
        var notification = assert2xx(HttpMethod.GET, "/api/notifications/" + fixture.notificationId, token, null);
        assertThat(assertDataObject(notification).get("id")).isEqualTo(fixture.notificationId.toString());

        // Account delete route exists; keep it near end.
        assert2xx(HttpMethod.DELETE, "/api/user/account", token, null);

        // Ensure logout route is still callable with a fresh token
        String logoutUser = "logout-" + UUID.randomUUID() + "@example.com";
        register(logoutUser, password);
        String logoutToken = login(logoutUser, password);
        assert2xx(HttpMethod.POST, "/api/auth/logout", logoutToken, Map.of());
    }

    private Fixture createFixtureData(UUID userId) {
        var user = userRepository.findById(userId).orElseThrow();

        var category = new ProductCategory();
        category.setName(Map.of("en", "Lighting"));
        category.setDescription(Map.of("en", "Lights"));
        category.setSlug("lighting-" + UUID.randomUUID().toString().substring(0, 8));
        productCategoryRepository.save(category);

        var tag = new ProductTag();
        tag.setName(Map.of("en", "New"));
        tag.setSlug("new-" + UUID.randomUUID().toString().substring(0, 8));
        tag.setDescription(Map.of("en", "Brand new"));
        productTagRepository.save(tag);

        var attribute = new ProductAttribute();
        attribute.setName(Map.of("en", "Color"));
        attribute.setSlug("color-" + UUID.randomUUID().toString().substring(0, 8));
        attribute.setType(0);
        productAttributeRepository.save(attribute);

        var option = new ProductAttributeOption();
        option.setName(Map.of("en", "Blue"));
        option.setSlug("blue-" + UUID.randomUUID().toString().substring(0, 8));
        option.setValue("blue");
        option.setProductAttribute(attribute);
        productAttributeOptionRepository.save(option);

        var product = new Product();
        product.setName(Map.of("en", "Maze Lamp"));
        product.setSlug("maze-lamp-" + UUID.randomUUID().toString().substring(0, 8));
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Description"));
        product.setPrice(BigDecimal.valueOf(49.99));
        product.setStock(10L);
        product.setVirtual(false);
        product.setCategories(Set.of(category));
        product.setTags(Set.of(tag));
        productRepository.save(product);

        var banner = new Banner();
        banner.setName(Map.of("en", "Hero"));
        banner.setSlug("hero-" + UUID.randomUUID().toString().substring(0, 8));
        bannerRepository.save(banner);

        var notification = new Notification();
        notification.setUser(user);
        notification.setType("info");
        notification.setTitle("Welcome");
        notification.setData(Map.of("x", 1));
        notificationRepository.save(notification);

        var address = new Address();
        address.setAddressLine1("1 Demo St");
        address.setTown("London");
        address.setPostCode("E1 1AA");
        address.setSlug("address-" + UUID.randomUUID().toString().substring(0, 8));
        address.setUser(user);
        addressRepository.save(address);

        var order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setSlug("order-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOriginalPrice(BigDecimal.valueOf(49.99));
        order.setSubtotal(BigDecimal.valueOf(49.99));
        order.setTotalPrice(BigDecimal.valueOf(49.99));
        orderRepository.save(order);

        var cart = new Cart();
        cart.setUser(user);
        cart.setSlug("cart-" + UUID.randomUUID().toString().substring(0, 8));
        cartRepository.save(cart);

        var cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(1L);
        cartItemRepository.save(cartItem);

        return new Fixture(
                product.getId(),
                product.getSlug(),
                category.getId(),
                category.getSlug(),
                tag.getId(),
                attribute.getId(),
                banner.getId(),
                notification.getId(),
                cart.getId(),
                order.getId()
        );
    }

    private void register(String email, String password) {
        var res = restTemplate.postForEntity("/api/auth/register", Map.of(
                "firstName", "Route",
                "lastName", "Parity",
                "email", email,
                "password", password
        ), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private String login(String email, String password) {
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", password
        ), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private ResponseEntity<Map> assert2xx(HttpMethod method, String path, String bearerToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        var res = exchange(method, path, headers, body);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as(method + " " + path + " -> " + res.getStatusCode() + " body=" + res.getBody())
                .isTrue();
        return res;
    }

    private ResponseEntity<Map> assertReachable(HttpMethod method, String path, String bearerToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        var res = exchange(method, path, headers, body);
        assertThat(res.getStatusCode().is2xxSuccessful() || res.getStatusCode().is4xxClientError())
                .as(method + " " + path + " -> " + res.getStatusCode() + " body=" + res.getBody())
                .isTrue();
        return res;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> assertDataObject(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("data");
        assertThat(response.getBody().get("data")).isInstanceOf(Map.class);
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assertDataList(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("data");
        assertThat(response.getBody().get("data")).isInstanceOf(List.class);
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Object> assertDataArray(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("data");
        assertThat(response.getBody().get("data")).isInstanceOf(List.class);
        return (List<Object>) response.getBody().get("data");
    }

    private List<String> idsOf(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> String.valueOf(row.get("id")))
                .toList();
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        try {
            var entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(path, method, entity, Map.class);
        } catch (ResourceAccessException ex) {
            if (method == HttpMethod.PATCH && ex.getMessage() != null && ex.getMessage().contains("Invalid HTTP method: PATCH")) {
                return patchWithJdkClient(path, headers.getFirst(HttpHeaders.AUTHORIZATION), body);
            }
            throw ex;
        }
    }

    private ResponseEntity<Map> patchWithJdkClient(String path, String authorization, Object body) {
        try {
            String json = body == null ? "{}" : OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json");
            if (authorization != null) {
                builder.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map responseBody = response.body() == null || response.body().isBlank()
                    ? null
                    : OBJECT_MAPPER.readValue(response.body(), Map.class);
            return ResponseEntity.status(response.statusCode()).body(responseBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Fixture(
            UUID productId,
            String productSlug,
            UUID categoryId,
            String categorySlug,
            UUID tagId,
            UUID attributeId,
            UUID bannerId,
            UUID notificationId,
            UUID cartSeedId,
            UUID orderId
    ) {}
}
