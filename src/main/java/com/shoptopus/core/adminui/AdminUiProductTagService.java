package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminProductTagService;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
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
public class AdminUiProductTagService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final ProductTagRepository productTagRepository;
    private final AdminProductTagService adminProductTagService;

    public AdminUiProductTagService(ProductTagRepository productTagRepository, AdminProductTagService adminProductTagService) {
        this.productTagRepository = productTagRepository;
        this.adminProductTagService = adminProductTagService;
    }

    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<ProductTag> filtered = productTagRepository.findAll().stream()
                .filter(tag -> tag.getDeletedAt() == null)
                .filter(tag -> matchesSearch(tag, search))
                .filter(tag -> matchesStatus(tag, status))
                .sorted(Comparator.comparing(ProductTag::getCreatedAt).reversed())
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

    public Map<String, Object> detail(String id) {
        ProductTag tag = findTag(id);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tag.getId());
        row.put("name", tag.getName());
        row.put("name_display", translated(tag.getName()));
        row.put("slug", tag.getSlug());
        row.put("enabled", tag.isEnabled());
        row.put("status_label", tag.isEnabled() ? "Enabled" : "Disabled");
        row.put("description", tag.getDescription());
        row.put("description_display", translated(tag.getDescription()));
        row.put("badge", tag.getBadge());
        row.put("display_badge", tag.isDisplayBadge());
        row.put("created_at", tag.getCreatedAt() == null ? null : tag.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", tag.getUpdatedAt() == null ? null : tag.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("form_options", formOptions());
        return row;
    }

    public Map<String, Object> formOptions() {
        return Map.of(
                "statuses", List.of(
                        Map.of("value", true, "label", "Enabled"),
                        Map.of("value", false, "label", "Disabled")
                ),
                "booleans", List.of(
                        Map.of("value", true, "label", "Yes"),
                        Map.of("value", false, "label", "No")
                )
        );
    }

    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> created = adminProductTagService.create(body);
        return detail(String.valueOf(created.get("id")));
    }

    public Map<String, Object> update(String id, Map<String, Object> body) {
        adminProductTagService.update(id, body);
        return detail(id);
    }

    public Map<String, Object> delete(String id) {
        adminProductTagService.delete(id);
        return Map.of("id", id);
    }

    private boolean matchesSearch(ProductTag tag, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(tag.getName()).toLowerCase(Locale.ROOT).contains(term)
                || translated(tag.getDescription()).toLowerCase(Locale.ROOT).contains(term)
                || (tag.getSlug() != null && tag.getSlug().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(ProductTag tag, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> tag.isEnabled();
            case "disabled" -> !tag.isEnabled();
            default -> true;
        };
    }

    private Map<String, Object> listRow(ProductTag tag) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tag.getId());
        row.put("name", translated(tag.getName()));
        row.put("slug", tag.getSlug());
        row.put("enabled", tag.isEnabled());
        row.put("status_label", tag.isEnabled() ? "Enabled" : "Disabled");
        row.put("description", translated(tag.getDescription()));
        row.put("display_badge", tag.isDisplayBadge());
        row.put("badge", tag.getBadge());
        row.put("created_at", tag.getCreatedAt() == null ? null : tag.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return row;
    }

    private ProductTag findTag(String id) {
        try {
            return productTagRepository.findById(UUID.fromString(id))
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tag not found", "Tag not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tag not found", "Tag not found");
        }
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }
}
