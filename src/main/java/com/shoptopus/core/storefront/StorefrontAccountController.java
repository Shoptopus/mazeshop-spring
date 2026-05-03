package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.engagement.dto.RatingRequest;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.messaging.OrderMessagingService;
import com.shoptopus.core.adminui.AdminUiMediaService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/account")
public class StorefrontAccountController {

    private final StorefrontAccountService storefrontAccountService;
    private final OrderMessagingService orderMessagingService;
    private final EngagementService engagementService;
    private final AdminUiMediaService adminUiMediaService;

    public StorefrontAccountController(StorefrontAccountService storefrontAccountService, OrderMessagingService orderMessagingService, EngagementService engagementService, AdminUiMediaService adminUiMediaService) {
        this.storefrontAccountService = storefrontAccountService;
        this.orderMessagingService = orderMessagingService;
        this.engagementService = engagementService;
        this.adminUiMediaService = adminUiMediaService;
    }

    @GetMapping("/profile")
    public ApiResponse<?> profile(Authentication authentication) {
        return ApiResponse.ok("Storefront account profile", storefrontAccountService.profile(authEmail(authentication)));
    }

    @PatchMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok("Storefront account profile updated", storefrontAccountService.updateProfile(
                authEmail(authentication),
                body == null ? Map.of() : body
        ));
    }

    @GetMapping("/addresses")
    public ApiResponse<?> addresses(Authentication authentication) {
        var rows = storefrontAccountService.addresses(authEmail(authentication));
        return ApiResponse.paged("Storefront account addresses", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/addresses")
    public ApiResponse<?> createAddress(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok("Storefront account address created", storefrontAccountService.createAddress(
                authEmail(authentication),
                body == null ? Map.of() : body
        ));
    }

    @GetMapping("/addresses/{addressId}")
    public ApiResponse<?> address(@PathVariable UUID addressId, Authentication authentication) {
        return ApiResponse.ok("Storefront account address", storefrontAccountService.address(authEmail(authentication), addressId));
    }

    @PatchMapping("/addresses/{addressId}")
    public ApiResponse<?> updateAddress(
            @PathVariable UUID addressId,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront account address updated", storefrontAccountService.updateAddress(
                authEmail(authentication),
                addressId,
                body == null ? Map.of() : body
        ));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<?> deleteAddress(@PathVariable UUID addressId, Authentication authentication) {
        storefrontAccountService.deleteAddress(authEmail(authentication), addressId);
        return ApiResponse.ok("Storefront account address deleted", null);
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders(Authentication authentication) {
        var rows = storefrontAccountService.orders(authEmail(authentication));
        return ApiResponse.paged("Storefront account orders", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<?> order(@PathVariable UUID orderId, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Storefront account order", storefrontAccountService.order(authEmail(authentication), orderId, locale));
    }

    @PostMapping("/orders/{orderId}/ratings/order")
    public ApiResponse<?> rateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody RatingRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront account order review saved", engagementService.saveRating(authEmail(authentication), orderId, "order", orderId, request));
    }

    @PostMapping("/orders/{orderId}/ratings/products/{productId}")
    public ApiResponse<?> rateOrderProduct(
            @PathVariable UUID orderId,
            @PathVariable UUID productId,
            @Valid @RequestBody RatingRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront account product review saved", engagementService.saveRating(authEmail(authentication), orderId, "product", productId, request));
    }

    @GetMapping("/messages")
    public ApiResponse<?> messageThreads(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront account messages", orderMessagingService.customerThreads(authEmail(authentication), page, perPage));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(Authentication authentication) {
        var rows = engagementService.getNotifications(authEmail(authentication));
        return ApiResponse.paged("Storefront account notifications", rows, rows.size(), (long) rows.size(), null);
    }

    @PatchMapping("/notifications/clear")
    public ApiResponse<?> clearNotifications(Authentication authentication) {
        engagementService.clearNotifications(authEmail(authentication));
        return ApiResponse.ok("Storefront account notifications cleared", null);
    }

    @PostMapping("/orders/{orderId}/message-thread")
    public ApiResponse<?> ensureMessageThread(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.ok("Storefront account message thread", orderMessagingService.ensureCustomerThread(authEmail(authentication), orderId));
    }

    @GetMapping("/messages/{threadId}")
    public ApiResponse<?> messageThread(@PathVariable UUID threadId, Authentication authentication) {
        return ApiResponse.ok("Storefront account message thread", orderMessagingService.customerThreadDetail(authEmail(authentication), threadId));
    }

    @PostMapping("/messages/{threadId}/messages")
    public ApiResponse<?> postMessage(@PathVariable UUID threadId, @RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok("Storefront account message sent", orderMessagingService.postCustomerMessage(authEmail(authentication), threadId, body == null ? Map.of() : body));
    }

    @PostMapping("/messages/media/images")
    public ApiResponse<?> uploadMessageImage(@RequestParam("file") MultipartFile file, Authentication authentication) {
        authEmail(authentication);
        return ApiResponse.ok("Storefront account message image uploaded", orderMessagingService.uploadMessageImage(file, true));
    }

    @PostMapping("/ratings/media/images")
    public ApiResponse<?> uploadRatingImage(@RequestParam("file") MultipartFile file, Authentication authentication) {
        authEmail(authentication);
        return ApiResponse.ok("Storefront account rating image uploaded", adminUiMediaService.uploadImage(file, "rating-customer"));
    }

    @PostMapping("/ratings/media/videos")
    public ApiResponse<?> uploadRatingVideo(@RequestParam("file") MultipartFile file, Authentication authentication) {
        authEmail(authentication);
        return ApiResponse.ok("Storefront account rating video uploaded", adminUiMediaService.uploadVideo(file, "rating-customer", "review"));
    }

    private String authEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
