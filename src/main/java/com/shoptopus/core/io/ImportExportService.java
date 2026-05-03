package com.shoptopus.core.io;

import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ImportExportService {

    private final ProductCategoryRepository categoryRepository;

    public ImportExportService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Map<String, Object> exportCategoriesCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("slug,name_en,description_en,enabled\n");
        for (ProductCategory category : categoryRepository.findAll()) {
            csv.append(category.getSlug()).append(',')
                    .append(escape(category.getName().getOrDefault("en", ""))).append(',')
                    .append(escape(category.getDescription().getOrDefault("en", ""))).append(',')
                    .append(category.isEnabled())
                    .append('\n');
        }
        return Map.of("fileName", "product-categories.csv", "content", csv.toString());
    }

    public Map<String, Object> validateCategoriesCsv(MultipartFile file) {
        var errors = parseAndValidate(file);
        if (errors.isEmpty()) {
            return Map.of("status", "success");
        }
        return Map.of("status", "invalid data", "errors", String.join("; ", errors));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> importCategoriesCsv(MultipartFile file) {
        var rows = parse(file);
        var errors = validateRows(rows);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid data", String.join("; ", errors));
        }

        for (var row : rows) {
            var category = categoryRepository.findBySlugAndDeletedAtIsNull(row.slug()).orElseGet(ProductCategory::new);
            category.setSlug(row.slug());
            category.setName(Map.of("en", row.nameEn()));
            category.setDescription(Map.of("en", row.descriptionEn()));
            category.setEnabled(Boolean.TRUE.equals(row.enabled()));
            categoryRepository.save(category);
        }

        return Map.of("status", "success", "imported", rows.size());
    }

    private List<String> parseAndValidate(MultipartFile file) {
        return validateRows(parse(file));
    }

    private List<com.shoptopus.core.io.dto.CategoryImportRow> parse(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String[] lines = text.split("\\R");
            if (lines.length < 2) {
                return List.of();
            }
            List<com.shoptopus.core.io.dto.CategoryImportRow> rows = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                String[] cols = lines[i].split(",", -1);
                if (cols.length < 4) {
                    continue;
                }
                rows.add(new com.shoptopus.core.io.dto.CategoryImportRow(
                        cols[0].trim(),
                        cols[1].trim(),
                        cols[2].trim(),
                        Boolean.parseBoolean(cols[3].trim())
                ));
            }
            return rows;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file", ex.getMessage());
        }
    }

    private List<String> validateRows(List<com.shoptopus.core.io.dto.CategoryImportRow> rows) {
        List<String> errors = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        for (var row : rows) {
            if (row.slug() == null || row.slug().isBlank()) {
                errors.add("Missing slug");
                continue;
            }
            if (!row.slug().matches("[a-z0-9-]+")) {
                errors.add("Invalid slugs found: " + row.slug());
            }
            if (!slugs.add(row.slug())) {
                errors.add("Duplicate slug in file: " + row.slug());
            }
            if (row.nameEn() == null || row.nameEn().isBlank()) {
                errors.add("Missing name for slug: " + row.slug());
            }
        }
        return errors;
    }

    private String escape(String s) {
        if (s.contains(",") || s.contains("\"")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
