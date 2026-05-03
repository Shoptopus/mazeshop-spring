package com.shoptopus.core.storefront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.social.SocialProviderSettingsService;
import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import com.shoptopus.core.store.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StorefrontConfigService {

    private final List<String> languages;
    private final List<String> socialProviders;
    private final String defaultLanguage;
    private final String currency;
    private final String mediaPublicBaseUrl;
    private final String googleMapsApiKey;
    private final boolean multiStoreEnabled;
    private final SocialProviderSettingsService socialProviderSettingsService;
    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public StorefrontConfigService(
            @Value("${app.i18n.languages:en}") String languages,
            @Value("${app.storefront.social-providers:google,facebook}") String socialProviders,
            @Value("${app.storefront.currency:GBP}") String currency,
            @Value("${app.media.public-base-url:/media}") String mediaPublicBaseUrl,
            @Value("${app.integrations.google-maps-api-key:}") String googleMapsApiKey,
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            SocialProviderSettingsService socialProviderSettingsService,
            SystemSettingRepository systemSettingRepository,
            ObjectMapper objectMapper,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.languages = splitCsv(languages);
        this.socialProviders = splitCsv(socialProviders);
        this.defaultLanguage = this.languages.isEmpty() ? "en" : this.languages.get(0);
        this.currency = currency == null || currency.isBlank() ? "GBP" : currency;
        this.mediaPublicBaseUrl = mediaPublicBaseUrl == null || mediaPublicBaseUrl.isBlank() ? "/media" : mediaPublicBaseUrl;
        this.googleMapsApiKey = googleMapsApiKey;
        this.multiStoreEnabled = multiStoreEnabled;
        this.socialProviderSettingsService = socialProviderSettingsService;
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    public Map<String, Object> config(String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        Store store = resolvedStore.store();
        boolean useStoreBranding = multiStoreEnabled && store != null;
        var auth = new LinkedHashMap<String, Object>();
        auth.put("social_providers", configuredSocialProviders());

        var i18n = new LinkedHashMap<String, Object>();
        i18n.put("languages", languages.isEmpty() ? List.of("en") : languages);
        i18n.put("default_language", defaultLanguage);

        var media = new LinkedHashMap<String, Object>();
        media.put("public_base_url", mediaPublicBaseUrl);

        var integrations = new LinkedHashMap<String, Object>();
        integrations.put("google_maps_api_key", googleMapsApiKey);

        var commerce = new LinkedHashMap<String, Object>();
        commerce.put("currency", currency);

        var shop = new LinkedHashMap<String, Object>();
        shop.put("name", useStoreBranding ? storefrontStoreResolver.displayName(store) : stringSetting("shop.name"));
        shop.put("logo", useStoreBranding && store.getLogo() != null && !store.getLogo().isEmpty() ? store.getLogo() : objectSetting("shop.logo"));
        shop.put("logo_light", objectSetting("shop.logo_light"));
        shop.put("logo_dark", objectSetting("shop.logo_dark"));
        shop.put("tagline", useStoreBranding && store.getTagline() != null ? store.getTagline() : stringSetting("shop.tagline"));
        shop.put("description", useStoreBranding ? store.getDescription() : stringSetting("shop.description"));
        shop.put("social_platforms", useStoreBranding && store.getSocialPlatforms() != null && !store.getSocialPlatforms().isEmpty()
                ? store.getSocialPlatforms()
                : listSetting("shop.social_platforms"));
        shop.put("contact_phone", useStoreBranding && store.getContactPhone() != null && !store.getContactPhone().isBlank()
                ? store.getContactPhone()
                : stringSetting("shop.contact_phone"));
        shop.put("contact_address", useStoreBranding && store.getContactAddress() != null && !store.getContactAddress().isBlank()
                ? store.getContactAddress()
                : stringSetting("shop.contact_address"));
        shop.put("theme", useStoreBranding && store.getTheme() != null && !store.getTheme().isEmpty()
                ? store.getTheme()
                : objectSetting("shop.theme"));
        shop.put("multistore_enabled", multiStoreEnabled);
        shop.put("current_store", useStoreBranding ? storefrontStoreResolver.storeRow(store) : null);
        shop.put("is_superstore", resolvedStore.superstore());
        shop.put("stores", multiStoreEnabled
                ? storefrontStoreResolver.activeStores().stream().map(storefrontStoreResolver::storeRow).toList()
                : List.of());

        var data = new LinkedHashMap<String, Object>();
        data.put("auth", auth);
        data.put("i18n", i18n);
        data.put("media", media);
        data.put("integrations", integrations);
        data.put("commerce", commerce);
        data.put("shop", shop);
        return data;
    }

    public List<String> socialProviders() {
        return socialProviderSettingsService.enabledConfigs().stream()
                .map(config -> String.valueOf(config.getProvider()))
                .toList();
    }

    private List<String> configuredSocialProviders() {
        List<String> configured = socialProviderSettingsService.enabledConfigs().stream()
                .map(config -> String.valueOf(config.getProvider()))
                .toList();
        return configured.isEmpty() ? socialProviders : configured;
    }

    private String stringSetting(String key) {
        return systemSettingRepository.findByKeyAndDeletedAtIsNull(key)
                .map(SystemSetting::getValue)
                .orElse("");
    }

    private Object objectSetting(String key) {
        String raw = stringSetting(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private Object listSetting(String key) {
        String raw = stringSetting(key);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> splitCsv(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }
}
