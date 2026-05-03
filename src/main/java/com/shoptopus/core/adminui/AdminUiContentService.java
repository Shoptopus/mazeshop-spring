package com.shoptopus.core.adminui;

import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.content.Content;
import com.shoptopus.core.content.repository.ContentRepository;
import com.shoptopus.core.discount.DiscountRule;
import com.shoptopus.core.discount.repository.DiscountRuleRepository;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.page.repository.PageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUiContentService {

    private static final int TYPE_BODY = 0;
    private static final int TYPE_SECONDARY = 1;
    private static final int TYPE_PRODUCT_COLLECTION = 10;
    private static final int TYPE_CATEGORY_REFERENCE = 11;

    private final ContentRepository contentRepository;
    private final ManagedFileService managedFileService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final DiscountRuleRepository discountRuleRepository;
    private final com.shoptopus.core.catalog.repository.ProductTagRepository productTagRepository;
    private final PageRepository pageRepository;
    private final List<String> languages;

    public AdminUiContentService(
            ContentRepository contentRepository,
            ManagedFileService managedFileService,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            ProductAttributeRepository productAttributeRepository,
            DiscountRuleRepository discountRuleRepository,
            com.shoptopus.core.catalog.repository.ProductTagRepository productTagRepository,
            PageRepository pageRepository,
            @Value("${app.i18n.languages:en}") String languages
    ) {
        this.contentRepository = contentRepository;
        this.managedFileService = managedFileService;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.discountRuleRepository = discountRuleRepository;
        this.productTagRepository = productTagRepository;
        this.pageRepository = pageRepository;
        this.languages = Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String targetType, String targetId) {
        UUID id = requireTarget(targetType, targetId);
        List<Map<String, Object>> items = contentRepository.findByContentableTypeAndContentableId(targetType.toLowerCase(Locale.ROOT), id).stream()
                .sorted(Comparator.comparing(Content::getSortOrder).thenComparing(Content::getCreatedAt))
                .map(this::toRow)
                .toList();
        return Map.of(
                "target_type", targetType,
                "target_id", id,
                "items", items,
                "form_options", Map.of(
                        "languages", languageOptions(),
                        "types", List.of(
                                Map.of("value", TYPE_BODY, "label", "Body"),
                                Map.of("value", TYPE_SECONDARY, "label", "Secondary"),
                                Map.of("value", TYPE_PRODUCT_COLLECTION, "label", "Products"),
                                Map.of("value", TYPE_CATEGORY_REFERENCE, "label", "Category")
                        ),
                        "product_block", productBlockOptions(),
                        "category_block", categoryBlockOptions()
                )
        );
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(String targetType, String targetId, Map<String, Object> body) {
        UUID id = requireTarget(targetType, targetId);
        Content content = new Content();
        content.setId(UUID.randomUUID());
        content.setContentableType(targetType.toLowerCase(Locale.ROOT));
        content.setContentableId(id);
        apply(content, body);
        contentRepository.save(content);
        syncAttachments(content, body);
        return toRow(content);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String contentId, Map<String, Object> body) {
        Content content = findContent(contentId);
        apply(content, body);
        contentRepository.save(content);
        syncAttachments(content, body);
        return toRow(content);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String contentId) {
        Content content = findContent(contentId);
        managedFileService.clearFor("content", content.getId());
        contentRepository.delete(content);
        return Map.of("id", content.getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewProducts(Map<String, Object> body) {
        Map<String, Object> data = map(body == null ? null : body.get("data"));
        if (data == null) {
            data = body == null ? Map.of() : body;
        }
        String language = str(body, "language", defaultLanguage());
        return Map.of("items", productPreviewItems(data, language));
    }

    private void apply(Content content, Map<String, Object> body) {
        content.setLanguageId(languageId(str(body, "language", defaultLanguage())));
        content.setType(intVal(body, "type", content.getType() == null ? 0 : content.getType()));
        content.setText(str(body, "text", ""));
        content.setData(cleanDataForType(content.getType(), map(body == null ? null : body.get("data"))));
        content.setSortOrder(intVal(body, "sort_order", content.getSortOrder() == null ? 0 : content.getSortOrder()));
    }

    private void syncAttachments(Content content, Map<String, Object> body) {
        if (body != null && body.containsKey("files")) {
            managedFileService.syncFiles("content", content.getId(), "content_file", mapList(body.get("files")));
        }
        if (body != null && body.containsKey("video")) {
            managedFileService.attachSingle("content", content.getId(), "content_video", map(body.get("video")));
        }
    }

    private Map<String, Object> toRow(Content content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", content.getId());
        row.put("contentable_type", content.getContentableType());
        row.put("contentable_id", content.getContentableId());
        row.put("language", languageCode(content.getLanguageId()));
        row.put("language_id", content.getLanguageId());
        row.put("type", content.getType());
        row.put("text", content.getText());
        row.put("data", content.getData() == null ? Map.of() : content.getData());
        row.put("sort_order", content.getSortOrder());
        row.put("files", managedFileService.filesFor("content", content.getId(), List.of("content_file")));
        row.put("video", managedFileService.findAttachedSingle("content", content.getId(), "content_video"));
        row.put("created_at", content.getCreatedAt() == null ? null : content.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", content.getUpdatedAt() == null ? null : content.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return row;
    }

    private UUID requireTarget(String targetType, String targetId) {
        UUID id = parseUuid(targetId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid content target."));
        String normalized = targetType == null ? "" : targetType.trim().toLowerCase(Locale.ROOT);
        boolean exists = switch (normalized) {
            case "product" -> productRepository.findById(id).filter(item -> item.getDeletedAt() == null).isPresent();
            case "category" -> productCategoryRepository.findById(id).filter(item -> item.getDeletedAt() == null).isPresent();
            case "attribute" -> productAttributeRepository.findById(id).filter(item -> item.getDeletedAt() == null).isPresent();
            case "page" -> pageRepository.findById(id).filter(item -> item.getDeletedAt() == null).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid content target.");
        }
        return id;
    }

    private Content findContent(String contentId) {
        UUID id = parseUuid(contentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Content not found", "Content not found"));
        return contentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Content not found", "Content not found"));
    }

    private List<Map<String, Object>> languageOptions() {
        return effectiveLanguages().stream()
                .map(language -> Map.<String, Object>of("value", language, "label", language.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private Map<String, Object> productBlockOptions() {
        return Map.of(
                "categories", productCategoryRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                        .sorted(Comparator.comparing(item -> translated(item.getName())))
                        .map(item -> Map.<String, Object>of(
                                "id", item.getId(),
                                "name", translated(item.getName()),
                                "slug", item.getSlug()
                        ))
                        .toList(),
                "tags", productTagRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                        .sorted(Comparator.comparing(item -> translated(item.getName())))
                        .map(item -> Map.<String, Object>of(
                                "id", item.getId(),
                                "name", translated(item.getName()),
                                "slug", item.getSlug()
                        ))
                        .toList(),
                "attributes", productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                        .sorted(Comparator.comparing(item -> translated(item.getName())))
                        .map(item -> Map.<String, Object>of(
                                "id", item.getId(),
                                "name", translated(item.getName()),
                                "slug", item.getSlug()
                        ))
                        .toList(),
                "sort_options", List.of(
                        Map.of("value", "newest_first", "label", "Newest first"),
                        Map.of("value", "oldest_first", "label", "Oldest first"),
                        Map.of("value", "price_low_to_high", "label", "Price low to high"),
                        Map.of("value", "price_high_to_low", "label", "Price high to low")
                )
        );
    }

    private Map<String, Object> categoryBlockOptions() {
        return Map.of(
                "categories", productCategoryRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                        .sorted(Comparator.comparing(item -> translated(item.getName())))
                        .map(item -> Map.<String, Object>of(
                                "id", item.getId(),
                                "name", translated(item.getName()),
                                "slug", item.getSlug()
                        ))
                        .toList()
        );
    }

    private List<String> effectiveLanguages() {
        return languages.isEmpty() ? List.of("en") : languages;
    }

    private String defaultLanguage() {
        return effectiveLanguages().get(0);
    }

    private long languageId(String language) {
        int index = effectiveLanguages().indexOf(language);
        return index >= 0 ? index + 1L : 1L;
    }

    private String languageCode(Long languageId) {
        if (languageId == null || languageId < 1 || languageId > effectiveLanguages().size()) {
            return defaultLanguage();
        }
        return effectiveLanguages().get(languageId.intValue() - 1);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private Integer intVal(Map<String, Object> body, String key, Integer fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<String, Object> body, String key) {
        if (body == null || !(body.get(key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank()).map(String::valueOf).toList();
    }

    private Boolean boolVal(Map<String, Object> body, String key, Boolean fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private String translated(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String code = defaultLanguage();
        return value.getOrDefault(code, value.getOrDefault("en", value.values().stream().findFirst().orElse("")));
    }

    private Map<String, Object> cleanDataForType(Integer type, Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        return switch (type == null ? TYPE_BODY : type) {
            case TYPE_PRODUCT_COLLECTION -> sanitizeProductCollectionData(data);
            case TYPE_CATEGORY_REFERENCE -> sanitizeCategoryReferenceData(data);
            default -> Map.of();
        };
    }

    private Map<String, Object> sanitizeProductCollectionData(Map<String, Object> data) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("title", str(data, "title", ""));
        cleaned.put("description", str(data, "description", ""));
        cleaned.put("category_ids", stringList(data, "category_ids"));
        cleaned.put("tag_ids", stringList(data, "tag_ids"));
        cleaned.put("attribute_ids", stringList(data, "attribute_ids"));
        cleaned.put("on_sale", boolVal(data, "on_sale", false));
        cleaned.put("sort_by", str(data, "sort_by", "newest_first"));
        cleaned.put("max_count", intVal(data, "max_count", 12));
        cleaned.put("products", sanitizeProductSelections(mapList(data.get("products"))));
        return cleaned;
    }

    private List<Map<String, Object>> sanitizeProductSelections(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> {
                    String id = str(item, "id", null);
                    if (id == null || parseUuid(id).isEmpty()) {
                        return null;
                    }
                    return Map.<String, Object>of(
                            "id", id,
                            "enabled", boolVal(item, "enabled", true)
                    );
                })
                .filter(item -> item != null)
                .toList();
    }

    private Map<String, Object> sanitizeCategoryReferenceData(Map<String, Object> data) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("description", str(data, "description", ""));
        cleaned.put("category_id", str(data, "category_id", ""));
        return cleaned;
    }

    private List<Map<String, Object>> productPreviewItems(Map<String, Object> data, String language) {
        Set<UUID> categoryIds = uuids(stringList(data, "category_ids"));
        Set<UUID> tagIds = uuids(stringList(data, "tag_ids"));
        Set<UUID> attributeIds = uuids(stringList(data, "attribute_ids"));
        boolean onSale = Boolean.TRUE.equals(boolVal(data, "on_sale", false));
        String sortBy = str(data, "sort_by", "newest_first");
        int maxCount = Math.max(1, Math.min(intVal(data, "max_count", 12), 100));
        Map<UUID, Boolean> enabledByProduct = selectedProductState(data);
        Set<UUID> discountedProducts = onSale ? discountedProductIds() : Set.of();

        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> categoryIds.isEmpty() || product.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                .filter(product -> tagIds.isEmpty() || product.getTags().stream().anyMatch(tag -> tagIds.contains(tag.getId())))
                .filter(product -> attributeIds.isEmpty() || matchesAttributes(product, attributeIds))
                .filter(product -> !onSale || discountedProducts.contains(product.getId()))
                .sorted(productComparator(sortBy))
                .limit(maxCount)
                .map(product -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", product.getId());
                    row.put("name", product.getName().getOrDefault(language, product.getName().getOrDefault(defaultLanguage(), product.getName().values().stream().findFirst().orElse(product.getSlug()))));
                    row.put("cover_image", productCover(product));
                    row.put("enabled", enabledByProduct.getOrDefault(product.getId(), true));
                    return row;
                })
                .toList();
    }

    private Comparator<com.shoptopus.core.catalog.Product> productComparator(String sortBy) {
        return switch (sortBy) {
            case "oldest_first" -> Comparator.comparing(com.shoptopus.core.catalog.Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_low_to_high" -> Comparator.comparing(com.shoptopus.core.catalog.Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(com.shoptopus.core.catalog.Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "price_high_to_low" -> Comparator.comparing(com.shoptopus.core.catalog.Product::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(com.shoptopus.core.catalog.Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(com.shoptopus.core.catalog.Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private boolean matchesAttributes(com.shoptopus.core.catalog.Product product, Set<UUID> attributeIds) {
        if (product.getAvailableAttributeOptions() == null || product.getAvailableAttributeOptions().isEmpty()) {
            return false;
        }
        Set<String> keys = product.getAvailableAttributeOptions().keySet();
        return attributeIds.stream().allMatch(attributeId -> keys.contains(attributeId.toString()));
    }

    private Set<UUID> discountedProductIds() {
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> ids = new HashSet<>();
        for (DiscountRule rule : discountRuleRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()) {
            boolean active = rule.isEnabled()
                    && (rule.getValidFrom() == null || !rule.getValidFrom().isAfter(now))
                    && (rule.getValidUntil() == null || !rule.getValidUntil().isBefore(now));
            if (!active) {
                continue;
            }
            rule.getProducts().forEach(product -> ids.add(product.getId()));
            Set<UUID> categoryIds = rule.getCategories().stream().map(com.shoptopus.core.catalog.ProductCategory::getId).collect(java.util.stream.Collectors.toSet());
            if (!categoryIds.isEmpty()) {
                productRepository.findAll().stream()
                        .filter(product -> product.getDeletedAt() == null)
                        .filter(product -> product.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                        .forEach(product -> ids.add(product.getId()));
            }
        }
        return ids;
    }

    private Set<UUID> uuids(List<String> raw) {
        return raw.stream()
                .map(this::parseUuid)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Map<UUID, Boolean> selectedProductState(Map<String, Object> data) {
        Map<UUID, Boolean> state = new LinkedHashMap<>();
        for (Map<String, Object> item : mapList(data.get("products"))) {
            parseUuid(str(item, "id", null)).ifPresent(id -> state.put(id, boolVal(item, "enabled", true)));
        }
        return state;
    }

    private Map<String, Object> productCover(com.shoptopus.core.catalog.Product product) {
        Map<String, Object> attached = managedFileService.findAttachedSingle("product", product.getId(), "cover_photo");
        if (attached != null) {
            return attached;
        }
        if (product.getCoverPhoto() == null || product.getCoverPhoto().isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(product.getCoverPhoto());
    }
}
