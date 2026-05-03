package com.shoptopus.core.adminui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.mail.EmailSettingsService;
import com.shoptopus.core.payment.PaymentProvider;
import com.shoptopus.core.payment.PaymentProviderConfig;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.social.SocialProviderSettingsService;
import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiSystemSettingsService {

    private static final String KEY_SHOP_NAME = "shop.name";
    private static final String KEY_SHOP_LOGO = "shop.logo";
    private static final String KEY_SHOP_LOGO_LIGHT = "shop.logo_light";
    private static final String KEY_SHOP_LOGO_DARK = "shop.logo_dark";
    private static final String KEY_SHOP_TAGLINE = "shop.tagline";
    private static final String KEY_SHOP_SOCIALS = "shop.social_platforms";
    private static final String KEY_SHOP_PHONE = "shop.contact_phone";
    private static final String KEY_SHOP_ADDRESS = "shop.contact_address";
    private static final String KEY_SHOP_THEME = "shop.theme";

    private final SystemSettingRepository systemSettingRepository;
    private final PaymentProviderRepository paymentProviderRepository;
    private final SocialProviderSettingsService socialProviderSettingsService;
    private final EmailSettingsService emailSettingsService;
    private final ObjectMapper objectMapper;

    public AdminUiSystemSettingsService(
            SystemSettingRepository systemSettingRepository,
            PaymentProviderRepository paymentProviderRepository,
            SocialProviderSettingsService socialProviderSettingsService,
            EmailSettingsService emailSettingsService,
            ObjectMapper objectMapper
    ) {
        this.systemSettingRepository = systemSettingRepository;
        this.paymentProviderRepository = paymentProviderRepository;
        this.socialProviderSettingsService = socialProviderSettingsService;
        this.emailSettingsService = emailSettingsService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail() {
        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("name", stringSetting(KEY_SHOP_NAME, ""));
        shop.put("logo", mediaSetting(KEY_SHOP_LOGO));
        shop.put("logo_light", mediaSetting(KEY_SHOP_LOGO_LIGHT));
        shop.put("logo_dark", mediaSetting(KEY_SHOP_LOGO_DARK));
        shop.put("tagline", stringSetting(KEY_SHOP_TAGLINE, ""));
        shop.put("social_platforms", jsonList(KEY_SHOP_SOCIALS));
        shop.put("contact_phone", stringSetting(KEY_SHOP_PHONE, ""));
        shop.put("contact_address", stringSetting(KEY_SHOP_ADDRESS, ""));
        shop.put("theme", jsonObject(KEY_SHOP_THEME, defaultTheme()));

        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("social_providers", socialProviderSettingsService.adminConfigs().stream().map(socialProviderSettingsService::toAdminRow).toList());

        return Map.of(
                "shop", shop,
                "auth", auth,
                "email", emailSettingsService.adminSettings(),
                "payment_providers", paymentProviderRepository.findAllByOrderByNameAsc().stream().map(this::providerRow).toList()
        );
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(Map<String, Object> body) {
        Map<String, Object> shop = map(body == null ? null : body.get("shop"));
        Map<String, Object> auth = map(body == null ? null : body.get("auth"));
        Map<String, Object> email = map(body == null ? null : body.get("email"));

        if (body != null && body.containsKey("payment_providers")) {
            Object rawProviders = body.get("payment_providers");
            if (!(rawProviders instanceof List<?> providers)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The payment_providers field is invalid.");
            }
            for (Object rawProvider : providers) {
                updatePaymentProvider(map(rawProvider));
            }
        }

        if (shop != null) {
            writeSetting(KEY_SHOP_NAME, str(shop.get("name")));
            writeSetting(KEY_SHOP_LOGO, objectString(shop.get("logo")));
            writeSetting(KEY_SHOP_LOGO_LIGHT, objectString(shop.get("logo_light")));
            writeSetting(KEY_SHOP_LOGO_DARK, objectString(shop.get("logo_dark")));
            writeSetting(KEY_SHOP_TAGLINE, str(shop.get("tagline")));
            writeSetting(KEY_SHOP_SOCIALS, json(shop.get("social_platforms")));
            writeSetting(KEY_SHOP_PHONE, str(shop.get("contact_phone")));
            writeSetting(KEY_SHOP_ADDRESS, str(shop.get("contact_address")));
            writeSetting(KEY_SHOP_THEME, jsonObjectString(shop.get("theme"), "Unable to store theme setting."));
        }

        if (auth != null) {
            socialProviderSettingsService.saveAdminConfigs(auth.get("social_providers"));
        }
        if (email != null) {
            emailSettingsService.saveAdminSettings(email);
        }

        return detail();
    }

    private void updatePaymentProvider(Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Each payment provider needs a name.");
        }
        PaymentProvider provider = paymentProviderRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unknown payment provider: " + name));
        if (body.containsKey("enabled")) {
            provider.setEnabled(bool(body.get("enabled")));
        }
        if (body.containsKey("test_mode")) {
            provider.setTestMode(bool(body.get("test_mode")));
        }
        if (body.containsKey("configs")) {
            List<PaymentProviderConfig> nextConfigs = new ArrayList<>();
            Object rawConfigs = body.get("configs");
            if (!(rawConfigs instanceof List<?> configs)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The configs field is invalid.");
            }
            for (Object rawConfig : configs) {
                Map<String, Object> config = map(rawConfig);
                String setting = str(config.get("setting"));
                if (setting == null || setting.isBlank()) {
                    continue;
                }
                PaymentProviderConfig row = new PaymentProviderConfig();
                row.setPaymentProvider(provider);
                row.setSetting(setting);
                row.setValue(str(config.get("value")));
                row.setTestValue(str(config.get("test_value")));
                row.setPublicValue(bool(config.get("public_value")));
                nextConfigs.add(row);
            }
            provider.getConfigs().clear();
            provider.getConfigs().addAll(nextConfigs);
        }
        paymentProviderRepository.save(provider);
    }

    private Map<String, Object> providerRow(PaymentProvider provider) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", provider.getId());
        row.put("name", provider.getName());
        row.put("enabled", provider.isEnabled());
        row.put("test_mode", provider.isTestMode());
        List<Map<String, Object>> configs = provider.getConfigs().stream()
                .map(config -> {
                    Map<String, Object> configRow = new LinkedHashMap<>();
                    configRow.put("id", config.getId());
                    configRow.put("setting", config.getSetting());
                    configRow.put("value", config.getValue());
                    configRow.put("test_value", config.getTestValue());
                    configRow.put("public_value", config.isPublicValue());
                    return configRow;
                })
                .toList();
        if ("stripe".equalsIgnoreCase(provider.getName())) {
            configs = ensureStripeConfigs(configs);
        }
        row.put("configs", configs);
        return row;
    }

    private List<Map<String, Object>> ensureStripeConfigs(List<Map<String, Object>> current) {
        List<Map<String, Object>> next = new ArrayList<>(current);
        boolean legacyPublic = next.stream()
                .filter(row -> "publishable_key".equalsIgnoreCase(String.valueOf(row.get("setting"))) || "public_key".equalsIgnoreCase(String.valueOf(row.get("setting"))))
                .findFirst()
                .map(row -> bool(row.get("public_value")))
                .orElse(true);
        ensureConfig(next, "publishable_key", false);
        ensureConfig(next, "secret_key", false);
        ensureConfigValue(next, "live_public", legacyPublic ? "true" : "false");
        ensureConfigValue(next, "test_public", legacyPublic ? "true" : "false");
        next.sort((left, right) -> {
            String l = String.valueOf(left.get("setting"));
            String r = String.valueOf(right.get("setting"));
            if (l.equals(r)) {
                return 0;
            }
            if ("publishable_key".equals(l)) {
                return -1;
            }
            if ("publishable_key".equals(r)) {
                return 1;
            }
            if ("secret_key".equals(l)) {
                return -1;
            }
            if ("secret_key".equals(r)) {
                return 1;
            }
            if ("live_public".equals(l)) {
                return -1;
            }
            if ("live_public".equals(r)) {
                return 1;
            }
            if ("test_public".equals(l)) {
                return -1;
            }
            if ("test_public".equals(r)) {
                return 1;
            }
            return l.compareToIgnoreCase(r);
        });
        return next;
    }

    private void ensureConfig(List<Map<String, Object>> current, String setting, boolean publicValue) {
        boolean present = current.stream().anyMatch(row -> setting.equalsIgnoreCase(String.valueOf(row.get("setting"))));
        if (present) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("setting", setting);
        row.put("value", null);
        row.put("test_value", null);
        row.put("public_value", publicValue);
        current.add(row);
    }

    private void ensureConfigValue(List<Map<String, Object>> current, String setting, String value) {
        boolean present = current.stream().anyMatch(row -> setting.equalsIgnoreCase(String.valueOf(row.get("setting"))));
        if (present) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("setting", setting);
        row.put("value", value);
        row.put("test_value", null);
        row.put("public_value", false);
        current.add(row);
    }

    private Map<String, Object> map(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
            return result;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid payload section.");
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

    private String stringSetting(String key, String fallback) {
        return systemSettingRepository.findByKeyAndDeletedAtIsNull(key)
                .map(SystemSetting::getValue)
                .filter(value -> value != null)
                .orElse(fallback);
    }

    private Map<String, Object> mediaSetting(String key) {
        String raw = stringSetting(key, "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Map<String, Object>> jsonList(String key) {
        String raw = stringSetting(key, "");
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> stringListSetting(String key) {
        String raw = stringSetting(key, "");
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String objectString(Object value) {
        if (value == null) {
            return "";
        }
        return jsonObjectString(value, "Unable to store media setting.");
    }

    private String jsonObjectString(Object value, String developerMessage) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", developerMessage);
        }
    }

    private String json(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unable to store JSON setting.");
        }
    }

    private Map<String, Object> jsonObject(String key, Map<String, Object> fallback) {
        String raw = stringSetting(key, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return parsed == null || parsed.isEmpty() ? fallback : parsed;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Map<String, Object> defaultTheme() {
        Map<String, Object> lightPalette = new LinkedHashMap<>();
        lightPalette.put("background", "#f6f0e8");
        lightPalette.put("surface", "#fffaf5");
        lightPalette.put("text", "#231a13");
        lightPalette.put("muted", "#7f6b5b");
        lightPalette.put("accent", "#a45b34");
        lightPalette.put("accent_contrast", "#fff7ef");
        lightPalette.put("border", "#d9c8b8");

        Map<String, Object> darkPalette = new LinkedHashMap<>();
        darkPalette.put("background", "#18120e");
        darkPalette.put("surface", "#241b15");
        darkPalette.put("text", "#f7efe6");
        darkPalette.put("muted", "#c0ae9d");
        darkPalette.put("accent", "#d88757");
        darkPalette.put("accent_contrast", "#170f0a");
        darkPalette.put("border", "#4a382d");

        Map<String, Object> typography = new LinkedHashMap<>();
        typography.put("body_font", "sans");
        typography.put("display_font", "editorial");
        typography.put("base_font_size", 17);
        typography.put("heading_scale", "balanced");

        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("radius_scale", "soft");

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("content_width", "standard");

        Map<String, Object> buttons = new LinkedHashMap<>();
        buttons.put("button_style", "solid");

        Map<String, Object> theme = new LinkedHashMap<>();
        theme.put("mode", "auto");
        theme.put("light_palette", lightPalette);
        theme.put("dark_palette", darkPalette);
        theme.put("typography", typography);
        theme.put("shape", shape);
        theme.put("layout", layout);
        theme.put("buttons", buttons);
        return theme;
    }
}
