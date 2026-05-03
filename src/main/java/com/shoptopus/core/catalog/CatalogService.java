package com.shoptopus.core.catalog;

import com.shoptopus.core.catalog.dto.*;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.storefront.StorefrontStoreResolver;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.text.Normalizer;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductTagRepository tagRepository;
    private final CatalogMapper catalogMapper;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public CatalogService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductTagRepository tagRepository,
            CatalogMapper catalogMapper,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.catalogMapper = catalogMapper;
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(ProductFilterRequest filter, Locale locale) {
        return getProducts(filter, locale, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(ProductFilterRequest filter, Locale locale, String storeSlug) {
        int page = filter.page() == null || filter.page() < 0 ? 0 : filter.page();
        int size = filter.size() == null || filter.size() <= 0 ? 20 : Math.min(filter.size(), 100);
        List<Product> filtered = filteredProducts(filter, locale, storeSlug);
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return filtered.subList(fromIndex, toIndex)
                .stream()
                .map(p -> catalogMapper.toProductResponse(p, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countProducts(ProductFilterRequest filter, Locale locale) {
        return countProducts(filter, locale, null);
    }

    @Transactional(readOnly = true)
    public long countProducts(ProductFilterRequest filter, Locale locale, String storeSlug) {
        return filteredProducts(filter, locale, storeSlug).size();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id, Locale locale) {
        return getProduct(id, locale, null);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id, Locale locale, String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        var p = productRepository.findById(id)
                .filter(it -> it.getDeletedAt() == null)
                .filter(this::isPubliclyVisible)
                .filter(it -> matchesStore(it.getStore(), resolvedStore))
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return catalogMapper.toProductResponse(p, locale);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug, Locale locale) {
        var p = productRepository.findBySlugAndDeletedAtIsNull(slug)
                .filter(this::isPubliclyVisible)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return catalogMapper.toProductResponse(p, locale);
    }

    @Transactional("mainTransactionManager")
    public ProductResponse createProduct(ProductUpsertRequest request, Locale locale) {
        var p = new Product();
        applyUpsert(p, request);
        productRepository.save(p);
        return catalogMapper.toProductResponse(p, locale);
    }

    @Transactional("mainTransactionManager")
    public ProductResponse updateProduct(UUID id, ProductUpsertRequest request, Locale locale) {
        var p = productRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Product not found"));
        applyUpsert(p, request);
        productRepository.save(p);
        return catalogMapper.toProductResponse(p, locale);
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getCategories(Locale locale) {
        return categoryRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .map(c -> catalogMapper.toCategoryResponse(c, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategory(UUID id, Locale locale) {
        var c = categoryRepository.findById(id)
                .filter(it -> it.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        return catalogMapper.toCategoryResponse(c, locale);
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategoryBySlug(String slug, Locale locale) {
        var c = categoryRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        return catalogMapper.toCategoryResponse(c, locale);
    }

    @Transactional(readOnly = true)
    public List<ProductTagResponse> getTags(Locale locale) {
        return tagRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .map(t -> catalogMapper.toTagResponse(t, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductTagResponse getTag(UUID id, Locale locale) {
        var t = tagRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Tag not found"));
        return catalogMapper.toTagResponse(t, locale);
    }

    private void applyUpsert(Product p, ProductUpsertRequest request) {
        p.setName(catalogMapper.toTranslationMap(request.name()));
        p.setSlug(request.slug());
        p.setShortDescription(catalogMapper.toTranslationMap(request.shortDescription()));
        p.setDescription(catalogMapper.toTranslationMap(request.description()));
        p.setPrice(request.price());
        if (request.headline() != null) {
            p.setHeadline(catalogMapper.toTranslationMap(request.headline()));
        }
        if (request.subtitle() != null) {
            p.setSubtitle(catalogMapper.toTranslationMap(request.subtitle()));
        }
        p.setStock(request.stock() == null ? 0 : request.stock());
        p.setSku(request.sku());

        p.getCategories().clear();
        if (request.categoryIds() != null) {
            request.categoryIds().forEach(categoryId -> {
                var category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid category", "Category not found: " + categoryId));
                p.getCategories().add(category);
            });
        }

        p.getTags().clear();
        if (request.tagIds() != null) {
            request.tagIds().forEach(tagId -> {
                var tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid tag", "Tag not found: " + tagId));
                p.getTags().add(tag);
            });
        }
    }

    private List<Product> filteredProducts(ProductFilterRequest filter, Locale locale, String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        String search = filter.search() == null ? null : filter.search().trim().toLowerCase(Locale.ROOT);
        UUID categoryId = parseUuid(filter.categoryId());
        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(this::isPubliclyVisible)
                .filter(product -> matchesStore(product.getStore(), resolvedStore))
                .filter(product -> search == null || search.isBlank() || matchesSearch(
                        TranslatedText.resolve(product.getName(), locale == null ? Locale.ENGLISH : locale),
                        search
                ))
                .filter(product -> categoryId == null || product.getCategories().stream().anyMatch(category -> categoryId.equals(category.getId())))
                .filter(product -> filter.minPrice() == null || product.getPrice() != null && product.getPrice().doubleValue() >= filter.minPrice())
                .filter(product -> filter.maxPrice() == null || product.getPrice() != null && product.getPrice().doubleValue() <= filter.maxPrice())
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean matchesSearch(String candidate, String search) {
        String normalizedCandidate = normalize(candidate);
        String normalizedSearch = normalize(search);
        if (normalizedSearch.isBlank()) {
            return true;
        }
        if (normalizedCandidate.contains(normalizedSearch)) {
            return true;
        }
        for (String token : normalizedSearch.split("\\s+")) {
            if (!token.isBlank() && normalizedCandidate.contains(token)) {
                return true;
            }
        }
        return levenshtein(normalizedCandidate, normalizedSearch) <= 2
                || normalizedCandidate.split("\\s+").length > 1
                && java.util.Arrays.stream(normalizedCandidate.split("\\s+"))
                .anyMatch(token -> !token.isBlank() && levenshtein(token, normalizedSearch) <= 2);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private int levenshtein(String left, String right) {
        if (left.equals(right)) {
            return 0;
        }
        if (left.isEmpty()) {
            return right.length();
        }
        if (right.isEmpty()) {
            return left.length();
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private boolean isPubliclyVisible(Product product) {
        Integer status = product.getStatus();
        return status == null || status == 0 || status == 1;
    }

    private boolean matchesStore(Store store, StorefrontStoreResolver.ResolvedStore resolvedStore) {
        if (!resolvedStore.multiStoreEnabled()) {
            return true;
        }
        if (resolvedStore.superstore()) {
            return store != null && store.getStatus() == StoreStatus.ACTIVE;
        }
        return store != null
                && resolvedStore.store() != null
                && resolvedStore.store().getId().equals(store.getId())
                && store.getStatus() == StoreStatus.ACTIVE;
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
