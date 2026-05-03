package com.shoptopus.core.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminProductVariantService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ObjectMapper objectMapper;

    public AdminProductVariantService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String productId) {
        Product product = requireProduct(productId);
        List<Map<String, Object>> data = variantRepository.findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .map(this::toRow)
                .toList();
        return Map.of("product_id", product.getId(), "data", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String productId, String variantId) {
        Product product = requireProduct(productId);
        UUID id = uuid(variantId).orElse(null);
        if (id == null) {
            return Map.of("id", variantId);
        }
        return variantRepository.findById(id)
                .filter(variant -> variant.getDeletedAt() == null)
                .filter(variant -> variant.getProduct() != null && product.getId().equals(variant.getProduct().getId()))
                .map(this::toRow)
                .orElseGet(() -> Map.of("id", id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(String productId, Map<String, Object> body) {
        Product product = requireProduct(productId);
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSlug(str(body, "slug", "variant-" + variant.getId()));
        variant.setPrice(decimalOrDefault(body, "price", product.getPrice()));
        variant.setStock(longVal(body, "stock", 0L));
        variant.setEnabled(bool(body, "enabled", true));
        variant.setSku(str(body, "sku", null));
        variant.setDescription(descriptionTranslations(body, Map.of()));
        variant.setAttributeOptions(jsonValue(body, "attribute_options"));
        variantRepository.save(variant);
        return Map.of("id", variant.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String productId, String variantId, Map<String, Object> body) {
        Product product = requireProduct(productId);
        UUID id = uuid(variantId).orElse(null);
        if (id == null) {
            return Map.of("id", variantId);
        }
        Optional<ProductVariant> variant = variantRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getProduct() != null && product.getId().equals(item.getProduct().getId()));
        if (variant.isEmpty()) {
            return Map.of("id", id);
        }
        ProductVariant entity = variant.orElseThrow();
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("price")) {
            entity.setPrice(decimalOrDefault(body, "price", product.getPrice()));
        }
        if (body != null && body.containsKey("stock")) {
            entity.setStock(longVal(body, "stock", entity.getStock()));
        }
        if (body != null && body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.isEnabled()));
        }
        if (body != null && body.containsKey("sku")) {
            entity.setSku(str(body, "sku", entity.getSku()));
        }
        if (body != null && (body.containsKey("name") || body.containsKey("description"))) {
            entity.setDescription(descriptionTranslations(body, entity.getDescription()));
        }
        if (body != null && body.containsKey("attribute_options")) {
            entity.setAttributeOptions(jsonValue(body, "attribute_options"));
        }
        variantRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String productId, String variantId) {
        Product product = requireProduct(productId);
        UUID id = uuid(variantId).orElse(null);
        if (id == null) {
            return Map.of("id", variantId);
        }
        variantRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getProduct() != null && product.getId().equals(item.getProduct().getId()))
                .ifPresent(variant -> {
                    variant.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    variantRepository.save(variant);
                });
        return Map.of("id", id);
    }

    private Product requireProduct(String productId) {
        UUID id = uuid(productId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_id is invalid."));
        return productRepository.findById(id)
                .filter(product -> product.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_id is invalid."));
    }

    private Map<String, Object> toRow(ProductVariant variant) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("product_id", variant.getProduct() == null ? null : variant.getProduct().getId());
        row.put("slug", variant.getSlug());
        row.put("price", variant.getPrice());
        row.put("stock", variant.getStock());
        row.put("sku", variant.getSku());
        row.put("description", variant.getDescription());
        row.put("description_display", TranslatedText.resolve(variant.getDescription(), DEFAULT_LOCALE));
        row.put("attribute_options", variant.attributeOptionsAsMap());
        row.put("enabled", variant.isEnabled());
        return row;
    }

    private Map<String, String> descriptionTranslations(Map<String, Object> body, Map<String, String> fallback) {
        if (body == null) {
            return fallback;
        }
        if (body.containsKey("description")) {
            return translations(body.get("description"), fallback);
        }
        if (body.containsKey("name")) {
            return translations(body.get("name"), fallback);
        }
        return fallback;
    }

    private Map<String, String> translations(Object raw, Map<String, String> fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue == null ? null : String.valueOf(entryValue)));
            return result;
        }
        String value = String.valueOf(raw);
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<>() {});
            } catch (Exception ignored) {
                return Map.of("en", value);
            }
        }
        return Map.of("en", value);
    }

    private Object jsonValue(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return new LinkedHashMap<>();
        }
        Object raw = body.get(key);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue));
            return result;
        }
        if (raw instanceof List<?> list) {
            return list;
        }
        String value = String.valueOf(raw);
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<>() {});
            } catch (Exception ignored) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
            }
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<Object>>() {});
            } catch (Exception ignored) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private boolean bool(Map<String, Object> body, String key, boolean fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        return raw instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(raw));
    }

    private Long longVal(Map<String, Object> body, String key, Long fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
    }

    private BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(body.get(key)));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
    }

    private BigDecimal decimalOrDefault(Map<String, Object> body, String key, BigDecimal fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        String raw = String.valueOf(body.get(key)).trim();
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
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
