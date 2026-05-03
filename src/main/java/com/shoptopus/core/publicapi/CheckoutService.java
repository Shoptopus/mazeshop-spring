package com.shoptopus.core.publicapi;

import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    public CheckoutService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            OrderRepository orderRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> checkAvailabilities(UUID cartId) {
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart not found."));
        var items = cartItemRepository.findByCartId(cart.getId());
        return Map.of("cart_id", cartId, "available", !items.isEmpty());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> createPendingOrder(Map<String, Object> request) {
        UUID cartId = uuid(request.get("cart_id"));
        if (cartId == null) {
            cartId = uuid(request.get("cartId"));
        }
        if (cartId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart id is required.");
        }
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart not found."));
        var items = cartItemRepository.findByCartId(cartId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout failed", "Cannot checkout an empty cart.");
        }
        Store orderStore = resolveOrderStore(items);

        User user = cart.getUser();
        if (user == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userPayload = request.get("user") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            String email = str(userPayload.get("email"));
            if (email == null || email.isBlank()) {
                userPayload = new java.util.HashMap<>(userPayload);
                email = "guest-" + UUID.randomUUID() + "@example.com";
                userPayload.put("email", email);
            }
            final String resolvedEmail = email;
            final Map<String, Object> finalUserPayload = userPayload;
            user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(resolvedEmail)
                    .orElseGet(() -> createTemporaryUser(finalUserPayload));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> addressPayload = request.get("address") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Address address = createAddress(user, addressPayload);

        BigDecimal subtotal = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setSlug("order-" + UUID.randomUUID());
        order.setOriginalPrice(subtotal);
        order.setSubtotal(subtotal);
        order.setTotalPrice(subtotal);
        order.setStatus(0);
        order.setStore(orderStore);
        orderRepository.save(order);

        return Map.of("order_id", order.getId(), "status", "pending");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> revertOrder(Map<String, Object> request) {
        UUID orderId = uuid(request.get("order_id"));
        if (orderId == null) {
            orderId = uuid(request.get("orderId"));
        }
        if (orderId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order id is required.");
        }
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order not found."));
        if (order.getStatus() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order is not pending.");
        }
        jdbcTemplate.update("""
                update orders
                set status = 6,
                    updated_at = now()
                where id = ?::uuid
                """, orderId.toString());
        return Map.of("reverted", true, "order_id", orderId);
    }

    private User createTemporaryUser(Map<String, Object> payload) {
        String firstName = str(payload.get("first_name"));
        String lastName = str(payload.get("last_name"));
        String email = str(payload.get("email"));

        User user = new User();
        user.setFirstName(firstName == null || firstName.isBlank() ? "Guest" : firstName);
        user.setLastName(lastName == null || lastName.isBlank() ? "User" : lastName);
        user.setName(user.getFirstName() + " " + user.getLastName());
        user.setEmail(email.toLowerCase());
        user.setTemporary(true);
        user.setSlug("user-" + UUID.randomUUID());
        user.setEmailVerifiedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private Address createAddress(User user, Map<String, Object> payload) {
        String line1 = str(payload.get("address_line_1"));
        String town = str(payload.get("town"));
        String postCode = str(payload.get("post_code"));
        if (line1 == null || line1.isBlank()) line1 = "1 Checkout Street";
        if (town == null || town.isBlank()) town = "London";
        if (postCode == null || postCode.isBlank()) postCode = "E1 1AA";

        Address address = new Address();
        address.setUser(user);
        address.setSlug("address-" + UUID.randomUUID());
        address.setAddressLine1(line1);
        address.setTown(town);
        address.setPostCode(postCode);
        address.setCountry(str(payload.get("country")) == null ? "UK" : str(payload.get("country")));
        address.setLat(str(payload.get("lat")));
        address.setLon(str(payload.get("lon")));
        return addressRepository.save(address);
    }

    private Store resolveOrderStore(java.util.List<com.shoptopus.core.cart.CartItem> items) {
        Set<UUID> storeIds = new LinkedHashSet<>();
        Store resolvedStore = null;
        for (var item : items) {
            if (item.getProduct() == null || item.getProduct().getStore() == null) {
                continue;
            }
            Store productStore = item.getProduct().getStore();
            storeIds.add(productStore.getId());
            if (resolvedStore == null) {
                resolvedStore = productStore;
            }
        }
        if (storeIds.size() > 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Checkout failed",
                    "This checkout flow only supports one store at a time."
            );
        }
        return resolvedStore;
    }

    private UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
