package com.shoptopus.core.common.i18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TranslatedText {

    private TranslatedText() {
    }

    public static String resolve(Map<String, String> values, Locale locale) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        var language = locale == null ? "en" : locale.getLanguage();
        if (values.containsKey(language)) {
            return values.get(language);
        }
        if (values.containsKey("en")) {
            return values.get("en");
        }
        return values.values().stream().findFirst().orElse("");
    }

    public static Map<String, String> ensureMap(String raw, Locale locale) {
        var map = new HashMap<String, String>();
        map.put(locale == null ? "en" : locale.getLanguage(), raw == null ? "" : raw);
        return map;
    }
}
