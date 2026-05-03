package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.PaidFileContent;
import com.shoptopus.core.files.repository.PaidFileContentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminPaidFileContentService {

    private static final String PRODUCT_FILEABLE_TYPE = "product";

    private final ProductRepository productRepository;
    private final PaidFileContentRepository paidFileContentRepository;

    public AdminPaidFileContentService(ProductRepository productRepository, PaidFileContentRepository paidFileContentRepository) {
        this.productRepository = productRepository;
        this.paidFileContentRepository = paidFileContentRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String productId) {
        Product product = requireProduct(productId);
        List<Map<String, Object>> data = paidFileContentRepository.findByFileableTypeAndFileableId(PRODUCT_FILEABLE_TYPE, product.getId()).stream()
                .map(this::toRow)
                .toList();
        return Map.of("product_id", product.getId(), "data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(String productId, Map<String, Object> body) {
        Product product = requireProduct(productId);
        PaidFileContent content = new PaidFileContent();
        content.setId(UUID.randomUUID());
        content.setFileableType(PRODUCT_FILEABLE_TYPE);
        content.setFileableId(product.getId());
        content.setOriginalFileName(required(body, "original_file_name", str(body, "name", null)));
        content.setFileName(required(body, "file_name", str(body, "name", null)));
        content.setUrl(str(body, "url", str(body, "path", "/files/" + content.getId())));
        content.setTitle(str(body, "title", content.getOriginalFileName()));
        content.setDescription(str(body, "description", null));
        content.setSize(str(body, "size", null));
        content.setType(intVal(body, "type", 1));
        paidFileContentRepository.save(content);
        return Map.of("id", content.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String productId, String paidFileContentId, Map<String, Object> body) {
        Product product = requireProduct(productId);
        UUID id = uuid(paidFileContentId).orElse(null);
        if (id == null) {
            return Map.of("id", paidFileContentId);
        }
        Optional<PaidFileContent> content = paidFileContentRepository.findById(id)
                .filter(item -> PRODUCT_FILEABLE_TYPE.equals(item.getFileableType()))
                .filter(item -> product.getId().equals(item.getFileableId()));
        if (content.isEmpty()) {
            return Map.of("id", id);
        }
        PaidFileContent entity = content.orElseThrow();
        if (body != null && body.containsKey("original_file_name")) {
            entity.setOriginalFileName(required(body, "original_file_name", entity.getOriginalFileName()));
        }
        if (body != null && body.containsKey("file_name")) {
            entity.setFileName(required(body, "file_name", entity.getFileName()));
        }
        if (body != null && body.containsKey("name")) {
            String value = required(body, "name", entity.getFileName());
            entity.setOriginalFileName(value);
            entity.setFileName(value);
        }
        if (body != null && (body.containsKey("url") || body.containsKey("path"))) {
            entity.setUrl(str(body, "url", str(body, "path", entity.getUrl())));
        }
        if (body != null && body.containsKey("title")) {
            entity.setTitle(str(body, "title", entity.getTitle()));
        }
        if (body != null && body.containsKey("description")) {
            entity.setDescription(str(body, "description", entity.getDescription()));
        }
        if (body != null && body.containsKey("size")) {
            entity.setSize(str(body, "size", entity.getSize()));
        }
        if (body != null && body.containsKey("type")) {
            entity.setType(intVal(body, "type", entity.getType()));
        }
        paidFileContentRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String productId, String paidFileContentId) {
        Product product = requireProduct(productId);
        UUID id = uuid(paidFileContentId).orElse(null);
        if (id == null) {
            return Map.of("id", paidFileContentId);
        }
        paidFileContentRepository.findById(id)
                .filter(item -> PRODUCT_FILEABLE_TYPE.equals(item.getFileableType()))
                .filter(item -> product.getId().equals(item.getFileableId()))
                .ifPresent(paidFileContentRepository::delete);
        return Map.of("id", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> download(String productId, String paidFileContentId) {
        Product product = requireProduct(productId);
        UUID id = uuid(paidFileContentId).orElse(null);
        if (id == null) {
            return Map.of("id", paidFileContentId, "url", "/files/" + paidFileContentId);
        }
        return paidFileContentRepository.findById(id)
                .filter(item -> PRODUCT_FILEABLE_TYPE.equals(item.getFileableType()))
                .filter(item -> product.getId().equals(item.getFileableId()))
                .map(content -> Map.<String, Object>of(
                        "id", content.getId(),
                        "url", content.getUrl(),
                        "name", content.getFileName(),
                        "original_file_name", content.getOriginalFileName()
                ))
                .orElseGet(() -> Map.of("id", id, "url", "/files/" + id));
    }

    private Product requireProduct(String productId) {
        UUID id = uuid(productId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_id is invalid."));
        return productRepository.findById(id)
                .filter(product -> product.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected product_id is invalid."));
    }

    private Map<String, Object> toRow(PaidFileContent content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", content.getId());
        row.put("product_id", content.getFileableId());
        row.put("url", content.getUrl());
        row.put("original_file_name", content.getOriginalFileName());
        row.put("file_name", content.getFileName());
        row.put("title", content.getTitle());
        row.put("description", content.getDescription());
        row.put("size", content.getSize());
        row.put("type", content.getType());
        return row;
    }

    private String required(Map<String, Object> body, String key, String fallback) {
        String value = str(body, key, fallback);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return value;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private Integer intVal(Map<String, Object> body, String key, Integer fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
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
