package com.shoptopus.core.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.delivery.repository.DeliveryTypeRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminDeliveryTypeService {

    private final DeliveryTypeRepository deliveryTypeRepository;
    private final ObjectMapper objectMapper;
    private final AdminStoreContext adminStoreContext;

    public AdminDeliveryTypeService(DeliveryTypeRepository deliveryTypeRepository, ObjectMapper objectMapper, AdminStoreContext adminStoreContext) {
        this.deliveryTypeRepository = deliveryTypeRepository;
        this.objectMapper = objectMapper;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = scopedTypes().stream()
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "delivery-types");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        return toRow(findType(id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        DeliveryType deliveryType = new DeliveryType();
        deliveryType.setName(readRequiredTranslations(payload, "name"));
        deliveryType.setDescription(readOptionalTranslations(payload.get("description")));
        deliveryType.setPrice(readDecimal(payload.get("price"), "The price must be a number.", BigDecimal.ZERO));
        deliveryType.setEnabled(readBoolean(payload.get("enabled"), true));
        deliveryType.setEnabledByDefaultOnCreation(readBoolean(payload.get("enabled_by_default_on_creation"), true));
        deliveryType.setSlug(optionalString(payload, "slug").orElse("delivery-type-" + UUID.randomUUID()));
        deliveryType.setStore(activeStore());
        deliveryTypeRepository.save(deliveryType);
        return toRow(deliveryType);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        DeliveryType deliveryType = findType(id);
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (payload.containsKey("name")) {
            deliveryType.setName(readTranslations(payload.get("name"), "The name field is required."));
        }
        if (payload.containsKey("description")) {
            deliveryType.setDescription(readOptionalTranslations(payload.get("description")));
        }
        if (payload.containsKey("price")) {
            deliveryType.setPrice(readDecimal(payload.get("price"), "The price must be a number.", deliveryType.getPrice()));
        }
        if (payload.containsKey("enabled")) {
            deliveryType.setEnabled(readBoolean(payload.get("enabled"), deliveryType.isEnabled()));
        }
        if (payload.containsKey("enabled_by_default_on_creation")) {
            deliveryType.setEnabledByDefaultOnCreation(readBoolean(payload.get("enabled_by_default_on_creation"), deliveryType.isEnabledByDefaultOnCreation()));
        }
        deliveryTypeRepository.save(deliveryType);
        return toRow(deliveryType);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        DeliveryType deliveryType = findType(id);
        deliveryType.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        deliveryTypeRepository.save(deliveryType);
        return Map.of("id", deliveryType.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkAvailability(Map<String, Object> body) {
        List<DeliveryType> deliveryTypes = requireBulkTypes(body);
        if (body == null || !body.containsKey("availability")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The availability field is required.");
        }
        boolean availability = readBoolean(body.get("availability"), true);
        deliveryTypes.forEach(deliveryType -> deliveryType.setEnabled(availability));
        deliveryTypeRepository.saveAll(deliveryTypes);
        return bulkResult(deliveryTypes);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<DeliveryType> deliveryTypes = requireBulkTypes(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        deliveryTypes.forEach(deliveryType -> deliveryType.setDeletedAt(now));
        deliveryTypeRepository.saveAll(deliveryTypes);
        return bulkResult(deliveryTypes);
    }

    private DeliveryType findType(String id) {
        UUID uuid = parseUuid(id, "delivery type");
        return deliveryTypeRepository.findByIdAndDeletedAtIsNull(uuid)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Delivery type not found."));
    }

    private List<DeliveryType> requireBulkTypes(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        List<DeliveryType> deliveryTypes = new ArrayList<>();
        int index = 0;
        for (Object rawId : rawIds) {
            final int currentIndex = index;
            UUID id = parseBulkUuid(rawId, currentIndex);
            deliveryTypes.add(deliveryTypeRepository.findByIdAndDeletedAtIsNull(id)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return deliveryTypes;
    }

    private UUID parseBulkUuid(Object rawId, int index) {
        try {
            return UUID.fromString(String.valueOf(rawId));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + index + " is invalid.");
        }
    }

    private UUID parseUuid(String rawId, String label) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid " + label + " id.");
        }
    }

    private Map<String, Object> bulkResult(List<DeliveryType> deliveryTypes) {
        return Map.of("ids", deliveryTypes.stream().map(DeliveryType::getId).toList(), "count", deliveryTypes.size());
    }

    private List<DeliveryType> scopedTypes() {
        Store activeStore = activeStore();
        if (activeStore == null) {
            return deliveryTypeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return deliveryTypeRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(DeliveryType type) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null && type.getStore() != null && activeStore.getId().equals(type.getStore().getId());
    }

    private Store activeStore() {
        return adminStoreContext.activeStoreForCurrentRequest().orElse(null);
    }

    private Map<String, Object> toRow(DeliveryType deliveryType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deliveryType.getId());
        row.put("name", deliveryType.getName());
        row.put("description", deliveryType.getDescription());
        row.put("price", deliveryType.getPrice());
        row.put("enabled", deliveryType.isEnabled());
        row.put("enabled_by_default_on_creation", deliveryType.isEnabledByDefaultOnCreation());
        row.put("notes", List.of());
        return row;
    }

    private Map<String, String> readRequiredTranslations(Map<String, Object> payload, String field) {
        if (!payload.containsKey(field)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + field + " field is required.");
        }
        return readTranslations(payload.get(field), "The " + field + " field is required.");
    }

    private Map<String, String> readTranslations(Object value, String requiredMessage) {
        Map<String, String> translations = readOptionalTranslations(value);
        if (translations == null || translations.getOrDefault("en", "").isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", requiredMessage);
        }
        return translations;
    }

    private Map<String, String> readOptionalTranslations(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String raw) {
                return objectMapper.readValue(raw, new TypeReference<>() {});
            }
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid translations payload.");
        }
    }

    private BigDecimal readDecimal(Object value, String message, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Optional<String> optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? Optional.empty() : Optional.of(stringValue);
    }
}
