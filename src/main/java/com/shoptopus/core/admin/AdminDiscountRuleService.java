package com.shoptopus.core.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.discount.DiscountRule;
import com.shoptopus.core.discount.repository.DiscountRuleRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminDiscountRuleService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DiscountRuleRepository discountRuleRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ObjectMapper objectMapper;
    private final AdminStoreContext adminStoreContext;

    public AdminDiscountRuleService(
            DiscountRuleRepository discountRuleRepository,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            ObjectMapper objectMapper,
            AdminStoreContext adminStoreContext
    ) {
        this.discountRuleRepository = discountRuleRepository;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.objectMapper = objectMapper;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = scopedRules().stream()
                .map(this::toListRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "discount-rules");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        return toDetailRow(findRule(id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        DiscountRule rule = new DiscountRule();
        rule.setName(readRequiredName(payload));
        rule.setType(readRequiredType(payload));
        rule.setAmount(readRequiredAmount(payload));
        rule.setValidFrom(readRequiredDate(payload, "valid_from"));
        rule.setValidUntil(readRequiredDate(payload, "valid_until"));
        rule.setEnabled(readRequiredBoolean(payload, "enabled"));
        rule.setSlug(optionalString(payload, "slug").orElse("discount-rule-" + UUID.randomUUID()));
        rule.setStore(activeStore());
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        DiscountRule rule = findRule(id);
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (payload.containsKey("name")) {
            rule.setName(readName(payload.get("name")));
        }
        if (payload.containsKey("type")) {
            rule.setType(readType(payload.get("type")));
        }
        if (payload.containsKey("amount")) {
            rule.setAmount(readDecimal(payload.get("amount"), "The amount must be a number."));
        }
        if (payload.containsKey("valid_from")) {
            rule.setValidFrom(readDate(payload.get("valid_from"), "The valid_from is not a valid date."));
        }
        if (payload.containsKey("valid_until")) {
            rule.setValidUntil(readDate(payload.get("valid_until"), "The valid_until is not a valid date."));
        }
        if (payload.containsKey("enabled")) {
            rule.setEnabled(readBoolean(payload.get("enabled"), "enabled"));
        }
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        DiscountRule rule = findRule(id);
        rule.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        discountRuleRepository.save(rule);
        return Map.of("id", rule.getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> availableProducts(String id) {
        DiscountRule rule = findRule(id);
        Set<UUID> attached = rule.getProducts().stream().map(Product::getId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> data = productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(product -> !attached.contains(product.getId()))
                .sorted(Comparator.comparing(Product::getCreatedAt))
                .map(this::productRow)
                .toList();
        return Map.of("data", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> availableCategories(String id) {
        DiscountRule rule = findRule(id);
        Set<UUID> attached = rule.getCategories().stream().map(ProductCategory::getId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> data = productCategoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .filter(category -> !attached.contains(category.getId()))
                .sorted(Comparator.comparing(ProductCategory::getCreatedAt))
                .map(this::categoryRow)
                .toList();
        return Map.of("data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> attachProduct(String ruleId, String productId) {
        DiscountRule rule = findRule(ruleId);
        Product product = findProduct(productId);
        Set<Product> products = new LinkedHashSet<>(rule.getProducts());
        products.add(product);
        rule.setProducts(products);
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> detachProduct(String ruleId, String productId) {
        DiscountRule rule = findRule(ruleId);
        UUID productUuid = parseUuid(productId, "product");
        rule.setProducts(rule.getProducts().stream()
                .filter(product -> !product.getId().equals(productUuid))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> attachCategory(String ruleId, String categoryId) {
        DiscountRule rule = findRule(ruleId);
        ProductCategory category = findCategory(categoryId);
        Set<ProductCategory> categories = new LinkedHashSet<>(rule.getCategories());
        categories.add(category);
        rule.setCategories(categories);
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> detachCategory(String ruleId, String categoryId) {
        DiscountRule rule = findRule(ruleId);
        UUID categoryUuid = parseUuid(categoryId, "product category");
        rule.setCategories(rule.getCategories().stream()
                .filter(category -> !category.getId().equals(categoryUuid))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        discountRuleRepository.save(rule);
        return toDetailRow(rule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkExpire(Map<String, Object> body) {
        List<DiscountRule> rules = requireBulkRules(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        rules.forEach(rule -> rule.setValidUntil(now));
        discountRuleRepository.saveAll(rules);
        return bulkResult(rules);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkStart(Map<String, Object> body) {
        List<DiscountRule> rules = requireBulkRules(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        rules.forEach(rule -> rule.setValidFrom(now));
        discountRuleRepository.saveAll(rules);
        return bulkResult(rules);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkActivate(Map<String, Object> body) {
        List<DiscountRule> rules = requireBulkRules(body);
        if (body == null || body.get("period") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is required.");
        }
        int period;
        try {
            period = Integer.parseInt(String.valueOf(body.get("period")));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is invalid.");
        }
        OffsetDateTime validFrom = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime validUntil = validFrom.withHour(23).withMinute(59).withSecond(59).withNano(0);
        validUntil = switch (period) {
            case 0 -> validUntil.plusDays(1);
            case 1 -> validUntil.plusWeeks(1);
            case 2 -> validUntil.plusMonths(1);
            case 3 -> validUntil.plusYears(1);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is invalid.");
        };
        for (DiscountRule rule : rules) {
            rule.setEnabled(true);
            rule.setValidFrom(validFrom);
            rule.setValidUntil(validUntil);
        }
        discountRuleRepository.saveAll(rules);
        return bulkResult(rules);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<DiscountRule> rules = requireBulkRules(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        rules.forEach(rule -> rule.setDeletedAt(now));
        discountRuleRepository.saveAll(rules);
        return bulkResult(rules);
    }

    private DiscountRule findRule(String id) {
        UUID ruleId = parseUuid(id, "discount rule");
        return discountRuleRepository.findByIdAndDeletedAtIsNull(ruleId)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Discount rule not found."));
    }

    private Product findProduct(String id) {
        UUID productId = parseUuid(id, "product");
        return productRepository.findById(productId)
                .filter(product -> product.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product is invalid."));
    }

    private ProductCategory findCategory(String id) {
        UUID categoryId = parseUuid(id, "product category");
        return productCategoryRepository.findById(categoryId)
                .filter(category -> category.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product category is invalid."));
    }

    private UUID parseUuid(String id, String label) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid " + label + " id.");
        }
    }

    private List<DiscountRule> requireBulkRules(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        List<DiscountRule> rules = new ArrayList<>();
        int index = 0;
        for (Object rawId : rawIds) {
            final int currentIndex = index;
            UUID id;
            try {
                id = UUID.fromString(String.valueOf(rawId));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.");
            }
            rules.add(discountRuleRepository.findByIdAndDeletedAtIsNull(id)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return rules;
    }

    private List<DiscountRule> scopedRules() {
        Store activeStore = activeStore();
        if (activeStore == null) {
            return discountRuleRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return discountRuleRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(DiscountRule rule) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null && rule.getStore() != null && activeStore.getId().equals(rule.getStore().getId());
    }

    private boolean matchesActiveStore(Product product) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null && product.getStore() != null && activeStore.getId().equals(product.getStore().getId());
    }

    private Store activeStore() {
        return adminStoreContext.activeStoreForCurrentRequest().orElse(null);
    }

    private Map<String, Object> bulkResult(List<DiscountRule> rules) {
        return Map.of("ids", rules.stream().map(DiscountRule::getId).toList(), "count", rules.size());
    }

    private Map<String, Object> toListRow(DiscountRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("type", rule.getType());
        row.put("name", rule.getName());
        row.put("value", displayValue(rule.getType(), rule.getAmount()));
        row.put("valid_from", format(rule.getValidFrom()));
        row.put("valid_until", format(rule.getValidUntil()));
        row.put("valid", isValid(rule));
        row.put("enabled", rule.isEnabled());
        return row;
    }

    private Map<String, Object> toDetailRow(DiscountRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("type", rule.getType());
        row.put("name", rule.getName());
        row.put("amount", displayValue(rule.getType(), rule.getAmount()));
        row.put("value", rule.getAmount());
        row.put("valid_from", format(rule.getValidFrom()));
        row.put("valid_until", format(rule.getValidUntil()));
        row.put("products", rule.getProducts().stream().sorted(Comparator.comparing(Product::getCreatedAt)).map(this::productRow).toList());
        row.put("categories", rule.getCategories().stream().sorted(Comparator.comparing(ProductCategory::getCreatedAt)).map(this::categoryRow).toList());
        row.put("notes", List.of());
        return row;
    }

    private Map<String, Object> productRow(Product product) {
        return Map.of(
                "id", product.getId(),
                "name", product.getName(),
                "slug", product.getSlug()
        );
    }

    private Map<String, Object> categoryRow(ProductCategory category) {
        return Map.of(
                "id", category.getId(),
                "name", category.getName(),
                "slug", category.getSlug()
        );
    }

    private Map<String, String> readRequiredName(Map<String, Object> payload) {
        if (!payload.containsKey("name") || payload.get("name") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
        }
        return readName(payload.get("name"));
    }

    private Map<String, String> readName(Object raw) {
        try {
            if (raw instanceof Map<?, ?> map) {
                Map<String, String> values = new LinkedHashMap<>();
                map.forEach((key, value) -> values.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
                return values;
            }
            String value = String.valueOf(raw);
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field must be valid json.");
        }
    }

    private Integer readRequiredType(Map<String, Object> payload) {
        if (!payload.containsKey("type")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The type field is required.");
        }
        return readType(payload.get("type"));
    }

    private Integer readType(Object raw) {
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The type field must be numeric.");
        }
    }

    private BigDecimal readRequiredAmount(Map<String, Object> payload) {
        if (!payload.containsKey("amount")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The amount field is required.");
        }
        return readDecimal(payload.get("amount"), "The amount must be a number.");
    }

    private BigDecimal readDecimal(Object raw, String message) {
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private OffsetDateTime readRequiredDate(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key) || payload.get(key) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return readDate(payload.get(key), "The " + key + " is not a valid date.");
    }

    private OffsetDateTime readDate(Object raw, String message) {
        try {
            String value = String.valueOf(raw);
            if (value.length() == 10) {
                return OffsetDateTime.parse(value + "T00:00:00Z");
            }
            if (value.endsWith("Z") || value.contains("+")) {
                return OffsetDateTime.parse(value);
            }
            return OffsetDateTime.parse(value.replace(" ", "T") + "Z");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private boolean readRequiredBoolean(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return readBoolean(payload.get(key), key);
    }

    private boolean readBoolean(Object raw, String key) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String value = String.valueOf(raw).toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value)) {
            return false;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field must be true or false.");
    }

    private Optional<String> optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String displayValue(Integer type, BigDecimal amount) {
        String value = amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
        if (type != null && type == 2) {
            return "GBP " + value;
        }
        return value + "%";
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(DATETIME_FORMAT);
    }

    private boolean isValid(DiscountRule rule) {
        OffsetDateTime now = OffsetDateTime.now();
        return rule.getValidFrom() != null && rule.getValidUntil() != null
                && !now.isBefore(rule.getValidFrom())
                && !now.isAfter(rule.getValidUntil());
    }
}
