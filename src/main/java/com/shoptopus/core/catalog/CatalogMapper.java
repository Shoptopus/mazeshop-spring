package com.shoptopus.core.catalog;

import com.shoptopus.core.catalog.dto.*;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.publicapi.PublicContentService;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CatalogMapper {

    private final ManagedFileService managedFileService;
    private final PublicContentService publicContentService;

    public CatalogMapper(ManagedFileService managedFileService, PublicContentService publicContentService) {
        this.managedFileService = managedFileService;
        this.publicContentService = publicContentService;
    }

    public Map<String, String> toTranslationMap(TranslationInput input) {
        if (input == null) {
            return Map.of();
        }
        if (input.i18n() != null && !input.i18n().isEmpty()) {
            return input.i18n();
        }
        return Map.of(input.fallbackLanguage(), input.text());
    }

    public ProductResponse toProductResponse(Product p, Locale locale) {
        Set<java.util.UUID> categoryIds = p.getCategories().stream().map(ProductCategory::getId).collect(Collectors.toSet());
        Set<java.util.UUID> tagIds = p.getTags().stream().map(ProductTag::getId).collect(Collectors.toSet());
        return new ProductResponse(
                p.getId(),
                TranslatedText.resolve(p.getName(), locale),
                p.getSlug(),
                TranslatedText.resolve(p.getShortDescription(), locale),
                TranslatedText.resolve(p.getDescription(), locale),
                p.getPrice(),
                p.getPrice(),
                p.getStock(),
                p.getSku(),
                categoryIds,
                tagIds,
                productCover(p),
                publicContentService.contents("product", p.getId(), locale, p.getStore() == null ? null : p.getStore().getSlug())
        );
    }

    public ProductCategoryResponse toCategoryResponse(ProductCategory c, Locale locale) {
        return new ProductCategoryResponse(
                c.getId(),
                c.getParent() == null ? null : c.getParent().getId(),
                TranslatedText.resolve(c.getName(), locale),
                c.getSlug(),
                TranslatedText.resolve(c.getDescription(), locale),
                c.getMenuImage(),
                c.getHeaderImage(),
                publicContentService.contents("category", c.getId(), locale)
        );
    }

    public ProductTagResponse toTagResponse(ProductTag tag, Locale locale) {
        return new ProductTagResponse(
                tag.getId(),
                TranslatedText.resolve(tag.getName(), locale),
                tag.getSlug(),
                TranslatedText.resolve(tag.getDescription(), locale)
        );
    }

    private Map<String, Object> productCover(Product product) {
        Map<String, Object> attached = managedFileService.findAttachedSingle("product", product.getId(), "cover_photo");
        if (attached != null) {
            return attached;
        }
        if (product.getCoverPhoto() == null || product.getCoverPhoto().isEmpty()) {
            return null;
        }
        return product.getCoverPhoto();
    }
}
