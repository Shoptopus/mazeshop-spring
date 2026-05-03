package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
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
public class AdminProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeOptionRepository optionRepository;

    public AdminProductAttributeService(ProductAttributeRepository attributeRepository, ProductAttributeOptionRepository optionRepository) {
        this.attributeRepository = attributeRepository;
        this.optionRepository = optionRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = attributeRepository.findAll().stream()
                .filter(attribute -> attribute.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("product-attributes", data);
    }

    public Map<String, Object> show(String id) {
        UUID attributeId = uuid(id).orElse(null);
        if (attributeId == null) {
            return Map.of("id", id);
        }
        return attributeRepository.findById(attributeId)
                .filter(attribute -> attribute.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", attributeId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        ProductAttribute attribute = new ProductAttribute();
        attribute.setId(UUID.randomUUID());
        attribute.setName(requiredTranslations(body, "name"));
        attribute.setSlug(str(body, "slug", "product-attribute-" + attribute.getId()));
        attribute.setType(intVal(body, "type", 0));
        attribute.setEnabled(bool(body, "enabled", true));
        attribute.setShowInFilters(bool(body, "show_in_filters", true));
        if (body != null && body.get("image") instanceof Map<?, ?> image) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            image.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            attribute.setImage(normalized);
        }
        attributeRepository.save(attribute);
        return Map.of("id", attribute.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID attributeId = uuid(id).orElse(null);
        if (attributeId == null) {
            return Map.of("id", id);
        }
        Optional<ProductAttribute> attribute = attributeRepository.findById(attributeId).filter(item -> item.getDeletedAt() == null);
        if (attribute.isEmpty()) {
            return Map.of("id", attributeId);
        }
        ProductAttribute entity = attribute.orElseThrow();
        if (body != null && body.containsKey("name")) {
            entity.setName(requiredTranslations(body, "name"));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("type")) {
            entity.setType(intVal(body, "type", entity.getType()));
        }
        if (body != null && body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.isEnabled()));
        }
        if (body != null && body.containsKey("show_in_filters")) {
            entity.setShowInFilters(bool(body, "show_in_filters", entity.isShowInFilters()));
        }
        if (body != null && body.containsKey("image") && body.get("image") instanceof Map<?, ?> image) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            image.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            entity.setImage(normalized);
        }
        attributeRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID attributeId = uuid(id).orElse(null);
        if (attributeId == null) {
            return Map.of("id", id);
        }
        attributeRepository.findById(attributeId).filter(item -> item.getDeletedAt() == null).ifPresent(attribute -> {
            attribute.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            attributeRepository.save(attribute);
        });
        return Map.of("id", attributeId);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkAvailability(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        boolean enabled = bool(body, "enabled", bool(body, "status", true));
        for (UUID id : ids) {
            attributeRepository.findById(id).filter(attribute -> attribute.getDeletedAt() == null).ifPresent(attribute -> {
                attribute.setEnabled(enabled);
                attributeRepository.save(attribute);
            });
        }
        return Map.of("ids", ids);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<UUID> ids = ids(body);
        for (UUID id : ids) {
            attributeRepository.findById(id).filter(attribute -> attribute.getDeletedAt() == null).ifPresent(attribute -> {
                attribute.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                attributeRepository.save(attribute);
            });
        }
        return Map.of("ids", ids);
    }

    public Map<String, Object> listOptions(String attributeId) {
        ProductAttribute attribute = requireAttribute(attributeId);
        List<Map<String, Object>> data = optionRepository.findAll().stream()
                .filter(option -> option.getDeletedAt() == null)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .map(this::toOptionRow)
                .toList();
        return Map.of("attribute_id", attribute.getId(), "data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> createOption(String attributeId, Map<String, Object> body) {
        ProductAttribute attribute = requireAttribute(attributeId);
        ProductAttributeOption option = new ProductAttributeOption();
        option.setId(UUID.randomUUID());
        option.setProductAttribute(attribute);
        option.setName(requiredTranslations(body, "name"));
        option.setSlug(str(body, "slug", "option-" + option.getId()));
        option.setValue(required(body, "value"));
        option.setEnabled(bool(body, "enabled", true));
        if (body != null && body.get("image") instanceof Map<?, ?> image) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            image.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            option.setImage(normalized);
        }
        optionRepository.save(option);
        return Map.of("id", option.getId());
    }

    public Map<String, Object> showOption(String attributeId, String optionId) {
        ProductAttribute attribute = requireAttribute(attributeId);
        UUID id = uuid(optionId).orElse(null);
        if (id == null) {
            return Map.of("id", optionId);
        }
        return optionRepository.findById(id)
                .filter(option -> option.getDeletedAt() == null)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .map(this::toOptionRow)
                .orElseGet(() -> Map.of("id", id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateOption(String attributeId, String optionId, Map<String, Object> body) {
        ProductAttribute attribute = requireAttribute(attributeId);
        UUID id = uuid(optionId).orElse(null);
        if (id == null) {
            return Map.of("id", optionId);
        }
        Optional<ProductAttributeOption> option = optionRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getProductAttribute() != null && attribute.getId().equals(item.getProductAttribute().getId()));
        if (option.isEmpty()) {
            return Map.of("id", id);
        }
        ProductAttributeOption entity = option.orElseThrow();
        if (body != null && body.containsKey("name")) {
            entity.setName(requiredTranslations(body, "name"));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("value")) {
            entity.setValue(required(body, "value"));
        }
        if (body != null && body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.isEnabled()));
        }
        if (body != null && body.containsKey("image") && body.get("image") instanceof Map<?, ?> image) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            image.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            entity.setImage(normalized);
        }
        optionRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> deleteOption(String attributeId, String optionId) {
        ProductAttribute attribute = requireAttribute(attributeId);
        UUID id = uuid(optionId).orElse(null);
        if (id == null) {
            return Map.of("id", optionId);
        }
        optionRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getProductAttribute() != null && attribute.getId().equals(item.getProductAttribute().getId()))
                .ifPresent(option -> {
                    option.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    optionRepository.save(option);
                });
        return Map.of("id", id);
    }

    private ProductAttribute requireAttribute(String attributeId) {
        UUID id = uuid(attributeId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_attribute_id is invalid."));
        return attributeRepository.findById(id)
                .filter(attribute -> attribute.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_attribute_id is invalid."));
    }

    private Map<String, Object> toDetail(ProductAttribute attribute) {
        Map<String, Object> row = toRow(attribute);
        row.put("options_count", optionRepository.findAll().stream()
                .filter(option -> option.getDeletedAt() == null)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .count());
        return row;
    }

    private Map<String, Object> toRow(ProductAttribute attribute) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", attribute.getName());
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("image", attribute.getImage());
        row.put("enabled", attribute.isEnabled());
        row.put("show_in_filters", attribute.isShowInFilters());
        return row;
    }

    private Map<String, Object> toOptionRow(ProductAttributeOption option) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", option.getId());
        row.put("product_attribute_id", option.getProductAttribute() == null ? null : option.getProductAttribute().getId());
        row.put("name", option.getName());
        row.put("slug", option.getSlug());
        row.put("value", option.getValue());
        row.put("image", option.getImage());
        row.put("enabled", option.isEnabled());
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

    private String required(Map<String, Object> body, String key) {
        String value = str(body, key, null);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return value;
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

    private int intVal(Map<String, Object> body, String key, int fallback) {
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
