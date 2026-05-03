package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.FileContent;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.files.repository.FileContentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminFileService {

    private final FileContentRepository fileContentRepository;
    private final ManagedFileService managedFileService;

    public AdminFileService(FileContentRepository fileContentRepository, ManagedFileService managedFileService) {
        this.fileContentRepository = fileContentRepository;
        this.managedFileService = managedFileService;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = fileContentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "files");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    public Map<String, Object> show(String id) {
        UUID fileId = uuid(id).orElse(null);
        if (fileId == null) {
            return Map.of("id", id);
        }
        return fileContentRepository.findByIdAndDeletedAtIsNull(fileId)
                .map(this::toRow)
                .orElseGet(() -> Map.of("id", fileId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        String name = requireName(body);
        FileContent file = new FileContent();
        file.setId(UUID.randomUUID());
        file.setName(name);
        file.setOriginalName(str(body, "original_name", name));
        file.setMimeType(str(body, "mime_type", "application/octet-stream"));
        file.setSize(longValue(body, "size", 0L));
        file.setPath(str(body, "path", "/tmp/" + name));
        file.setSlug(str(body, "slug", "file-" + file.getId()));
        file.setType(str(body, "type", null));
        file.setFileableType(str(body, "fileable_type", null));
        file.setFileableId(uuid(str(body, "fileable_id", null)).orElse(null));
        fileContentRepository.save(file);
        return Map.of("id", file.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID fileId = uuid(id).orElse(null);
        if (fileId == null) {
            return Map.of("id", id);
        }
        Optional<FileContent> file = fileContentRepository.findByIdAndDeletedAtIsNull(fileId);
        if (file.isEmpty()) {
            return Map.of("id", fileId);
        }
        FileContent entity = file.orElseThrow();
        if (body != null && body.containsKey("name")) {
            String name = str(body, "name", null);
            if (name == null || name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            entity.setName(name);
        }
        if (body != null && body.containsKey("mime_type")) {
            entity.setMimeType(str(body, "mime_type", entity.getMimeType()));
        }
        if (body != null && body.containsKey("original_name")) {
            entity.setOriginalName(str(body, "original_name", entity.getOriginalName()));
        }
        if (body != null && body.containsKey("size")) {
            entity.setSize(longValue(body, "size", entity.getSize()));
        }
        if (body != null && body.containsKey("path")) {
            entity.setPath(str(body, "path", entity.getPath()));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(str(body, "slug", entity.getSlug()));
        }
        if (body != null && body.containsKey("type")) {
            entity.setType(str(body, "type", entity.getType()));
        }
        if (body != null && body.containsKey("fileable_type")) {
            entity.setFileableType(str(body, "fileable_type", entity.getFileableType()));
        }
        if (body != null && body.containsKey("fileable_id")) {
            entity.setFileableId(uuid(str(body, "fileable_id", null)).orElse(null));
        }
        fileContentRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID fileId = uuid(id).orElse(null);
        if (fileId == null) {
            return Map.of("id", id);
        }
        managedFileService.deleteById(fileId);
        return Map.of("id", fileId);
    }

    public Map<String, Object> download(String id) {
        UUID fileId = uuid(id).orElse(null);
        if (fileId == null) {
            return Map.of("id", id, "url", "/files/" + id);
        }
        return fileContentRepository.findByIdAndDeletedAtIsNull(fileId)
                .<Map<String, Object>>map(file -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", file.getId());
                    row.put("name", file.getName());
                    row.put("mime_type", file.getMimeType());
                    row.put("path", file.getPath());
                    row.put("url", managedFileService.publicUrl(file.getPath()));
                    return row;
                })
                .orElseGet(() -> Map.of("id", fileId, "url", "/files/" + fileId));
    }

    private Map<String, Object> toRow(FileContent file) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", file.getId());
        row.put("name", file.getName());
        row.put("mime_type", file.getMimeType());
        row.put("original_name", file.getOriginalName());
        row.put("size", file.getSize());
        row.put("path", file.getPath());
        row.put("slug", file.getSlug());
        row.put("type", file.getType());
        row.put("fileable_type", file.getFileableType());
        row.put("fileable_id", file.getFileableId());
        row.put("download_url", managedFileService.publicUrl(file.getPath()));
        return row;
    }

    private String requireName(Map<String, Object> body) {
        String name = str(body, "name", null);
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
        }
        return name;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private Long longValue(Map<String, Object> body, String key, Long fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(body.get(key)));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field must be a number.");
        }
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
}
