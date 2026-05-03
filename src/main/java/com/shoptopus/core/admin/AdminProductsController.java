package com.shoptopus.core.admin;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminProductsController {

    private final AdminSupportService adminSupportService;
    private final AdminAccessService adminAccessService;
    private final AdminProductService adminProductService;
    private final AdminProductVariantService adminProductVariantService;
    private final AdminPaidFileContentService adminPaidFileContentService;
    private final AdminReportingService adminReportingService;

    public AdminProductsController(
            AdminSupportService adminSupportService,
            AdminAccessService adminAccessService,
            AdminProductService adminProductService,
            AdminProductVariantService adminProductVariantService,
            AdminPaidFileContentService adminPaidFileContentService,
            AdminReportingService adminReportingService
    ) {
        this.adminSupportService = adminSupportService;
        this.adminAccessService = adminAccessService;
        this.adminProductService = adminProductService;
        this.adminProductVariantService = adminProductVariantService;
        this.adminPaidFileContentService = adminPaidFileContentService;
        this.adminReportingService = adminReportingService;
    }

    @GetMapping("/products")
    public Map<String, Object> products(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "products.view");
        return adminProductService.list();
    }

    @GetMapping("/products/summary")
    public ApiResponse<?> productsSummary(org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Products summary", adminProductService.summary());
    }

    @PostMapping("/products/bulk/archive")
    public ApiResponse<?> bulkArchive(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Archived", adminProductService.bulkArchive(body));
    }

    @DeleteMapping("/products/bulk")
    public ApiResponse<?> bulkDelete(org.springframework.security.core.Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Deleted", adminProductService.bulkDelete(body));
    }

    @PostMapping("/product")
    public ApiResponse<?> create(org.springframework.security.core.Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Created", adminProductService.create(body));
    }

    @GetMapping("/product/{product}")
    public ApiResponse<?> show(org.springframework.security.core.Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Product", adminProductService.show(product));
    }

    @PatchMapping("/product/{product}")
    public ApiResponse<?> update(org.springframework.security.core.Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Updated", adminProductService.update(product, body));
    }

    @PostMapping("/product/{product}")
    public ApiResponse<?> updateViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) { return update(authentication, product, body); }

    @DeleteMapping("/product/{product}")
    public ApiResponse<?> delete(org.springframework.security.core.Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Deleted", adminProductService.delete(product));
    }

    @GetMapping("/product/{product}/insights")
    public ApiResponse<?> insights(org.springframework.security.core.Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return ApiResponse.ok("Insights", adminReportingService.productInsights(product));
    }

    @GetMapping("/product/{product}/paid-contents")
    public ApiResponse<?> paidContents(org.springframework.security.core.Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Paid contents", adminPaidFileContentService.list(product));
    }

    @PostMapping("/product/{product}/paid-content")
    public ApiResponse<?> createPaidContent(org.springframework.security.core.Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Created", adminPaidFileContentService.create(product, body));
    }

    @PatchMapping("/product/{product}/paid-content/{paidFileContent}")
    public ApiResponse<?> updatePaidContent(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String paidFileContent, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Updated", adminPaidFileContentService.update(product, paidFileContent, body));
    }

    @PostMapping("/product/{product}/paid-content/{paidFileContent}")
    public ApiResponse<?> updatePaidContentViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String paidFileContent, @RequestBody Map<String, Object> body) { return updatePaidContent(authentication, product, paidFileContent, body); }

    @DeleteMapping("/product/{product}/paid-content/{paidFileContent}")
    public ApiResponse<?> deletePaidContent(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String paidFileContent) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Deleted", adminPaidFileContentService.delete(product, paidFileContent));
    }

    @GetMapping("/product/{product}/paid-content/{paidFileContent}/download")
    public ApiResponse<?> downloadPaidContent(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String paidFileContent) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Download", adminPaidFileContentService.download(product, paidFileContent));
    }

    @GetMapping("/product/{product}/variants")
    public ApiResponse<?> listVariants(org.springframework.security.core.Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Variants", adminProductVariantService.list(product));
    }

    @PostMapping("/product/{product}/variant")
    public ApiResponse<?> createVariant(org.springframework.security.core.Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Created", adminProductVariantService.create(product, body));
    }

    @GetMapping("/product/{product}/variant/{variant}")
    public ApiResponse<?> showVariant(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String variant) {
        adminAccessService.requirePermission(authentication, "products.view");
        return ApiResponse.ok("Variant", adminProductVariantService.show(product, variant));
    }

    @PatchMapping("/product/{product}/variant/{variant}")
    public ApiResponse<?> updateVariant(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String variant, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Updated", adminProductVariantService.update(product, variant, body));
    }

    @PostMapping("/product/{product}/variant/{variant}")
    public ApiResponse<?> updateVariantViaPost(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String variant, @RequestBody Map<String, Object> body) { return updateVariant(authentication, product, variant, body); }

    @DeleteMapping("/product/{product}/variant/{variant}")
    public ApiResponse<?> deleteVariant(org.springframework.security.core.Authentication authentication, @PathVariable String product, @PathVariable String variant) {
        adminAccessService.requirePermission(authentication, "products.manage");
        return ApiResponse.ok("Deleted", adminProductVariantService.delete(product, variant));
    }
}
