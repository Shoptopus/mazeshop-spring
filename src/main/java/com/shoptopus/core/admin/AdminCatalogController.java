package com.shoptopus.core.admin;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminSupportService adminSupportService;
    private final AdminAccessService adminAccessService;
    private final AdminProductCategoryService adminProductCategoryService;
    private final AdminProductCategoryImportService adminProductCategoryImportService;
    private final AdminProductTagService adminProductTagService;
    private final AdminProductAttributeService adminProductAttributeService;

    public AdminCatalogController(
            AdminSupportService adminSupportService,
            AdminAccessService adminAccessService,
            AdminProductCategoryService adminProductCategoryService,
            AdminProductCategoryImportService adminProductCategoryImportService,
            AdminProductTagService adminProductTagService,
            AdminProductAttributeService adminProductAttributeService
    ) {
        this.adminSupportService = adminSupportService;
        this.adminAccessService = adminAccessService;
        this.adminProductCategoryService = adminProductCategoryService;
        this.adminProductCategoryImportService = adminProductCategoryImportService;
        this.adminProductTagService = adminProductTagService;
        this.adminProductAttributeService = adminProductAttributeService;
    }

    @GetMapping("/product-categories")
    public Map<String, Object> categories(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "product_categories.view");
        return adminProductCategoryService.list();
    }

    @GetMapping("/product-categories/select-data")
    public ApiResponse<?> categorySelect(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "product_categories.view");
        return ApiResponse.ok("Select data", adminProductCategoryService.selectData());
    }

    @PostMapping("/product-categories/bulk/availability")
    public ApiResponse<?> categoriesAvailability(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Updated", adminProductCategoryService.bulkAvailability(body));
    }

    @DeleteMapping("/product-categories/bulk")
    public ApiResponse<?> categoriesDelete(org.springframework.security.core.Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Deleted", adminProductCategoryService.bulkDelete(body));
    }

    @PostMapping("/product-category")
    public ApiResponse<?> createCategory(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Created", adminProductCategoryService.create(body));
    }

    @PostMapping("/product-categories/import")
    public ApiResponse<?> importCategories(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Imported", adminProductCategoryImportService.importTree(body));
    }

    @GetMapping("/product-category/{category}")
    public ApiResponse<?> showCategory(org.springframework.security.core.Authentication authentication, @PathVariable String category) {
        adminAccessService.requirePermission(authentication, "product_categories.view");
        return ApiResponse.ok("Category", adminProductCategoryService.show(category));
    }

    @PatchMapping("/product-category/{category}")
    public ApiResponse<?> updateCategory(org.springframework.security.core.Authentication authentication, @PathVariable String category, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Updated", adminProductCategoryService.update(category, body));
    }

    @PostMapping("/product-category/{category}")
    public ApiResponse<?> updateCategoryViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String category, @RequestBody Map<String, Object> body) { return updateCategory(authentication, category, body); }

    @DeleteMapping("/product-category/{category}")
    public ApiResponse<?> deleteCategory(org.springframework.security.core.Authentication authentication, @PathVariable String category) {
        adminAccessService.requirePermission(authentication, "product_categories.manage");
        return ApiResponse.ok("Deleted", adminProductCategoryService.delete(category));
    }

    @GetMapping("/product-tags")
    public Map<String, Object> tags(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "product_tags.view");
        return adminProductTagService.list();
    }

    @PostMapping("/product-tags/bulk/availability")
    public ApiResponse<?> tagsAvailability(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_tags.manage");
        return ApiResponse.ok("Updated", adminProductTagService.bulkAvailability(body));
    }

    @DeleteMapping("/product-tags/bulk")
    public ApiResponse<?> tagsDelete(org.springframework.security.core.Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_tags.manage");
        return ApiResponse.ok("Deleted", adminProductTagService.bulkDelete(body));
    }

    @PostMapping("/product-tag")
    public ApiResponse<?> createTag(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_tags.manage");
        return ApiResponse.ok("Created", adminProductTagService.create(body));
    }

    @GetMapping("/product-tag/{tag}")
    public ApiResponse<?> showTag(org.springframework.security.core.Authentication authentication, @PathVariable String tag) {
        adminAccessService.requirePermission(authentication, "product_tags.view");
        return ApiResponse.ok("Tag", adminProductTagService.show(tag));
    }

    @PatchMapping("/product-tag/{tag}")
    public ApiResponse<?> updateTag(org.springframework.security.core.Authentication authentication, @PathVariable String tag, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_tags.manage");
        return ApiResponse.ok("Updated", adminProductTagService.update(tag, body));
    }

    @PostMapping("/product-tag/{tag}")
    public ApiResponse<?> updateTagViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String tag, @RequestBody Map<String, Object> body) { return updateTag(authentication, tag, body); }

    @DeleteMapping("/product-tag/{tag}")
    public ApiResponse<?> deleteTag(org.springframework.security.core.Authentication authentication, @PathVariable String tag) {
        adminAccessService.requirePermission(authentication, "product_tags.manage");
        return ApiResponse.ok("Deleted", adminProductTagService.delete(tag));
    }

    @GetMapping("/product-attributes")
    public Map<String, Object> attributes(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "product_attributes.view");
        return adminProductAttributeService.list();
    }

    @PostMapping("/product-attributes/bulk/availability")
    public ApiResponse<?> attributesAvailability(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Updated", adminProductAttributeService.bulkAvailability(body));
    }

    @DeleteMapping("/product-attributes/bulk")
    public ApiResponse<?> attributesDelete(org.springframework.security.core.Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Deleted", adminProductAttributeService.bulkDelete(body));
    }

    @PostMapping("/product-attribute")
    public ApiResponse<?> createAttribute(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Created", adminProductAttributeService.create(body));
    }

    @GetMapping("/product-attribute/{attribute}")
    public ApiResponse<?> showAttribute(org.springframework.security.core.Authentication authentication, @PathVariable String attribute) {
        adminAccessService.requirePermission(authentication, "product_attributes.view");
        return ApiResponse.ok("Attribute", adminProductAttributeService.show(attribute));
    }

    @PatchMapping("/product-attribute/{attribute}")
    public ApiResponse<?> updateAttribute(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Updated", adminProductAttributeService.update(attribute, body));
    }

    @PostMapping("/product-attribute/{attribute}")
    public ApiResponse<?> updateAttributeViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @RequestBody Map<String, Object> body) { return updateAttribute(authentication, attribute, body); }

    @DeleteMapping("/product-attribute/{attribute}")
    public ApiResponse<?> deleteAttribute(org.springframework.security.core.Authentication authentication, @PathVariable String attribute) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Deleted", adminProductAttributeService.delete(attribute));
    }

    @GetMapping("/product-attribute/{attribute}/options")
    public ApiResponse<?> listOptions(org.springframework.security.core.Authentication authentication, @PathVariable String attribute) {
        adminAccessService.requirePermission(authentication, "product_attributes.view");
        return ApiResponse.ok("Options", adminProductAttributeService.listOptions(attribute));
    }

    @PostMapping("/product-attribute/{attribute}/option")
    public ApiResponse<?> createOption(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Created", adminProductAttributeService.createOption(attribute, body));
    }

    @GetMapping("/product-attribute/{attribute}/option/{option}")
    public ApiResponse<?> showOption(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @PathVariable String option) {
        adminAccessService.requirePermission(authentication, "product_attributes.view");
        return ApiResponse.ok("Option", adminProductAttributeService.showOption(attribute, option));
    }

    @PatchMapping("/product-attribute/{attribute}/option/{option}")
    public ApiResponse<?> updateOption(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @PathVariable String option, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Updated", adminProductAttributeService.updateOption(attribute, option, body));
    }

    @PostMapping("/product-attribute/{attribute}/option/{option}")
    public ApiResponse<?> updateOptionViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @PathVariable String option, @RequestBody Map<String, Object> body) { return updateOption(authentication, attribute, option, body); }

    @DeleteMapping("/product-attribute/{attribute}/option/{option}")
    public ApiResponse<?> deleteOption(org.springframework.security.core.Authentication authentication, @PathVariable String attribute, @PathVariable String option) {
        adminAccessService.requirePermission(authentication, "product_attributes.manage");
        return ApiResponse.ok("Deleted", adminProductAttributeService.deleteOption(attribute, option));
    }
}
