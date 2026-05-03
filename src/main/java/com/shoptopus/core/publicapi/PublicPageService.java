package com.shoptopus.core.publicapi;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.page.Page;
import com.shoptopus.core.page.PageMenuService;
import com.shoptopus.core.page.repository.PageRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.storefront.StorefrontStoreResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicPageService {

    private final PageRepository pageRepository;
    private final ManagedFileService managedFileService;
    private final PublicContentService publicContentService;
    private final PageMenuService pageMenuService;
    private final List<String> languages;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public PublicPageService(
            PageRepository pageRepository,
            ManagedFileService managedFileService,
            PublicContentService publicContentService,
            PageMenuService pageMenuService,
            @Value("${app.i18n.languages:en}") String languages,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.pageRepository = pageRepository;
        this.managedFileService = managedFileService;
        this.publicContentService = publicContentService;
        this.pageMenuService = pageMenuService;
        this.languages = Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(boolean menuOnly, Locale locale) {
        return list(menuOnly, locale, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(boolean menuOnly, Locale locale, String storeSlug) {
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return pageRepository.findAll().stream()
                .filter(page -> page.getDeletedAt() == null)
                .filter(page -> Integer.valueOf(1).equals(page.getStatus()))
                .filter(page -> matchesStore(page.getStore(), resolvedStore))
                .filter(page -> !menuOnly || page.isShowInMenu())
                .sorted(Comparator.comparing(Page::isShowInMenu).reversed().thenComparing(Page::getCreatedAt).reversed())
                .map(page -> listRow(page, resolvedLocale))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> footerPages(Locale locale, String storeSlug) {
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return pageRepository.findAll().stream()
                .filter(page -> page.getDeletedAt() == null)
                .filter(page -> Integer.valueOf(1).equals(page.getStatus()))
                .filter(page -> page.isAddToFooter())
                .filter(page -> matchesStore(page.getStore(), resolvedStore))
                .sorted(Comparator.comparing((Page page) -> translated(page.getTitle(), resolvedLocale), String.CASE_INSENSITIVE_ORDER))
                .map(page -> listRow(page, resolvedLocale))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> homePage(Locale locale) {
        return homePage(locale, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> homePage(Locale locale, String storeSlug) {
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return pageRepository.findAll().stream()
                .filter(page -> page.getDeletedAt() == null)
                .filter(page -> matchesStore(page.getStore(), resolvedStore))
                .filter(Page::isHomePage)
                .filter(page -> Integer.valueOf(1).equals(page.getStatus()))
                .sorted(Comparator.comparing(Page::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .map(page -> detail(page.getSlug(), resolvedLocale, storeSlug))
                .orElse(null);
    }

    @Transactional
    public List<Map<String, Object>> menuTree(Locale locale) {
        return menuTree(locale, null);
    }

    @Transactional
    public List<Map<String, Object>> menuTree(Locale locale, String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return resolvedStore.store() == null ? List.of() : pageMenuService.storefrontMenu(locale, resolvedStore.store());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String slug, Locale locale) {
        return detail(slug, locale, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String slug, Locale locale, String storeSlug) {
        Locale resolvedLocale = locale == null ? Locale.ENGLISH : locale;
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        Page page = pageRepository.findAll().stream()
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .filter(item -> slug.equals(item.getSlug()))
                .filter(item -> matchesStore(item.getStore(), resolvedStore))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Page not found", "Page not found"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", page.getId());
        row.put("title", translated(page.getTitle(), resolvedLocale));
        row.put("translations", page.getTitle());
        row.put("slug", page.getSlug());
        row.put("status", page.getStatus());
        row.put("show_in_menu", page.isShowInMenu());
        row.put("add_to_footer", page.isAddToFooter());
        row.put("version", page.getVersion());
        row.put("files", managedFileService.filesFor("page", page.getId(), List.of("page_file")));
        row.put("video", managedFileService.findAttachedSingle("page", page.getId(), "page_video"));
        row.put("contents", publicContentService.contents("page", page.getId(), resolvedLocale, storeSlug));
        row.put("created_at", page.getCreatedAt() == null ? null : page.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", page.getUpdatedAt() == null ? null : page.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return row;
    }

    private boolean matchesStore(Store pageStore, StorefrontStoreResolver.ResolvedStore resolvedStore) {
        if (!resolvedStore.multiStoreEnabled()) {
            return true;
        }
        if (resolvedStore.superstore()) {
            return pageStore != null && pageStore.getStatus() == com.shoptopus.core.store.StoreStatus.ACTIVE;
        }
        return pageStore != null && resolvedStore.store() != null && resolvedStore.store().getId().equals(pageStore.getId());
    }

    private Map<String, Object> listRow(Page page, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", page.getId());
        row.put("title", translated(page.getTitle(), locale));
        row.put("slug", page.getSlug());
        row.put("show_in_menu", page.isShowInMenu());
        row.put("add_to_footer", page.isAddToFooter());
        row.put("home_page", page.isHomePage());
        row.put("version", page.getVersion());
        return row;
    }

    private String translated(Map<String, String> value, Locale locale) {
        return TranslatedText.resolve(value, locale == null ? Locale.ENGLISH : locale);
    }
}
