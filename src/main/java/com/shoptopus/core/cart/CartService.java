package com.shoptopus.core.cart;

import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.cart.dto.AddCartItemRequest;
import com.shoptopus.core.cart.dto.CartItemResponse;
import com.shoptopus.core.cart.dto.CartResponse;
import com.shoptopus.core.cart.dto.UpdateCartQuantityRequest;
import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Transactional("mainTransactionManager")
    public List<CartResponse> addItem(AddCartItemRequest request, String ipAddress, Locale locale, String authenticatedEmail) {
        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid product id"));

        var authenticatedUser = resolveUser(authenticatedEmail);
        var productVariant = resolveVariant(request.productId(), request.productVariantId());
        var availableStock = productVariant.map(v -> v.getStock()).orElse(product.getStock());
        validateStock(availableStock, request.quantity());

        Cart cart;
        if (request.cartId() == null) {
            cart = authenticatedUser
                    .flatMap(user -> cartRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()))
                    .orElseGet(() -> {
                        Cart created = new Cart();
                        created.setIpAddress(ipAddress);
                        created.setSlug(UUID.randomUUID().toString());
                        authenticatedUser.ifPresent(created::setUser);
                        return cartRepository.save(created);
                    });
        } else {
            cart = cartRepository.findById(request.cartId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid cart id"));
            validateCartOwnership(cart, authenticatedUser.orElse(null));
        }

        var customizationValue = normalizeCustomizationValue(request.customizationValue());
        var existing = cartItemRepository.findLineItem(cart.getId(), request.productId(), request.productVariantId(), customizationValue).orElse(null);
        long existingQuantity = existing == null ? 0L : existing.getQuantity();
        long targetQuantity = existingQuantity + request.quantity();
        validateStock(availableStock, targetQuantity);
        if (existing == null) {
            existing = new CartItem();
            existing.setCart(cart);
            existing.setProduct(product);
            existing.setProductVariant(productVariant.orElse(null));
            existing.setCustomizationValue(customizationValue);
            existing.setQuantity(request.quantity());
            cart.getItems().add(existing);
            cartItemRepository.save(existing);
        } else {
            jdbcTemplate.update("""
                    update cart_product
                    set quantity = ?,
                        customization_value = ?,
                        updated_at = now()
                    where id = ?::uuid
                    """, targetQuantity, customizationValue, existing.getId().toString());
            entityManager.clear();
        }
        touchLastSeen(authenticatedUser.orElse(null));
        return List.of(toCartResponse(cartRepository.findById(cart.getId()).orElse(cart), locale));
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID cartId, Locale locale) {
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        return toCartResponse(cart, locale);
    }

    @Transactional("mainTransactionManager")
    public List<CartResponse> updateQuantity(UUID cartId, UpdateCartQuantityRequest request, Locale locale, String authenticatedEmail) {
        var authenticatedUser = resolveUser(authenticatedEmail);
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        validateCartOwnership(cart, authenticatedUser.orElse(null));

        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid product id"));
        var productVariant = resolveVariant(request.productId(), request.productVariantId());
        var availableStock = productVariant.map(v -> v.getStock()).orElse(product.getStock());
        validateStock(availableStock, request.quantity());

        var item = cartItemRepository.findLineItem(cartId, request.productId(), request.productVariantId(), null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item not found", "Cart item not found"));
        jdbcTemplate.update("""
                update cart_product
                set quantity = ?,
                    product_variant_id = ?::uuid,
                    updated_at = now()
                where id = ?::uuid
                """,
                request.quantity(),
                request.productVariantId() == null ? null : request.productVariantId().toString(),
                item.getId().toString());
        entityManager.clear();
        touchLastSeen(authenticatedUser.orElse(null));
        return List.of(toCartResponse(cartRepository.findById(cartId).orElse(cart), locale));
    }

    @Transactional("mainTransactionManager")
    public void removeItem(UUID cartId, UUID productId, UUID productVariantId, String authenticatedEmail) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid product id"));
        var authenticatedUser = resolveUser(authenticatedEmail);
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        validateCartOwnership(cart, authenticatedUser.orElse(null));
        jdbcTemplate.update("""
                delete from cart_product
                where cart_id = ?::uuid
                  and product_id = ?::uuid
                  and ((?::uuid is null and product_variant_id is null) or product_variant_id = ?::uuid)
                """,
                cartId.toString(),
                productId.toString(),
                productVariantId == null ? null : productVariantId.toString(),
                productVariantId == null ? null : productVariantId.toString());
        entityManager.clear();
        touchLastSeen(authenticatedUser.orElse(null));
    }

    @Transactional("mainTransactionManager")
    public void clear(UUID cartId, String authenticatedEmail) {
        var authenticatedUser = resolveUser(authenticatedEmail);
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        validateCartOwnership(cart, authenticatedUser.orElse(null));
        jdbcTemplate.update("delete from cart_product where cart_id = ?::uuid", cartId.toString());
        entityManager.clear();
        touchLastSeen(authenticatedUser.orElse(null));
    }

    @Transactional("mainTransactionManager")
    public CartResponse updateCart(UUID cartId, String ipAddress, Locale locale, String authenticatedEmail) {
        var authenticatedUser = resolveUser(authenticatedEmail);
        var cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        if (authenticatedUser.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        if (cart.getUser() != null && !cart.getUser().getId().equals(authenticatedUser.get().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Sorry there was a system error, the administrator has been informed.",
                    "This action is unauthorized.",
                    1403
            );
        }
        cart.setIpAddress(ipAddress);
        cartRepository.save(cart);
        touchLastSeen(authenticatedUser.get());
        return toCartResponse(cart, locale);
    }

    @Transactional("mainTransactionManager")
    public CartResponse getOrCreateUserCart(String email, String ipAddress, Locale locale) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        var cart = cartRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(user);
            created.setIpAddress(ipAddress);
            created.setSlug(UUID.randomUUID().toString());
            return cartRepository.save(created);
        });
        if (cart.getIpAddress() == null || cart.getIpAddress().isBlank()) {
            cart.setIpAddress(ipAddress);
            cartRepository.save(cart);
        }
        return toCartResponse(cart, locale);
    }

    @Transactional("mainTransactionManager")
    public CartResponse addItemToUserCart(String email, UUID productId, Long quantity, String ipAddress, Locale locale) {
        var cart = getOrCreateUserCart(email, ipAddress, locale);
        var updated = addItem(new AddCartItemRequest(cart.id(), productId, null, null, quantity), ipAddress, locale, email);
        return updated.get(0);
    }

    @Transactional("mainTransactionManager")
    public CartResponse updateCartItemById(String email, UUID cartItemId, Long quantity, Locale locale) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        var item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item not found", "Cart item not found"));
        if (item.getCart().getUser() == null || !item.getCart().getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Cart item does not belong to user");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toCartResponse(item.getCart(), locale);
    }

    @Transactional("mainTransactionManager")
    public void deleteCartItemById(String email, UUID cartItemId) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        var item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item not found", "Cart item not found"));
        if (item.getCart().getUser() == null || !item.getCart().getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Cart item does not belong to user");
        }
        cartItemRepository.delete(item);
    }

    private CartResponse toCartResponse(Cart cart, Locale locale) {
        var items = cartItemRepository.findByCartId(cart.getId()).stream().map(item -> {
            var product = item.getProduct();
            var unit = product.getPrice();
            return new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    item.getProductVariant() == null ? null : item.getProductVariant().getId(),
                    TranslatedText.resolve(product.getName(), locale),
                    unit,
                    unit,
                    unit.multiply(java.math.BigDecimal.valueOf(item.getQuantity())),
                    unit.multiply(java.math.BigDecimal.valueOf(item.getQuantity())),
                    item.getCustomizationValue(),
                    item.getQuantity(),
                    Math.max(0, product.getStock() - item.getQuantity()),
                    0L
            );
        }).toList();

        return new CartResponse(
                cart.getId(),
                cart.getUser() == null ? null : cart.getUser().getId(),
                cart.getIpAddress(),
                null,
                items
        );
    }

    private Optional<com.shoptopus.core.catalog.ProductVariant> resolveVariant(UUID productId, UUID productVariantId) {
        if (productVariantId == null) {
            return Optional.empty();
        }
        var variant = productVariantRepository.findById(productVariantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid product variant id"));
        if (variant.getDeletedAt() != null || !variant.isEnabled() || !variant.getProduct().getId().equals(productId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid product variant id");
        }
        return Optional.of(variant);
    }

    private String normalizeCustomizationValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateStock(Long availableStock, Long requestedQuantity) {
        if (availableStock == null || availableStock <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Out of stock.");
        }
        if (requestedQuantity > availableStock) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Only " + availableStock + " left.");
        }
    }

    private Optional<com.shoptopus.core.user.User> resolveUser(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(authenticatedEmail);
    }

    private void validateCartOwnership(Cart cart, com.shoptopus.core.user.User authenticatedUser) {
        if (authenticatedUser == null) {
            return;
        }
        if (cart.getUser() == null || !cart.getUser().getId().equals(authenticatedUser.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Sorry there was a system error, the administrator has been informed.",
                    "This action is unauthorized.",
                    1403
            );
        }
    }

    private void touchLastSeen(com.shoptopus.core.user.User authenticatedUser) {
        if (authenticatedUser == null) {
            return;
        }
        authenticatedUser.setLastSeen(java.time.OffsetDateTime.now());
        userRepository.save(authenticatedUser);
    }
}
