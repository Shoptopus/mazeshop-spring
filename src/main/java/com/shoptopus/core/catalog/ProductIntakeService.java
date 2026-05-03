package com.shoptopus.core.catalog;

import com.shoptopus.core.admin.AdminProductService;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductIntakeService {

    private final AdminProductService adminProductService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public ProductIntakeService(
            AdminProductService adminProductService,
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository
    ) {
        this.adminProductService = adminProductService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> createProduct(Map<String, Object> body) {
        Validation validation = validate(body);
        if (!validation.errors().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed",
                    "Please correct the highlighted fields and try again.",
                    validation.errors()
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", validation.name());
        payload.put("slug", generateSlug(validation.slugSource()));
        payload.put("price", validation.priceGbp());
        payload.put("weight", validation.weightGrams());
        payload.put("headline", validation.headline());
        payload.put("subtitle", validation.subtitle());
        payload.put("short_description", validation.shortDescription());
        payload.put("description", validation.description());
        payload.put("meta_tags", validation.metaTags());
        payload.put("category_ids", matchedCategoryIds(validation.categories()));
        payload.put("stock", validation.stock());
        payload.put("status", 0);
        payload.put("customizable", false);
        payload.put("virtual", false);
        payload.put("customization_text", Map.of("en", ""));
        payload.put("tag_ids", List.of());

        Map<String, Object> created = adminProductService.create(payload);
        UUID productId = UUID.fromString(String.valueOf(created.get("id")));
        Product product = productRepository.findById(productId).orElseThrow();

        return Map.of(
                "id", product.getId(),
                "slug", product.getSlug(),
                "status", product.getStatus(),
                "status_label", "Draft"
        );
    }

    private Validation validate(Map<String, Object> body) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Object name = translationLike(body, "name", true, errors);
        BigDecimal priceGbp = decimal(body, "price_gbp", true, errors);
        Integer weightGrams = integer(body, "weight_grams", true, errors, 0);
        Object headline = translationLike(body, "headline", true, errors);
        Object subtitle = translationLike(body, "subtitle", true, errors);
        Object shortDescription = translationLike(body, "short_description", true, errors);
        Object description = translationLike(body, "description", true, errors);
        List<String> metaTags = stringList(body, "meta_tags", false, errors);
        List<String> categories = stringList(body, "categories", false, errors);
        Long stock = longValue(body, "stock", true, errors, 0L);

        if (priceGbp != null && priceGbp.signum() < 0) {
            addError(errors, "price_gbp", "Price must be zero or greater.");
        }
        if (weightGrams != null && weightGrams < 0) {
            addError(errors, "weight_grams", "Weight must be zero or greater.");
        }
        if (stock != null && stock < 0) {
            addError(errors, "stock", "Stock must be zero or greater.");
        }

        String slugSource = translationText(name);
        return new Validation(
                errors,
                name,
                slugSource,
                priceGbp,
                weightGrams,
                headline,
                subtitle,
                shortDescription,
                description,
                metaTags,
                categories,
                stock
        );
    }

    private Object translationLike(Map<String, Object> body, String key, boolean required, Map<String, List<String>> errors) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            if (required) {
                addError(errors, key, label(key) + " is required.");
            }
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> normalized = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> {
                String value = entryValue == null ? "" : String.valueOf(entryValue).trim();
                if (!value.isBlank()) {
                    normalized.put(String.valueOf(entryKey), value);
                }
            });
            if (normalized.isEmpty()) {
                addError(errors, key, label(key) + " must include at least one non-empty translation.");
                return null;
            }
            return normalized;
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            addError(errors, key, label(key) + " is required.");
            return null;
        }
        return value;
    }

    private BigDecimal decimal(Map<String, Object> body, String key, boolean required, Map<String, List<String>> errors) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            if (required) {
                addError(errors, key, label(key) + " is required.");
            }
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            addError(errors, key, label(key) + " must be a number.");
            return null;
        }
    }

    private Integer integer(Map<String, Object> body, String key, boolean required, Map<String, List<String>> errors, Integer minimum) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            if (required) {
                addError(errors, key, label(key) + " is required.");
            }
            return null;
        }
        Integer value;
        if (raw instanceof Number number) {
            if (number.doubleValue() % 1 != 0) {
                addError(errors, key, label(key) + " must be a whole number.");
                return null;
            }
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ex) {
                addError(errors, key, label(key) + " must be a whole number.");
                return null;
            }
        }
        if (minimum != null && value < minimum) {
            addError(errors, key, label(key) + " must be " + minimum + " or greater.");
        }
        return value;
    }

    private Long longValue(Map<String, Object> body, String key, boolean required, Map<String, List<String>> errors, Long minimum) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            if (required) {
                addError(errors, key, label(key) + " is required.");
            }
            return null;
        }
        Long value;
        if (raw instanceof Number number) {
            if (number.doubleValue() % 1 != 0) {
                addError(errors, key, label(key) + " must be a whole number.");
                return null;
            }
            value = number.longValue();
        } else {
            try {
                value = Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException ex) {
                addError(errors, key, label(key) + " must be a whole number.");
                return null;
            }
        }
        if (minimum != null && value < minimum) {
            addError(errors, key, label(key) + " must be " + minimum + " or greater.");
        }
        return value;
    }

    private List<String> stringList(Map<String, Object> body, String key, boolean required, Map<String, List<String>> errors) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            if (required) {
                addError(errors, key, label(key) + " is required.");
            }
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            addError(errors, key, label(key) + " must be a list of text values.");
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                continue;
            }
            normalized.add(text);
        }
        return normalized;
    }

    private List<String> matchedCategoryIds(List<String> categoryPaths) {
        List<ProductCategory> categories = categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .sorted(Comparator.comparing(category -> translated(category).toLowerCase(Locale.ROOT)))
                .toList();

        Set<UUID> matchedCategoryIds = new LinkedHashSet<>();
        Map<String, ProductCategory> chosenByContext = new LinkedHashMap<>();
        for (String categoryPath : categoryPaths) {
            List<String> segments = splitCategoryPath(categoryPath);
            ProductCategory previous = null;
            for (int i = 0; i < segments.size(); i++) {
                String segment = segments.get(i);
                String contextKey = categoryContextKey(previous, segment);
                ProductCategory chosen = chosenByContext.get(contextKey);
                if (chosen == null) {
                    chosen = chooseCategoryForSegment(segment, previous, categories).orElse(null);
                    if (chosen != null) {
                        chosenByContext.put(contextKey, chosen);
                    }
                }
                if (chosen != null) {
                    matchedCategoryIds.add(chosen.getId());
                    previous = chosen;
                } else {
                    previous = null;
                }
            }
        }
        return matchedCategoryIds.stream().map(UUID::toString).toList();
    }

    private java.util.Optional<ProductCategory> chooseCategoryForSegment(
            String segment,
            ProductCategory previous,
            List<ProductCategory> categories
    ) {
        if (segment == null || segment.isBlank()) {
            return java.util.Optional.empty();
        }
        List<ProductCategory> matches = categories.stream()
                .filter(category -> categoryNameMatches(category, segment))
                .toList();
        if (matches.isEmpty()) {
            return java.util.Optional.empty();
        }

        UUID previousId = previous == null ? null : previous.getId();
        List<ProductCategory> childrenOfPrevious = matches.stream()
                .filter(category -> {
                    UUID parentId = category.getParent() == null ? null : category.getParent().getId();
                    return java.util.Objects.equals(parentId, previousId);
                })
                .toList();
        if (!childrenOfPrevious.isEmpty()) {
            return java.util.Optional.of(bestCandidate(childrenOfPrevious));
        }

        if (previous != null) {
            if (matches.size() == 1) {
                return java.util.Optional.of(matches.get(0));
            }
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(bestCandidate(matches));
    }

    private ProductCategory bestCandidate(List<ProductCategory> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt(this::categoryDepth)
                        .thenComparing(ProductCategory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(category -> translated(category).toLowerCase(Locale.ROOT))
                        .thenComparing(ProductCategory::getSlug, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ProductCategory::getId))
                .findFirst()
                .orElseThrow();
    }

    private String categoryContextKey(ProductCategory previous, String segment) {
        String parentKey = previous == null ? "root" : previous.getId().toString();
        return parentKey + "|" + normalizeCategoryToken(segment);
    }

    private int categoryDepth(ProductCategory category) {
        int depth = 0;
        ProductCategory current = category;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private List<String> splitCategoryPath(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(categoryPath.split(">"))
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private boolean categoryNameMatches(ProductCategory category, String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        String normalizedSegment = normalizeCategoryToken(segment);
        String segmentSlug = slugify(segment);
        if (!segmentSlug.isBlank() && category.getSlug() != null) {
            String normalizedSlug = slugify(category.getSlug());
            if (normalizedSlug.equals(segmentSlug)) {
                return true;
            }
        }
        return category.getName().values().stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> {
                    String normalizedValue = normalizeCategoryToken(value);
                    return normalizedValue.equals(normalizedSegment) || slugify(value).equals(segmentSlug);
                });
    }

    private String translated(ProductCategory category) {
        return TranslatedText.resolve(category.getName(), Locale.ENGLISH);
    }

    private String generateSlug(String source) {
        String base = slugify(source);
        if (base.isBlank()) {
            base = "product";
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        int maxBaseLength = Math.max(1, 48 - suffix.length());
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength).replaceAll("-+$", "");
        }
        return base + "-" + suffix;
    }

    private String slugify(String input) {
        return input == null
                ? ""
                : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeCategoryToken(String input) {
        return input == null
                ? ""
                : input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String translationText(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object english = map.get("en");
            if (english != null && !String.valueOf(english).isBlank()) {
                return String.valueOf(english);
            }
            return map.values().stream()
                    .filter(value -> value != null && !String.valueOf(value).isBlank())
                    .map(String::valueOf)
                    .findFirst()
                    .orElse("");
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private void addError(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    private String label(String key) {
        return switch (key) {
            case "price_gbp" -> "Price in GBP";
            case "weight_grams" -> "Weight in grams";
            case "short_description" -> "Short description";
            case "meta_tags" -> "Meta tags";
            default -> key.replace('_', ' ').substring(0, 1).toUpperCase(Locale.ROOT) + key.replace('_', ' ').substring(1);
        };
    }

    private record Validation(
            Map<String, List<String>> errors,
            Object name,
            String slugSource,
            BigDecimal priceGbp,
            Integer weightGrams,
            Object headline,
            Object subtitle,
            Object shortDescription,
            Object description,
            List<String> metaTags,
            List<String> categories,
            Long stock
    ) {
    }
}
