package com.shoptopus.core.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SocialProviderSettingsService {

    public static final String KEY_SOCIAL_PROVIDER_CONFIGS = "storefront.social_provider_configs";

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;
    private final String storefrontPublicBaseUrl;
    private final List<String> defaultEnabledProviders;

    public SocialProviderSettingsService(
            SystemSettingRepository systemSettingRepository,
            ObjectMapper objectMapper,
            @Value("${app.storefront.social-providers:google,facebook}") String defaultEnabledProviders,
            @Value("${app.storefront.public-base-url:http://localhost:8080}") String storefrontPublicBaseUrl
    ) {
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
        this.defaultEnabledProviders = List.of(defaultEnabledProviders.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        this.storefrontPublicBaseUrl = storefrontPublicBaseUrl == null || storefrontPublicBaseUrl.isBlank()
                ? "http://localhost:8080"
                : storefrontPublicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<SocialProviderConfig> adminConfigs() {
        return readConfigs().stream().map(this::normalizedConfig).toList();
    }

    @Transactional(readOnly = true)
    public List<SocialProviderConfig> enabledConfigs() {
        return adminConfigs().stream().filter(SocialProviderConfig::isEnabled).toList();
    }

    @Transactional(readOnly = true)
    public SocialProviderConfig enabledConfig(String provider) {
        return enabledConfigs().stream()
                .filter(config -> providerKey(config).equals(providerKey(provider)))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Provider is invalid or disabled."));
    }

    @Transactional("mainTransactionManager")
    public List<SocialProviderConfig> saveAdminConfigs(Object rawValue) {
        if (!(rawValue instanceof List<?> rows)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social_providers field is invalid.");
        }
        List<SocialProviderConfig> next = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> map = toMap(row);
            SocialProviderConfig config = new SocialProviderConfig();
            config.setProvider(str(map.get("provider")));
            config.setLabel(str(map.get("label")));
            config.setEnabled(bool(map.get("enabled")));
            config.setClientId(str(map.get("client_id")));
            config.setClientSecret(str(map.get("client_secret")));
            config.setAuthorizationUri(str(map.get("authorization_uri")));
            config.setTokenUri(str(map.get("token_uri")));
            config.setUserInfoUri(str(map.get("user_info_uri")));
            config.setScopes(stringList(map.get("scopes")));
            config.setUserIdAttribute(str(map.get("user_id_attribute")));
            config.setEmailAttribute(str(map.get("email_attribute")));
            config.setFirstNameAttribute(str(map.get("first_name_attribute")));
            config.setLastNameAttribute(str(map.get("last_name_attribute")));
            config.setNameAttribute(str(map.get("name_attribute")));
            next.add(normalizedConfig(config));
        }
        writeSetting(KEY_SOCIAL_PROVIDER_CONFIGS, serialize(next.stream().map(this::toStorageRow).toList()));
        return next;
    }

    public List<Map<String, Object>> publicProviders() {
        return enabledConfigs().stream().map(config -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", providerKey(config));
            row.put("label", config.getLabel());
            row.put("login_url", "/api/storefront/auth/social/" + providerKey(config));
            row.put("authorize_url", "/api/storefront/auth/social/" + providerKey(config) + "/authorize");
            row.put("callback_url", callbackUrl(config));
            return row;
        }).toList();
    }

    public String callbackUrl(String provider) {
        return callbackUrl(defaultConfig(provider));
    }

    public String callbackUrl(SocialProviderConfig config) {
        return storefrontPublicBaseUrl + "/api/storefront/auth/social/" + providerKey(config) + "/callback";
    }

    private List<SocialProviderConfig> readConfigs() {
        String raw = systemSettingRepository.findByKeyAndDeletedAtIsNull(KEY_SOCIAL_PROVIDER_CONFIGS)
                .map(SystemSetting::getValue)
                .orElse("");
        if (raw == null || raw.isBlank()) {
            return defaultConfigs();
        }
        try {
            List<SocialProviderConfig> configs = objectMapper.readValue(raw, new TypeReference<>() {});
            if (configs == null || configs.isEmpty()) {
                return defaultConfigs();
            }
            return configs;
        } catch (Exception ex) {
            return defaultConfigs();
        }
    }

    private List<SocialProviderConfig> defaultConfigs() {
        return List.of(defaultConfig("google"), defaultConfig("facebook"));
    }

    private SocialProviderConfig defaultConfig(String provider) {
        String key = providerKey(provider);
        SocialProviderConfig config = new SocialProviderConfig();
        config.setProvider(key);
        if ("facebook".equals(key)) {
            config.setLabel("Facebook");
            config.setEnabled(defaultEnabledProviders.contains("facebook"));
            config.setAuthorizationUri("https://www.facebook.com/v19.0/dialog/oauth");
            config.setTokenUri("https://graph.facebook.com/v19.0/oauth/access_token");
            config.setUserInfoUri("https://graph.facebook.com/me?fields=id,name,first_name,last_name,email");
            config.setScopes(List.of("public_profile", "email"));
            config.setUserIdAttribute("id");
            config.setEmailAttribute("email");
            config.setFirstNameAttribute("first_name");
            config.setLastNameAttribute("last_name");
            config.setNameAttribute("name");
            return config;
        }
        config.setLabel("Google");
        config.setEnabled(defaultEnabledProviders.contains("google"));
        config.setAuthorizationUri("https://accounts.google.com/o/oauth2/v2/auth");
        config.setTokenUri("https://oauth2.googleapis.com/token");
        config.setUserInfoUri("https://openidconnect.googleapis.com/v1/userinfo");
        config.setScopes(List.of("openid", "profile", "email"));
        config.setUserIdAttribute("sub");
        config.setEmailAttribute("email");
        config.setFirstNameAttribute("given_name");
        config.setLastNameAttribute("family_name");
        config.setNameAttribute("name");
        return config;
    }

    private SocialProviderConfig normalizedConfig(SocialProviderConfig config) {
        SocialProviderConfig defaults = defaultConfig(config.getProvider());
        SocialProviderConfig normalized = new SocialProviderConfig();
        normalized.setProvider(providerKey(config));
        normalized.setLabel(blank(config.getLabel()) ? defaults.getLabel() : config.getLabel().trim());
        normalized.setEnabled(config.isEnabled());
        normalized.setClientId(trim(config.getClientId()));
        normalized.setClientSecret(trim(config.getClientSecret()));
        normalized.setAuthorizationUri(blank(config.getAuthorizationUri()) ? defaults.getAuthorizationUri() : config.getAuthorizationUri().trim());
        normalized.setTokenUri(blank(config.getTokenUri()) ? defaults.getTokenUri() : config.getTokenUri().trim());
        normalized.setUserInfoUri(blank(config.getUserInfoUri()) ? defaults.getUserInfoUri() : config.getUserInfoUri().trim());
        normalized.setScopes(config.getScopes() == null || config.getScopes().isEmpty() ? defaults.getScopes() : config.getScopes().stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList());
        normalized.setUserIdAttribute(blank(config.getUserIdAttribute()) ? defaults.getUserIdAttribute() : config.getUserIdAttribute().trim());
        normalized.setEmailAttribute(blank(config.getEmailAttribute()) ? defaults.getEmailAttribute() : config.getEmailAttribute().trim());
        normalized.setFirstNameAttribute(blank(config.getFirstNameAttribute()) ? defaults.getFirstNameAttribute() : config.getFirstNameAttribute().trim());
        normalized.setLastNameAttribute(blank(config.getLastNameAttribute()) ? defaults.getLastNameAttribute() : config.getLastNameAttribute().trim());
        normalized.setNameAttribute(blank(config.getNameAttribute()) ? defaults.getNameAttribute() : config.getNameAttribute().trim());
        return normalized;
    }

    private Map<String, Object> toStorageRow(SocialProviderConfig config) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("provider", providerKey(config));
        row.put("label", config.getLabel());
        row.put("enabled", config.isEnabled());
        row.put("client_id", config.getClientId());
        row.put("client_secret", config.getClientSecret());
        row.put("authorization_uri", config.getAuthorizationUri());
        row.put("token_uri", config.getTokenUri());
        row.put("user_info_uri", config.getUserInfoUri());
        row.put("scopes", config.getScopes());
        row.put("user_id_attribute", config.getUserIdAttribute());
        row.put("email_attribute", config.getEmailAttribute());
        row.put("first_name_attribute", config.getFirstNameAttribute());
        row.put("last_name_attribute", config.getLastNameAttribute());
        row.put("name_attribute", config.getNameAttribute());
        return row;
    }

    public Map<String, Object> toAdminRow(SocialProviderConfig config) {
        Map<String, Object> row = toStorageRow(config);
        row.put("callback_url", callbackUrl(config));
        return row;
    }

    private void writeSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findByKeyAndDeletedAtIsNull(key).orElseGet(() -> {
            SystemSetting created = new SystemSetting();
            created.setId(UUID.randomUUID());
            created.setKey(key);
            return created;
        });
        setting.setValue(value == null ? "" : value);
        systemSettingRepository.save(setting);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unable to store social provider settings.");
        }
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
            return result;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social_providers field is invalid.");
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String string) {
            return List.of(string.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String providerKey(SocialProviderConfig config) {
        return providerKey(config == null ? null : config.getProvider());
    }

    private String providerKey(String provider) {
        return provider == null || provider.isBlank() ? "google" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
