package com.shoptopus.core.storefront;

import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class StorefrontCartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final UserRepository userRepository;
    private final ManagedFileService managedFileService;
    private final StorefrontPricingService storefrontPricingService;
    private final StorefrontStoreResolver storefrontStoreResolver;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<StorefrontCheckoutService>
            checkoutProvider;

    public StorefrontCartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            UserRepository userRepository,
            ManagedFileService managedFileService,
            StorefrontPricingService storefrontPricingService,
            StorefrontStoreResolver storefrontStoreResolver,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.userRepository = userRepository;
        this.managedFileService = managedFileService;
        this.storefrontPricingService = storefrontPricingService;
        this.storefrontStoreResolver = storefrontStoreResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cart(UUID cartId, Locale locale, String authenticatedEmail) {
        Cart cart = requireCart(cartId);
        validateCartAccess(cart, resolveUser(authenticatedEmail).orElse(null));
        return toCartRow(cart, locale);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> addItem(
            Map<String, Object> body, String ipAddress, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body.get("cart_id"));
        UUID productId = requiredUuid(body.get("product_id"), "The product_id field is required.");
        UUID variantId = uuid(body.get("product_variant_id"));
        String customizationValue =
                normalizeCustomizationValue(str(body.get("customization_value")));
        long quantity = longVal(body.get("quantity"), 1L);
        if (quantity < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Validation failed", "Quantity must be at least 1.");
        }

        User user = resolveUser(authenticatedEmail).orElse(null);
        Product product = requireProduct(productId);
        ProductVariant variant = requireVariant(productId, variantId);
        validateCustomization(product, customizationValue);

        Cart cart = cartId == null ? createOrReuseCart(user, ipAddress) : requireCart(cartId);
        validateCartAccess(cart, user);

        CartItem existing =
                cartItemRepository
                        .findLineItem(cart.getId(), productId, variantId, customizationValue)
                        .orElse(null);
        long targetQuantity = quantity + (existing == null ? 0L : existing.getQuantity());
        validateStock(product, variant, targetQuantity);

        if (existing == null) {
            existing = new CartItem();
            existing.setCart(cart);
            existing.setProduct(product);
            existing.setProductVariant(variant);
            existing.setCustomizationValue(customizationValue);
            existing.setQuantity(quantity);
        } else {
            existing.setQuantity(targetQuantity);
            existing.setCustomizationValue(customizationValue);
        }
        cartItemRepository.save(existing);
        return toCartRow(requireCart(cart.getId()), locale);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateItem(
            UUID lineId, Map<String, Object> body, Locale locale, String authenticatedEmail) {
        CartItem item =
                cartItemRepository
                        .findById(lineId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Cart item not found",
                                                "Cart item not found"));
        User user = resolveUser(authenticatedEmail).orElse(null);
        validateCartAccess(item.getCart(), user);

        long quantity = longVal(body.get("quantity"), item.getQuantity());
        if (quantity < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Validation failed", "Quantity must be at least 1.");
        }
        validateStock(item.getProduct(), item.getProductVariant(), quantity);
        jdbcTemplate.update(
                """
                update cart_product
                set quantity = ?,
                    updated_at = now()
                where id = ?::uuid
                """,
                quantity,
                item.getId().toString());
        entityManager.clear();
        return toCartRow(requireCart(item.getCart().getId()), locale);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> removeItem(UUID lineId, Locale locale, String authenticatedEmail) {
        CartItem item =
                cartItemRepository
                        .findById(lineId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Cart item not found",
                                                "Cart item not found"));
        User user = resolveUser(authenticatedEmail).orElse(null);
        validateCartAccess(item.getCart(), user);
        UUID cartId = item.getCart().getId();
        cartItemRepository.delete(item);
        return toCartRow(requireCart(cartId), locale);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> resolve(
            Map<String, Object> body, String ipAddress, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body == null ? null : body.get("cart_id"));
        User user = resolveUser(authenticatedEmail).orElse(null);
        if (user == null) {
            if (cartId != null) {
                Cart existing = requireCart(cartId);
                validateCartAccess(existing, null);
                return toCartRow(existing, locale);
            }
            return toCartRow(createOrReuseCart(null, ipAddress), locale);
        }

        Cart userCart =
                cartRepository
                        .findTopByUserIdOrderByCreatedAtDesc(user.getId())
                        .orElseGet(
                                () -> {
                                    Cart created = new Cart();
                                    created.setUser(user);
                                    created.setIpAddress(ipAddress);
                                    created.setSlug(UUID.randomUUID().toString());
                                    return cartRepository.save(created);
                                });

        if (cartId != null && !userCart.getId().equals(cartId)) {
            Cart guestCart = requireCart(cartId);
            if (guestCart.getUser() == null) {
                checkoutProvider.getObject().cancelUnpaidCart(guestCart.getId());
                mergeInto(userCart, guestCart);
            } else {
                validateCartAccess(guestCart, user);
                mergeInto(userCart, guestCart);
            }
        }

        userCart.setIpAddress(ipAddress);
        cartRepository.save(userCart);
        return toCartRow(requireCart(userCart.getId()), locale);
    }

    @Transactional(readOnly = true)
    public List<CartItem> itemsForStore(Cart cart, Store store) {
        if (cart == null) {
            return List.of();
        }
        return cart.getItems().stream()
                .filter(
                        item ->
                                Objects.equals(
                                        item.getProduct() == null
                                                        || item.getProduct().getStore() == null
                                                ? null
                                                : item.getProduct().getStore().getId(),
                                        store == null ? null : store.getId()))
                .toList();
    }

    private void mergeInto(Cart target, Cart source) {
        for (CartItem item : List.copyOf(source.getItems())) {
            UUID productId = item.getProduct().getId();
            UUID variantId =
                    item.getProductVariant() == null ? null : item.getProductVariant().getId();
            CartItem existing =
                    cartItemRepository
                            .findLineItem(
                                    target.getId(),
                                    productId,
                                    variantId,
                                    item.getCustomizationValue())
                            .orElse(null);
            long mergedQuantity =
                    item.getQuantity() + (existing == null ? 0L : existing.getQuantity());
            validateStock(item.getProduct(), item.getProductVariant(), mergedQuantity);
            if (existing == null) {
                existing = new CartItem();
                existing.setCart(target);
                existing.setProduct(item.getProduct());
                existing.setProductVariant(item.getProductVariant());
                existing.setCustomizationValue(item.getCustomizationValue());
                existing.setQuantity(item.getQuantity());
            } else {
                existing.setQuantity(mergedQuantity);
                existing.setCustomizationValue(item.getCustomizationValue());
            }
            cartItemRepository.save(existing);
        }
        source.getItems().clear();
        cartRepository.save(source);
    }

    private Map<String, Object> toCartRow(Cart cart, Locale locale) {
        List<Map<String, Object>> items =
                cartItemRepository.findByCartId(cart.getId()).stream()
                        .map(item -> lineItem(item, locale))
                        .toList();

        BigDecimal subtotalOriginal =
                items.stream()
                        .map(item -> (BigDecimal) item.get("line_original_amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotalFinal =
                items.stream()
                        .map(item -> (BigDecimal) item.get("line_final_amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("currency", "GBP");
        totals.put("original_amount", subtotalOriginal);
        totals.put("final_amount", subtotalFinal);
        totals.put("original", money(subtotalOriginal));
        totals.put("final", money(subtotalFinal));
        totals.put("discount_amount", subtotalOriginal.subtract(subtotalFinal));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", cart.getId());
        row.put("user_id", cart.getUser() == null ? null : cart.getUser().getId());
        row.put("ip_address", cart.getIpAddress());
        row.put("slug", cart.getSlug());
        row.put("items", items.stream().map(this::sanitizeLineItem).toList());
        row.put("groups", groupRows(items));
        row.put("totals", totals);
        return row;
    }

    private List<Map<String, Object>> groupRows(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> groupedItems =
                items.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        item -> {
                                            Map<String, Object> store = map(item.get("store"));
                                            Object id = store.get("id");
                                            return id == null ? "store:none" : String.valueOf(id);
                                        },
                                        LinkedHashMap::new,
                                        java.util.stream.Collectors.toList()));

        return groupedItems.values().stream()
                .map(
                        group -> {
                            Map<String, Object> first = group.get(0);
                            BigDecimal originalAmount =
                                    group.stream()
                                            .map(item -> decimal(item.get("line_original_amount")))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal finalAmount =
                                    group.stream()
                                            .map(item -> decimal(item.get("line_final_amount")))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("store", first.get("store"));
                            row.put("items", group.stream().map(this::sanitizeLineItem).toList());
                            row.put(
                                    "item_count",
                                    group.stream()
                                            .map(item -> item.get("quantity"))
                                            .mapToLong(
                                                    value ->
                                                            value instanceof Number number
                                                                    ? number.longValue()
                                                                    : longVal(value, 0L))
                                            .sum());
                            row.put(
                                    "totals",
                                    Map.of(
                                            "currency",
                                            "GBP",
                                            "original_amount",
                                            originalAmount,
                                            "final_amount",
                                            finalAmount,
                                            "discount_amount",
                                            originalAmount.subtract(finalAmount),
                                            "original",
                                            money(originalAmount),
                                            "final",
                                            money(finalAmount)));
                            return row;
                        })
                .toList();
    }

    private Map<String, Object> lineItem(CartItem item, Locale locale) {
        Product product = item.getProduct();
        ProductVariant variant = item.getProductVariant();
        StorefrontPricingService.Pricing pricing = storefrontPricingService.pricing(product);
        BigDecimal originalUnit =
                variant != null && variant.getPrice() != null
                        ? variant.getPrice()
                        : pricing.original();
        BigDecimal finalUnit =
                pricing.discounted()
                        ? applyDiscountToVariantPrice(pricing, originalUnit)
                        : originalUnit;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("product_id", product.getId());
        row.put("product_variant_id", variant == null ? null : variant.getId());
        row.put("slug", product.getSlug());
        row.put("name", TranslatedText.resolve(product.getName(), locale));
        row.put("store", storefrontStoreResolver.storeRow(product.getStore()));
        row.put(
                "cover_image",
                managedFileService.findAttachedSingle("product", product.getId(), "cover_photo"));
        row.put("quantity", item.getQuantity());
        row.put("customization_value", item.getCustomizationValue());
        row.put(
                "variant",
                variant == null
                        ? null
                        : Map.of(
                                "id", variant.getId(),
                                "slug", variant.getSlug(),
                                "description",
                                        TranslatedText.resolve(variant.getDescription(), locale),
                                "attributes", variantAttributeSelections(variant, locale)));
        row.put(
                "pricing",
                Map.of(
                        "currency",
                        "GBP",
                        "original_amount",
                        originalUnit,
                        "final_amount",
                        finalUnit,
                        "original",
                        money(originalUnit),
                        "final",
                        money(finalUnit),
                        "is_discounted",
                        finalUnit.compareTo(originalUnit) < 0));
        row.put(
                "line_original_amount",
                originalUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
        row.put("line_final_amount", finalUnit.multiply(BigDecimal.valueOf(item.getQuantity())));
        return row;
    }

    private Map<String, Object> sanitizeLineItem(Map<String, Object> item) {
        Map<String, Object> row = new LinkedHashMap<>(item);
        row.remove("line_original_amount");
        row.remove("line_final_amount");
        return row;
    }

    private List<Map<String, Object>> variantAttributeSelections(
            ProductVariant variant, Locale locale) {
        Map<String, Object> normalized = resolvedVariantOptionMap(variant);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        normalized.forEach(
                (attributeIdValue, optionIdValue) -> {
                    UUID optionId = uuid(optionIdValue);
                    if (optionId == null) {
                        return;
                    }
                    productAttributeOptionRepository
                            .findById(optionId)
                            .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                            .ifPresent(
                                    option -> {
                                        Map<String, Object> row = new LinkedHashMap<>();
                                        row.put(
                                                "attribute_id",
                                                option.getProductAttribute() == null
                                                        ? null
                                                        : option.getProductAttribute().getId());
                                        row.put(
                                                "attribute_name",
                                                option.getProductAttribute() == null
                                                        ? null
                                                        : TranslatedText.resolve(
                                                                option.getProductAttribute()
                                                                        .getName(),
                                                                locale));
                                        row.put("option_id", option.getId());
                                        row.put(
                                                "option_name",
                                                TranslatedText.resolve(option.getName(), locale));
                                        row.put("option_value", option.getValue());
                                        rows.add(row);
                                    });
                });
        return rows;
    }

    private Map<String, Object> resolvedVariantOptionMap(ProductVariant variant) {
        Map<String, Object> map = variant.attributeOptionsAsMap();
        if (!map.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach(
                    (attributeRef, optionRef) -> {
                        var option = resolveAttributeOption(attributeRef, optionRef);
                        if (option != null && option.getProductAttribute() != null) {
                            normalized.put(
                                    option.getProductAttribute().getId().toString(),
                                    option.getId().toString());
                        }
                    });
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        List<Object> optionIds = variant.attributeOptionsAsList();
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Object item : optionIds) {
            UUID optionId = uuid(item);
            if (optionId == null) {
                continue;
            }
            productAttributeOptionRepository
                    .findById(optionId)
                    .ifPresent(
                            option -> {
                                if (option.getProductAttribute() != null) {
                                    normalized.put(
                                            option.getProductAttribute().getId().toString(),
                                            option.getId().toString());
                                }
                            });
        }
        return normalized;
    }

    private com.shoptopus.core.catalog.ProductAttributeOption resolveAttributeOption(
            Object attributeRef, Object optionRef) {
        String attributeKey = attributeRef == null ? null : String.valueOf(attributeRef);
        String optionKey = optionRef == null ? null : String.valueOf(optionRef);
        if (optionKey == null || optionKey.isBlank()) {
            return null;
        }

        UUID optionId = uuid(optionKey);
        if (optionId != null) {
            return productAttributeOptionRepository
                    .findById(optionId)
                    .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                    .orElse(null);
        }

        var attribute = resolveAttribute(attributeKey);
        if (attribute != null) {
            return productAttributeOptionRepository
                    .findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId())
                    .stream()
                    .filter(
                            option ->
                                    optionKey.equalsIgnoreCase(option.getSlug())
                                            || optionKey.equalsIgnoreCase(option.getValue()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private ProductAttribute resolveAttribute(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return null;
        }
        UUID attributeId = uuid(attributeKey);
        if (attributeId != null) {
            return productAttributeRepository.findById(attributeId).orElse(null);
        }
        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(attribute -> attributeKey.equalsIgnoreCase(attribute.getSlug()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal applyDiscountToVariantPrice(
            StorefrontPricingService.Pricing pricing, BigDecimal variantPrice) {
        if (!pricing.discounted()) {
            return variantPrice;
        }
        return switch (pricing.discountType() == null ? 0 : pricing.discountType()) {
            case 1 ->
                    variantPrice
                            .subtract(
                                    variantPrice
                                            .multiply(pricing.discountValue())
                                            .divide(
                                                    BigDecimal.valueOf(100),
                                                    2,
                                                    RoundingMode.HALF_UP))
                            .max(BigDecimal.ZERO);
            case 2 -> variantPrice.subtract(pricing.discountValue()).max(BigDecimal.ZERO);
            default -> variantPrice;
        };
    }

    private String normalizeCustomizationValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Cart createOrReuseCart(User user, String ipAddress) {
        if (user != null) {
            return cartRepository
                    .findTopByUserIdOrderByCreatedAtDesc(user.getId())
                    .orElseGet(
                            () -> {
                                Cart created = new Cart();
                                created.setUser(user);
                                created.setIpAddress(ipAddress);
                                created.setSlug(UUID.randomUUID().toString());
                                return cartRepository.save(created);
                            });
        }
        Cart created = new Cart();
        created.setIpAddress(ipAddress);
        created.setSlug(UUID.randomUUID().toString());
        return cartRepository.save(created);
    }

    private Product requireProduct(UUID productId) {
        return productRepository
                .findById(productId)
                .filter(product -> product.getDeletedAt() == null)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "Validation failed",
                                        "Invalid product id"));
    }

    private ProductVariant requireVariant(UUID productId, UUID variantId) {
        if (variantId == null) {
            return null;
        }
        return productVariantRepository
                .findById(variantId)
                .filter(variant -> variant.getDeletedAt() == null)
                .filter(ProductVariant::isEnabled)
                .filter(
                        variant ->
                                variant.getProduct() != null
                                        && productId.equals(variant.getProduct().getId()))
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "Validation failed",
                                        "Invalid product variant id"));
    }

    private Cart requireCart(UUID cartId) {
        return cartRepository
                .findById(cartId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
    }

    private Optional<User> resolveUser(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    private void validateCartAccess(Cart cart, User user) {
        if (cart.getUser() != null
                && (user == null || !cart.getUser().getId().equals(user.getId())))
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Sign in to access this basket.",
                    "Cart owner authentication required");
    }

    private void validateStock(Product product, ProductVariant variant, long requestedQuantity) {
        Long current =
                variant != null
                        ? jdbcTemplate.queryForObject(
                                "select stock from product_variants where id=?",
                                Long.class,
                                variant.getId())
                        : jdbcTemplate.queryForObject(
                                "select stock from products where id=?",
                                Long.class,
                                product.getId());
        long available = current == null ? 0L : current;
        if (available <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Out of stock.");
        }
        if (requestedQuantity > available) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Validation failed", "Only " + available + " left.");
        }
    }

    private void validateCustomization(Product product, String customizationValue) {
        // Customization is optional; this hook only exists to normalize/retain compatibility.
    }

    private UUID requiredUuid(Object raw, String message) {
        UUID value = uuid(raw);
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
        return value;
    }

    private UUID uuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long longVal(Object raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
    }

    private BigDecimal decimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        return raw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }
}
