package com.shoptopus.core.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.ClearManagedFiles;
import com.shoptopus.core.files.ManagedFileService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductTagRepository tagRepository;
    private final ObjectMapper objectMapper;
    private final ManagedFileService managedFileService;

    public AdminProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductTagRepository tagRepository,
            ObjectMapper objectMapper,
            ManagedFileService managedFileService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.objectMapper = objectMapper;
        this.managedFileService = managedFileService;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("products", data);
    }

    public Map<String, Object> summary() {
        long total = productRepository.findAll().stream().filter(product -> product.getDeletedAt() == null).count();
        long archived = productRepository.findAll().stream().filter(product -> product.getDeletedAt() == null && product.getStatus() == 2).count();
        long active = productRepository.findAll().stream().filter(product -> product.getDeletedAt() == null && product.getStatus() != 2).count();
        return Map.of("summary", Map.of(
                "total", total,
                "active", active,
                "archived", archived
        ));
    }

    public Map<String, Object> show(String id) {
        UUID productId = uuid(id).orElse(null);
        if (productId == null) {
            return Map.of("id", id);
        }
        return productRepository.findById(productId)
                .filter(product -> product.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", productId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(requiredTranslations(body, "name"));
        product.setSlug(str(body, "slug", "product-" + product.getId()));
        product.setShortDescription(translations(body, "short_description", Map.of("en", "")));
        product.setDescription(translations(body, "description", Map.of("en", "")));
        product.setCustomizable(bool(body, "customizable", false));
        product.setCustomizationText(translations(body, "customization_text", Map.of("en", "")));
        product.setPrice(decimal(body, "price", BigDecimal.ZERO));
        product.setStock(longVal(body, "stock", 0L));
        product.setVirtual(bool(body, "virtual", false));
        product.setStatus(intVal(body, "status", 0));
        product.setHeadline(translations(body, "headline", product.getHeadline()));
        product.setSubtitle(translations(body, "subtitle", product.getSubtitle()));
        product.setSku(str(body, "sku", null));
        product.setWeight(body != null && body.containsKey("weight") ? intVal(body, "weight", null) : null);
        product.setCategories(resolveCategories(body));
        product.setTags(resolveTags(body));
        product.setMetaTags(stringList(body, "meta_tags", List.of()));
        productRepository.saveAndFlush(product);

        Product persisted = productRepository.findById(product.getId()).orElseThrow();
        applyManagedMedia(persisted, body);
        productRepository.save(persisted);
        return Map.of("id", product.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID productId = uuid(id).orElse(null);
        if (productId == null) {
            return Map.of("id", id);
        }
        Optional<Product> product = productRepository.findById(productId).filter(item -> item.getDeletedAt() == null);
        if (product.isEmpty()) {
            return Map.of("id", productId);
        }
        Product entity = product.orElseThrow();
        if (body != null && body.containsKey("name")) {
            entity.setName(requiredTranslations(body, "name"));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("short_description")) {
            entity.setShortDescription(translations(body, "short_description", entity.getShortDescription()));
        }
        if (body != null && body.containsKey("description")) {
            entity.setDescription(translations(body, "description", entity.getDescription()));
        }
        if (body != null && body.containsKey("customizable")) {
            entity.setCustomizable(bool(body, "customizable", entity.isCustomizable()));
        }
        if (body != null && body.containsKey("customization_text")) {
            entity.setCustomizationText(translations(body, "customization_text", entity.getCustomizationText()));
        }
        if (body != null && body.containsKey("price")) {
            entity.setPrice(decimal(body, "price", entity.getPrice()));
        }
        if (body != null && body.containsKey("stock")) {
            entity.setStock(longVal(body, "stock", entity.getStock()));
        }
        if (body != null && body.containsKey("virtual")) {
            entity.setVirtual(bool(body, "virtual", entity.isVirtual()));
        }
        if (body != null && body.containsKey("status")) {
            entity.setStatus(intVal(body, "status", entity.getStatus()));
        }
        if (body != null && body.containsKey("headline")) {
            entity.setHeadline(translations(body, "headline", entity.getHeadline()));
        }
        if (body != null && body.containsKey("subtitle")) {
            entity.setSubtitle(translations(body, "subtitle", entity.getSubtitle()));
        }
        if (body != null && body.containsKey("sku")) {
            entity.setSku(str(body, "sku", entity.getSku()));
        }
        if (body != null && body.containsKey("weight")) {
            entity.setWeight(intVal(body, "weight", entity.getWeight()));
        }
        if (body != null && body.containsKey("category_ids")) {
            entity.setCategories(resolveCategories(body));
        }
        if (body != null && body.containsKey("tag_ids")) {
            entity.setTags(resolveTags(body));
        }
        if (body != null && body.containsKey("meta_tags")) {
            entity.setMetaTags(stringList(body, "meta_tags", entity.getMetaTags()));
        }
        applyManagedMedia(entity, body);
        productRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    @ClearManagedFiles(fileableType = "product")
    public Map<String, Object> delete(String id) {
        UUID productId = uuid(id).orElse(null);
        if (productId == null) {
            return Map.of("id", id);
        }
        productRepository.findById(productId).filter(item -> item.getDeletedAt() == null).ifPresent(product -> {
            product.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            productRepository.save(product);
        });
        return Map.of("id", productId);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkArchive(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        for (UUID id : ids) {
            productRepository.findById(id).filter(product -> product.getDeletedAt() == null).ifPresent(product -> {
                product.setStatus(2);
                productRepository.save(product);
            });
        }
        return Map.of("ids", ids);
    }

    @Transactional("mainTransactionManager")
    @ClearManagedFiles(fileableType = "product")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        for (UUID id : ids) {
            productRepository.findById(id).filter(product -> product.getDeletedAt() == null).ifPresent(product -> {
                product.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                productRepository.save(product);
            });
        }
        return Map.of("ids", ids);
    }

    private Map<String, Object> toDetail(Product product) {
        Map<String, Object> row = toRow(product);
        row.put("short_description", product.getShortDescription());
        row.put("description", product.getDescription());
        row.put("customizable", product.isCustomizable());
        row.put("customization_text", product.getCustomizationText());
        row.put("headline", product.getHeadline());
        row.put("subtitle", product.getSubtitle());
        row.put("meta_tags", product.getMetaTags());
        row.put("virtual", product.isVirtual());
        row.put("sku", product.getSku());
        row.put("weight", product.getWeight());
        row.put("cover_photo", product.getCoverPhoto());
        row.put("gallery_photos", managedFileService.filesFor("product", product.getId(), List.of("cover_photo", "gallery_photo")));
        row.put("product_video", managedFileService.findAttachedSingle("product", product.getId(), "gallery_video"));
        row.put("categories", product.getCategories().stream().filter(category -> category.getDeletedAt() == null).map(this::categoryRow).toList());
        row.put("tags", product.getTags().stream().filter(tag -> tag.getDeletedAt() == null).map(this::tagRow).toList());
        return row;
    }

    private Map<String, Object> toRow(Product product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("name", product.getName());
        row.put("slug", product.getSlug());
        row.put("price", product.getPrice());
        row.put("final_price", product.getPrice());
        row.put("status", product.getStatus());
        row.put("stock", product.getStock());
        row.put("virtual", product.isVirtual());
        row.put("customizable", product.isCustomizable());
        row.put("meta_tags", product.getMetaTags());
        row.put("categories", product.getCategories().stream().filter(category -> category.getDeletedAt() == null).map(this::categoryRow).toList());
        row.put("tags", product.getTags().stream().filter(tag -> tag.getDeletedAt() == null).map(this::tagRow).toList());
        return row;
    }

    private Map<String, Object> categoryRow(ProductCategory category) {
        return Map.of(
                "id", category.getId(),
                "name", category.getName(),
                "slug", category.getSlug()
        );
    }

    private Map<String, Object> tagRow(ProductTag tag) {
        return Map.of(
                "id", tag.getId(),
                "name", tag.getName(),
                "slug", tag.getSlug()
        );
    }

    private void applyManagedMedia(Product product, Map<String, Object> body) {
        if (body == null) {
            return;
        }

        if (body.containsKey("gallery_photos") || body.containsKey("cover_photo") || body.containsKey("cover_photo_id")) {
            List<Map<String, Object>> gallery = normalizedFileList(body.get("gallery_photos"));
            if (gallery.isEmpty() && body.get("cover_photo") instanceof Map<?, ?> coverMap) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                coverMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                gallery = List.of(normalized);
            }

            UUID selectedCoverId = uuid(body.get("cover_photo_id"));
            if (selectedCoverId == null && body.get("cover_photo") instanceof Map<?, ?> coverMap) {
                selectedCoverId = uuid(coverMap.get("id"));
            }

            List<Map<String, Object>> savedGallery = managedFileService.syncOrderedSelection(
                    "product",
                    product.getId(),
                    "gallery_photo",
                    "cover_photo",
                    gallery,
                    selectedCoverId
            );
            product.setCoverPhoto(savedGallery.stream()
                    .filter(item -> "cover_photo".equals(String.valueOf(item.get("type"))))
                    .findFirst()
                    .orElse(null));
        }

        if (body.containsKey("product_video")) {
            Object rawVideo = body.get("product_video");
            Map<String, Object> selectedVideo = normalizedFile(rawVideo, "product_video");
            managedFileService.attachSingle("product", product.getId(), "gallery_video", selectedVideo);
        }
    }

    private Map<String, Object> normalizedFile(Object rawFile, String fieldName) {
        if (rawFile == null) {
            return null;
        }
        if (rawFile instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + fieldName + " field is invalid.");
    }

    private List<Map<String, Object>> normalizedFileList(Object rawFiles) {
        if (rawFiles == null) {
            return List.of();
        }
        if (!(rawFiles instanceof List<?> items)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The gallery_photos field is invalid.");
        }
        return items.stream()
                .map(item -> normalizedFile(item, "gallery_photos"))
                .toList();
    }

    private Set<ProductCategory> resolveCategories(Map<String, Object> body) {
        if (body == null || !body.containsKey("category_ids") || body.get("category_ids") == null) {
            return new LinkedHashSet<>();
        }
        return resolveIds(body.get("category_ids")).stream()
                .map(id -> categoryRepository.findById(id)
                        .filter(category -> category.getDeletedAt() == null)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected category_ids is invalid.")))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<ProductTag> resolveTags(Map<String, Object> body) {
        if (body == null || !body.containsKey("tag_ids") || body.get("tag_ids") == null) {
            return new LinkedHashSet<>();
        }
        return resolveIds(body.get("tag_ids")).stream()
                .map(id -> tagRepository.findById(id)
                        .filter(tag -> tag.getDeletedAt() == null)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected tag_ids is invalid.")))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<UUID> ids(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        return parseIds(values, "ids");
    }

    private List<UUID> resolveIds(Object raw) {
        if (!(raw instanceof List<?> values)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids is invalid.");
        }
        return parseIds(values, "ids");
    }

    private List<UUID> parseIds(List<?> values, String fieldName) {
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            try {
                ids.add(UUID.fromString(String.valueOf(values.get(i))));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected " + fieldName + "." + i + " is invalid.");
            }
        }
        return ids;
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

    private Map<String, String> requiredTranslations(Map<String, Object> body, String key) {
        Map<String, String> value = translations(body, key, null);
        if (value == null || value.values().stream().noneMatch(item -> item != null && !item.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return value;
    }

    private Map<String, String> translations(Map<String, Object> body, String key, Map<String, String> fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue == null ? null : String.valueOf(entryValue)));
            return result;
        }
        String value = String.valueOf(raw);
        if (value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(trimmed, new TypeReference<>() {});
                Map<String, String> result = new LinkedHashMap<>();
                parsed.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue == null ? null : String.valueOf(entryValue)));
                return result;
            } catch (Exception ignored) {
                // Fall back to English text handling when the string is not valid JSON.
            }
        }
        return Map.of("en", value);
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

    private Integer intVal(Map<String, Object> body, String key, Integer fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
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

    private List<String> stringList(Map<String, Object> body, String key, List<String> fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (!(raw instanceof List<?> values)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String normalized = String.valueOf(value).trim();
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, Object> listResponse(String message, List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
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
