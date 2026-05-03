package com.shoptopus.core.publicapi;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.content.Content;
import com.shoptopus.core.content.repository.ContentRepository;
import com.shoptopus.core.discount.DiscountRule;
import com.shoptopus.core.discount.repository.DiscountRuleRepository;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.storefront.StorefrontPricingService;
import com.shoptopus.core.storefront.StorefrontStoreResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicContentService {

    private static final int TYPE_PRODUCT_COLLECTION = 10;
    private static final int TYPE_CATEGORY_REFERENCE = 11;

    private final ContentRepository contentRepository;
    private final ManagedFileService managedFileService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final DiscountRuleRepository discountRuleRepository;
    private final StorefrontPricingService storefrontPricingService;
    private final EngagementService engagementService;
    private final List<String> languages;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public PublicContentService(
            ContentRepository contentRepository,
            ManagedFileService managedFileService,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductCategoryRepository productCategoryRepository,
            DiscountRuleRepository discountRuleRepository,
            StorefrontPricingService storefrontPricingService,
            EngagementService engagementService,
            @Value("${app.i18n.languages:en}") String languages,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.contentRepository = contentRepository;
        this.managedFileService = managedFileService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.discountRuleRepository = discountRuleRepository;
        this.storefrontPricingService = storefrontPricingService;
        this.engagementService = engagementService;
        this.languages = Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> contents(String contentableType, UUID contentableId, Locale locale) {
        return contents(contentableType, contentableId, locale, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> contents(String contentableType, UUID contentableId, Locale locale, String storeSlug) {
        List<Content> all = contentRepository.findByContentableTypeAndContentableId(contentableType, contentableId).stream()
                .sorted(Comparator.comparing(Content::getSortOrder).thenComparing(Content::getCreatedAt))
                .toList();
        String requestedLanguage = locale == null ? defaultLanguage() : locale.getLanguage();
        List<Content> matching = all.stream()
                .filter(content -> languageCode(content.getLanguageId()).equalsIgnoreCase(requestedLanguage))
                .toList();
        List<Content> effective = matching.isEmpty()
                ? all.stream().filter(content -> languageCode(content.getLanguageId()).equalsIgnoreCase(defaultLanguage())).toList()
                : matching;
        return effective.stream().map(content -> contentRow(content, locale, storeSlug)).toList();
    }

    public Map<String, Object> productCard(Product product, Locale locale) {
        return productCards(List.of(product), locale).stream()
                .findFirst()
                .orElseGet(LinkedHashMap::new);
    }

    private List<Map<String, Object>> productCards(List<Product> products, Locale locale) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<Map<String, Object>>> galleriesByProductId = managedFileService.filesForMany(
                "product",
                productIds,
                List.of("cover_photo", "gallery_photo")
        );
        Map<UUID, Map<String, Object>> videosByProductId = managedFileService.findAttachedSingles("product", productIds, "gallery_video");
        Map<UUID, List<ProductVariant>> variantsByProductId = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds).stream()
                .filter(ProductVariant::isEnabled)
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Long> reviewCounts = engagementService.productReviewCounts(productIds);

        return products.stream().map(product -> {
            List<Map<String, Object>> gallery = galleriesByProductId.getOrDefault(product.getId(), List.of());
            Map<String, Object> cover = gallery.stream()
                    .filter(file -> "cover_photo".equals(String.valueOf(file.get("type"))))
                    .findFirst()
                    .orElseGet(() -> productCover(product));
            Map<String, Object> video = videosByProductId.get(product.getId());
            List<ProductVariant> variants = variantsByProductId.getOrDefault(product.getId(), List.of());
            StorefrontPricingService.Pricing pricing = storefrontPricingService.pricing(product);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.getId());
            row.put("name", TranslatedText.resolve(product.getName(), locale == null ? Locale.ENGLISH : locale));
            row.put("slug", product.getSlug());
            row.put("short_description", TranslatedText.resolve(product.getShortDescription(), locale == null ? Locale.ENGLISH : locale));
            row.put("cover_image", cover);
            row.put("hover_media", hoverMedia(gallery, video));
            row.put("pricing", pricingRow(pricing));
            row.put("has_variants", !variants.isEmpty());
            row.put("customizable", product.isCustomizable());
            row.put("stock", product.getStock() == null ? 0L : product.getStock());
            row.put("available", (product.getStock() == null ? 0L : product.getStock()) > 0);
            row.put("rating", product.getRating() == null ? 0d : product.getRating());
            row.put("rating_count", reviewCounts.getOrDefault(product.getId(), 0L));
            row.put("store", storefrontStoreResolver.storeRow(product.getStore()));
            return row;
        }).toList();
    }

    public Map<String, Object> categoryCard(ProductCategory category, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", TranslatedText.resolve(category.getName(), locale == null ? Locale.ENGLISH : locale));
        row.put("slug", category.getSlug());
        row.put("description", TranslatedText.resolve(category.getDescription(), locale == null ? Locale.ENGLISH : locale));
        row.put("menu_image", category.getMenuImage());
        row.put("header_image", category.getHeaderImage());
        return row;
    }

    private Map<String, Object> contentRow(Content content, Locale locale, String storeSlug) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", content.getId());
        row.put("language", languageCode(content.getLanguageId()));
        row.put("type", content.getType());
        row.put("text", content.getText());
        row.put("data", content.getData() == null ? Map.of() : content.getData());
        row.put("sort_order", content.getSortOrder());
        row.put("files", managedFileService.filesFor("content", content.getId(), List.of("content_file")));
        row.put("video", managedFileService.findAttachedSingle("content", content.getId(), "content_video"));
        row.put("rendered", renderSpecial(content, locale, storeSlug));
        return row;
    }

    private Map<String, Object> renderSpecial(Content content, Locale locale, String storeSlug) {
        Map<String, Object> data = content.getData() == null ? Map.of() : content.getData();
        return switch (content.getType() == null ? 0 : content.getType()) {
            case TYPE_PRODUCT_COLLECTION -> Map.of(
                    "kind", "products",
                    "items", renderedProducts(data, locale, storeSlug)
            );
            case TYPE_CATEGORY_REFERENCE -> Map.of(
                    "kind", "category",
                    "category", renderedCategory(data, locale)
            );
            default -> Map.of();
        };
    }

    private List<Map<String, Object>> renderedProducts(Map<String, Object> data, Locale locale, String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        Set<UUID> selectedIds = selectedEnabledProductIds(data);
        if (!selectedIds.isEmpty()) {
            List<Product> products = selectedIds.stream()
                    .map(productRepository::findById)
                    .flatMap(Optional::stream)
                    .filter(product -> product.getDeletedAt() == null)
                    .filter(product -> product.getStatus() == null || product.getStatus() == 1)
                    .filter(product -> matchesStore(product.getStore(), resolvedStore))
                    .toList();
            return productCards(products, locale);
        }

        Set<UUID> categoryIds = uuids(stringList(data.get("category_ids")));
        Set<UUID> tagIds = uuids(stringList(data.get("tag_ids")));
        Set<UUID> attributeIds = uuids(stringList(data.get("attribute_ids")));
        boolean onSale = Boolean.TRUE.equals(boolVal(data.get("on_sale"), false));
        String sortBy = str(data.get("sort_by"), "newest_first");
        int maxCount = Math.max(1, Math.min(intVal(data.get("max_count"), 12), 100));
        Set<UUID> discounted = onSale ? discountedProductIds(resolvedStore) : Set.of();

        List<Product> products = productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getStatus() == null || product.getStatus() == 1)
                .filter(product -> matchesStore(product.getStore(), resolvedStore))
                .filter(product -> categoryIds.isEmpty() || product.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                .filter(product -> tagIds.isEmpty() || product.getTags().stream().anyMatch(tag -> tagIds.contains(tag.getId())))
                .filter(product -> attributeIds.isEmpty() || matchesAttributes(product, attributeIds))
                .filter(product -> !onSale || discounted.contains(product.getId()))
                .sorted(productComparator(sortBy))
                .limit(maxCount)
                .toList();
        return productCards(products, locale);
    }

    private Map<String, Object> renderedCategory(Map<String, Object> data, Locale locale) {
        String categoryId = str(data.get("category_id"), "");
        return parseUuid(categoryId)
                .flatMap(productCategoryRepository::findById)
                .filter(category -> category.getDeletedAt() == null && category.isEnabled())
                .map(category -> categoryCard(category, locale))
                .orElse(null);
    }

    private Map<String, Object> productCover(Product product) {
        Map<String, Object> attached = managedFileService.findAttachedSingle("product", product.getId(), "cover_photo");
        if (attached != null) {
            return attached;
        }
        if (product.getCoverPhoto() == null || product.getCoverPhoto().isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(product.getCoverPhoto());
    }

    private Map<String, Object> pricingRow(StorefrontPricingService.Pricing pricing) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("currency", "GBP");
        row.put("original_amount", pricing.original());
        row.put("final_amount", pricing.finalAmount());
        row.put("is_discounted", pricing.discounted());
        return row;
    }

    private Map<String, Object> hoverMedia(List<Map<String, Object>> gallery, Map<String, Object> video) {
        if (video != null && !video.isEmpty()) {
            return video;
        }
        return gallery.stream()
                .filter(file -> "gallery_photo".equals(String.valueOf(file.get("type"))))
                .findFirst()
                .orElse(null);
    }

    private Comparator<Product> productComparator(String sortBy) {
        return switch (sortBy) {
            case "oldest_first" -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_low_to_high" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "price_high_to_low" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private boolean matchesAttributes(Product product, Set<UUID> attributeIds) {
        if (product.getAvailableAttributeOptions() == null || product.getAvailableAttributeOptions().isEmpty()) {
            return false;
        }
        Set<String> keys = product.getAvailableAttributeOptions().keySet();
        return attributeIds.stream().allMatch(attributeId -> keys.contains(attributeId.toString()));
    }

    private Set<UUID> discountedProductIds(StorefrontStoreResolver.ResolvedStore resolvedStore) {
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        for (DiscountRule rule : activeDiscountRules(resolvedStore)) {
            boolean active = rule.isEnabled()
                    && (rule.getValidFrom() == null || !rule.getValidFrom().isAfter(now))
                    && (rule.getValidUntil() == null || !rule.getValidUntil().isBefore(now));
            if (!active) {
                continue;
            }
            rule.getProducts().forEach(product -> ids.add(product.getId()));
            Set<UUID> categoryIds = rule.getCategories().stream().map(ProductCategory::getId).collect(Collectors.toSet());
            if (!categoryIds.isEmpty()) {
                productRepository.findAll().stream()
                        .filter(product -> product.getDeletedAt() == null)
                        .filter(product -> product.getStatus() == null || product.getStatus() == 1)
                        .filter(product -> matchesStore(product.getStore(), resolvedStore))
                        .filter(product -> product.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                        .forEach(product -> ids.add(product.getId()));
            }
        }
        return ids;
    }

    private List<DiscountRule> activeDiscountRules(StorefrontStoreResolver.ResolvedStore resolvedStore) {
        if (!resolvedStore.multiStoreEnabled() || resolvedStore.superstore() || resolvedStore.store() == null) {
            return discountRuleRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                    .filter(rule -> matchesStore(rule.getStore(), resolvedStore))
                    .toList();
        }
        return discountRuleRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(resolvedStore.store().getId()).stream()
                .filter(rule -> matchesStore(rule.getStore(), resolvedStore))
                .toList();
    }

    private boolean matchesStore(Store productStore, StorefrontStoreResolver.ResolvedStore resolvedStore) {
        if (!resolvedStore.multiStoreEnabled()) {
            return true;
        }
        if (resolvedStore.superstore()) {
            return productStore != null && productStore.getStatus() == StoreStatus.ACTIVE;
        }
        return productStore != null
                && resolvedStore.store() != null
                && resolvedStore.store().getId().equals(productStore.getId())
                && productStore.getStatus() == StoreStatus.ACTIVE;
    }

    private Set<UUID> selectedEnabledProductIds(Map<String, Object> data) {
        return mapList(data.get("products")).stream()
                .filter(item -> boolVal(item.get("enabled"), true))
                .map(item -> parseUuid(str(item.get("id"), null)))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank()).map(String::valueOf).toList();
    }

    private String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private Boolean boolVal(Object value, Boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer intVal(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Set<UUID> uuids(List<String> values) {
        return values.stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String languageCode(Long languageId) {
        List<String> effectiveLanguages = languages.isEmpty() ? List.of("en") : languages;
        if (languageId == null || languageId < 1 || languageId > effectiveLanguages.size()) {
            return effectiveLanguages.get(0);
        }
        return effectiveLanguages.get(languageId.intValue() - 1);
    }

    private String defaultLanguage() {
        return languages.isEmpty() ? "en" : languages.get(0);
    }
}
