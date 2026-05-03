package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/orders")
public class StorefrontOrderConfirmationController {

    private final StorefrontOrderConfirmationService storefrontOrderConfirmationService;

    public StorefrontOrderConfirmationController(StorefrontOrderConfirmationService storefrontOrderConfirmationService) {
        this.storefrontOrderConfirmationService = storefrontOrderConfirmationService;
    }

    @GetMapping("/{orderId}/confirmation")
    public ApiResponse<?> confirmation(@PathVariable UUID orderId) {
        return ApiResponse.ok("Storefront order confirmation", storefrontOrderConfirmationService.confirmation(orderId));
    }
}
