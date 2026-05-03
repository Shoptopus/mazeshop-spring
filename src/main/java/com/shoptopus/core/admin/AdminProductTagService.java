package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
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
public class AdminProductTagService {

    private final ProductTagRepository tagRepository;

    public AdminProductTagService(ProductTagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = tagRepository.findAll().stream()
                .filter(tag -> tag.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("product-tags", data);
    }

    public Map<String, Object> show(String id) {
        UUID tagId = uuid(id).orElse(null);
        if (tagId == null) {
            return Map.of("id", id);
        }
        return tagRepository.findById(tagId)
                .filter(tag -> tag.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", tagId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        ProductTag tag = new ProductTag();
        tag.setId(UUID.randomUUID());
        tag.setName(requiredTranslations(body, "name"));
        tag.setDescription(translations(body, "description", Map.of("en", "")));
        tag.setSlug(str(body, "slug", "product-tag-" + tag.getId()));
        tag.setEnabled(bool(body, "enabled", true));
        tag.setDisplayBadge(bool(body, "display_badge", false));
        if (body != null && body.get("badge") instanceof Map<?, ?> badge) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            badge.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            tag.setBadge(normalized);
        }
        tagRepository.save(tag);
        return Map.of("id", tag.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID tagId = uuid(id).orElse(null);
        if (tagId == null) {
            return Map.of("id", id);
        }
        Optional<ProductTag> tag = tagRepository.findById(tagId).filter(item -> item.getDeletedAt() == null);
        if (tag.isEmpty()) {
            return Map.of("id", tagId);
        }
        ProductTag entity = tag.orElseThrow();
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
        if (body != null && body.containsKey("display_badge")) {
            entity.setDisplayBadge(bool(body, "display_badge", entity.isDisplayBadge()));
        }
        if (body != null && body.containsKey("badge") && body.get("badge") instanceof Map<?, ?> badge) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            badge.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            entity.setBadge(normalized);
        }
        tagRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID tagId = uuid(id).orElse(null);
        if (tagId == null) {
            return Map.of("id", id);
        }
        tagRepository.findById(tagId).filter(item -> item.getDeletedAt() == null).ifPresent(tag -> {
            tag.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            tagRepository.save(tag);
        });
        return Map.of("id", tagId);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkAvailability(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        boolean enabled = bool(body, "enabled", bool(body, "status", true));
        for (UUID id : ids) {
            tagRepository.findById(id).filter(tag -> tag.getDeletedAt() == null).ifPresent(tag -> {
                tag.setEnabled(enabled);
                tagRepository.save(tag);
            });
        }
        return Map.of("ids", ids);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        for (UUID id : ids) {
            tagRepository.findById(id).filter(tag -> tag.getDeletedAt() == null).ifPresent(tag -> {
                tag.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                tagRepository.save(tag);
            });
        }
        return Map.of("ids", ids);
    }

    private Map<String, Object> toDetail(ProductTag tag) {
        return toRow(tag);
    }

    private Map<String, Object> toRow(ProductTag tag) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tag.getId());
        row.put("name", tag.getName());
        row.put("slug", tag.getSlug());
        row.put("description", tag.getDescription());
        row.put("badge", tag.getBadge());
        row.put("display_badge", tag.isDisplayBadge());
        row.put("enabled", tag.isEnabled());
        return row;
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
            try {
                ids.add(UUID.fromString(String.valueOf(values.get(i))));
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
