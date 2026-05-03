package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminProductAttributeService;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiProductAttributeService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final AdminProductAttributeService adminProductAttributeService;
    private final AdminStoreContext adminStoreContext;

    public AdminUiProductAttributeService(
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            AdminProductAttributeService adminProductAttributeService,
            AdminStoreContext adminStoreContext
    ) {
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.adminProductAttributeService = adminProductAttributeService;
        this.adminStoreContext = adminStoreContext;
    }

    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<ProductAttribute> filtered = productAttributeRepository.findAll().stream()
                .filter(attribute -> attribute.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(attribute -> matchesSearch(attribute, search))
                .filter(attribute -> matchesStatus(attribute, status))
                .sorted(Comparator.comparing(ProductAttribute::getCreatedAt).reversed())
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
        filters.put("statuses", statusOptions());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", filtered.subList(fromIndex, toIndex).stream().map(this::listRow).toList());
        response.put("meta", meta);
        response.put("filters", filters);
        return response;
    }

    public Map<String, Object> detail(String id) {
        ProductAttribute attribute = findAttribute(id);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", attribute.getName());
        row.put("name_display", translated(attribute.getName()));
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("type_label", typeLabel(attribute.getType()));
        row.put("image", attribute.getImage());
        row.put("enabled", attribute.isEnabled());
        row.put("show_in_filters", attribute.isShowInFilters());
        row.put("status_label", attribute.isEnabled() ? "Enabled" : "Disabled");
        row.put("created_at", format(attribute.getCreatedAt()));
        row.put("updated_at", format(attribute.getUpdatedAt()));
        row.put("options", optionsFor(attribute));
        row.put("form_options", formOptions());
        return row;
    }

    public Map<String, Object> formOptions() {
        return Map.of(
                "statuses", statusOptions(),
                "types", List.of(
                        Map.of("value", 0, "label", "Select"),
                        Map.of("value", 1, "label", "Text"),
                        Map.of("value", 2, "label", "Colour")
                ),
                "booleans", List.of(
                        Map.of("value", true, "label", "Enabled"),
                        Map.of("value", false, "label", "Disabled")
                )
        );
    }

    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> created = adminProductAttributeService.create(body);
        scopedAttribute(String.valueOf(created.get("id")));
        return detail(String.valueOf(created.get("id")));
    }

    public Map<String, Object> update(String id, Map<String, Object> body) {
        scopedAttribute(id);
        adminProductAttributeService.update(id, body);
        scopedAttribute(id);
        return detail(id);
    }

    public Map<String, Object> createOption(String attributeId, Map<String, Object> body) {
        ProductAttribute attribute = scopedAttribute(attributeId);
        adminProductAttributeService.createOption(attributeId, body);
        syncOptionStores(attribute);
        return detail(attributeId);
    }

    public Map<String, Object> updateOption(String attributeId, String optionId, Map<String, Object> body) {
        ProductAttribute attribute = scopedAttribute(attributeId);
        adminProductAttributeService.updateOption(attributeId, optionId, body);
        syncOptionStores(attribute);
        return detail(attributeId);
    }

    public Map<String, Object> deleteOption(String attributeId, String optionId) {
        scopedAttribute(attributeId);
        adminProductAttributeService.deleteOption(attributeId, optionId);
        return detail(attributeId);
    }

    public Map<String, Object> delete(String id) {
        scopedAttribute(id);
        adminProductAttributeService.delete(id);
        return Map.of("id", id);
    }

    private boolean matchesSearch(ProductAttribute attribute, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(attribute.getName()).toLowerCase(Locale.ROOT).contains(term)
                || (attribute.getSlug() != null && attribute.getSlug().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(ProductAttribute attribute, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> attribute.isEnabled();
            case "disabled" -> !attribute.isEnabled();
            default -> true;
        };
    }

    private Map<String, Object> listRow(ProductAttribute attribute) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attribute.getId());
        row.put("name", translated(attribute.getName()));
        row.put("slug", attribute.getSlug());
        row.put("type", attribute.getType());
        row.put("type_label", typeLabel(attribute.getType()));
        row.put("image", attribute.getImage());
        row.put("enabled", attribute.isEnabled());
        row.put("show_in_filters", attribute.isShowInFilters());
        row.put("status_label", attribute.isEnabled() ? "Enabled" : "Disabled");
        row.put("options_count", optionsFor(attribute).size());
        row.put("created_at", format(attribute.getCreatedAt()));
        return row;
    }

    private List<Map<String, Object>> optionsFor(ProductAttribute attribute) {
        return productAttributeOptionRepository.findAll().stream()
                .filter(option -> option.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .sorted(Comparator.comparing(ProductAttributeOption::getCreatedAt))
                .map(this::optionRow)
                .toList();
    }

    private Map<String, Object> optionRow(ProductAttributeOption option) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", option.getId());
        row.put("product_attribute_id", option.getProductAttribute() == null ? null : option.getProductAttribute().getId());
        row.put("name", option.getName());
        row.put("name_display", translated(option.getName()));
        row.put("slug", option.getSlug());
        row.put("value", option.getValue());
        row.put("image", option.getImage());
        row.put("enabled", option.isEnabled());
        row.put("status_label", option.isEnabled() ? "Enabled" : "Disabled");
        row.put("created_at", format(option.getCreatedAt()));
        row.put("updated_at", format(option.getUpdatedAt()));
        return row;
    }

    private ProductAttribute findAttribute(String id) {
        try {
            return productAttributeRepository.findById(UUID.fromString(id))
                    .filter(item -> item.getDeletedAt() == null)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attribute not found", "Attribute not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attribute not found", "Attribute not found");
        }
    }

    private List<Map<String, Object>> statusOptions() {
        return List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "enabled", "label", "Enabled"),
                Map.of("value", "disabled", "label", "Disabled")
        );
    }

    private String typeLabel(Integer type) {
        if (type == null) {
            return "Unknown";
        }
        return switch (type) {
            case 1 -> "Text";
            case 2 -> "Colour";
            default -> "Select";
        };
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private ProductAttribute scopedAttribute(String id) {
        ProductAttribute attribute = findAttribute(id);
        if (adminStoreContext.multiStoreEnabled() && attribute.getStore() == null) {
            attribute.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
            return productAttributeRepository.save(attribute);
        }
        return attribute;
    }

    private void syncOptionStores(ProductAttribute attribute) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return;
        }
        Store store = attribute.getStore();
        if (store == null) {
            return;
        }
        productAttributeOptionRepository.findAll().stream()
                .filter(option -> option.getDeletedAt() == null)
                .filter(option -> option.getProductAttribute() != null && attribute.getId().equals(option.getProductAttribute().getId()))
                .filter(option -> option.getStore() == null || !store.getId().equals(option.getStore().getId()))
                .forEach(option -> {
                    option.setStore(store);
                    productAttributeOptionRepository.save(option);
                });
    }

    private boolean matchesActiveStore(ProductAttribute attribute) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && attribute.getStore() != null && activeStore.getId().equals(attribute.getStore().getId());
    }

    private boolean matchesActiveStore(ProductAttributeOption option) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null
                && option.getStore() != null
                && activeStore.getId().equals(option.getStore().getId());
    }
}
