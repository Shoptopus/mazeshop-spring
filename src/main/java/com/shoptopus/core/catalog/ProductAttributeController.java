package com.shoptopus.core.catalog;

import com.shoptopus.core.catalog.dto.ProductAttributeOptionResponse;
import com.shoptopus.core.catalog.dto.ProductAttributeResponse;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.i18n.TranslatedText;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProductAttributeController {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeOptionRepository optionRepository;

    public ProductAttributeController(ProductAttributeRepository attributeRepository, ProductAttributeOptionRepository optionRepository) {
        this.attributeRepository = attributeRepository;
        this.optionRepository = optionRepository;
    }

    @GetMapping("/product_attributes")
    public ApiResponse<?> getAll(Locale locale) {
        var rows = attributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream().map(attr -> new ProductAttributeResponse(
                attr.getId(),
                TranslatedText.resolve(attr.getName(), locale),
                attr.getSlug(),
                attr.getType(),
                attr.getImage(),
                optionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attr.getId()).stream().map(o ->
                        new ProductAttributeOptionResponse(o.getId(), TranslatedText.resolve(o.getName(), locale), o.getSlug(), o.getValue(), o.getImage())
                ).toList()
        )).toList();
        return ApiResponse.paged("Product attributes", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/product_attributes/product_category/{productCategoryId}")
    public ApiResponse<?> getAllForCategory(@PathVariable UUID productCategoryId, Locale locale) {
        return getAll(locale);
    }

    @GetMapping("/product_attribute/{id}")
    public ApiResponse<?> get(@PathVariable UUID id, Locale locale) {
        var attr = attributeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product attribute not found"));
        var row = new ProductAttributeResponse(
                attr.getId(),
                TranslatedText.resolve(attr.getName(), locale),
                attr.getSlug(),
                attr.getType(),
                attr.getImage(),
                optionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attr.getId()).stream().map(o ->
                        new ProductAttributeOptionResponse(o.getId(), TranslatedText.resolve(o.getName(), locale), o.getSlug(), o.getValue(), o.getImage())
                ).toList()
        );
        return ApiResponse.ok("Product attribute", row);
    }
}
