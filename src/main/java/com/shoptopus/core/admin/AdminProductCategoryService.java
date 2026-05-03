package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminProductCategoryService {

    private final ProductCategoryRepository categoryRepository;

    public AdminProductCategoryService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("product-categories", data);
    }

    public Map<String, Object> show(String id) {
        UUID categoryId = uuid(id).orElse(null);
        if (categoryId == null) {
            return Map.of("id", id);
        }
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", categoryId));
    }

    public Map<String, Object> selectData() {
        List<Map<String, Object>> data = categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .map(category -> Map.<String, Object>of(
                        "id", category.getId(),
                        "name", category.getName(),
                        "slug", category.getSlug()
                ))
                .toList();
        return Map.of("data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setName(requiredTranslations(body, "name"));
        category.setDescription(translations(body, "description", Map.of("en", "")));
        category.setSlug(str(body, "slug", "product-category-" + category.getId()));
        category.setEnabled(bool(body, "enabled", true));
        category.setParent(resolveParent(body));
        category.setMenuImage(media(body, "menu_image"));
        category.setHeaderImage(media(body, "header_image"));
        categoryRepository.save(category);
        return Map.of("id", category.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID categoryId = uuid(id).orElse(null);
        if (categoryId == null) {
            return Map.of("id", id);
        }
        Optional<ProductCategory> category = categoryRepository.findById(categoryId).filter(item -> item.getDeletedAt() == null);
        if (category.isEmpty()) {
            return Map.of("id", categoryId);
        }
        ProductCategory entity = category.orElseThrow();
        if (body != null && body.containsKey("name")) {
            entity.setName(requiredTranslations(body, "name"));
        }
        if (body != null && body.containsKey("description")) {
            entity.setDescription(translations(body, "description", entity.getDescription()));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.isEnabled()));
        }
        if (body != null && body.containsKey("parent_id")) {
            entity.setParent(resolveParent(body));
        }
        if (body != null && body.containsKey("menu_image")) {
            entity.setMenuImage(media(body, "menu_image"));
        }
        if (body != null && body.containsKey("header_image")) {
            entity.setHeaderImage(media(body, "header_image"));
        }
        categoryRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID categoryId = uuid(id).orElse(null);
        if (categoryId == null) {
            return Map.of("id", id);
        }
        categoryRepository.findById(categoryId).filter(item -> item.getDeletedAt() == null).ifPresent(category -> {
            category.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            categoryRepository.save(category);
        });
        return Map.of("id", categoryId);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkAvailability(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        boolean enabled = bool(body, "enabled", bool(body, "status", true));
        for (UUID id : ids) {
            categoryRepository.findById(id).filter(category -> category.getDeletedAt() == null).ifPresent(category -> {
                category.setEnabled(enabled);
                categoryRepository.save(category);
            });
        }
        return Map.of("ids", ids);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        for (UUID id : ids) {
            categoryRepository.findById(id).filter(category -> category.getDeletedAt() == null).ifPresent(category -> {
                category.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                categoryRepository.save(category);
            });
        }
        return Map.of("ids", ids);
    }

    private ProductCategory resolveParent(Map<String, Object> body) {
        UUID parentId = uuid(str(body, "parent_id", null)).orElse(null);
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .filter(category -> category.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected parent_id is invalid."));
    }

    private Map<String, Object> toDetail(ProductCategory category) {
        Map<String, Object> row = toRow(category);
        row.put("description", category.getDescription());
        return row;
    }

    private Map<String, Object> toRow(ProductCategory category) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", category.getName());
        row.put("slug", category.getSlug());
        row.put("enabled", category.isEnabled());
        row.put("description", category.getDescription());
        row.put("parent_id", category.getParent() == null ? null : category.getParent().getId());
        row.put("menu_image", category.getMenuImage());
        row.put("header_image", category.getHeaderImage());
        return row;
    }

    private Map<String, Object> media(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return new LinkedHashMap<>();
        }
        if (body.get(key) instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> normalized.put(String.valueOf(entryKey), entryValue));
            return normalized;
        }
        return new LinkedHashMap<>();
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

    private List<UUID> ids(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            try {
                ids.add(UUID.fromString(String.valueOf(value)));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + i + " is invalid.");
            }
        }
        return ids;
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
