package com.shoptopus.core.adminui;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.admin.AdminProductService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.files.repository.PaidFileContentRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiProductService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductTagRepository tagRepository;
    private final ProductVariantRepository variantRepository;
    private final PaidFileContentRepository paidFileContentRepository;
    private final AdminProductService adminProductService;
    private final ManagedFileService managedFileService;
    private final AdminStoreContext adminStoreContext;
    private final JdbcTemplate jdbc;

    public AdminUiProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductTagRepository tagRepository,
            ProductVariantRepository variantRepository,
            PaidFileContentRepository paidFileContentRepository,
            AdminProductService adminProductService,
            ManagedFileService managedFileService,
            AdminStoreContext adminStoreContext,
            JdbcTemplate jdbc
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.variantRepository = variantRepository;
        this.paidFileContentRepository = paidFileContentRepository;
        this.adminProductService = adminProductService;
        this.managedFileService = managedFileService;
        this.adminStoreContext = adminStoreContext;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String status, String categoryId, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<Product> filtered = productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(product -> matchesSearch(product, search))
                .filter(product -> matchesStatus(product, status))
                .filter(product -> matchesCategory(product, categoryId))
                .sorted(Comparator.comparing(Product::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search == null ? "" : search);
        filters.put("status", status == null ? "all" : status);
        filters.put("category_id", categoryId);
        filters.put("categories", availableCategories());
        filters.put("statuses", listStatuses());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", filtered.subList(fromIndex, toIndex).stream().map(this::listItem).toList());
        response.put("meta", meta);
        response.put("filters", filters);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        UUID productId;
        try {
            productId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found");
        }

        Product product = productRepository.findById(productId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        Map<UUID, ProductCategory> categoriesById = availableCategoryLookup();

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(product.getId());
        long paidContents = paidFileContentRepository.findByFileableTypeAndFileableId("product", product.getId()).size();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", product.getId());
        detail.put("name", product.getName());
        detail.put("name_display", translated(product.getName()));
        detail.put("slug", product.getSlug());
        detail.put("status", product.getStatus());
        detail.put("status_label", statusLabel(product.getStatus()));
        detail.put("price", money(product.getPrice()));
        detail.put("price_amount", product.getPrice());
        detail.put("stock", product.getStock());
        detail.put("virtual", product.isVirtual());
        detail.put("customizable", product.isCustomizable());
        detail.put("sku", product.getSku());
        detail.put("weight", product.getWeight());
        detail.put("short_description", product.getShortDescription());
        detail.put("description", product.getDescription());
        detail.put("customization_text", product.getCustomizationText());
        detail.put("headline", product.getHeadline());
        detail.put("subtitle", product.getSubtitle());
        detail.put("meta_tags", product.getMetaTags());
        detail.put("favourites", safeLong(product.getFavourites()));
        List<Map<String, Object>> galleryPhotos = managedFileService.filesFor("product", product.getId(), List.of("cover_photo", "gallery_photo"));
        Map<String, Object> coverPhoto = galleryPhotos.stream()
                .filter(item -> "cover_photo".equals(String.valueOf(item.get("type"))))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> legacy = product.getCoverPhoto();
                    return legacy == null || legacy.isEmpty() ? null : legacy;
                });
        detail.put("cover_photo", coverPhoto);
        detail.put("gallery_photos", galleryPhotos.isEmpty() && coverPhoto != null ? List.of(coverPhoto) : galleryPhotos);
        detail.put("product_video", managedFileService.findAttachedSingle("product", product.getId(), "gallery_video"));
        detail.put("files", managedFileService.filesFor("product", product.getId()));
        detail.put("created_at", product.getCreatedAt() == null ? null : product.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        detail.put("updated_at", product.getUpdatedAt() == null ? null : product.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        detail.put("categories", product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null)
                .sorted(Comparator.comparing(category -> translated(category.getName())))
                .map(category -> categoryOption(category, categoriesById))
                .toList());
        detail.put("tags", product.getTags().stream()
                .filter(tag -> tag.getDeletedAt() == null)
                .sorted(Comparator.comparing(tag -> translated(tag.getName())))
                .map(this::tagOption)
                .toList());
        detail.put("metrics", Map.of(
                "variants", variants.size(),
                "paid_contents", paidContents,
                "purchase_count", product.getPurchaseCount() == null ? 0L : product.getPurchaseCount(),
                "rating", product.getRating() == null ? 0.0d : product.getRating(),
                "favourites", safeLong(product.getFavourites())
        ));
        detail.put("favorited_by", favoriteUsers(product.getId()));
        detail.put("variants_preview", variants.stream().limit(5).map(this::variantPreview).toList());
        detail.put("form_options", formOptions());
        return detail;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> formOptions() {
        return Map.of(
                "categories", availableCategories(),
                "tags", availableTags(),
                "statuses", editStatuses(),
                "video_processing", Map.of(
                        "tool", "ffmpeg",
                        "duration_seconds", 15,
                        "audio_removed", true,
                        "format", "mp4"
                )
        );
    }

    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> created = adminProductService.create(body);
        Object id = created.get("id");
        scopedProduct(String.valueOf(id));
        return detail(String.valueOf(id));
    }

    public Map<String, Object> update(String id, Map<String, Object> body) {
        scopedProduct(id);
        adminProductService.update(id, body);
        scopedProduct(id);
        return detail(id);
    }

    public Map<String, Object> delete(String id) {
        scopedProduct(id);
        adminProductService.delete(id);
        return Map.of("id", id);
    }

    private Product scopedProduct(String id) {
        UUID productId;
        try {
            productId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found");
        }
        Product product = productRepository.findById(productId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        if (!adminStoreContext.multiStoreEnabled()) {
            return product;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available."));
        if (product.getStore() == null) {
            product.setStore(activeStore);
            return productRepository.save(product);
        }
        if (!activeStore.getId().equals(product.getStore().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found");
        }
        return product;
    }

    private boolean matchesActiveStore(Product product) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && product.getStore() != null && activeStore.getId().equals(product.getStore().getId());
    }

    private boolean matchesSearch(Product product, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(product.getName()).toLowerCase(Locale.ROOT).contains(term)
                || (product.getSlug() != null && product.getSlug().toLowerCase(Locale.ROOT).contains(term))
                || (product.getSku() != null && product.getSku().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(Product product, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "archived" -> product.getStatus() == 2;
            case "draft" -> product.getStatus() == 0;
            case "active" -> product.getStatus() != 2;
            default -> true;
        };
    }

    private boolean matchesCategory(Product product, String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return true;
        }
        return product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null)
                .anyMatch(category -> category.getId().toString().equals(categoryId));
    }

    private Map<String, Object> listItem(Product product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("name", translated(product.getName()));
        row.put("slug", product.getSlug());
        row.put("status", product.getStatus());
        row.put("status_label", statusLabel(product.getStatus()));
        row.put("price", money(product.getPrice()));
        row.put("stock", product.getStock());
        row.put("sku", product.getSku());
        row.put("virtual", product.isVirtual());
        row.put("customizable", product.isCustomizable());
        row.put("rating", product.getRating() == null ? 0.0d : product.getRating());
        row.put("favourites", safeLong(product.getFavourites()));
        row.put("meta_tags", product.getMetaTags());
        row.put("categories", product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null)
                .map(category -> translated(category.getName()))
                .sorted()
                .toList());
        row.put("tags", product.getTags().stream()
                .filter(tag -> tag.getDeletedAt() == null)
                .map(tag -> translated(tag.getName()))
                .sorted()
                .toList());
        row.put("created_at", product.getCreatedAt() == null ? null : product.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return row;
    }

    private Map<String, Object> categoryOption(ProductCategory category, Map<UUID, ProductCategory> categoriesById) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", translated(category.getName()));
        row.put("slug", category.getSlug());
        UUID parentId = category.getParent() == null ? null : category.getParent().getId();
        ProductCategory parent = parentId == null ? null : categoriesById.get(parentId);
        row.put("parent", parent == null ? null : Map.of(
                "id", parent.getId(),
                "name", translated(parent.getName()),
                "slug", parent.getSlug()
        ));
        return row;
    }

    private Map<String, Object> tagOption(ProductTag tag) {
        return Map.of(
                "id", tag.getId(),
                "name", translated(tag.getName()),
                "slug", tag.getSlug()
        );
    }

    private Map<String, Object> variantPreview(ProductVariant variant) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("price", money(variant.getPrice()));
        row.put("stock", variant.getStock());
        row.put("sku", variant.getSku());
        row.put("description", translated(variant.getDescription()));
        return row;
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case 2 -> "Archived";
            case 0 -> "Draft";
            default -> "Active";
        };
    }

    private String money(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private List<Map<String, Object>> favoriteUsers(UUID productId) {
        return jdbc.query("""
                select u.id, u.name, u.email
                from favorited_products fp
                join users u on u.id = fp.user_id
                where fp.product_id = ?::uuid
                  and u.deleted_at is null
                order by lower(coalesce(u.name, u.email)), lower(u.email)
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("name", rs.getString("name"));
            row.put("email", rs.getString("email"));
            return row;
        }, productId.toString());
    }

    private List<Map<String, Object>> availableCategories() {
        Map<UUID, ProductCategory> categoriesById = availableCategoryLookup();
        return categoriesById.values().stream()
                .sorted(Comparator.comparing(category -> translated(category.getName())))
                .map(category -> categoryOption(category, categoriesById))
                .toList();
    }

    private Map<UUID, ProductCategory> availableCategoryLookup() {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toMap(
                        ProductCategory::getId,
                        category -> category,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<Map<String, Object>> availableTags() {
        return tagRepository.findAll().stream()
                .filter(tag -> tag.getDeletedAt() == null)
                .sorted(Comparator.comparing(tag -> translated(tag.getName())))
                .map(this::tagOption)
                .toList();
    }

    private List<Map<String, String>> listStatuses() {
        return List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "active", "label", "Active"),
                Map.of("value", "archived", "label", "Archived"),
                Map.of("value", "draft", "label", "Draft")
        );
    }

    private List<Map<String, Object>> editStatuses() {
        return List.of(
                Map.of("value", 0, "label", "Draft"),
                Map.of("value", 1, "label", "Active"),
                Map.of("value", 2, "label", "Archived")
        );
    }
}
