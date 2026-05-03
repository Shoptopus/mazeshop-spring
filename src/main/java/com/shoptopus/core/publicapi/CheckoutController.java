package com.shoptopus.core.publicapi;

import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/get-available-delivery-types")
    public ApiResponse<?> getAvailableDeliveryTypes(@RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.ok("Available delivery types", List.of(
                Map.of("id", "standard", "name", "Standard", "cost", 4.99),
                Map.of("id", "express", "name", "Express", "cost", 9.99)
        ));
    }

    @GetMapping("/check-availabilities/{cartId}")
    public ApiResponse<?> checkAvailabilities(@PathVariable UUID cartId) {
        return ApiResponse.ok("Availability check", checkoutService.checkAvailabilities(cartId));
    }

    @PostMapping("/create-pending-order")
    public ApiResponse<?> createPendingOrder(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok("Pending order created", checkoutService.createPendingOrder(request));
    }

    @PostMapping("/revert-order")
    public ApiResponse<?> revertOrder(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok("Order reverted", checkoutService.revertOrder(request));
    }

    @PostMapping("/apply-voucher-code")
    public ApiResponse<?> applyVoucherCode(@RequestBody Map<String, Object> request) {
        Object code = request.get("code");
        if (code == null || String.valueOf(code).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code is required.");
        }
        return ApiResponse.ok("Voucher applied", Map.of("discount", 0, "valid", false, "code", String.valueOf(code)));
    }
}
