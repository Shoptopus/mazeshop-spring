package com.shoptopus.core.publicapi;

import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.engagement.dto.BannerResponse;
import com.shoptopus.core.engagement.dto.RatingRequest;
import com.shoptopus.core.storefront.StorefrontShopService;
import com.shoptopus.core.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PublicApiController {

    private final EngagementService engagementService;
    private final UserService userService;
    private final PublicPageService publicPageService;
    private final StorefrontShopService storefrontShopService;
    private final ProductRepository productRepository;

    public PublicApiController(
            EngagementService engagementService,
            UserService userService,
            PublicPageService publicPageService,
            StorefrontShopService storefrontShopService,
            ProductRepository productRepository
    ) {
        this.engagementService = engagementService;
        this.userService = userService;
        this.publicPageService = publicPageService;
        this.storefrontShopService = storefrontShopService;
        this.productRepository = productRepository;
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerResponse>> banners(
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        var rows = engagementService.getBanners(locale, store);
        return ApiResponse.paged("Banners", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/banners/{bannerId}/click")
    public ApiResponse<?> clicked(@PathVariable UUID bannerId) {
        engagementService.clickBanner(bannerId);
        return ApiResponse.ok("Banner clicked", null);
    }

    @GetMapping("/ratings/{productId}")
    public ApiResponse<?> ratings(@PathVariable UUID productId) {
        var rows = engagementService.getProductRatings(productId);
        return ApiResponse.paged("Ratings", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/product/{id}/favorite")
    public ApiResponse<?> favorite(@PathVariable UUID id, Authentication authentication) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        return ApiResponse.ok("Favorite updated", userService.toggleFavorite(authentication.getName(), id));
    }

    @PostMapping("/product/{id}/rating")
    public ApiResponse<?> rate(@PathVariable UUID id, @Valid @RequestBody RatingRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        return ApiResponse.ok("Rating saved", engagementService.rateProduct(id, request, authentication.getName()));
    }

    @GetMapping("/product/{id}/available-attribute-options")
    public ApiResponse<?> availableOptions(
            @PathVariable UUID id,
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found", "Product not found"));
        Map<String, Object> selection = Map.of();
        try {
            var detail = storefrontShopService.productDetail(product.getSlug(), locale, store);
            Object variantSelection = detail.get("variant_selection");
            selection = variantSelection instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
        }
        return ApiResponse.ok("Available attribute options", Map.of(
                "product_id", id,
                "options", selection.getOrDefault("attributes", List.of()),
                "option_availability", selection.getOrDefault("option_availability", List.of()),
                "available_combinations", selection.getOrDefault("available_combinations", List.of())
        ));
    }

    @GetMapping("/home")
    public ApiResponse<?> home(
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("featured_products", List.of());
        data.put("featured_categories", List.of());
        data.put("menu_pages", publicPageService.list(true, locale, store));
        data.put("menu_tree", publicPageService.menuTree(locale, store));
        data.put("home_page", publicPageService.homePage(locale, store));
        return ApiResponse.ok("Home", data);
    }

    @GetMapping("/pages")
    public ApiResponse<?> pages(
            @RequestParam(name = "menu_only", defaultValue = "true") boolean menuOnly,
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        var rows = publicPageService.list(menuOnly, locale, store);
        return ApiResponse.paged("Pages", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/pages/{slug}")
    public ApiResponse<?> page(
            @PathVariable String slug,
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        return ApiResponse.ok("Page", publicPageService.detail(slug, locale, store));
    }

    @GetMapping("/pages/home")
    public ApiResponse<?> homePage(
            Locale locale,
            @RequestParam(name = "store", required = false) String store
    ) {
        return ApiResponse.ok("Home page", publicPageService.homePage(locale, store));
    }

    @GetMapping("/meta")
    public ApiResponse<?> meta() {
        return ApiResponse.ok("Meta", Map.of("currency", "GBP", "locale", "en", "version", "spring-port"));
    }

    @GetMapping("/shared-options")
    public ApiResponse<?> sharedOptions() {
        return ApiResponse.ok("Shared options", Map.of("countries", List.of("UK"), "languages", List.of("en", "de")));
    }

    @GetMapping("/download-paid-file/{paidFileContent}")
    public ApiResponse<?> downloadPaidFile(@PathVariable UUID paidFileContent) {
        return ApiResponse.ok("Paid file download", Map.of("paid_file_content_id", paidFileContent, "download_url", "/files/" + paidFileContent));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(Authentication authentication) {
        var rows = engagementService.getNotifications(authentication.getName());
        return ApiResponse.paged("Notifications", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/notifications/latest")
    public ApiResponse<?> latestNotifications(Authentication authentication) {
        var rows = engagementService.getNotifications(authentication.getName());
        var latest = rows.isEmpty() ? null : rows.get(0);
        return ApiResponse.ok("Latest notification", latest);
    }

    @PatchMapping("/notifications/clear")
    public ApiResponse<?> clearNotifications(Authentication authentication) {
        engagementService.clearNotifications(authentication.getName());
        return ApiResponse.ok("Notifications cleared", null);
    }

    @GetMapping("/notifications/{notificationId}")
    public ApiResponse<?> showNotification(Authentication authentication, @PathVariable UUID notificationId) {
        var row = engagementService.getNotifications(authentication.getName()).stream()
                .filter(n -> n.getId().equals(notificationId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found", "Notification not found"));
        return ApiResponse.ok("Notification", row);
    }
}
