package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/orders")
public class StorefrontOrderConfirmationController {

    private final StorefrontOrderConfirmationService storefrontOrderConfirmationService;
    private final StorefrontOrderAccessService access;
    private final StorefrontCheckoutService checkout;

    public StorefrontOrderConfirmationController(
            StorefrontOrderConfirmationService storefrontOrderConfirmationService,
            StorefrontOrderAccessService access,
            StorefrontCheckoutService checkout) {
        this.storefrontOrderConfirmationService = storefrontOrderConfirmationService;
        this.access = access;
        this.checkout = checkout;
    }

    @GetMapping("/{orderId}/confirmation")
    public ApiResponse<?> confirmation(
            @PathVariable UUID orderId,
            Authentication authentication,
            @RequestHeader(value = "X-Order-Access", required = false) String token) {
        access.requireAccess(
                orderId, authentication == null ? null : authentication.getName(), token);
        String providerStatus = checkout.reconcileOrder(orderId);
        var response =
                new java.util.LinkedHashMap<>(
                        storefrontOrderConfirmationService.confirmation(orderId));
        response.put("checkout_status", providerStatus);
        return ApiResponse.ok("Storefront order confirmation", response);
    }
}
