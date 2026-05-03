package com.shoptopus.core.storefront;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.publicapi.PublicContentService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StorefrontShopService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ManagedFileService managedFileService;
    private final PublicContentService publicContentService;
    private final StorefrontPricingService storefrontPricingService;
    private final EngagementService engagementService;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public StorefrontShopService(
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            ProductTagRepository productTagRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            ProductVariantRepository productVariantRepository,
            ManagedFileService managedFileService,
            PublicContentService publicContentService,
            StorefrontPricingService storefrontPricingService,
            EngagementService engagementService,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productTagRepository = productTagRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.productVariantRepository = productVariantRepository;
        this.managedFileService = managedFileService;
        this.publicContentService = publicContentService;
        this.storefrontPricingService = storefrontPricingService;
        this.engagementService = engagementService;
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filters(Locale locale, String storeSlug) {
        List<Product> products = scopedProducts(storeSlug);
        Set<UUID> tagIds = products.stream()
                .flatMap(product -> product.getTags().stream())
                .filter(tag -> tag.getDeletedAt() == null && tag.isEnabled())
                .map(ProductTag::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ProductCategory> categories = storefrontVisibleCategories(storeSlug);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categories", categoryTreeRows(categories, null, locale));
        data.put("tags", productTagRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(tag -> tagIds.contains(tag.getId()))
                .map(tag -> tagFilterRow(tag, locale))
                .toList());
        data.put("attributes", productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(attribute -> matchesActiveStore(attribute.getStore(), storeSlug))
                .filter(ProductAttribute::isShowInFilters)
                .map(attribute -> attributeFilterRow(attribute, locale))
                .toList());
        data.put("sort_options", List.of(
                Map.of("value", "newest_added", "label", "Newest added"),
                Map.of("value", "price_low_to_high", "label", "Price low to high"),
                Map.of("value", "price_high_to_low", "label", "Price high to low"),
                Map.of("value", "top_rated", "label", "Top rated"),
                Map.of("value", "most_popular", "label", "Most popular"),
                Map.of("value", "name_alphabetical", "label", "Name alphabetical")
        ));
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> shop(
            String search,
            String categoryIds,
            String tagIds,
            String attributeOptionIds,
            String sort,
            Integer page,
            Integer perPage,
            Locale locale,
            String storeSlug
    ) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 48);

        Set<UUID> categoryFilter = csvUuids(categoryIds);
        Set<UUID> tagFilter = csvUuids(tagIds);
        Set<UUID> attributeOptionFilter = csvUuids(attributeOptionIds);
        List<ProductCategory> categories = storefrontVisibleCategories(storeSlug);
        Set<UUID> expandedCategoryFilter = expandCategoryFilter(categoryFilter, categories);
        ProductCategory selectedCategory = categoryFilter.size() == 1
                ? categories.stream().filter(category -> categoryFilter.contains(category.getId())).findFirst().orElse(null)
                : null;

        List<Product> filtered = scopedProducts(storeSlug).stream()
                .filter(product -> matchesSearch(product, search, locale))
                .filter(product -> matchesCategories(product, expandedCategoryFilter))
                .filter(product -> matchesTags(product, tagFilter))
                .filter(product -> matchesAttributeOptions(product, attributeOptionFilter))
                .sorted(productComparator(sort, locale))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        Map<String, Object> activeFilters = new LinkedHashMap<>();
        activeFilters.put("search", search == null ? "" : search);
        activeFilters.put("category_ids", new ArrayList<>(categoryFilter));
        activeFilters.put("tag_ids", new ArrayList<>(tagFilter));
        activeFilters.put("attribute_option_ids", new ArrayList<>(attributeOptionFilter));
        activeFilters.put("sort", sort == null || sort.isBlank() ? "newest_added" : sort);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", productCards(filtered.subList(fromIndex, toIndex), locale));
        response.put("meta", meta);
        response.put("filters", filters(locale, storeSlug));
        response.put("active_filters", activeFilters);
        response.put("selected_category", selectedCategory == null ? null : categoryDetailRow(selectedCategory, categories, locale, storeSlug));
        Set<UUID> visibleCategoryIds = categories.stream().map(ProductCategory::getId).collect(Collectors.toSet());
        response.put("selected_parent_category", selectedCategory == null || selectedCategory.getParent() == null
                ? null
                : visibleParentId(selectedCategory, visibleCategoryIds) == null
                ? null
                : categoryFilterRow(selectedCategory.getParent(), locale, visibleCategoryIds));
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> featuredProducts(int limit, Locale locale, String storeSlug) {
        int safeLimit = Math.max(1, Math.min(limit, 24));
        List<Product> products = scopedProducts(storeSlug).stream()
                .sorted(productComparator("most_popular", locale))
                .limit(safeLimit)
                .toList();
        return productCards(products, locale);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> productDetail(String slug, Locale locale, String storeSlug) {
        Product product = scopedProducts(storeSlug).stream()
                .filter(item -> slug.equals(item.getSlug()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));

        List<Map<String, Object>> gallery = managedFileService.filesFor("product", product.getId(), List.of("cover_photo", "gallery_photo"));
        Map<String, Object> cover = gallery.stream()
                .filter(file -> "cover_photo".equals(String.valueOf(file.get("type"))))
                .findFirst()
                .orElseGet(() -> legacyMap(product.getCoverPhoto()));
        Map<String, Object> video = managedFileService.findAttachedSingle("product", product.getId(), "gallery_video");
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .filter(ProductVariant::isEnabled)
                .toList();

        StorefrontPricingService.Pricing pricing = storefrontPricingService.pricing(product);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", product.getId());
        data.put("slug", product.getSlug());
        data.put("title", translated(product.getName(), locale));
        data.put("translations", product.getName());
        data.put("short_description", translated(product.getShortDescription(), locale));
        data.put("short_description_translations", product.getShortDescription());
        data.put("description", translated(product.getDescription(), locale));
        data.put("description_translations", product.getDescription());
        data.put("customizable", product.isCustomizable());
        data.put("customization_text", translated(product.getCustomizationText(), locale));
        data.put("customization_text_translations", product.getCustomizationText());
        data.put("headline", translated(product.getHeadline(), locale));
        data.put("subtitle", translated(product.getSubtitle(), locale));
        data.put("meta_tags", product.getMetaTags() == null ? List.of() : product.getMetaTags());
        data.put("rating", product.getRating() == null ? 0d : product.getRating());
        data.put("rating_count", engagementService.productReviewCount(product.getId()));
        data.put("store", storeRow(product.getStore()));
        data.put("pricing", pricingRow(pricing));
        data.put("stock", product.getStock() == null ? 0L : product.getStock());
        data.put("available", (product.getStock() == null ? 0L : product.getStock()) > 0);
        data.put("cover_image", cover);
        data.put("gallery_images", gallery.isEmpty() && cover != null ? List.of(cover) : gallery);
        data.put("video", video);
        data.put("hover_media", hoverMedia(gallery, video));
        data.put("categories", product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null && category.isEnabled())
                .map(category -> categoryFilterRow(category, locale))
                .toList());
        data.put("tags", product.getTags().stream()
                .filter(tag -> tag.getDeletedAt() == null && tag.isEnabled())
                .map(tag -> tagFilterRow(tag, locale))
                .toList());
        data.put("contents", publicContentService.contents("product", product.getId(), locale, storeSlug));
        data.put("has_variants", !variants.isEmpty());
        data.put("can_add_directly", variants.isEmpty());
        data.put("variant_attributes", variantAttributes(product, variants, locale));
        data.put("variant_combinations", variantCombinations(variants));
        data.put("variant_selection", variantSelection(product, variants, locale));
        data.put("variants", variants.stream().map(variant -> variantRow(variant, locale)).toList());
        data.put("variant_price_range", variantPriceRange(variants));
        data.put("related_items", relatedProducts(product, 4, locale, product.getStore()));
        return data;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> relatedProducts(String slug, Integer limit, Locale locale, String storeSlug) {
        Product product = scopedProducts(storeSlug).stream()
                .filter(item -> slug.equals(item.getSlug()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        return relatedProducts(product, limit, locale, product.getStore());
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
                    .orElseGet(() -> legacyMap(product.getCoverPhoto()));
            Map<String, Object> video = videosByProductId.get(product.getId());
            List<ProductVariant> variants = variantsByProductId.getOrDefault(product.getId(), List.of());
            StorefrontPricingService.Pricing pricing = storefrontPricingService.pricing(product);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.getId());
            row.put("slug", product.getSlug());
            row.put("name", translated(product.getName(), locale));
            row.put("short_description", translated(product.getShortDescription(), locale));
            row.put("cover_image", cover);
            row.put("hover_media", hoverMedia(gallery, video));
            row.put("pricing", pricingRow(pricing));
            row.put("has_variants", !variants.isEmpty());
            row.put("variant_price_range", variantPriceRange(variants));
            row.put("customizable", product.isCustomizable());
            row.put("stock", product.getStock() == null ? 0L : product.getStock());
            row.put("available", (product.getStock() == null ? 0L : product.getStock()) > 0);
            row.put("rating", product.getRating() == null ? 0d : product.getRating());
            row.put("rating_count", reviewCounts.getOrDefault(product.getId(), 0L));
            row.put("store", storeRow(product.getStore()));
            return row;
        }).toList();
    }

    private Map<String, Object> pricingRow(StorefrontPricingService.Pricing pricing) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("currency", "GBP");
        row.put("original_amount", pricing.original());
        row.put("final_amount", pricing.finalAmount());
        row.put("original", money(pricing.original()));
        row.put("final", money(pricing.finalAmount()));
        row.put("is_discounted", pricing.discounted());
        row.put("discount_type", pricing.discountType());
        row.put("discount_value", pricing.discountValue());
        return row;
    }

    private Map<String, Object> hoverMedia(List<Map<String, Object>> gallery, Map<String, Object> video) {
        if (video != null) {
            return video;
        }
        return gallery.stream()
                .filter(file -> "gallery_photo".equals(String.valueOf(file.get("type"))))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> variantAttributes(Product product, List<ProductVariant> variants, Locale locale) {
        Map<String, Set<UUID>> allowedOptionIdsByAttribute = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            Map<String, Object> map = resolvedVariantOptionMap(variant);
            if (!map.isEmpty()) {
                map.forEach((attributeId, optionValue) -> {
                    UUID optionId = uuid(optionValue);
                    if (optionId != null) {
                        allowedOptionIdsByAttribute.computeIfAbsent(attributeId, ignored -> new LinkedHashSet<>()).add(optionId);
                    }
                });
                continue;
            }
            for (Object item : variant.attributeOptionsAsList()) {
                UUID optionId = uuid(item);
                if (optionId == null) {
                    continue;
                }
                productAttributeOptionRepository.findById(optionId)
                        .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                        .ifPresent(option -> allowedOptionIdsByAttribute
                                .computeIfAbsent(option.getProductAttribute().getId().toString(), ignored -> new LinkedHashSet<>())
                                .add(optionId));
            }
        }

        if (allowedOptionIdsByAttribute.isEmpty() && product.getAvailableAttributeOptions() != null) {
            product.getAvailableAttributeOptions().forEach((attributeId, rawOptionIds) ->
                    allowedOptionIdsByAttribute.computeIfAbsent(attributeId, ignored -> new LinkedHashSet<>()).addAll(uuidSet(rawOptionIds)));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Set<UUID>> entry : allowedOptionIdsByAttribute.entrySet()) {
            UUID attributeId = uuid(entry.getKey());
            if (attributeId == null) {
                continue;
            }
            productAttributeRepository.findById(attributeId)
                    .filter(attribute -> attribute.getDeletedAt() == null && attribute.isEnabled())
                    .ifPresent(attribute -> rows.add(attributeDetailRow(attribute, entry.getValue(), locale)));
        }
        return rows.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("name"))))
                .toList();
    }

    private Map<String, Object> attributeDetailRow(ProductAttribute attribute, Set<UUID> allowedOptionIds, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", translated(attribute.getName(), locale));
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("options", productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId()).stream()
                .filter(option -> allowedOptionIds.isEmpty() || allowedOptionIds.contains(option.getId()))
                .map(option -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", option.getId());
                    item.put("name", translated(option.getName(), locale));
                    item.put("slug", option.getSlug());
                    item.put("value", option.getValue());
                    item.put("image", option.getImage());
                    return item;
                })
                .toList());
        return row;
    }

    private List<Map<String, Object>> variantCombinations(List<ProductVariant> variants) {
        return variants.stream().map(variant -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", variant.getId());
            row.put("slug", variant.getSlug());
            row.put("stock", variant.getStock());
            row.put("available", (variant.getStock() == null ? 0L : variant.getStock()) > 0);
            row.put("sku", variant.getSku());
            row.put("attribute_options", resolvedVariantOptionMap(variant));
            return row;
        }).toList();
    }

    private Map<String, Object> variantSelection(Product product, List<ProductVariant> variants, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        List<Map<String, Object>> attributes = variantAttributes(product, variants, locale);
        row.put("complete_selection_required", !variants.isEmpty());
        row.put("required_attribute_count", attributes.size());
        row.put("required_attribute_ids", attributes.stream().map(item -> item.get("id")).toList());
        row.put("attributes", attributes);
        row.put("available_combinations", variantCombinations(variants));
        row.put("option_availability", optionAvailability(attributes, variants));
        return row;
    }

    private List<Map<String, Object>> optionAvailability(List<Map<String, Object>> attributes, List<ProductVariant> variants) {
        return attributes.stream().map(attribute -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attribute_id", attribute.get("id"));
            row.put("attribute_slug", attribute.get("slug"));
            row.put("options", ((List<?>) attribute.get("options")).stream().map(rawOption -> {
                Map<?, ?> option = (Map<?, ?>) rawOption;
                UUID optionId = uuid(option.get("id"));
                Set<String> compatibleOptionIds = variants.stream()
                        .map(this::resolvedVariantOptionMap)
                        .filter(map -> optionId != null && map.values().stream().anyMatch(value -> optionId.toString().equals(String.valueOf(value))))
                        .flatMap(map -> map.values().stream())
                        .map(String::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", option.get("id"));
                item.put("available", !compatibleOptionIds.isEmpty());
                item.put("compatible_option_ids", new ArrayList<>(compatibleOptionIds));
                return item;
            }).toList());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> relatedProducts(Product product, Integer limit, Locale locale, Store store) {
        int safeLimit = limit == null || limit < 1 ? 4 : Math.min(limit, 12);
        Set<UUID> categoryIds = product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null && category.isEnabled())
                .map(ProductCategory::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return scopedProducts(store == null ? null : store.getSlug()).stream()
                .filter(candidate -> !candidate.getId().equals(product.getId()))
                .filter(candidate -> candidate.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                .sorted(Comparator
                        .comparing((Product candidate) -> sharedCategoryCount(candidate, categoryIds), Comparator.reverseOrder())
                        .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .collect(Collectors.collectingAndThen(Collectors.toList(), candidates -> productCards(candidates, locale)));
    }

    private long sharedCategoryCount(Product product, Set<UUID> categoryIds) {
        return product.getCategories().stream()
                .map(ProductCategory::getId)
                .filter(categoryIds::contains)
                .count();
    }

    private Map<String, Object> variantRow(ProductVariant variant, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("slug", variant.getSlug());
        row.put("description", translated(variant.getDescription(), locale));
        row.put("description_translations", variant.getDescription());
        row.put("price", variant.getPrice());
        row.put("stock", variant.getStock());
        row.put("available", (variant.getStock() == null ? 0L : variant.getStock()) > 0);
        row.put("sku", variant.getSku());
        row.put("attribute_options", resolvedVariantOptionMap(variant));
        return row;
    }

    private Map<String, Object> variantPriceRange(List<ProductVariant> variants) {
        List<BigDecimal> prices = variants.stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return null;
        }
        BigDecimal min = prices.get(0);
        BigDecimal max = prices.get(prices.size() - 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("min_amount", min);
        row.put("max_amount", max);
        row.put("min", money(min));
        row.put("max", money(max));
        row.put("is_range", min.compareTo(max) != 0);
        row.put("display", min.compareTo(max) == 0 ? money(min) : "GBP " + compactMoney(min) + "-" + compactMoney(max));
        return row;
    }

    private Map<String, Object> resolvedVariantOptionMap(ProductVariant variant) {
        Map<String, Object> map = variant.attributeOptionsAsMap();
        if (!map.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((attributeRef, optionRef) -> {
                ProductAttributeOption option = resolveAttributeOption(attributeRef, optionRef);
                if (option != null && option.getProductAttribute() != null) {
                    normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
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
            productAttributeOptionRepository.findById(optionId).ifPresent(option -> {
                if (option.getProductAttribute() != null) {
                    normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
                }
            });
        }
        return normalized;
    }

    private ProductAttributeOption resolveAttributeOption(Object attributeRef, Object optionRef) {
        String attributeKey = attributeRef == null ? null : String.valueOf(attributeRef);
        String optionKey = optionRef == null ? null : String.valueOf(optionRef);
        if (optionKey == null || optionKey.isBlank()) {
            return null;
        }

        UUID optionId = uuid(optionKey);
        if (optionId != null) {
            return productAttributeOptionRepository.findById(optionId)
                    .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                    .orElse(null);
        }

        ProductAttribute attribute = resolveAttribute(attributeKey);
        if (attribute != null) {
            return productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId()).stream()
                    .filter(option -> optionKey.equalsIgnoreCase(option.getSlug()) || optionKey.equalsIgnoreCase(option.getValue()))
                    .findFirst()
                    .orElse(null);
        }

        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .flatMap(candidate -> productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(candidate.getId()).stream())
                .filter(option -> optionKey.equalsIgnoreCase(option.getSlug()) || optionKey.equalsIgnoreCase(option.getValue()))
                .findFirst()
                .orElse(null);
    }

    private ProductAttribute resolveAttribute(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return null;
        }
        UUID attributeId = uuid(attributeKey);
        if (attributeId != null) {
            return productAttributeRepository.findById(attributeId)
                    .filter(attribute -> attribute.getDeletedAt() == null && attribute.isEnabled())
                    .orElse(null);
        }
        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(attribute -> attributeKey.equalsIgnoreCase(attribute.getSlug()))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesSearch(Product product, String search, Locale locale) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(product.getName(), locale).toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean matchesCategories(Product product, Set<UUID> categoryIds) {
        return categoryIds.isEmpty() || product.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId()));
    }

    private boolean matchesTags(Product product, Set<UUID> tagIds) {
        return tagIds.isEmpty() || product.getTags().stream().anyMatch(tag -> tagIds.contains(tag.getId()));
    }

    private boolean matchesAttributeOptions(Product product, Set<UUID> optionIds) {
        if (optionIds.isEmpty()) {
            return true;
        }
        Map<UUID, Set<UUID>> selectedByAttribute = optionIdsByAttribute(optionIds);
        if (selectedByAttribute.isEmpty()) {
            return true;
        }

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .filter(ProductVariant::isEnabled)
                .filter(variant -> (variant.getStock() == null ? 0L : variant.getStock()) > 0)
                .toList();
        if (!variants.isEmpty()) {
            return variants.stream().anyMatch(variant -> variantMatchesSelectedOptions(variant, selectedByAttribute));
        }

        Map<UUID, Set<UUID>> productOptions = optionIdsByAttribute(uuidSet(
                product.getAvailableAttributeOptions() == null ? List.of() : product.getAvailableAttributeOptions().values()
        ));
        return matchesAttributeSelection(productOptions, selectedByAttribute);
    }

    private boolean variantMatchesSelectedOptions(ProductVariant variant, Map<UUID, Set<UUID>> selectedByAttribute) {
        Map<UUID, Set<UUID>> variantOptions = optionIdsByAttribute(uuidSet(resolvedVariantOptionMap(variant).values()));
        if (variantOptions.isEmpty()) {
            variantOptions = optionIdsByAttribute(uuidSet(variant.attributeOptionsAsList()));
        }
        return matchesAttributeSelection(variantOptions, selectedByAttribute);
    }

    private boolean matchesAttributeSelection(Map<UUID, Set<UUID>> candidateOptions, Map<UUID, Set<UUID>> selectedByAttribute) {
        return selectedByAttribute.entrySet().stream().allMatch(entry -> {
            Set<UUID> candidate = candidateOptions.get(entry.getKey());
            return candidate != null && candidate.stream().anyMatch(entry.getValue()::contains);
        });
    }

    private Map<UUID, Set<UUID>> optionIdsByAttribute(Set<UUID> optionIds) {
        Map<UUID, Set<UUID>> grouped = new LinkedHashMap<>();
        for (UUID optionId : optionIds) {
            productAttributeOptionRepository.findById(optionId)
                    .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                    .filter(option -> option.getProductAttribute() != null && option.getProductAttribute().isShowInFilters())
                    .ifPresent(option -> grouped
                            .computeIfAbsent(option.getProductAttribute().getId(), ignored -> new LinkedHashSet<>())
                            .add(option.getId()));
        }
        return grouped;
    }

    private Comparator<Product> productComparator(String sort, Locale locale) {
        String safeSort = sort == null || sort.isBlank() ? "newest_added" : sort;
        return switch (safeSort) {
            case "price_low_to_high" -> Comparator.comparing((Product product) -> storefrontPricingService.pricing(product).finalAmount(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "price_high_to_low" -> Comparator.comparing((Product product) -> storefrontPricingService.pricing(product).finalAmount(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "top_rated" -> Comparator.comparing(Product::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "most_popular" -> Comparator.comparing(Product::getPurchaseCount, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "name_alphabetical" -> Comparator.comparing((Product product) -> translated(product.getName(), locale), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private Map<String, Object> categoryFilterRow(ProductCategory category, Locale locale) {
        return categoryFilterRow(category, locale, Set.of());
    }

    private Map<String, Object> categoryFilterRow(ProductCategory category, Locale locale, Set<UUID> visibleCategoryIds) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", translated(category.getName(), locale));
        row.put("slug", category.getSlug());
        row.put("description", translated(category.getDescription(), locale));
        row.put("parent_id", visibleParentId(category, visibleCategoryIds));
        row.put("menu_image", legacyMap(category.getMenuImage()));
        row.put("header_image", legacyMap(category.getHeaderImage()));
        row.put("image", category.getMenuImage() == null || category.getMenuImage().isEmpty() ? category.getHeaderImage() : category.getMenuImage());
        return row;
    }

    private Map<String, Object> categoryDetailRow(ProductCategory category, List<ProductCategory> categories, Locale locale, String storeSlug) {
        Set<UUID> visibleCategoryIds = categories.stream().map(ProductCategory::getId).collect(Collectors.toSet());
        Map<String, Object> row = new LinkedHashMap<>(categoryFilterRow(category, locale, visibleCategoryIds));
        row.put("contents", publicContentService.contents("category", category.getId(), locale, storeSlug));
        row.put("children", categoryTreeRows(categories, category.getId(), locale));
        return row;
    }

    private List<ProductCategory> enabledCategories() {
        return productCategoryRepository.findByDeletedAtIsNullAndEnabledTrue();
    }

    private List<Product> scopedProducts(String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(this::isEnabledStorefrontProduct)
                .filter(product -> matchesStore(product.getStore(), resolvedStore))
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

    private boolean matchesActiveStore(Store store, String storeSlug) {
        return matchesStore(store, storefrontStoreResolver.resolve(storeSlug));
    }

    private Map<String, Object> storeRow(Store store) {
        if (store == null) {
            return null;
        }
        return Map.of(
                "id", store.getId(),
                "name", storefrontStoreResolver.displayName(store),
                "slug", store.getSlug(),
                "rating_average", store.getRatingAverage(),
                "rating_count", store.getRatingCount()
        );
    }

    private List<Map<String, Object>> categoryTreeRows(List<ProductCategory> categories, UUID parentId, Locale locale) {
        Set<UUID> visibleCategoryIds = categories.stream().map(ProductCategory::getId).collect(Collectors.toSet());
        return categories.stream()
                .filter(category -> Objects.equals(visibleParentId(category, visibleCategoryIds), parentId))
                .sorted(Comparator.comparing(category -> translated(category.getName(), locale), String.CASE_INSENSITIVE_ORDER))
                .map(category -> {
                    Map<String, Object> row = new LinkedHashMap<>(categoryFilterRow(category, locale, visibleCategoryIds));
                    row.put("children", categoryTreeRows(categories, category.getId(), locale));
                    return row;
                })
                .toList();
    }

    private List<ProductCategory> storefrontVisibleCategories(String storeSlug) {
        Set<UUID> categoryIds = scopedProducts(storeSlug).stream()
                .filter(this::isEnabledStorefrontProduct)
                .flatMap(product -> product.getCategories().stream())
                .filter(category -> category.getDeletedAt() == null && category.isEnabled())
                .map(ProductCategory::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return enabledCategories().stream()
                .filter(category -> categoryIds.contains(category.getId()))
                .toList();
    }

    private boolean isEnabledStorefrontProduct(Product product) {
        return product.getStatus() != null && product.getStatus() == 1;
    }

    private UUID visibleParentId(ProductCategory category, Set<UUID> visibleCategoryIds) {
        if (category.getParent() == null) {
            return null;
        }
        UUID parentId = category.getParent().getId();
        return visibleCategoryIds.isEmpty() || visibleCategoryIds.contains(parentId) ? parentId : null;
    }

    private Set<UUID> expandCategoryFilter(Set<UUID> selectedIds, List<ProductCategory> categories) {
        if (selectedIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> expanded = new LinkedHashSet<>(selectedIds);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ProductCategory category : categories) {
                UUID parentId = category.getParent() == null ? null : category.getParent().getId();
                if (parentId != null && expanded.contains(parentId) && expanded.add(category.getId())) {
                    changed = true;
                }
            }
        }
        return expanded;
    }

    private Map<String, Object> tagFilterRow(ProductTag tag, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tag.getId());
        row.put("name", translated(tag.getName(), locale));
        row.put("slug", tag.getSlug());
        row.put("badge", tag.getBadge());
        return row;
    }

    private Map<String, Object> attributeFilterRow(ProductAttribute attribute, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", translated(attribute.getName(), locale));
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("options", productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId()).stream()
                .map(option -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", option.getId());
                    item.put("name", translated(option.getName(), locale));
                    item.put("slug", option.getSlug());
                    item.put("value", option.getValue());
                    item.put("image", option.getImage());
                    return item;
                })
                .toList());
        return row;
    }

    private String translated(Map<String, String> value, Locale locale) {
        return TranslatedText.resolve(value, locale == null ? Locale.ENGLISH : locale);
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private String compactMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.stripTrailingZeros().toPlainString();
    }

    private Set<UUID> csvUuids(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .map(this::uuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> uuidSet(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::uuid)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (raw instanceof Map<?, ?> map) {
            return map.values().stream()
                    .flatMap(value -> uuidSet(value).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        UUID parsed = uuid(raw);
        return parsed == null ? Set.of() : Set.of(parsed);
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> legacyMap(Map<String, Object> value) {
        return value == null || value.isEmpty() ? null : new LinkedHashMap<>(value);
    }
}
