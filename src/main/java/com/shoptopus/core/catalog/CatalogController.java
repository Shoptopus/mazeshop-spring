package com.shoptopus.core.catalog;

import com.shoptopus.core.admin.AdminAccessService;
import com.shoptopus.core.catalog.dto.ProductFilterRequest;
import com.shoptopus.core.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;
    private final ProductIntakeService productIntakeService;
    private final AdminAccessService adminAccessService;

    public CatalogController(
            CatalogService catalogService,
            ProductIntakeService productIntakeService,
            AdminAccessService adminAccessService
    ) {
        this.catalogService = catalogService;
        this.productIntakeService = productIntakeService;
        this.adminAccessService = adminAccessService;
    }

    @GetMapping("/products")
    public ApiResponse<?> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Locale locale
    ) {
        var filter = new ProductFilterRequest(search, categoryId, minPrice, maxPrice, page, size);
        var rows = catalogService.getProducts(filter, locale);
        var total = catalogService.countProducts(filter, locale);
        return ApiResponse.paged("Products", rows, rows.size(), total, null);
    }

    @GetMapping("/products/search/{search}")
    public ApiResponse<?> searchProducts(@PathVariable String search, Locale locale) {
        var filter = new ProductFilterRequest(search, null, null, null, 0, 20);
        var rows = catalogService.getProducts(filter, locale);
        return ApiResponse.paged("Products", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/products")
    public ApiResponse<?> createProduct(@RequestBody Map<String, Object> body, Authentication authentication) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Product created", productIntakeService.createProduct(body));
    }

    @GetMapping("/product/{id}")
    public ApiResponse<?> getProduct(@PathVariable UUID id, Locale locale) {
        return ApiResponse.ok("Product", catalogService.getProduct(id, locale));
    }

    @GetMapping("/product/slug/{slug}")
    public ApiResponse<?> getProductBySlug(@PathVariable String slug, Locale locale) {
        return ApiResponse.ok("Product", catalogService.getProductBySlug(slug, locale));
    }

    @GetMapping("/product_categories")
    public ApiResponse<?> getAllCategories(Locale locale) {
        var rows = catalogService.getCategories(locale);
        return ApiResponse.paged("Categories", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/product_category/{id}")
    public ApiResponse<?> getCategory(@PathVariable UUID id, Locale locale) {
        return ApiResponse.ok("Category", catalogService.getCategory(id, locale));
    }

    @GetMapping("/product_category/slug/{slug}")
    public ApiResponse<?> getCategoryBySlug(@PathVariable String slug, Locale locale) {
        return ApiResponse.ok("Category", catalogService.getCategoryBySlug(slug, locale));
    }

    @GetMapping("/product_tags")
    public ApiResponse<?> getAllTags(Locale locale) {
        var rows = catalogService.getTags(locale);
        return ApiResponse.paged("Tags", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/product_tag/{id}")
    public ApiResponse<?> getTag(@PathVariable UUID id, Locale locale) {
        return ApiResponse.ok("Tag", catalogService.getTag(id, locale));
    }
}
