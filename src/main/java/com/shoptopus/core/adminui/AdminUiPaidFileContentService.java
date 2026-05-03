package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminPaidFileContentService;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.PaidFileContent;
import com.shoptopus.core.files.repository.PaidFileContentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiPaidFileContentService {

    private final ProductRepository productRepository;
    private final PaidFileContentRepository paidFileContentRepository;
    private final AdminPaidFileContentService adminPaidFileContentService;

    public AdminUiPaidFileContentService(
            ProductRepository productRepository,
            PaidFileContentRepository paidFileContentRepository,
            AdminPaidFileContentService adminPaidFileContentService
    ) {
        this.productRepository = productRepository;
        this.paidFileContentRepository = paidFileContentRepository;
        this.adminPaidFileContentService = adminPaidFileContentService;
    }

    public Map<String, Object> list(String productId) {
        Product product = requireProduct(productId);
        List<Map<String, Object>> items = paidFileContentRepository.findByFileableTypeAndFileableId("product", product.getId()).stream()
                .sorted(Comparator.comparing(PaidFileContent::getCreatedAt))
                .map(this::row)
                .toList();
        return Map.of(
                "product", Map.of(
                        "id", product.getId(),
                        "name", product.getName(),
                        "slug", product.getSlug()
                ),
                "items", items
        );
    }

    public Map<String, Object> detail(String productId, String paidContentId) {
        Product product = requireProduct(productId);
        PaidFileContent content = requirePaidContent(product, paidContentId);
        Map<String, Object> row = new LinkedHashMap<>(row(content));
        row.put("created_at", content.getCreatedAt() == null ? null : content.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("updated_at", content.getUpdatedAt() == null ? null : content.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("download", adminPaidFileContentService.download(productId, paidContentId));
        return row;
    }

    public Map<String, Object> formOptions(String productId) {
        Product product = requireProduct(productId);
        return Map.of(
                "product", Map.of(
                        "id", product.getId(),
                        "name", product.getName(),
                        "slug", product.getSlug()
                ),
                "types", List.of(
                        Map.of("value", 1, "label", "Paid file"),
                        Map.of("value", 2, "label", "Downloadable bundle")
                )
        );
    }

    public Map<String, Object> create(String productId, Map<String, Object> body) {
        Object id = adminPaidFileContentService.create(productId, body).get("id");
        return detail(productId, String.valueOf(id));
    }

    public Map<String, Object> update(String productId, String paidContentId, Map<String, Object> body) {
        adminPaidFileContentService.update(productId, paidContentId, body);
        return detail(productId, paidContentId);
    }

    public Map<String, Object> delete(String productId, String paidContentId) {
        return adminPaidFileContentService.delete(productId, paidContentId);
    }

    private Product requireProduct(String productId) {
        try {
            return productRepository.findById(UUID.fromString(productId))
                    .filter(product -> product.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found");
        }
    }

    private PaidFileContent requirePaidContent(Product product, String paidContentId) {
        try {
            UUID id = UUID.fromString(paidContentId);
            return paidFileContentRepository.findById(id)
                    .filter(item -> "product".equals(item.getFileableType()))
                    .filter(item -> product.getId().equals(item.getFileableId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Paid content not found", "Paid content not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Paid content not found", "Paid content not found");
        }
    }

    private Map<String, Object> row(PaidFileContent content) {
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
}
