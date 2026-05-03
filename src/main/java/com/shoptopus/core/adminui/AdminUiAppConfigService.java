package com.shoptopus.core.adminui;

import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUiAppConfigService {

    private final String googleMapsApiKey;
    private final String mediaPublicBaseUrl;
    private final List<String> languages;
    private final SystemSettingRepository systemSettingRepository;
    private final boolean multiStoreEnabled;

    public AdminUiAppConfigService(
            @Value("${app.integrations.google-maps-api-key:}") String googleMapsApiKey,
            @Value("${app.media.public-base-url:/media}") String mediaPublicBaseUrl,
            @Value("${app.i18n.languages:en}") String languages,
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            SystemSettingRepository systemSettingRepository
    ) {
        this.googleMapsApiKey = googleMapsApiKey;
        this.mediaPublicBaseUrl = mediaPublicBaseUrl;
        this.languages = Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        this.multiStoreEnabled = multiStoreEnabled;
        this.systemSettingRepository = systemSettingRepository;
    }

    public Map<String, Object> config() {
        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("google_maps_api_key", googleMapsApiKey);

        Map<String, Object> media = new LinkedHashMap<>();
        media.put("public_base_url", mediaPublicBaseUrl);

        Map<String, Object> i18n = new LinkedHashMap<>();
        i18n.put("languages", languages.isEmpty() ? List.of("en") : languages);
        i18n.put("default_language", languages.isEmpty() ? "en" : languages.get(0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("integrations", integrations);
        data.put("media", media);
        data.put("i18n", i18n);
        data.put("shop", Map.of(
                "name", stringSetting("shop.name")
        ));
        data.put("features", Map.of(
                "multistore_enabled", multiStoreEnabled
        ));
        return data;
    }

    private String stringSetting(String key) {
        return systemSettingRepository.findByKeyAndDeletedAtIsNull(key)
                .map(SystemSetting::getValue)
                .orElse("");
    }
}
