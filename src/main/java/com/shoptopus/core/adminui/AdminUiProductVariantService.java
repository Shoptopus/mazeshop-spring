package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminProductVariantService;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiProductVariantService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final AdminProductVariantService adminProductVariantService;

    public AdminUiProductVariantService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            AdminProductVariantService adminProductVariantService
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.adminProductVariantService = adminProductVariantService;
    }

    public Map<String, Object> list(String productId) {
        Product product = requireProduct(productId);
        List<Map<String, Object>> items = productVariantRepository.findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .sorted(Comparator.comparing(ProductVariant::getCreatedAt))
                .map(this::variantSummary)
                .toList();
        return Map.of(
                "product", Map.of(
                        "id", product.getId(),
                        "name", translated(product.getName()),
                        "slug", product.getSlug()
                ),
                "items", items
        );
    }

    public Map<String, Object> detail(String productId, String variantId) {
        Product product = requireProduct(productId);
        ProductVariant variant = requireVariant(product, variantId);

        Map<String, Object> row = new LinkedHashMap<>(variantSummary(variant));
        row.put("description", variant.getDescription());
        row.put("description_display", translated(variant.getDescription()));
        row.put("price_amount", variant.getPrice());
        row.put("created_at", variant.getCreatedAt() == null ? null : variant.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", variant.getUpdatedAt() == null ? null : variant.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("form_options", formOptions(productId));
        return row;
    }

    public Map<String, Object> formOptions(String productId) {
        Product product = requireProduct(productId);
        List<Map<String, Object>> attributes = productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .sorted(Comparator.comparing(attribute -> translated(attribute.getName())))
                .map(this::attributeOptionRow)
                .toList();
        return Map.of(
                "product", Map.of(
                        "id", product.getId(),
                        "name", translated(product.getName()),
                        "slug", product.getSlug()
                ),
                "attributes", attributes
        );
    }

    public Map<String, Object> create(String productId, Map<String, Object> body) {
        Object id = adminProductVariantService.create(productId, body).get("id");
        return detail(productId, String.valueOf(id));
    }

    public Map<String, Object> update(String productId, String variantId, Map<String, Object> body) {
        adminProductVariantService.update(productId, variantId, body);
        return detail(productId, variantId);
    }

    public Map<String, Object> delete(String productId, String variantId) {
        return adminProductVariantService.delete(productId, variantId);
    }

    private Product requireProduct(String productId) {
        try {
            return productRepository.findById(UUID.fromString(productId))
                    .filter(product -> product.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found");
        }
    }

    private ProductVariant requireVariant(Product product, String variantId) {
        try {
            UUID id = UUID.fromString(variantId);
            return productVariantRepository.findById(id)
                    .filter(variant -> variant.getDeletedAt() == null)
                    .filter(variant -> variant.getProduct() != null && product.getId().equals(variant.getProduct().getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variant not found", "Variant not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Variant not found", "Variant not found");
        }
    }

    private Map<String, Object> variantSummary(ProductVariant variant) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("product_id", variant.getProduct() == null ? null : variant.getProduct().getId());
        row.put("slug", variant.getSlug());
        row.put("price", money(variant.getPrice()));
        row.put("price_amount", variant.getPrice());
        row.put("stock", variant.getStock());
        row.put("sku", variant.getSku());
        row.put("description", variant.getDescription());
        row.put("description_display", translated(variant.getDescription()));
        row.put("attribute_options", variant.attributeOptionsAsMap());
        row.put("attribute_option_labels", attributeOptionLabels(variant));
        row.put("enabled", variant.isEnabled());
        row.put("status_label", variant.isEnabled() ? "Enabled" : "Disabled");
        return row;
    }

    private Map<String, Object> attributeOptionRow(ProductAttribute attribute) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", translated(attribute.getName()));
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("options", productAttributeOptionRepository.findAll().stream()
                .filter(option -> option.getDeletedAt() == null)
                .filter(ProductAttributeOption::isEnabled)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .sorted(Comparator.comparing(option -> translated(option.getName())))
                .map(option -> Map.of(
                        "id", option.getId(),
                        "name", translated(option.getName()),
                        "slug", option.getSlug(),
                        "value", option.getValue()
                ))
                .toList());
        return row;
    }

    private List<String> attributeOptionLabels(ProductVariant variant) {
        if (variant.getAttributeOptions() == null) {
            return List.of();
        }
        return variant.attributeOptionsAsList().stream()
                .map(this::stringValue)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }

    private String money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return "GBP " + amount.stripTrailingZeros().toPlainString();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
