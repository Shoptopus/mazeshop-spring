package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.files.ClearManagedFiles;
import com.shoptopus.core.files.ManagedFileService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminBannerService {

    private final BannerRepository bannerRepository;
    private final ManagedFileService managedFileService;

    public AdminBannerService(BannerRepository bannerRepository, ManagedFileService managedFileService) {
        this.bannerRepository = bannerRepository;
        this.managedFileService = managedFileService;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = bannerRepository.findAll().stream()
                .filter(banner -> banner.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "banners");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    public Map<String, Object> show(String id) {
        UUID bannerId = uuid(id).orElse(null);
        if (bannerId == null) {
            return Map.of("id", id);
        }
        return bannerRepository.findById(bannerId)
                .filter(banner -> banner.getDeletedAt() == null)
                .map(this::toRow)
                .orElseGet(() -> Map.of("id", bannerId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, String> name = nameMap(body);
        Banner banner = new Banner();
        banner.setId(UUID.randomUUID());
        banner.setName(name);
        banner.setSlug(str(body, "slug", "banner-" + banner.getId()));
        banner.setButtonText(optionalNameMap(body, "button_text"));
        banner.setContent(optionalNameMap(body, "content"));
        banner.setUrl(blankToNull(str(body, "url", null)));
        banner.setEnabled(bool(body, "enabled", true));
        applyManagedMedia(banner, body);
        bannerRepository.save(banner);
        return Map.of("id", banner.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID bannerId = uuid(id).orElse(null);
        if (bannerId == null) {
            return Map.of("id", id);
        }
        Optional<Banner> banner = bannerRepository.findById(bannerId).filter(item -> item.getDeletedAt() == null);
        if (banner.isEmpty()) {
            return Map.of("id", bannerId);
        }
        Banner entity = banner.orElseThrow();
        if (body != null && body.containsKey("name")) {
            entity.setName(nameMap(body));
        }
        if (body != null && body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.isEnabled()));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("button_text")) {
            entity.setButtonText(optionalNameMap(body, "button_text"));
        }
        if (body != null && body.containsKey("content")) {
            entity.setContent(optionalNameMap(body, "content"));
        }
        if (body != null && body.containsKey("url")) {
            entity.setUrl(blankToNull(str(body, "url", entity.getUrl())));
        }
        applyManagedMedia(entity, body);
        bannerRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    @ClearManagedFiles(fileableType = "banner")
    public Map<String, Object> delete(String id) {
        UUID bannerId = uuid(id).orElse(null);
        if (bannerId == null) {
            return Map.of("id", id);
        }
        bannerRepository.findById(bannerId).filter(item -> item.getDeletedAt() == null).ifPresent(banner -> {
            banner.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            bannerRepository.save(banner);
        });
        return Map.of("id", bannerId);
    }

    private Map<String, Object> toRow(Banner banner) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", banner.getId());
        row.put("name", banner.getName());
        row.put("button_text", banner.getButtonText());
        row.put("content", banner.getContent());
        row.put("slug", banner.getSlug());
        row.put("url", banner.getUrl());
        row.put("enabled", banner.isEnabled());
        row.put("click_count", banner.getClickCount());
        row.put("image", managedFileService.findAttachedSingle("banner", banner.getId(), "banner_image"));
        row.put("video", managedFileService.findAttachedSingle("banner", banner.getId(), "banner_video"));
        return row;
    }

    private void applyManagedMedia(Banner banner, Map<String, Object> body) {
        if (body == null) {
            return;
        }
        if (body.containsKey("image")) {
            Map<String, Object> image = normalizedFile(body.get("image"), "image");
            Map<String, Object> attached = managedFileService.attachSingle("banner", banner.getId(), "banner_image", image);
            banner.setImage(attached == null ? new LinkedHashMap<>() : attached);
        }
        if (body.containsKey("video")) {
            Map<String, Object> video = normalizedFile(body.get("video"), "video");
            managedFileService.attachSingle("banner", banner.getId(), "banner_video", video);
        }
    }

    private Map<String, Object> normalizedFile(Object rawFile, String fieldName) {
        if (rawFile == null) {
            return null;
        }
        if (rawFile instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + fieldName + " field is invalid.");
    }

    private Map<String, String> nameMap(Map<String, Object> body) {
        return requiredTranslationMap(body, "name");
    }

    private Map<String, String> optionalNameMap(Map<String, Object> body, String key) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) {
                return new LinkedHashMap<>();
            }
            return Map.of("en", normalized);
        }
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, String> value = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> value.put(String.valueOf(entryKey), String.valueOf(entryValue)));
            return value;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
    }

    private Map<String, String> requiredTranslationMap(Map<String, Object> body, String key) {
        Object raw = body == null ? null : body.get(key);
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, String> value = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> value.put(String.valueOf(entryKey), String.valueOf(entryValue)));
            return value;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
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
        return raw instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(raw));
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
