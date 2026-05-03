package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminDeliveryRuleService;
import com.shoptopus.core.admin.AdminDeliveryTypeService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.delivery.DeliveryRule;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.delivery.repository.DeliveryRuleRepository;
import com.shoptopus.core.delivery.repository.DeliveryTypeRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
public class AdminUiDeliveryService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeliveryTypeRepository deliveryTypeRepository;
    private final DeliveryRuleRepository deliveryRuleRepository;
    private final AdminDeliveryTypeService adminDeliveryTypeService;
    private final AdminDeliveryRuleService adminDeliveryRuleService;
    private final AdminStoreContext adminStoreContext;

    public AdminUiDeliveryService(
            DeliveryTypeRepository deliveryTypeRepository,
            DeliveryRuleRepository deliveryRuleRepository,
            AdminDeliveryTypeService adminDeliveryTypeService,
            AdminDeliveryRuleService adminDeliveryRuleService,
            AdminStoreContext adminStoreContext
    ) {
        this.deliveryTypeRepository = deliveryTypeRepository;
        this.deliveryRuleRepository = deliveryRuleRepository;
        this.adminDeliveryTypeService = adminDeliveryTypeService;
        this.adminDeliveryRuleService = adminDeliveryRuleService;
        this.adminStoreContext = adminStoreContext;
    }

    public Map<String, Object> deliveryTypes(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<DeliveryType> filtered = scopedDeliveryTypes().stream()
                .filter(type -> matchesSearch(type, search))
                .filter(type -> matchesStatus(type, status))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::typeListItem).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", List.of(
                                Map.of("value", "all", "label", "All"),
                                Map.of("value", "enabled", "label", "Enabled"),
                                Map.of("value", "disabled", "label", "Disabled")
                        )
                )
        );
    }

    public Map<String, Object> deliveryType(String id) {
        DeliveryType deliveryType = scopedType(id);
        List<Map<String, Object>> rules = deliveryRuleRepository.findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(deliveryType.getId()).stream()
                .filter(this::matchesActiveStore)
                .map(this::ruleRow)
                .toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deliveryType.getId());
        row.put("name", deliveryType.getName());
        row.put("name_display", displayName(deliveryType.getName()));
        row.put("description", deliveryType.getDescription());
        row.put("description_display", displayName(deliveryType.getDescription()));
        row.put("slug", deliveryType.getSlug());
        row.put("price", money(deliveryType.getPrice()));
        row.put("price_value", deliveryType.getPrice());
        row.put("enabled", deliveryType.isEnabled());
        row.put("enabled_by_default_on_creation", deliveryType.isEnabledByDefaultOnCreation());
        row.put("created_at", format(deliveryType.getCreatedAt()));
        row.put("updated_at", format(deliveryType.getUpdatedAt()));
        row.put("metrics", Map.of(
                "rules", rules.size(),
                "enabled_rules", rules.stream().filter(rule -> Boolean.TRUE.equals(rule.get("enabled"))).count()
        ));
        row.put("rules", rules);
        row.put("form_options", deliveryTypeFormOptions());
        return row;
    }

    public Map<String, Object> deliveryRules(String deliveryTypeId) {
        DeliveryType deliveryType = scopedType(deliveryTypeId);
        return Map.of(
                "delivery_type_id", deliveryType.getId(),
                "items", deliveryRuleRepository.findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(deliveryType.getId()).stream()
                        .filter(this::matchesActiveStore)
                        .map(this::ruleRow)
                        .toList()
        );
    }

    public Map<String, Object> deliveryRule(String deliveryTypeId, String ruleId) {
        DeliveryType deliveryType = scopedType(deliveryTypeId);
        DeliveryRule rule = findRule(ruleId);
        if (!rule.getDeliveryType().getId().equals(deliveryType.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Delivery rule not found", "Delivery rule not found");
        }
        return ruleRow(rule);
    }

    public Map<String, Object> deliveryTypeFormOptions() {
        return Map.of(
                "booleans", List.of(
                        Map.of("value", true, "label", "Yes"),
                        Map.of("value", false, "label", "No")
                )
        );
    }

    public Map<String, Object> createDeliveryType(Map<String, Object> body) {
        Map<String, Object> created = adminDeliveryTypeService.create(body);
        scopedType(String.valueOf(created.get("id")));
        return deliveryType(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateDeliveryType(String id, Map<String, Object> body) {
        scopedType(id);
        adminDeliveryTypeService.update(id, body);
        scopedType(id);
        return deliveryType(id);
    }

    public Map<String, Object> createDeliveryRule(String deliveryTypeId, Map<String, Object> body) {
        DeliveryType type = scopedType(deliveryTypeId);
        adminDeliveryRuleService.create(deliveryTypeId, body);
        syncRules(type);
        return deliveryType(deliveryTypeId);
    }

    public Map<String, Object> updateDeliveryRule(String deliveryTypeId, String ruleId, Map<String, Object> body) {
        DeliveryType type = scopedType(deliveryTypeId);
        adminDeliveryRuleService.update(deliveryTypeId, ruleId, body);
        syncRules(type);
        return deliveryType(deliveryTypeId);
    }

    public Map<String, Object> deleteDeliveryRule(String deliveryTypeId, String ruleId) {
        scopedType(deliveryTypeId);
        findRule(ruleId);
        adminDeliveryRuleService.delete(deliveryTypeId, ruleId);
        return deliveryType(deliveryTypeId);
    }

    public Map<String, Object> deleteDeliveryType(String id) {
        scopedType(id);
        adminDeliveryTypeService.delete(id);
        return Map.of("id", id);
    }

    private boolean matchesSearch(DeliveryType deliveryType, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return displayName(deliveryType.getName()).toLowerCase(Locale.ROOT).contains(term)
                || displayName(deliveryType.getDescription()).toLowerCase(Locale.ROOT).contains(term)
                || (deliveryType.getSlug() != null && deliveryType.getSlug().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(DeliveryType deliveryType, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> deliveryType.isEnabled();
            case "disabled" -> !deliveryType.isEnabled();
            default -> true;
        };
    }

    private Map<String, Object> typeListItem(DeliveryType deliveryType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", deliveryType.getId());
        row.put("name", displayName(deliveryType.getName()));
        row.put("slug", deliveryType.getSlug());
        row.put("description", displayName(deliveryType.getDescription()));
        row.put("price", money(deliveryType.getPrice()));
        row.put("enabled", deliveryType.isEnabled());
        row.put("enabled_by_default_on_creation", deliveryType.isEnabledByDefaultOnCreation());
        row.put("rules_count", deliveryRuleRepository.findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(deliveryType.getId()).stream()
                .filter(this::matchesActiveStore)
                .count());
        row.put("created_at", format(deliveryType.getCreatedAt()));
        return row;
    }

    private Map<String, Object> ruleRow(DeliveryRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("delivery_type_id", rule.getDeliveryType().getId());
        row.put("name", rule.getName());
        row.put("name_display", displayName(rule.getName()));
        row.put("country", rule.getCountry());
        row.put("postcodes", rule.getPostcodes());
        row.put("min_cart_price", money(rule.getMinCartPrice()));
        row.put("max_cart_price", money(rule.getMaxCartPrice()));
        row.put("min_weight", rule.getMinWeight());
        row.put("max_weight", rule.getMaxWeight());
        row.put("min_distance", decimal(rule.getMinDistance()));
        row.put("max_distance", decimal(rule.getMaxDistance()));
        row.put("lat", decimal(rule.getLat()));
        row.put("lon", decimal(rule.getLon()));
        row.put("enabled", rule.isEnabled());
        row.put("created_at", format(rule.getCreatedAt()));
        row.put("updated_at", format(rule.getUpdatedAt()));
        return row;
    }

    private DeliveryType findType(String id) {
        try {
            return deliveryTypeRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery type not found", "Delivery type not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Delivery type not found", "Delivery type not found");
        }
    }

    private DeliveryRule findRule(String id) {
        try {
            return deliveryRuleRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery rule not found", "Delivery rule not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Delivery rule not found", "Delivery rule not found");
        }
    }

    private DeliveryType scopedType(String id) {
        DeliveryType type = findType(id);
        if (adminStoreContext.multiStoreEnabled() && type.getStore() == null) {
            type.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
            return deliveryTypeRepository.save(type);
        }
        return type;
    }

    private void syncRules(DeliveryType type) {
        if (!adminStoreContext.multiStoreEnabled() || type.getStore() == null) {
            return;
        }
        deliveryRuleRepository.findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(type.getId()).stream()
                .filter(rule -> rule.getStore() == null || !type.getStore().getId().equals(rule.getStore().getId()))
                .forEach(rule -> {
                    rule.setStore(type.getStore());
                    deliveryRuleRepository.save(rule);
                });
    }

    private Map<String, Object> pageMeta(int page, int perPage, int total) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("per_page", perPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / perPage));
        return meta;
    }

    private String displayName(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.containsKey("en")) {
            return String.valueOf(values.get("en"));
        }
        return values.values().stream().findFirst().map(String::valueOf).orElse("");
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return "GBP " + value.setScale(2, RoundingMode.HALF_UP);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }

    private boolean matchesActiveStore(DeliveryType deliveryType) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null
                && deliveryType.getStore() != null
                && activeStore.getId().equals(deliveryType.getStore().getId());
    }

    private List<DeliveryType> scopedDeliveryTypes() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return deliveryTypeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return deliveryTypeRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(DeliveryRule deliveryRule) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null
                && deliveryRule.getStore() != null
                && activeStore.getId().equals(deliveryRule.getStore().getId());
    }
}
