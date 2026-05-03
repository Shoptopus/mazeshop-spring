package com.shoptopus.core.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.delivery.DeliveryRule;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.delivery.repository.DeliveryRuleRepository;
import com.shoptopus.core.delivery.repository.DeliveryTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminDeliveryRuleService {

    private final DeliveryRuleRepository deliveryRuleRepository;
    private final DeliveryTypeRepository deliveryTypeRepository;
    private final ObjectMapper objectMapper;
    private final AdminStoreContext adminStoreContext;

    public AdminDeliveryRuleService(
            DeliveryRuleRepository deliveryRuleRepository,
            DeliveryTypeRepository deliveryTypeRepository,
            ObjectMapper objectMapper,
            AdminStoreContext adminStoreContext
    ) {
        this.deliveryRuleRepository = deliveryRuleRepository;
        this.deliveryTypeRepository = deliveryTypeRepository;
        this.objectMapper = objectMapper;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String deliveryTypeId) {
        DeliveryType deliveryType = findType(deliveryTypeId);
        List<Map<String, Object>> data = deliveryRuleRepository.findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(deliveryType.getId()).stream()
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "delivery-rules");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String deliveryTypeId, String deliveryRuleId) {
        return toRow(findRule(deliveryTypeId, deliveryRuleId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(String deliveryTypeId, Map<String, Object> body) {
        DeliveryType deliveryType = findType(deliveryTypeId);
        Map<String, Object> payload = body == null ? Map.of() : body;
        DeliveryRule deliveryRule = new DeliveryRule();
        deliveryRule.setDeliveryType(deliveryType);
        deliveryRule.setStore(deliveryType.getStore());
        deliveryRule.setName(readTranslations(payload.get("name")));
        deliveryRule.setSlug(optionalString(payload, "slug").orElse("delivery-rule-" + UUID.randomUUID()));
        applyMutations(deliveryRule, payload);
        deliveryRuleRepository.save(deliveryRule);
        return toRow(deliveryRule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String deliveryTypeId, String deliveryRuleId, Map<String, Object> body) {
        DeliveryRule deliveryRule = findRule(deliveryTypeId, deliveryRuleId);
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (payload.containsKey("name")) {
            deliveryRule.setName(readTranslations(payload.get("name")));
        }
        applyMutations(deliveryRule, payload);
        deliveryRuleRepository.save(deliveryRule);
        return toRow(deliveryRule);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String deliveryTypeId, String deliveryRuleId) {
        DeliveryRule deliveryRule = findRule(deliveryTypeId, deliveryRuleId);
        deliveryRule.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        deliveryRuleRepository.save(deliveryRule);
        return Map.of("id", deliveryRule.getId());
    }

    private void applyMutations(DeliveryRule deliveryRule, Map<String, Object> payload) {
        if (payload.containsKey("country")) {
            deliveryRule.setCountry(optionalString(payload, "country").orElse(null));
        }
        if (payload.containsKey("min_cart_price")) {
            deliveryRule.setMinCartPrice(readNullableDecimal(payload.get("min_cart_price"), "The min_cart_price must be a number."));
        }
        if (payload.containsKey("max_cart_price")) {
            deliveryRule.setMaxCartPrice(readNullableDecimal(payload.get("max_cart_price"), "The max_cart_price must be a number."));
        }
        if (payload.containsKey("postcodes")) {
            deliveryRule.setPostcodes(readPostcodes(payload.get("postcodes")));
        }
        if (payload.containsKey("min_weight")) {
            deliveryRule.setMinWeight(readNullableInteger(payload.get("min_weight"), "The min_weight must be an integer."));
        }
        if (payload.containsKey("max_weight")) {
            deliveryRule.setMaxWeight(readNullableInteger(payload.get("max_weight"), "The max_weight must be an integer."));
        }
        if (payload.containsKey("min_distance")) {
            deliveryRule.setMinDistance(readNullableDecimal(payload.get("min_distance"), "The min_distance must be a number."));
        }
        if (payload.containsKey("max_distance")) {
            deliveryRule.setMaxDistance(readNullableDecimal(payload.get("max_distance"), "The max_distance must be a number."));
        }
        if (payload.containsKey("lat")) {
            deliveryRule.setLat(readNullableDecimal(payload.get("lat"), "The lat must be a number."));
        }
        if (payload.containsKey("lon")) {
            deliveryRule.setLon(readNullableDecimal(payload.get("lon"), "The lon must be a number."));
        }
        if (payload.containsKey("enabled")) {
            deliveryRule.setEnabled(readBoolean(payload.get("enabled"), deliveryRule.isEnabled()));
        }
    }

    private DeliveryType findType(String id) {
        UUID uuid = parseUuid(id, "delivery type");
        return deliveryTypeRepository.findByIdAndDeletedAtIsNull(uuid)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Delivery type not found."));
    }

    private DeliveryRule findRule(String deliveryTypeId, String deliveryRuleId) {
        DeliveryType deliveryType = findType(deliveryTypeId);
        UUID ruleUuid = parseUuid(deliveryRuleId, "delivery rule");
        DeliveryRule deliveryRule = deliveryRuleRepository.findByIdAndDeletedAtIsNull(ruleUuid)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Delivery rule not found."));
        if (!deliveryRule.getDeliveryType().getId().equals(deliveryType.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "Delivery rule not found.");
        }
        return deliveryRule;
    }

    private boolean matchesActiveStore(DeliveryType type) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        var activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && type.getStore() != null && activeStore.getId().equals(type.getStore().getId());
    }

    private boolean matchesActiveStore(DeliveryRule rule) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        var activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && rule.getStore() != null && activeStore.getId().equals(rule.getStore().getId());
    }

    private UUID parseUuid(String rawId, String label) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid " + label + " id.");
        }
    }

    private Map<String, Object> toRow(DeliveryRule deliveryRule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deliveryRule.getId());
        row.put("delivery_type_id", deliveryRule.getDeliveryType().getId());
        row.put("name", deliveryRule.getName());
        row.put("country", deliveryRule.getCountry());
        row.put("postcodes", deliveryRule.getPostcodes());
        row.put("min_cart_price", deliveryRule.getMinCartPrice());
        row.put("max_cart_price", deliveryRule.getMaxCartPrice());
        row.put("min_weight", deliveryRule.getMinWeight());
        row.put("max_weight", deliveryRule.getMaxWeight());
        row.put("min_distance", deliveryRule.getMinDistance());
        row.put("max_distance", deliveryRule.getMaxDistance());
        row.put("lat", deliveryRule.getLat());
        row.put("lon", deliveryRule.getLon());
        row.put("enabled", deliveryRule.isEnabled());
        return row;
    }

    private Map<String, String> readTranslations(Object value) {
        if (value == null) {
            return Map.of();
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

    private List<String> readPostcodes(Object value) {
        try {
            if (value instanceof String raw) {
                return objectMapper.readValue(raw, new TypeReference<>() {});
            }
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The postcodes field is invalid.");
        }
    }

    private BigDecimal readNullableDecimal(Object value, String message) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private Integer readNullableInteger(Object value, String message) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
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
