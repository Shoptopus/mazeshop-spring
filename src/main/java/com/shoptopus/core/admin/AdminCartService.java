package com.shoptopus.core.admin;

import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminCartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public AdminCartService(CartRepository cartRepository, UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = cartRepository.findAll().stream()
                .map(this::toRow)
                .toList();
        return listResponse("carts", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        UUID cartId = uuid(id).orElse(null);
        if (cartId == null) {
            return Map.of("id", id);
        }
        return cartRepository.findById(cartId)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", cartId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setSlug(str(body, "slug", "cart-" + cart.getId()));
        cart.setIpAddress(str(body, "ip_address", null));
        cart.setUser(resolveUser(body));
        cartRepository.save(cart);
        return Map.of("id", cart.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID cartId = uuid(id).orElse(null);
        if (cartId == null) {
            return Map.of("id", id);
        }
        Optional<Cart> cart = cartRepository.findById(cartId);
        if (cart.isEmpty()) {
            return Map.of("id", cartId);
        }
        Cart entity = cart.orElseThrow();
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("ip_address")) {
            entity.setIpAddress(str(body, "ip_address", entity.getIpAddress()));
        }
        if (body != null && body.containsKey("user_id")) {
            entity.setUser(resolveUser(body));
        }
        cartRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID cartId = uuid(id).orElse(null);
        if (cartId == null) {
            return Map.of("id", id);
        }
        cartRepository.findById(cartId).ifPresent(cartRepository::delete);
        return Map.of("id", cartId);
    }

    private User resolveUser(Map<String, Object> body) {
        UUID userId = uuid(str(body, "user_id", null)).orElse(null);
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected user_id is invalid."));
    }

    private Map<String, Object> toDetail(Cart cart) {
        Map<String, Object> row = toRow(cart);
        row.put("items", cart.getItems().stream().map(item -> {
            Map<String, Object> itemRow = new LinkedHashMap<>();
            itemRow.put("id", item.getId());
            itemRow.put("product_id", item.getProduct() == null ? null : item.getProduct().getId());
            itemRow.put("product_variant_id", item.getProductVariant() == null ? null : item.getProductVariant().getId());
            itemRow.put("customization_value", item.getCustomizationValue());
            itemRow.put("quantity", item.getQuantity());
            return itemRow;
        }).toList());
        return row;
    }

    private Map<String, Object> toRow(Cart cart) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", cart.getId());
        row.put("slug", cart.getSlug());
        row.put("ip_address", cart.getIpAddress());
        row.put("items_count", cart.getItems().size());
        row.put("user_id", cart.getUser() == null ? null : cart.getUser().getId());
        if (cart.getUser() == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", cart.getUser().getId());
            userRow.put("email", cart.getUser().getEmail());
            userRow.put("first_name", cart.getUser().getFirstName());
            userRow.put("last_name", cart.getUser().getLastName());
            row.put("user", userRow);
        }
        return row;
    }

    private Map<String, Object> listResponse(String message, List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
