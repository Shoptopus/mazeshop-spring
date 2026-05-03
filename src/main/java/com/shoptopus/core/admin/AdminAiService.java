package com.shoptopus.core.admin;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAiService {

    public Map<String, Object> translations(Map<String, Object> body) {
        Object text = body == null ? null : body.get("text");
        Object languages = body == null ? null : body.get("languages");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", text == null ? "" : String.valueOf(text));
        response.put("languages", languages instanceof List<?> list ? list : List.of("en"));
        response.put("translations", Map.of(
                "en", text == null ? "" : String.valueOf(text)
        ));
        return response;
    }

    public Map<String, Object> optimiseRewrite(Map<String, Object> body) {
        String text = body == null || body.get("text") == null ? "" : String.valueOf(body.get("text"));
        String tone = body == null || body.get("tone") == null ? "neutral" : String.valueOf(body.get("tone"));
        return Map.of(
                "original", text,
                "tone", tone,
                "rewritten", rewrite(text, tone)
        );
    }

    private String rewrite(String text, String tone) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return switch (tone.toLowerCase()) {
            case "short", "concise" -> text.trim();
            case "formal" -> "Please note: " + text.trim();
            default -> text.trim();
        };
    }
}
