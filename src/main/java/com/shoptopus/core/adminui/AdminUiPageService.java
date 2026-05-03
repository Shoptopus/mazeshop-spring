package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.files.ClearManagedFiles;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.page.Page;
import com.shoptopus.core.page.PageMenuService;
import com.shoptopus.core.page.repository.PageRepository;
import com.shoptopus.core.store.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminUiPageService {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final PageRepository pageRepository;
    private final ManagedFileService managedFileService;
    private final AdminUiContentService adminUiContentService;
    private final PageMenuService pageMenuService;
    private final List<String> languages;
    private final AdminStoreContext adminStoreContext;

    public AdminUiPageService(
            PageRepository pageRepository,
            ManagedFileService managedFileService,
            AdminUiContentService adminUiContentService,
            PageMenuService pageMenuService,
            @Value("${app.i18n.languages:en}") String languages,
            AdminStoreContext adminStoreContext
    ) {
        this.pageRepository = pageRepository;
        this.managedFileService = managedFileService;
        this.adminUiContentService = adminUiContentService;
        this.pageMenuService = pageMenuService;
        this.languages = Arrays.stream(languages.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);
        List<Page> filtered = pageRepository.findAll().stream()
                .filter(item -> item.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(item -> matchesSearch(item, search))
                .filter(item -> matchesStatus(item, status))
                .sorted(Comparator.comparing(Page::getCreatedAt).reversed())
                .toList();
        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);
        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::listRow).toList(),
                "meta", Map.of(
                        "page", safePage,
                        "per_page", safePerPage,
                        "total", total,
                        "total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage)
                ),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", statusOptions()
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        Page page = find(id);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", page.getId());
        row.put("title", page.getTitle());
        row.put("title_display", translated(page.getTitle()));
        row.put("slug", page.getSlug());
        row.put("status", page.getStatus());
        row.put("status_label", statusLabel(page.getStatus()));
        row.put("show_in_menu", page.isShowInMenu());
        row.put("home_page", page.isHomePage());
        row.put("add_to_footer", page.isAddToFooter());
        row.put("version", page.getVersion());
        row.put("files", managedFileService.filesFor("page", page.getId(), List.of("page_file")));
        row.put("video", managedFileService.findAttachedSingle("page", page.getId(), "page_video"));
        row.put("contents", adminUiContentService.list("page", page.getId().toString()).get("items"));
        row.put("created_at", format(page.getCreatedAt()));
        row.put("updated_at", format(page.getUpdatedAt()));
        row.put("form_options", formOptions());
        return row;
    }

    public Map<String, Object> formOptions() {
        return Map.of(
                "statuses", List.of(
                        Map.of("value", 0, "label", "Preview"),
                        Map.of("value", 1, "label", "Enabled"),
                        Map.of("value", 2, "label", "Disabled")
                ),
                "booleans", List.of(
                        Map.of("value", true, "label", "Yes"),
                        Map.of("value", false, "label", "No")
                ),
                "home_page_description", "Exactly one page can be the home page. Enabling it here clears the flag from any other page.",
                "languages", (languages.isEmpty() ? List.of("en") : languages)
        );
    }

    @Transactional
    public Map<String, Object> menu() {
        return pageMenuService.adminMenu(DEFAULT_LOCALE);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> saveMenu(Map<String, Object> body) {
        return pageMenuService.saveMenu(mapList(body == null ? null : body.get("tree")), DEFAULT_LOCALE);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Page page = new Page();
        page.setId(UUID.randomUUID());
        page.setTitle(requiredTranslations(body, "title"));
        page.setSlug(str(body, "slug", "page-" + page.getId()));
        page.setStatus(intVal(body, "status", 0));
        page.setShowInMenu(bool(body, "show_in_menu", false));
        page.setHomePage(bool(body, "home_page", false));
        page.setAddToFooter(bool(body, "add_to_footer", false));
        page.setVersion(1);
        assignActiveStore(page);
        applyHomePageSelection(page);
        pageRepository.save(page);
        syncAttachments(page, body);
        return detail(page.getId().toString());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        Page page = find(id);
        if (body != null && body.containsKey("title")) {
            page.setTitle(requiredTranslations(body, "title"));
        }
        if (body != null && body.containsKey("slug")) {
            page.setSlug(str(body, "slug", page.getSlug()));
        }
        if (body != null && body.containsKey("status")) {
            page.setStatus(intVal(body, "status", page.getStatus()));
        }
        if (body != null && body.containsKey("show_in_menu")) {
            page.setShowInMenu(bool(body, "show_in_menu", page.isShowInMenu()));
        }
        if (body != null && body.containsKey("home_page")) {
            page.setHomePage(bool(body, "home_page", page.isHomePage()));
        }
        if (body != null && body.containsKey("add_to_footer")) {
            page.setAddToFooter(bool(body, "add_to_footer", page.isAddToFooter()));
        }
        page.setVersion(page.getVersion() == null ? 1 : page.getVersion() + 1);
        assignActiveStore(page);
        applyHomePageSelection(page);
        pageRepository.save(page);
        syncAttachments(page, body);
        return detail(page.getId().toString());
    }

    @Transactional("mainTransactionManager")
    @ClearManagedFiles(fileableType = "page")
    public Map<String, Object> delete(String id) {
        Page page = find(id);
        page.setDeletedAt(OffsetDateTime.now());
        pageRepository.save(page);
        return Map.of("id", page.getId());
    }

    private void syncAttachments(Page page, Map<String, Object> body) {
        if (body != null && body.containsKey("files")) {
            managedFileService.syncFiles("page", page.getId(), "page_file", mapList(body.get("files")));
        }
        if (body != null && body.containsKey("video")) {
            managedFileService.attachSingle("page", page.getId(), "page_video", map(body.get("video")));
        }
    }

    private boolean matchesSearch(Page page, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return translated(page.getTitle()).toLowerCase(Locale.ROOT).contains(term)
                || (page.getSlug() != null && page.getSlug().toLowerCase(Locale.ROOT).contains(term));
    }

    private boolean matchesStatus(Page page, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "preview" -> page.getStatus() == 0;
            case "enabled" -> page.getStatus() == 1;
            case "disabled" -> page.getStatus() == 2;
            default -> true;
        };
    }

    private Map<String, Object> listRow(Page page) {
        return Map.of(
                "id", page.getId(),
                "title", translated(page.getTitle()),
                "slug", page.getSlug(),
                "status", page.getStatus(),
                "status_label", statusLabel(page.getStatus()),
                "show_in_menu", page.isShowInMenu(),
                "add_to_footer", page.isAddToFooter(),
                "home_page", page.isHomePage(),
                "version", page.getVersion()
        );
    }

    private void applyHomePageSelection(Page selected) {
        if (!selected.isHomePage()) {
            return;
        }
        pageRepository.findAll().stream()
                .filter(page -> page.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(page -> !page.getId().equals(selected.getId()))
                .filter(Page::isHomePage)
                .forEach(page -> {
                    page.setHomePage(false);
                    pageRepository.save(page);
                });
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }

    private String statusLabel(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "Enabled";
            case 2 -> "Disabled";
            default -> "Preview";
        };
    }

    private Page find(String id) {
        UUID pageId = parseUuid(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Page not found", "Page not found"));
        return pageRepository.findById(pageId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Page not found", "Page not found"));
    }

    private void assignActiveStore(Page page) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return;
        }
        if (page.getStore() == null) {
            page.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
        }
    }

    private boolean matchesActiveStore(Page page) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && page.getStore() != null && activeStore.getId().equals(page.getStore().getId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
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
        if (body.get(key) instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue == null ? null : String.valueOf(entryValue)));
            return result;
        }
        return fallback;
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
        return Integer.parseInt(String.valueOf(raw));
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private List<Map<String, Object>> statusOptions() {
        return List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "preview", "label", "Preview"),
                Map.of("value", "enabled", "label", "Enabled"),
                Map.of("value", "disabled", "label", "Disabled")
        );
    }
}
