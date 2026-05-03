package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminProductCategoryImportService {

    private final ProductCategoryRepository categoryRepository;

    public AdminProductCategoryImportService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> importTree(Map<String, Object> body) {
        List<String> errors = new ArrayList<>();
        List<ImportNode> nodes = collectNodes(body, errors);
        if (!errors.isEmpty()) {
            throw validationFailed(errors);
        }

        Map<String, ProductCategory> existingBySlug = categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .filter(category -> category.getSlug() != null && !category.getSlug().isBlank())
                .collect(LinkedHashMap::new, (map, category) -> map.putIfAbsent(category.getSlug(), category), Map::putAll);

        int created = 0;
        int updated = 0;
        Map<String, ProductCategory> importedBySlug = new HashMap<>();
        for (ImportNode node : nodes) {
            ProductCategory category = existingBySlug.get(node.slug());
            boolean isNew = category == null;
            if (isNew) {
                category = new ProductCategory();
                category.setId(UUID.randomUUID());
                created++;
            } else {
                updated++;
            }

            Map<String, String> name = category.getName() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(category.getName());
            name.put("en", node.englishName());
            category.setName(name);

            if (category.getDescription() == null || category.getDescription().isEmpty()) {
                category.setDescription(new LinkedHashMap<>(Map.of("en", "")));
            }

            category.setSlug(node.slug());
            category.setEnabled(true);
            category.setParent(node.parentSlug() == null ? null : importedBySlug.get(node.parentSlug()));

            categoryRepository.save(category);
            importedBySlug.put(node.slug(), category);
        }

        return Map.of(
                "status", "success",
                "imported", nodes.size(),
                "created", created,
                "updated", updated
        );
    }

    private List<ImportNode> collectNodes(Map<String, Object> body, List<String> errors) {
        if (body == null || body.isEmpty()) {
            errors.add("The request body must be a non-empty category tree object.");
            return List.of();
        }

        List<ImportNode> nodes = new ArrayList<>();
        Set<String> seenSlugs = new LinkedHashSet<>();
        visitLevel(body, List.of(), null, nodes, seenSlugs, errors);
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private void visitLevel(
            Map<String, Object> level,
            List<String> path,
            String parentSlug,
            List<ImportNode> nodes,
            Set<String> seenSlugs,
            List<String> errors
    ) {
        for (Map.Entry<String, Object> entry : level.entrySet()) {
            String rawName = entry.getKey();
            String name = rawName == null ? "" : rawName.trim();
            String pathLabel = pathLabel(path, rawName);
            if (name.isBlank()) {
                errors.add("Category names must not be blank at " + pathLabel + ".");
                continue;
            }

            List<String> nextPath = new ArrayList<>(path);
            nextPath.add(name);
            String slug = pathSlug(nextPath);
            if (slug.isBlank()) {
                errors.add("Category name \"" + name + "\" at " + pathLabel + " cannot be converted to a valid slug.");
            } else if (!seenSlugs.add(slug)) {
                errors.add("The generated slug \"" + slug + "\" is duplicated for " + String.join(" > ", nextPath) + ".");
            }

            Object childrenRaw = entry.getValue();
            if (!(childrenRaw instanceof Map<?, ?> childMap)) {
                errors.add("Category \"" + String.join(" > ", nextPath) + "\" must map to an object of child categories.");
                continue;
            }

            Map<String, Object> normalizedChildren = new LinkedHashMap<>();
            for (Map.Entry<?, ?> childEntry : childMap.entrySet()) {
                normalizedChildren.put(String.valueOf(childEntry.getKey()), childEntry.getValue());
            }

            nodes.add(new ImportNode(name, slug, parentSlug));
            visitLevel(normalizedChildren, nextPath, slug, nodes, seenSlugs, errors);
        }
    }

    private String pathSlug(List<String> path) {
        return path.stream()
                .map(this::slugify)
                .filter(segment -> !segment.isBlank())
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
    }

    private String slugify(String input) {
        return input == null
                ? ""
                : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String pathLabel(List<String> path, String current) {
        List<String> full = new ArrayList<>(path);
        full.add(current == null ? "(blank)" : current);
        return "\"" + String.join(" > ", full) + "\"";
    }

    private ApiException validationFailed(List<String> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", String.join(" ", errors));
    }

    private record ImportNode(String englishName, String slug, String parentSlug) {
    }
}
