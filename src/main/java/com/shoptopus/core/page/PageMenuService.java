package com.shoptopus.core.page;

import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.page.repository.PageMenuItemRepository;
import com.shoptopus.core.page.repository.PageRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PageMenuService {

    public static final String ITEM_TYPE_PAGE = "page";
    public static final String ITEM_TYPE_SHOP = "shop";

    private final PageRepository pageRepository;
    private final PageMenuItemRepository pageMenuItemRepository;
    private final AdminStoreContext adminStoreContext;

    public PageMenuService(
            PageRepository pageRepository,
            PageMenuItemRepository pageMenuItemRepository,
            AdminStoreContext adminStoreContext
    ) {
        this.pageRepository = pageRepository;
        this.pageMenuItemRepository = pageMenuItemRepository;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> adminMenu(Locale locale) {
        Store store = currentStore();
        ensureMenuItemsExist(store);
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        List<Page> candidates = adminMenuEligiblePages(store);
        List<PageMenuItem> items = pageMenuItems(store);
        return Map.of(
                "tree", treeRows(items, pageLookup(candidates), resolvedLocale, false, null),
                "pages", candidates.stream()
                        .sorted(Comparator.comparing((Page page) -> translated(page.getTitle(), resolvedLocale), String.CASE_INSENSITIVE_ORDER))
                        .map(page -> pageRow(page, resolvedLocale))
                        .toList()
        );
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> storefrontMenu(Locale locale) {
        Store store = currentStore();
        return storefrontMenu(locale, store);
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> storefrontMenu(Locale locale, Store store) {
        if (store == null) {
            store = currentStore();
        }
        ensureMenuItemsExist(store);
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        List<Page> candidates = storefrontMenuEligiblePages(store);
        return treeRows(pageMenuItems(store), pageLookup(candidates), resolvedLocale, true, null);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> saveMenu(List<Map<String, Object>> payload, Locale locale) {
        Store store = currentStore();
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        List<MenuDraftNode> roots = parseDrafts(payload);
        validateDrafts(roots);

        Map<UUID, Page> allowedPages = pageLookup(adminMenuEligiblePages(store));
        for (MenuDraftNode node : flatten(roots)) {
            if (ITEM_TYPE_PAGE.equals(node.kind()) && !allowedPages.containsKey(node.pageId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Only pages with show_in_menu enabled can be added to the menu.");
            }
        }

        pageMenuItemRepository.deleteByStoreId(store.getId());
        pageMenuItemRepository.flush();
        persistDrafts(roots, null, store);
        return adminMenu(resolvedLocale);
    }

    private void ensureMenuItemsExist(Store store) {
        if (pageMenuItemRepository.findByStoreId(store.getId()).isEmpty()) {
            List<MenuDraftNode> defaults = new ArrayList<>();
            defaults.add(new MenuDraftNode(ITEM_TYPE_SHOP, null, List.of()));
            adminMenuEligiblePages(store).stream()
                    .sorted(Comparator
                            .comparing(Page::isHomePage).reversed()
                            .thenComparing(Page::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(page -> defaults.add(new MenuDraftNode(ITEM_TYPE_PAGE, page.getId(), List.of())));
            persistDrafts(defaults, null, store);
            return;
        }

        boolean hasShop = pageMenuItemRepository.findByItemTypeAndStoreId(ITEM_TYPE_SHOP, store.getId()).isPresent();
        if (!hasShop) {
            PageMenuItem shop = new PageMenuItem();
            shop.setId(UUID.randomUUID());
            shop.setStore(store);
            shop.setItemType(ITEM_TYPE_SHOP);
            shop.setSortOrder(nextRootSortOrder(store));
            pageMenuItemRepository.save(shop);
        }
    }

    private int nextRootSortOrder(Store store) {
        return pageMenuItems(store).stream()
                .filter(item -> item.getParent() == null)
                .map(PageMenuItem::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private List<Page> adminMenuEligiblePages(Store store) {
        return pageRepository.findAll().stream()
                .filter(page -> page.getDeletedAt() == null)
                .filter(page -> storeMatches(page.getStore(), store))
                .filter(Page::isShowInMenu)
                .toList();
    }

    private List<Page> storefrontMenuEligiblePages(Store store) {
        return adminMenuEligiblePages(store).stream()
                .filter(page -> Integer.valueOf(1).equals(page.getStatus()))
                .toList();
    }

    private Map<UUID, Page> pageLookup(Collection<Page> pages) {
        Map<UUID, Page> lookup = new LinkedHashMap<>();
        for (Page page : pages) {
            lookup.put(page.getId(), page);
        }
        return lookup;
    }

    private List<PageMenuItem> pageMenuItems(Store store) {
        return pageMenuItemRepository.findByStoreId(store.getId()).stream()
                .sorted(Comparator
                        .comparing((PageMenuItem item) -> item.getParent() == null ? "" : item.getParent().getId().toString())
                        .thenComparing(PageMenuItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PageMenuItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<Map<String, Object>> treeRows(
            List<PageMenuItem> items,
            Map<UUID, Page> allowedPages,
            Locale locale,
            boolean storefront,
            UUID parentId
    ) {
        return items.stream()
                .filter(item -> {
                    UUID itemParentId = item.getParent() == null ? null : item.getParent().getId();
                    return Objects.equals(itemParentId, parentId);
                })
                .sorted(Comparator
                        .comparing(PageMenuItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PageMenuItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> treeRow(item, items, allowedPages, locale, storefront))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> treeRow(
            PageMenuItem item,
            List<PageMenuItem> items,
            Map<UUID, Page> allowedPages,
            Locale locale,
            boolean storefront
    ) {
        if (ITEM_TYPE_SHOP.equals(item.getItemType())) {
            Map<String, Object> row = baseRow(item);
            row.put("title", "Shop");
            row.put("slug", "shop");
            row.put("home_page", false);
            row.put("removable", false);
            row.put("children", treeRows(items, allowedPages, locale, storefront, item.getId()));
            return row;
        }

        Page page = item.getPage();
        if (page == null || !allowedPages.containsKey(page.getId())) {
            return null;
        }

        Map<String, Object> row = baseRow(item);
        row.put("page_id", page.getId());
        row.put("title", translated(page.getTitle(), locale));
        row.put("slug", page.getSlug());
        row.put("home_page", page.isHomePage());
        row.put("removable", true);
        if (!storefront) {
            row.put("status", page.getStatus());
        }
        row.put("children", treeRows(items, allowedPages, locale, storefront, item.getId()));
        return row;
    }

    private Map<String, Object> baseRow(PageMenuItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("kind", item.getItemType());
        return row;
    }

    private Map<String, Object> pageRow(Page page, Locale locale) {
        return Map.of(
                "id", page.getId(),
                "title", translated(page.getTitle(), locale),
                "slug", page.getSlug(),
                "home_page", page.isHomePage(),
                "status", page.getStatus()
        );
    }

    private String translated(Map<String, String> value, Locale locale) {
        return TranslatedText.resolve(value, locale == null ? Locale.ENGLISH : locale);
    }

    private List<MenuDraftNode> parseDrafts(List<Map<String, Object>> payload) {
        if (payload == null) {
            return List.of();
        }
        return payload.stream().map(this::parseDraft).toList();
    }

    @SuppressWarnings("unchecked")
    private MenuDraftNode parseDraft(Map<String, Object> payload) {
        if (payload == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Menu items must be objects.");
        }
        String kind = string(payload.get("kind"));
        if (!ITEM_TYPE_PAGE.equals(kind) && !ITEM_TYPE_SHOP.equals(kind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Menu items must be either page or shop.");
        }
        UUID pageId = null;
        if (ITEM_TYPE_PAGE.equals(kind)) {
            pageId = uuid(payload.get("page_id"));
            if (pageId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Page menu items must include page_id.");
            }
        }
        List<Map<String, Object>> children = payload.get("children") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
        return new MenuDraftNode(kind, pageId, parseDrafts(children));
    }

    private void validateDrafts(List<MenuDraftNode> roots) {
        long shopCount = flatten(roots).stream()
                .filter(node -> ITEM_TYPE_SHOP.equals(node.kind()))
                .count();
        if (shopCount != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The menu must contain exactly one shop item.");
        }

        Set<UUID> seenPages = new LinkedHashSet<>();
        for (MenuDraftNode node : flatten(roots)) {
            if (ITEM_TYPE_PAGE.equals(node.kind()) && !seenPages.add(node.pageId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Pages can only appear once in the menu.");
            }
        }
    }

    private List<MenuDraftNode> flatten(List<MenuDraftNode> roots) {
        List<MenuDraftNode> nodes = new ArrayList<>();
        for (MenuDraftNode node : roots) {
            nodes.add(node);
            nodes.addAll(flatten(node.children()));
        }
        return nodes;
    }

    private void persistDrafts(List<MenuDraftNode> nodes, PageMenuItem parent, Store store) {
        for (int index = 0; index < nodes.size(); index++) {
            MenuDraftNode draft = nodes.get(index);
            PageMenuItem item = new PageMenuItem();
            item.setId(UUID.randomUUID());
            item.setStore(store);
            item.setParent(parent);
            item.setItemType(draft.kind());
            item.setSortOrder(index);
            if (draft.pageId() != null) {
                item.setPage(pageRepository.findById(draft.pageId())
                        .filter(page -> storeMatches(page.getStore(), store))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected page is invalid.")));
            }
            PageMenuItem saved = pageMenuItemRepository.save(item);
            persistDrafts(draft.children(), saved, store);
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Store currentStore() {
        return adminStoreContext.activeStoreForCurrentRequest()
                .or(() -> adminStoreContext.defaultStore())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available."));
    }

    private boolean storeMatches(Store candidate, Store activeStore) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        return candidate != null && activeStore != null && Objects.equals(candidate.getId(), activeStore.getId());
    }

    private record MenuDraftNode(String kind, UUID pageId, List<MenuDraftNode> children) {
    }
}
