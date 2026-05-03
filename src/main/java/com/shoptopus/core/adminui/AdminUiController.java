package com.shoptopus.core.adminui;

import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-ui")
public class AdminUiController {

    private final AdminUiSessionService adminUiSessionService;
    private final AuthService authService;
    private final AdminUiProductService adminUiProductService;
    private final AdminUiOrderService adminUiOrderService;
    private final AdminUiPeopleService adminUiPeopleService;
    private final AdminUiPaymentService adminUiPaymentService;
    private final AdminUiVoucherDiscountService adminUiVoucherDiscountService;
    private final AdminUiDeliveryService adminUiDeliveryService;
    private final AdminUiRolePermissionService adminUiRolePermissionService;
    private final AdminUiSupportRecordsService adminUiSupportRecordsService;
    private final AdminUiMarketingService adminUiMarketingService;
    private final AdminUiAppConfigService adminUiAppConfigService;
    private final AdminUiMediaService adminUiMediaService;
    private final AdminUiProductCategoryService adminUiProductCategoryService;
    private final AdminUiProductTagService adminUiProductTagService;
    private final AdminUiProductAttributeService adminUiProductAttributeService;
    private final AdminUiProductVariantService adminUiProductVariantService;
    private final AdminUiPaidFileContentService adminUiPaidFileContentService;
    private final AdminUiSupportChannelsService adminUiSupportChannelsService;
    private final AdminUiContentService adminUiContentService;
    private final AdminUiPageService adminUiPageService;
    private final AdminUiReportingService adminUiReportingService;
    private final AdminUiSystemSettingsService adminUiSystemSettingsService;
    private final AdminUiMessagingService adminUiMessagingService;
    private final AdminUiStoreService adminUiStoreService;
    private final AdminUiGoogleMerchantService adminUiGoogleMerchantService;

    public AdminUiController(
            AdminUiSessionService adminUiSessionService,
            AuthService authService,
            AdminUiProductService adminUiProductService,
            AdminUiOrderService adminUiOrderService,
            AdminUiPeopleService adminUiPeopleService,
            AdminUiPaymentService adminUiPaymentService,
            AdminUiVoucherDiscountService adminUiVoucherDiscountService,
            AdminUiDeliveryService adminUiDeliveryService,
            AdminUiRolePermissionService adminUiRolePermissionService,
            AdminUiSupportRecordsService adminUiSupportRecordsService,
            AdminUiMarketingService adminUiMarketingService,
            AdminUiAppConfigService adminUiAppConfigService,
            AdminUiMediaService adminUiMediaService,
            AdminUiProductCategoryService adminUiProductCategoryService,
            AdminUiProductTagService adminUiProductTagService,
            AdminUiProductAttributeService adminUiProductAttributeService,
            AdminUiProductVariantService adminUiProductVariantService,
            AdminUiPaidFileContentService adminUiPaidFileContentService,
            AdminUiSupportChannelsService adminUiSupportChannelsService,
            AdminUiContentService adminUiContentService,
            AdminUiPageService adminUiPageService,
            AdminUiReportingService adminUiReportingService,
            AdminUiSystemSettingsService adminUiSystemSettingsService,
            AdminUiMessagingService adminUiMessagingService,
            AdminUiStoreService adminUiStoreService,
            AdminUiGoogleMerchantService adminUiGoogleMerchantService
    ) {
        this.adminUiSessionService = adminUiSessionService;
        this.authService = authService;
        this.adminUiProductService = adminUiProductService;
        this.adminUiOrderService = adminUiOrderService;
        this.adminUiPeopleService = adminUiPeopleService;
        this.adminUiPaymentService = adminUiPaymentService;
        this.adminUiVoucherDiscountService = adminUiVoucherDiscountService;
        this.adminUiDeliveryService = adminUiDeliveryService;
        this.adminUiRolePermissionService = adminUiRolePermissionService;
        this.adminUiSupportRecordsService = adminUiSupportRecordsService;
        this.adminUiMarketingService = adminUiMarketingService;
        this.adminUiAppConfigService = adminUiAppConfigService;
        this.adminUiMediaService = adminUiMediaService;
        this.adminUiProductCategoryService = adminUiProductCategoryService;
        this.adminUiProductTagService = adminUiProductTagService;
        this.adminUiProductAttributeService = adminUiProductAttributeService;
        this.adminUiProductVariantService = adminUiProductVariantService;
        this.adminUiPaidFileContentService = adminUiPaidFileContentService;
        this.adminUiSupportChannelsService = adminUiSupportChannelsService;
        this.adminUiContentService = adminUiContentService;
        this.adminUiPageService = adminUiPageService;
        this.adminUiReportingService = adminUiReportingService;
        this.adminUiSystemSettingsService = adminUiSystemSettingsService;
        this.adminUiMessagingService = adminUiMessagingService;
        this.adminUiStoreService = adminUiStoreService;
        this.adminUiGoogleMerchantService = adminUiGoogleMerchantService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Logged in", adminUiSessionService.login(request));
    }

    @PostMapping("/auth/forgot-password")
    public ApiResponse<?> forgotPassword(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : string(body.get("email"));
        return ApiResponse.ok("Admin password reset email sent", authService.forgotPassword(email));
    }

    @PostMapping("/auth/reset-password")
    public ApiResponse<?> resetPassword(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : string(body.get("email"));
        String token = body == null ? null : string(body.get("token"));
        String password = body == null ? null : string(body.get("password"));
        String confirmation = body == null ? null : string(body.get("password_confirmation"));
        return ApiResponse.ok("Admin password updated", authService.resetPassword(email, token, password, confirmation));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        authService.logout(authorization.substring("Bearer ".length()));
        return ApiResponse.ok("Logged out", null);
    }

    @GetMapping("/session")
    public ApiResponse<?> session(Authentication authentication) {
        return ApiResponse.ok("Session", adminUiSessionService.session(requireEmail(authentication)));
    }

    @GetMapping("/navigation")
    public ApiResponse<?> navigation(Authentication authentication) {
        return ApiResponse.ok("Navigation", adminUiSessionService.navigation(requireEmail(authentication)));
    }

    @GetMapping("/permissions")
    public ApiResponse<?> permissions(Authentication authentication) {
        return ApiResponse.ok("Permissions", adminUiSessionService.permissions(requireEmail(authentication)));
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard(Authentication authentication) {
        return ApiResponse.ok("Dashboard", adminUiSessionService.dashboard(requireEmail(authentication)));
    }

    @GetMapping("/dashboard/overview")
    public ApiResponse<?> dashboardOverview(
            Authentication authentication,
            @RequestParam(name = "signups_range", required = false) Integer signupsRange,
            @RequestParam(name = "orders_range", required = false) Integer ordersRange
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "reports.view");
        return ApiResponse.ok("Dashboard overview", adminUiReportingService.overview(signupsRange, ordersRange));
    }

    @GetMapping("/dashboard/sales")
    public ApiResponse<?> dashboardSales(
            Authentication authentication,
            @RequestParam(name = "revenue_range", required = false) Integer revenueRange,
            @RequestParam(name = "products_breakdown_range", required = false) Integer productsBreakdownRange,
            @RequestParam(name = "category_id", required = false) String categoryId
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "reports.view");
        return ApiResponse.ok("Dashboard sales", adminUiReportingService.sales(revenueRange, productsBreakdownRange, categoryId));
    }

    @GetMapping("/config")
    public ApiResponse<?> config(Authentication authentication) {
        requireEmail(authentication);
        return ApiResponse.ok("Config", adminUiAppConfigService.config());
    }

    @PostMapping("/media/images")
    public ApiResponse<?> uploadImage(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folder
    ) {
        requireEmail(authentication);
        return ApiResponse.ok("Image uploaded", adminUiMediaService.uploadImage(file, folder));
    }

    @PostMapping("/messages/media/images")
    public ApiResponse<?> uploadMessageImage(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "messages.manage");
        return ApiResponse.ok("Message image uploaded", adminUiMessagingService.uploadImage(file));
    }

    @PostMapping("/media/videos")
    public ApiResponse<?> uploadVideo(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false, defaultValue = "product") String profile
    ) {
        requireEmail(authentication);
        return ApiResponse.ok("Video uploaded", adminUiMediaService.uploadVideo(file, folder, profile));
    }

    @GetMapping("/content-targets/{targetType}/{targetId}/contents")
    public ApiResponse<?> contents(Authentication authentication, @PathVariable String targetType, @PathVariable String targetId) {
        adminUiSessionService.requirePermission(requireEmail(authentication), permissionForTarget(targetType, false));
        return ApiResponse.ok("Contents", adminUiContentService.list(targetType, targetId));
    }

    @PostMapping("/content-targets/{targetType}/{targetId}/contents")
    public ApiResponse<?> createContent(Authentication authentication, @PathVariable String targetType, @PathVariable String targetId, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), permissionForTarget(targetType, true));
        return ApiResponse.ok("Content created", adminUiContentService.create(targetType, targetId, body));
    }

    @PatchMapping("/contents/{content}")
    public ApiResponse<?> updateContent(Authentication authentication, @PathVariable String content, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "contents.manage");
        return ApiResponse.ok("Content updated", adminUiContentService.update(content, body));
    }

    @PostMapping("/content-previews/products")
    public ApiResponse<?> contentProductPreview(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "contents.manage");
        return ApiResponse.ok("Content product preview", adminUiContentService.previewProducts(body));
    }

    @DeleteMapping("/contents/{content}")
    public ApiResponse<?> deleteContent(Authentication authentication, @PathVariable String content) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "contents.manage");
        return ApiResponse.ok("Content deleted", adminUiContentService.delete(content));
    }

    @PostMapping("/media/files")
    public ApiResponse<?> uploadFile(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folder
    ) {
        requireEmail(authentication);
        return ApiResponse.ok("File uploaded", adminUiMediaService.uploadFile(file, folder));
    }

    @GetMapping("/email-targets")
    public ApiResponse<?> emailTargets(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "emails.manage");
        return ApiResponse.ok("Email targets", adminUiSupportChannelsService.emailTargets());
    }

    @PostMapping("/emails/send")
    public ApiResponse<?> sendEmail(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "emails.manage");
        return ApiResponse.ok("Email queued", adminUiSupportChannelsService.sendEmail(body));
    }

    @GetMapping("/emails")
    public ApiResponse<?> emails(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "emails.manage");
        return ApiResponse.ok("Emails", adminUiSupportChannelsService.emails());
    }

    @PostMapping("/emails/process")
    public ApiResponse<?> processEmails(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "emails.manage");
        return ApiResponse.ok("Emails processed", adminUiSupportChannelsService.processEmails());
    }

    @GetMapping("/shop-settings")
    public ApiResponse<?> shopSettings(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "shop_settings.view");
        return ApiResponse.ok("Shop settings", adminUiSystemSettingsService.detail());
    }

    @PatchMapping("/shop-settings")
    public ApiResponse<?> updateShopSettings(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "shop_settings.manage");
        return ApiResponse.ok("Shop settings updated", adminUiSystemSettingsService.update(body));
    }

    @PostMapping("/shop-settings/email/test")
    public ApiResponse<?> testEmailSettings(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "shop_settings.manage");
        return ApiResponse.ok("Test email sent", adminUiSupportChannelsService.testEmailSettings(body));
    }

    @PostMapping("/shop-settings/email/gmail/authorize")
    public ApiResponse<?> gmailAuthorize(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "shop_settings.manage");
        return ApiResponse.ok("Gmail authorization URL", adminUiSupportChannelsService.gmailAuthorization(body));
    }

    @PostMapping("/shop-settings/email/gmail/disconnect")
    public ApiResponse<?> gmailDisconnect(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "shop_settings.manage");
        return ApiResponse.ok("Gmail disconnected", adminUiSupportChannelsService.disconnectGmail());
    }

    @GetMapping("/shop-settings/email/gmail/callback")
    public RedirectView gmailCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        return new RedirectView(adminUiSupportChannelsService.gmailCallback(code, state, error).toString());
    }

    @GetMapping("/stores")
    public ApiResponse<?> stores(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage,
            @RequestParam(name = "search", required = false) String search
    ) {
        adminUiSessionService.requireGlobalStoreManagement(requireEmail(authentication));
        return ApiResponse.ok("Stores", adminUiStoreService.list(page, perPage, search));
    }

    @GetMapping("/stores/{store}")
    public ApiResponse<?> store(Authentication authentication, @PathVariable UUID store) {
        adminUiSessionService.requireGlobalStoreManagement(requireEmail(authentication));
        return ApiResponse.ok("Store", adminUiStoreService.detail(store));
    }

    @PatchMapping("/stores/{store}/status")
    public ApiResponse<?> updateStoreStatus(Authentication authentication, @PathVariable UUID store, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requireGlobalStoreManagement(requireEmail(authentication));
        return ApiResponse.ok("Store updated", adminUiStoreService.updateStatus(store, body));
    }

    @PatchMapping("/stores/{store}")
    public ApiResponse<?> updateStore(Authentication authentication, @PathVariable UUID store, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requireGlobalStoreManagement(requireEmail(authentication));
        return ApiResponse.ok("Store updated", adminUiStoreService.updateDetails(store, body));
    }

    @GetMapping("/google-merchant")
    public ApiResponse<?> googleMerchant(Authentication authentication) {
        String email = requireEmail(authentication);
        adminUiSessionService.requireGoogleMerchantAccess(email);
        return ApiResponse.ok("Google Merchant", adminUiGoogleMerchantService.detail(email));
    }

    @PatchMapping("/google-merchant")
    public ApiResponse<?> updateGoogleMerchant(Authentication authentication, @RequestBody Map<String, Object> body) {
        String email = requireEmail(authentication);
        adminUiSessionService.requireGoogleMerchantAccess(email);
        return ApiResponse.ok("Google Merchant updated", adminUiGoogleMerchantService.update(email, body));
    }

    @PostMapping("/google-merchant/sync")
    public ApiResponse<?> syncGoogleMerchant(Authentication authentication) {
        String email = requireEmail(authentication);
        adminUiSessionService.requireGoogleMerchantAccess(email);
        return ApiResponse.ok("Google Merchant sync completed", adminUiGoogleMerchantService.sync(email));
    }

    @PostMapping("/google-merchant/runs/{run}/retry")
    public ApiResponse<?> retryGoogleMerchantRun(Authentication authentication, @PathVariable UUID run) {
        String email = requireEmail(authentication);
        adminUiSessionService.requireGoogleMerchantAccess(email);
        return ApiResponse.ok("Google Merchant retry completed", adminUiGoogleMerchantService.retryFailed(email, run));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(Authentication authentication) {
        String email = requireEmail(authentication);
        adminUiSessionService.requirePermission(email, "notifications.view");
        return ApiResponse.ok("Notifications", adminUiSupportChannelsService.notifications(email));
    }

    @PostMapping("/notifications/clear")
    public ApiResponse<?> clearNotifications(Authentication authentication) {
        String email = requireEmail(authentication);
        adminUiSessionService.requirePermission(email, "notifications.manage");
        return ApiResponse.ok("Notifications cleared", adminUiSupportChannelsService.clearNotifications(email));
    }

    @GetMapping("/products")
    public ApiResponse<?> products(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(name = "category_id", required = false) String categoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Products", adminUiProductService.list(search, status, categoryId, page, perPage));
    }

    @GetMapping("/products/{product}")
    public ApiResponse<?> product(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Product", adminUiProductService.detail(product));
    }

    @GetMapping("/products/form-options")
    public ApiResponse<?> productFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product form options", adminUiProductService.formOptions());
    }

    @PostMapping("/products")
    public ApiResponse<?> createProduct(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product created", adminUiProductService.create(body));
    }

    @PatchMapping("/products/{product}")
    public ApiResponse<?> updateProduct(Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product updated", adminUiProductService.update(product, body));
    }

    @DeleteMapping("/products/{product}")
    public ApiResponse<?> deleteProduct(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product deleted", adminUiProductService.delete(product));
    }

    @GetMapping("/products/{product}/variants")
    public ApiResponse<?> productVariants(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Product variants", adminUiProductVariantService.list(product));
    }

    @GetMapping("/products/{product}/variants/form-options")
    public ApiResponse<?> productVariantFormOptions(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product variant form options", adminUiProductVariantService.formOptions(product));
    }

    @GetMapping("/products/{product}/variants/{variant}")
    public ApiResponse<?> productVariant(Authentication authentication, @PathVariable String product, @PathVariable String variant) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Product variant", adminUiProductVariantService.detail(product, variant));
    }

    @PostMapping("/products/{product}/variants")
    public ApiResponse<?> createProductVariant(Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product variant created", adminUiProductVariantService.create(product, body));
    }

    @PatchMapping("/products/{product}/variants/{variant}")
    public ApiResponse<?> updateProductVariant(Authentication authentication, @PathVariable String product, @PathVariable String variant, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product variant updated", adminUiProductVariantService.update(product, variant, body));
    }

    @DeleteMapping("/products/{product}/variants/{variant}")
    public ApiResponse<?> deleteProductVariant(Authentication authentication, @PathVariable String product, @PathVariable String variant) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Product variant deleted", adminUiProductVariantService.delete(product, variant));
    }

    @GetMapping("/products/{product}/paid-contents")
    public ApiResponse<?> paidContents(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Paid contents", adminUiPaidFileContentService.list(product));
    }

    @GetMapping("/products/{product}/paid-contents/form-options")
    public ApiResponse<?> paidContentFormOptions(Authentication authentication, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Paid content form options", adminUiPaidFileContentService.formOptions(product));
    }

    @GetMapping("/products/{product}/paid-contents/{paidContent}")
    public ApiResponse<?> paidContent(Authentication authentication, @PathVariable String product, @PathVariable String paidContent) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.view");
        return ApiResponse.ok("Paid content", adminUiPaidFileContentService.detail(product, paidContent));
    }

    @PostMapping("/products/{product}/paid-contents")
    public ApiResponse<?> createPaidContent(Authentication authentication, @PathVariable String product, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Paid content created", adminUiPaidFileContentService.create(product, body));
    }

    @PatchMapping("/products/{product}/paid-contents/{paidContent}")
    public ApiResponse<?> updatePaidContent(Authentication authentication, @PathVariable String product, @PathVariable String paidContent, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Paid content updated", adminUiPaidFileContentService.update(product, paidContent, body));
    }

    @DeleteMapping("/products/{product}/paid-contents/{paidContent}")
    public ApiResponse<?> deletePaidContent(Authentication authentication, @PathVariable String product, @PathVariable String paidContent) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "products.manage");
        return ApiResponse.ok("Paid content deleted", adminUiPaidFileContentService.delete(product, paidContent));
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.view");
        return ApiResponse.ok("Categories", adminUiProductCategoryService.list(search, status, page, perPage));
    }

    @GetMapping("/categories/{category}")
    public ApiResponse<?> category(Authentication authentication, @PathVariable String category) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.view");
        return ApiResponse.ok("Category", adminUiProductCategoryService.detail(category));
    }

    @GetMapping("/categories/form-options")
    public ApiResponse<?> categoryFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.manage");
        return ApiResponse.ok("Category form options", adminUiProductCategoryService.formOptions());
    }

    @PostMapping("/categories")
    public ApiResponse<?> createCategory(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.manage");
        return ApiResponse.ok("Category created", adminUiProductCategoryService.create(body));
    }

    @PatchMapping("/categories/{category}")
    public ApiResponse<?> updateCategory(Authentication authentication, @PathVariable String category, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.manage");
        return ApiResponse.ok("Category updated", adminUiProductCategoryService.update(category, body));
    }

    @DeleteMapping("/categories/{category}")
    public ApiResponse<?> deleteCategory(Authentication authentication, @PathVariable String category) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_categories.manage");
        return ApiResponse.ok("Category deleted", adminUiProductCategoryService.delete(category));
    }

    @GetMapping("/tags")
    public ApiResponse<?> tags(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.view");
        return ApiResponse.ok("Tags", adminUiProductTagService.list(search, status, page, perPage));
    }

    @GetMapping("/tags/{tag}")
    public ApiResponse<?> tag(Authentication authentication, @PathVariable String tag) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.view");
        return ApiResponse.ok("Tag", adminUiProductTagService.detail(tag));
    }

    @GetMapping("/tags/form-options")
    public ApiResponse<?> tagFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.manage");
        return ApiResponse.ok("Tag form options", adminUiProductTagService.formOptions());
    }

    @PostMapping("/tags")
    public ApiResponse<?> createTag(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.manage");
        return ApiResponse.ok("Tag created", adminUiProductTagService.create(body));
    }

    @PatchMapping("/tags/{tag}")
    public ApiResponse<?> updateTag(Authentication authentication, @PathVariable String tag, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.manage");
        return ApiResponse.ok("Tag updated", adminUiProductTagService.update(tag, body));
    }

    @DeleteMapping("/tags/{tag}")
    public ApiResponse<?> deleteTag(Authentication authentication, @PathVariable String tag) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_tags.manage");
        return ApiResponse.ok("Tag deleted", adminUiProductTagService.delete(tag));
    }

    @GetMapping("/pages")
    public ApiResponse<?> pages(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.view");
        return ApiResponse.ok("Pages", adminUiPageService.list(search, status, page, perPage));
    }

    @GetMapping("/pages/form-options")
    public ApiResponse<?> pageFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.manage");
        return ApiResponse.ok("Page form options", adminUiPageService.formOptions());
    }

    @GetMapping("/pages/{pageId}")
    public ApiResponse<?> page(Authentication authentication, @PathVariable String pageId) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.view");
        return ApiResponse.ok("Page", adminUiPageService.detail(pageId));
    }

    @PostMapping("/pages")
    public ApiResponse<?> createPage(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.manage");
        return ApiResponse.ok("Page created", adminUiPageService.create(body));
    }

    @PatchMapping("/pages/{pageId}")
    public ApiResponse<?> updatePage(Authentication authentication, @PathVariable String pageId, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.manage");
        return ApiResponse.ok("Page updated", adminUiPageService.update(pageId, body));
    }

    @DeleteMapping("/pages/{pageId}")
    public ApiResponse<?> deletePage(Authentication authentication, @PathVariable String pageId) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.manage");
        return ApiResponse.ok("Page deleted", adminUiPageService.delete(pageId));
    }

    @GetMapping("/pages/menu")
    public ApiResponse<?> pageMenu(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.view");
        return ApiResponse.ok("Page menu", adminUiPageService.menu());
    }

    @PutMapping("/pages/menu")
    public ApiResponse<?> updatePageMenu(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "pages.manage");
        return ApiResponse.ok("Page menu updated", adminUiPageService.saveMenu(body));
    }

    @GetMapping("/attributes")
    public ApiResponse<?> attributes(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.view");
        return ApiResponse.ok("Attributes", adminUiProductAttributeService.list(search, status, page, perPage));
    }

    @GetMapping("/attributes/{attribute}")
    public ApiResponse<?> attribute(Authentication authentication, @PathVariable String attribute) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.view");
        return ApiResponse.ok("Attribute", adminUiProductAttributeService.detail(attribute));
    }

    @GetMapping("/attributes/form-options")
    public ApiResponse<?> attributeFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute form options", adminUiProductAttributeService.formOptions());
    }

    @PostMapping("/attributes")
    public ApiResponse<?> createAttribute(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute created", adminUiProductAttributeService.create(body));
    }

    @PatchMapping("/attributes/{attribute}")
    public ApiResponse<?> updateAttribute(Authentication authentication, @PathVariable String attribute, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute updated", adminUiProductAttributeService.update(attribute, body));
    }

    @DeleteMapping("/attributes/{attribute}")
    public ApiResponse<?> deleteAttribute(Authentication authentication, @PathVariable String attribute) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute deleted", adminUiProductAttributeService.delete(attribute));
    }

    @PostMapping("/attributes/{attribute}/options")
    public ApiResponse<?> createAttributeOption(Authentication authentication, @PathVariable String attribute, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute option created", adminUiProductAttributeService.createOption(attribute, body));
    }

    @PatchMapping("/attributes/{attribute}/options/{option}")
    public ApiResponse<?> updateAttributeOption(Authentication authentication, @PathVariable String attribute, @PathVariable String option, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute option updated", adminUiProductAttributeService.updateOption(attribute, option, body));
    }

    @DeleteMapping("/attributes/{attribute}/options/{option}")
    public ApiResponse<?> deleteAttributeOption(Authentication authentication, @PathVariable String attribute, @PathVariable String option) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "product_attributes.manage");
        return ApiResponse.ok("Attribute option deleted", adminUiProductAttributeService.deleteOption(attribute, option));
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "orders.view");
        return ApiResponse.ok("Orders", adminUiOrderService.list(search, status, page, perPage));
    }

    @GetMapping("/messages")
    public ApiResponse<?> messageThreads(
            Authentication authentication,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "messages.view");
        return ApiResponse.ok("Message threads", adminUiMessagingService.threads(page, perPage));
    }

    @PostMapping("/orders/{order}/message-thread")
    public ApiResponse<?> ensureOrderMessageThread(Authentication authentication, @PathVariable UUID order) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "messages.manage");
        return ApiResponse.ok("Message thread", adminUiMessagingService.ensureThread(order));
    }

    @GetMapping("/messages/{thread}")
    public ApiResponse<?> messageThread(Authentication authentication, @PathVariable UUID thread) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "messages.view");
        return ApiResponse.ok("Message thread", adminUiMessagingService.thread(thread));
    }

    @PostMapping("/messages/{thread}/messages")
    public ApiResponse<?> postThreadMessage(Authentication authentication, @PathVariable UUID thread, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "messages.manage");
        return ApiResponse.ok("Message sent", adminUiMessagingService.postMessage(requireEmail(authentication), thread, body == null ? Map.of() : body));
    }

    @GetMapping("/orders/{order}")
    public ApiResponse<?> order(Authentication authentication, @PathVariable String order) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "orders.view");
        return ApiResponse.ok("Order", adminUiOrderService.detail(order));
    }

    @PatchMapping("/orders/{order}/status")
    public ApiResponse<?> updateOrderStatus(Authentication authentication, @PathVariable String order, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "orders.manage");
        return ApiResponse.ok("Order updated", adminUiOrderService.updateStatus(order, body));
    }

    @GetMapping("/customers")
    public ApiResponse<?> customers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "customers.view");
        return ApiResponse.ok("Customers", adminUiPeopleService.customers(search, page, perPage));
    }

    @GetMapping("/customers/{customer}")
    public ApiResponse<?> customer(Authentication authentication, @PathVariable String customer) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "customers.view");
        return ApiResponse.ok("Customer", adminUiPeopleService.customer(customer));
    }

    @PatchMapping("/customers/{customer}/favorite")
    public ApiResponse<?> updateCustomerFavorite(Authentication authentication, @PathVariable String customer, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "customers.view");
        return ApiResponse.ok("Customer updated", adminUiPeopleService.updateCustomerFavorite(customer, body));
    }

    @PatchMapping("/customers/{customer}")
    public ApiResponse<?> updateCustomer(Authentication authentication, @PathVariable String customer, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "customers.view");
        return ApiResponse.ok("Customer updated", adminUiPeopleService.updateCustomer(customer, body));
    }

    @DeleteMapping("/customers/{customer}")
    public ApiResponse<?> deleteCustomer(Authentication authentication, @PathVariable String customer) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "customers.view");
        return ApiResponse.ok("Customer deleted", adminUiPeopleService.deleteCustomer(customer));
    }

    @GetMapping("/users")
    public ApiResponse<?> users(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.view");
        return ApiResponse.ok("Users", adminUiPeopleService.users(search, role, page, perPage));
    }

    @GetMapping("/users/{user}")
    public ApiResponse<?> user(Authentication authentication, @PathVariable String user) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.view");
        return ApiResponse.ok("User", adminUiPeopleService.user(user));
    }

    @GetMapping("/users/invite-options")
    public ApiResponse<?> userInviteOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.can.invite");
        return ApiResponse.ok("Invite options", adminUiPeopleService.inviteOptions(requireEmail(authentication)));
    }

    @PostMapping("/users/invite")
    public ApiResponse<?> inviteUser(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.can.invite");
        return ApiResponse.ok("Invite sent", adminUiPeopleService.inviteUser(requireEmail(authentication), body));
    }

    @PostMapping("/users")
    public ApiResponse<?> createUser(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.manage");
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Users must be invited before they can register.");
    }

    @PatchMapping("/users/{user}")
    public ApiResponse<?> updateUser(Authentication authentication, @PathVariable String user, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.manage");
        return ApiResponse.ok("User updated", adminUiPeopleService.updateUser(user, body));
    }

    @PostMapping("/users/{user}/store-memberships")
    public ApiResponse<?> addUserStoreMembership(Authentication authentication, @PathVariable String user, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.manage");
        return ApiResponse.ok("User updated", adminUiPeopleService.assignStoreMembership(user, body));
    }

    @DeleteMapping("/users/{user}/store-memberships")
    public ApiResponse<?> removeUserStoreMembership(Authentication authentication, @PathVariable String user, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.manage");
        return ApiResponse.ok("User updated", adminUiPeopleService.removeStoreMembership(user, body));
    }

    @DeleteMapping("/users/{user}")
    public ApiResponse<?> deleteUser(Authentication authentication, @PathVariable String user) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "users.manage");
        return ApiResponse.ok("User deleted", adminUiPeopleService.deleteUser(user));
    }

    @GetMapping("/carts")
    public ApiResponse<?> carts(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "carts.view");
        return ApiResponse.ok("Carts", adminUiPeopleService.carts(search, page, perPage));
    }

    @GetMapping("/carts/{cart}")
    public ApiResponse<?> cart(Authentication authentication, @PathVariable String cart) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "carts.view");
        return ApiResponse.ok("Cart", adminUiPeopleService.cart(cart));
    }

    @PostMapping("/carts")
    public ApiResponse<?> createCart(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "carts.manage");
        return ApiResponse.ok("Cart created", adminUiPeopleService.createCart(body));
    }

    @PatchMapping("/carts/{cart}")
    public ApiResponse<?> updateCart(Authentication authentication, @PathVariable String cart, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "carts.manage");
        return ApiResponse.ok("Cart updated", adminUiPeopleService.updateCart(cart, body));
    }

    @DeleteMapping("/carts/{cart}")
    public ApiResponse<?> deleteCart(Authentication authentication, @PathVariable String cart) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "carts.manage");
        return ApiResponse.ok("Cart deleted", adminUiPeopleService.deleteCart(cart));
    }

    @GetMapping("/payments")
    public ApiResponse<?> payments(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "payments.manage");
        return ApiResponse.ok("Payments", adminUiPaymentService.list(search, status, page, perPage));
    }

    @GetMapping("/payments/{payment}")
    public ApiResponse<?> payment(Authentication authentication, @PathVariable String payment) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "payments.manage");
        return ApiResponse.ok("Payment", adminUiPaymentService.detail(payment));
    }

    @PatchMapping("/payments/{payment}/status")
    public ApiResponse<?> updatePaymentStatus(Authentication authentication, @PathVariable String payment, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "payments.manage");
        return ApiResponse.ok("Payment updated", adminUiPaymentService.updateStatus(payment, body));
    }

    @PostMapping("/payments/{payment}/refund")
    public ApiResponse<?> refundPayment(Authentication authentication, @PathVariable String payment, @RequestBody(required = false) Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "payments.manage");
        return ApiResponse.ok("Payment refunded", adminUiPaymentService.refund(payment, body == null ? Map.of() : body));
    }

    @GetMapping("/voucher-codes")
    public ApiResponse<?> voucherCodes(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.view");
        return ApiResponse.ok("Voucher codes", adminUiVoucherDiscountService.vouchers(search, status, page, perPage));
    }

    @GetMapping("/voucher-codes/{voucher}")
    public ApiResponse<?> voucherCode(Authentication authentication, @PathVariable String voucher) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.view");
        return ApiResponse.ok("Voucher code", adminUiVoucherDiscountService.voucher(voucher));
    }

    @GetMapping("/voucher-codes/form-options")
    public ApiResponse<?> voucherFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.manage");
        return ApiResponse.ok("Voucher form options", adminUiVoucherDiscountService.voucherFormOptions());
    }

    @PostMapping("/voucher-codes")
    public ApiResponse<?> createVoucherCode(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.manage");
        return ApiResponse.ok("Voucher code created", adminUiVoucherDiscountService.createVoucher(body));
    }

    @PatchMapping("/voucher-codes/{voucher}")
    public ApiResponse<?> updateVoucherCode(Authentication authentication, @PathVariable String voucher, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.manage");
        return ApiResponse.ok("Voucher code updated", adminUiVoucherDiscountService.updateVoucher(voucher, body));
    }

    @DeleteMapping("/voucher-codes/{voucher}")
    public ApiResponse<?> deleteVoucherCode(Authentication authentication, @PathVariable String voucher) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.manage");
        return ApiResponse.ok("Voucher code deleted", adminUiVoucherDiscountService.deleteVoucher(voucher));
    }

    @PostMapping("/voucher-codes/{voucher}/activate")
    public ApiResponse<?> activateVoucherCode(Authentication authentication, @PathVariable String voucher, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "voucher_codes.manage");
        return ApiResponse.ok("Voucher code activated", adminUiVoucherDiscountService.activateVoucher(voucher, body));
    }

    @GetMapping("/discount-rules")
    public ApiResponse<?> discountRules(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.view");
        return ApiResponse.ok("Discount rules", adminUiVoucherDiscountService.discountRules(search, status, page, perPage));
    }

    @GetMapping("/discount-rules/{rule}")
    public ApiResponse<?> discountRule(Authentication authentication, @PathVariable String rule) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.view");
        return ApiResponse.ok("Discount rule", adminUiVoucherDiscountService.discountRule(rule));
    }

    @GetMapping("/discount-rules/form-options")
    public ApiResponse<?> discountRuleFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule form options", adminUiVoucherDiscountService.discountRuleFormOptions());
    }

    @PostMapping("/discount-rules")
    public ApiResponse<?> createDiscountRule(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule created", adminUiVoucherDiscountService.createDiscountRule(body));
    }

    @PatchMapping("/discount-rules/{rule}")
    public ApiResponse<?> updateDiscountRule(Authentication authentication, @PathVariable String rule, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule updated", adminUiVoucherDiscountService.updateDiscountRule(rule, body));
    }

    @DeleteMapping("/discount-rules/{rule}")
    public ApiResponse<?> deleteDiscountRule(Authentication authentication, @PathVariable String rule) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule deleted", adminUiVoucherDiscountService.deleteDiscountRule(rule));
    }

    @PostMapping("/discount-rules/{rule}/activate")
    public ApiResponse<?> activateDiscountRule(Authentication authentication, @PathVariable String rule, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule activated", adminUiVoucherDiscountService.activateDiscountRule(rule, body));
    }

    @PostMapping("/discount-rules/{rule}/products/{product}")
    public ApiResponse<?> attachDiscountRuleProduct(Authentication authentication, @PathVariable String rule, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule updated", adminUiVoucherDiscountService.attachDiscountRuleProduct(rule, product));
    }

    @DeleteMapping("/discount-rules/{rule}/products/{product}")
    public ApiResponse<?> detachDiscountRuleProduct(Authentication authentication, @PathVariable String rule, @PathVariable String product) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule updated", adminUiVoucherDiscountService.detachDiscountRuleProduct(rule, product));
    }

    @PostMapping("/discount-rules/{rule}/categories/{category}")
    public ApiResponse<?> attachDiscountRuleCategory(Authentication authentication, @PathVariable String rule, @PathVariable String category) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule updated", adminUiVoucherDiscountService.attachDiscountRuleCategory(rule, category));
    }

    @DeleteMapping("/discount-rules/{rule}/categories/{category}")
    public ApiResponse<?> detachDiscountRuleCategory(Authentication authentication, @PathVariable String rule, @PathVariable String category) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "discount_rules.manage");
        return ApiResponse.ok("Discount rule updated", adminUiVoucherDiscountService.detachDiscountRuleCategory(rule, category));
    }

    @GetMapping("/delivery-types")
    public ApiResponse<?> deliveryTypes(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.view");
        return ApiResponse.ok("Delivery types", adminUiDeliveryService.deliveryTypes(search, status, page, perPage));
    }

    @GetMapping("/delivery-types/{deliveryType}")
    public ApiResponse<?> deliveryType(Authentication authentication, @PathVariable String deliveryType) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.view");
        return ApiResponse.ok("Delivery type", adminUiDeliveryService.deliveryType(deliveryType));
    }

    @GetMapping("/delivery-types/form-options")
    public ApiResponse<?> deliveryTypeFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery type form options", adminUiDeliveryService.deliveryTypeFormOptions());
    }

    @PostMapping("/delivery-types")
    public ApiResponse<?> createDeliveryType(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery type created", adminUiDeliveryService.createDeliveryType(body));
    }

    @PatchMapping("/delivery-types/{deliveryType}")
    public ApiResponse<?> updateDeliveryType(Authentication authentication, @PathVariable String deliveryType, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery type updated", adminUiDeliveryService.updateDeliveryType(deliveryType, body));
    }

    @DeleteMapping("/delivery-types/{deliveryType}")
    public ApiResponse<?> deleteDeliveryType(Authentication authentication, @PathVariable String deliveryType) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery type deleted", adminUiDeliveryService.deleteDeliveryType(deliveryType));
    }

    @GetMapping("/delivery-types/{deliveryType}/rules")
    public ApiResponse<?> deliveryRules(Authentication authentication, @PathVariable String deliveryType) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.view");
        return ApiResponse.ok("Delivery rules", adminUiDeliveryService.deliveryRules(deliveryType));
    }

    @GetMapping("/delivery-types/{deliveryType}/rules/{rule}")
    public ApiResponse<?> deliveryRule(Authentication authentication, @PathVariable String deliveryType, @PathVariable String rule) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.view");
        return ApiResponse.ok("Delivery rule", adminUiDeliveryService.deliveryRule(deliveryType, rule));
    }

    @PostMapping("/delivery-types/{deliveryType}/rules")
    public ApiResponse<?> createDeliveryRule(Authentication authentication, @PathVariable String deliveryType, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery rule created", adminUiDeliveryService.createDeliveryRule(deliveryType, body));
    }

    @PatchMapping("/delivery-types/{deliveryType}/rules/{rule}")
    public ApiResponse<?> updateDeliveryRule(Authentication authentication, @PathVariable String deliveryType, @PathVariable String rule, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery rule updated", adminUiDeliveryService.updateDeliveryRule(deliveryType, rule, body));
    }

    @DeleteMapping("/delivery-types/{deliveryType}/rules/{rule}")
    public ApiResponse<?> deleteDeliveryRule(Authentication authentication, @PathVariable String deliveryType, @PathVariable String rule) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "delivery_types.manage");
        return ApiResponse.ok("Delivery rule deleted", adminUiDeliveryService.deleteDeliveryRule(deliveryType, rule));
    }

    @GetMapping("/roles")
    public ApiResponse<?> roles(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.view");
        return ApiResponse.ok("Roles", adminUiRolePermissionService.roles(search, page, perPage));
    }

    @GetMapping("/roles/{role}")
    public ApiResponse<?> role(Authentication authentication, @PathVariable String role) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.view");
        return ApiResponse.ok("Role", adminUiRolePermissionService.role(role));
    }

    @GetMapping("/roles/form-options")
    public ApiResponse<?> roleFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role form options", adminUiRolePermissionService.roleFormOptions());
    }

    @PostMapping("/roles")
    public ApiResponse<?> createRole(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role created", adminUiRolePermissionService.createRole(body));
    }

    @PatchMapping("/roles/{role}")
    public ApiResponse<?> updateRole(Authentication authentication, @PathVariable String role, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role updated", adminUiRolePermissionService.updateRole(role, body));
    }

    @DeleteMapping("/roles/{role}")
    public ApiResponse<?> deleteRole(Authentication authentication, @PathVariable String role) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role deleted", adminUiRolePermissionService.deleteRole(role));
    }

    @PostMapping("/roles/{role}/permissions/{permission}")
    public ApiResponse<?> assignRolePermission(Authentication authentication, @PathVariable String role, @PathVariable String permission) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role updated", adminUiRolePermissionService.assignPermission(role, permission));
    }

    @DeleteMapping("/roles/{role}/permissions/{permission}")
    public ApiResponse<?> removeRolePermission(Authentication authentication, @PathVariable String role, @PathVariable String permission) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role updated", adminUiRolePermissionService.removePermission(role, permission));
    }

    @PostMapping("/roles/{role}/users/{user}")
    public ApiResponse<?> assignRoleUser(Authentication authentication, @PathVariable String role, @PathVariable String user) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role updated", adminUiRolePermissionService.assignUser(role, user));
    }

    @DeleteMapping("/roles/{role}/users/{user}")
    public ApiResponse<?> removeRoleUser(Authentication authentication, @PathVariable String role, @PathVariable String user) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.manage");
        return ApiResponse.ok("Role updated", adminUiRolePermissionService.removeUser(role, user));
    }

    @GetMapping("/permissions-catalog")
    public ApiResponse<?> permissionsCatalog(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "roles.view");
        return ApiResponse.ok("Permissions", adminUiRolePermissionService.permissions(search, page, perPage));
    }

    @GetMapping("/files")
    public ApiResponse<?> files(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.view");
        return ApiResponse.ok("Files", adminUiSupportRecordsService.files(search, page, perPage));
    }

    @GetMapping("/files/{file}")
    public ApiResponse<?> file(Authentication authentication, @PathVariable String file) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.view");
        return ApiResponse.ok("File", adminUiSupportRecordsService.file(file));
    }

    @PostMapping("/files")
    public ApiResponse<?> createFile(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.manage");
        return ApiResponse.ok("File created", adminUiSupportRecordsService.createFile(body));
    }

    @PatchMapping("/files/{file}")
    public ApiResponse<?> updateFile(Authentication authentication, @PathVariable String file, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.manage");
        return ApiResponse.ok("File updated", adminUiSupportRecordsService.updateFile(file, body));
    }

    @PostMapping("/files/{file}/optimize")
    public ApiResponse<?> optimizeFile(Authentication authentication, @PathVariable String file) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.manage");
        return ApiResponse.ok("File optimized", adminUiSupportRecordsService.optimizeFile(file));
    }

    @DeleteMapping("/files/{file}")
    public ApiResponse<?> deleteFile(Authentication authentication, @PathVariable String file) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "files.manage");
        return ApiResponse.ok("File deleted", adminUiSupportRecordsService.deleteFile(file));
    }

    @GetMapping("/notes")
    public ApiResponse<?> notes(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(name = "noteable_type", required = false) String noteableType,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.view");
        return ApiResponse.ok("Notes", adminUiSupportRecordsService.notes(search, noteableType, page, perPage));
    }

    @GetMapping("/notes/{note}")
    public ApiResponse<?> note(Authentication authentication, @PathVariable String note) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.view");
        return ApiResponse.ok("Note", adminUiSupportRecordsService.note(note));
    }

    @GetMapping("/notes/form-options")
    public ApiResponse<?> noteFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.manage");
        return ApiResponse.ok("Note form options", adminUiSupportRecordsService.noteFormOptions());
    }

    @PostMapping("/notes")
    public ApiResponse<?> createNote(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.manage");
        return ApiResponse.ok("Note created", adminUiSupportRecordsService.createNote(body));
    }

    @PatchMapping("/notes/{note}")
    public ApiResponse<?> updateNote(Authentication authentication, @PathVariable String note, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.manage");
        return ApiResponse.ok("Note updated", adminUiSupportRecordsService.updateNote(note, body));
    }

    @DeleteMapping("/notes/{note}")
    public ApiResponse<?> deleteNote(Authentication authentication, @PathVariable String note) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "notes.manage");
        return ApiResponse.ok("Note deleted", adminUiSupportRecordsService.deleteNote(note));
    }

    @GetMapping("/audits")
    public ApiResponse<?> audits(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "audits.view");
        return ApiResponse.ok("Audits", adminUiSupportRecordsService.audits(search, type, userId, dateFrom, dateTo, page, perPage));
    }

    @GetMapping("/event-logs")
    public ApiResponse<?> eventLogs(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "audits.view");
        return ApiResponse.ok("Event logs", adminUiSupportRecordsService.eventLogs(search, type, dateFrom, dateTo, page, perPage));
    }

    @GetMapping("/event-logs/{eventLog}")
    public ApiResponse<?> eventLog(Authentication authentication, @PathVariable String eventLog) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "audits.view");
        return ApiResponse.ok("Event log", adminUiSupportRecordsService.eventLog(eventLog));
    }

    @GetMapping("/audits/{audit}")
    public ApiResponse<?> audit(Authentication authentication, @PathVariable String audit) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "audits.view");
        return ApiResponse.ok("Audit", adminUiSupportRecordsService.audit(audit));
    }

    @GetMapping("/ratings")
    public ApiResponse<?> ratings(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "ratings.manage");
        return ApiResponse.ok("Ratings", adminUiMarketingService.ratings(search, status, page, perPage));
    }

    @GetMapping("/ratings/{rating}")
    public ApiResponse<?> rating(Authentication authentication, @PathVariable String rating) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "ratings.manage");
        return ApiResponse.ok("Rating", adminUiMarketingService.rating(rating));
    }

    @PatchMapping("/ratings/{rating}/status")
    public ApiResponse<?> updateRatingStatus(Authentication authentication, @PathVariable String rating, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "ratings.manage");
        return ApiResponse.ok("Rating updated", adminUiMarketingService.updateRatingStatus(rating, body));
    }

    @PatchMapping("/ratings/{rating}/verification")
    public ApiResponse<?> updateRatingVerification(Authentication authentication, @PathVariable String rating, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "ratings.manage");
        return ApiResponse.ok("Rating updated", adminUiMarketingService.updateRatingVerification(rating, body));
    }

    @GetMapping("/banners")
    public ApiResponse<?> banners(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banners", adminUiMarketingService.banners(search, status, page, perPage));
    }

    @GetMapping("/banners/{banner}")
    public ApiResponse<?> banner(Authentication authentication, @PathVariable String banner) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banner", adminUiMarketingService.banner(banner));
    }

    @GetMapping("/banners/form-options")
    public ApiResponse<?> bannerFormOptions(Authentication authentication) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banner form options", adminUiMarketingService.bannerFormOptions());
    }

    @PostMapping("/banners")
    public ApiResponse<?> createBanner(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banner created", adminUiMarketingService.createBanner(body));
    }

    @PatchMapping("/banners/{banner}")
    public ApiResponse<?> updateBanner(Authentication authentication, @PathVariable String banner, @RequestBody Map<String, Object> body) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banner updated", adminUiMarketingService.updateBanner(banner, body));
    }

    @DeleteMapping("/banners/{banner}")
    public ApiResponse<?> deleteBanner(Authentication authentication, @PathVariable String banner) {
        adminUiSessionService.requirePermission(requireEmail(authentication), "banners.manage");
        return ApiResponse.ok("Banner deleted", adminUiMarketingService.deleteBanner(banner));
    }

    private String requireEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        return authentication.getName();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String permissionForTarget(String targetType, boolean manage) {
        String normalized = targetType == null ? "" : targetType.trim().toLowerCase();
        return switch (normalized) {
            case "product" -> manage ? "products.manage" : "products.view";
            case "category" -> manage ? "product_categories.manage" : "product_categories.view";
            case "attribute" -> manage ? "product_attributes.manage" : "product_attributes.view";
            case "page" -> manage ? "pages.manage" : "pages.view";
            default -> manage ? "contents.manage" : "contents.view";
        };
    }
}
