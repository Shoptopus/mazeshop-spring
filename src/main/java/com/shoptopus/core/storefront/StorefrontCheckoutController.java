package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/checkout")
public class StorefrontCheckoutController {

    private final StorefrontCheckoutService storefrontCheckoutService;

    public StorefrontCheckoutController(StorefrontCheckoutService storefrontCheckoutService) {
        this.storefrontCheckoutService = storefrontCheckoutService;
    }

    @GetMapping("/{cartId}")
    public ApiResponse<?> summary(
            @PathVariable UUID cartId,
            @RequestParam(value = "store", required = false) String storeSlug,
            Locale locale,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront checkout summary",
                storefrontCheckoutService.summary(
                        cartId, storeSlug, locale, authEmail(authentication)));
    }

    @PostMapping("/delivery-options")
    public ApiResponse<?> deliveryOptions(
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront delivery options",
                storefrontCheckoutService.deliveryOptions(
                        body == null ? Map.of() : body, locale, authEmail(authentication)));
    }

    @PostMapping("/payment-intent")
    public ApiResponse<?> paymentIntent(
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront checkout payment intent",
                storefrontCheckoutService.paymentIntent(
                        body == null ? Map.of() : body, locale, authEmail(authentication)));
    }

    @PostMapping("/voucher")
    public ApiResponse<?> validateVoucher(
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront checkout voucher",
                storefrontCheckoutService.validateVoucher(
                        body == null ? Map.of() : body, locale, authEmail(authentication)));
    }

    @PostMapping("/submit")
    public ApiResponse<?> submit(
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront checkout submitted",
                storefrontCheckoutService.submit(
                        body == null ? Map.of() : body, locale, authEmail(authentication)));
    }

    @PostMapping("/confirm")
    public ApiResponse<?> confirm(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        return ApiResponse.ok(
                "Storefront checkout confirmed",
                storefrontCheckoutService.confirm(
                        body == null ? Map.of() : body, authEmail(authentication)));
    }

    @PostMapping("/cancel")
    public ApiResponse<?> cancel(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(
                "Payment session updated",
                storefrontCheckoutService.cancelCheckout(body, authEmail(authentication)));
    }

    @PostMapping("/stripe/webhook")
    public ApiResponse<?> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank())
            throw new com.shoptopus.core.common.error.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid payment notification.",
                    "Missing Stripe signature");
        storefrontCheckoutService.stripeWebhook(payload, signature);
        return ApiResponse.ok("Payment notification received", Map.of("received", true));
    }

    private String authEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
