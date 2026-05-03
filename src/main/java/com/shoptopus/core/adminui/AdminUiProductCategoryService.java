package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminProductCategoryService;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiProductCategoryService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductCategoryRepository productCategoryRepository;
    private final AdminProductCategoryService adminProductCategoryService;

    public AdminUiProductCategoryService(
            ProductCategoryRepository productCategoryRepository,
            AdminProductCategoryService adminProductCategoryService
    ) {
        this.productCategoryRepository = productCategoryRepository;
        this.adminProductCategoryService = adminProductCategoryService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<ProductCategory> filtered = productCategoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .filter(category -> matchesSearch(category, search))
                .filter(category -> matchesStatus(category, status))
                .sorted(Comparator.comparing(ProductCategory::getCreatedAt).reversed())
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
        filters.put("statuses", List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "enabled", "label", "Enabled"),
                Map.of("value", "disabled", "label", "Disabled")
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", filtered.subList(fromIndex, toIndex).stream().map(this::listRow).toList());
        response.put("meta", meta);
        response.put("filters", filters);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        ProductCategory category = findCategory(id);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", category.getName());
        row.put("name_display", translated(category.getName()));
        row.put("slug", category.getSlug());
        row.put("enabled", category.isEnabled());
        row.put("status_label", category.isEnabled() ? "Enabled" : "Disabled");
        row.put("description", category.getDescription());
        row.put("description_display", translated(category.getDescription()));
        row.put("parent", parentSummary(category.getParent()));
        row.put("menu_image", category.getMenuImage());
        row.put("header_image", category.getHeaderImage());
        row.put("created_at", category.getCreatedAt() == null ? null : category.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", category.getUpdatedAt() == null ? null : category.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("form_options", formOptions(category.getId()));
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> formOptions(UUID excludeCategoryId) {
        return Map.of(
                "statuses", List.of(
                        Map.of("value", true, "label", "Enabled"),
                        Map.of("value", false, "label", "Disabled")
                ),
                "parents", productCategoryRepository.findAll().stream()
                        .filter(category -> category.getDeletedAt() == null)
                        .filter(category -> excludeCategoryId == null || !category.getId().equals(excludeCategoryId))
                        .sorted(Comparator.comparing(category -> translated(category.getName())))
                        .map(this::parentSummary)
                        .toList()
        );
    }

    public Map<String, Object> formOptions() {
        return formOptions(null);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> created = adminProductCategoryService.create(body);
        return detail(String.valueOf(created.get("id")));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        adminProductCategoryService.update(id, body);
        return detail(id);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        adminProductCategoryService.delete(id);
        return Map.of("id", id);
    }

    private boolean matchesSearch(ProductCategory category, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(category.getName()).toLowerCase(Locale.ROOT).contains(term)
                || translated(category.getDescription()).toLowerCase(Locale.ROOT).contains(term)
                || (category.getSlug() != null && category.getSlug().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(ProductCategory category, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> category.isEnabled();
            case "disabled" -> !category.isEnabled();
            default -> true;
        };
    }

    private Map<String, Object> listRow(ProductCategory category) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", translated(category.getName()));
        row.put("slug", category.getSlug());
        row.put("enabled", category.isEnabled());
        row.put("status_label", category.isEnabled() ? "Enabled" : "Disabled");
        row.put("description", translated(category.getDescription()));
        row.put("parent", parentSummary(category.getParent()));
        row.put("menu_image", category.getMenuImage());
        row.put("header_image", category.getHeaderImage());
        row.put("created_at", category.getCreatedAt() == null ? null : category.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return row;
    }

    private Map<String, Object> parentSummary(ProductCategory category) {
        if (category == null || category.getDeletedAt() != null) {
            return null;
        }
        return Map.of(
                "id", category.getId(),
                "name", translated(category.getName()),
                "slug", category.getSlug()
        );
    }

    private ProductCategory findCategory(String id) {
        try {
            return productCategoryRepository.findById(UUID.fromString(id))
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found", "Category not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Category not found", "Category not found");
        }
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }
}
