package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/cart")
public class StorefrontCartController {

    private final StorefrontCartService storefrontCartService;

    public StorefrontCartController(StorefrontCartService storefrontCartService) {
        this.storefrontCartService = storefrontCartService;
    }

    @GetMapping("/{cartId}")
    public ApiResponse<?> cart(@PathVariable UUID cartId, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Storefront cart", storefrontCartService.cart(cartId, locale, authEmail(authentication)));
    }

    @PostMapping("/items")
    public ApiResponse<?> addItem(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            Locale locale,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront cart updated", storefrontCartService.addItem(
                body == null ? Map.of() : body,
                request.getRemoteAddr(),
                locale,
                authEmail(authentication)
        ));
    }

    @PatchMapping("/items/{lineId}")
    public ApiResponse<?> updateItem(
            @PathVariable UUID lineId,
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront cart updated", storefrontCartService.updateItem(
                lineId,
                body == null ? Map.of() : body,
                locale,
                authEmail(authentication)
        ));
    }

    @DeleteMapping("/items/{lineId}")
    public ApiResponse<?> removeItem(@PathVariable UUID lineId, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Storefront cart updated", storefrontCartService.removeItem(lineId, locale, authEmail(authentication)));
    }

    @PostMapping("/resolve")
    public ApiResponse<?> resolve(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request,
            Locale locale,
            Authentication authentication
    ) {
        return ApiResponse.ok("Storefront cart resolved", storefrontCartService.resolve(
                body == null ? Map.of() : body,
                request.getRemoteAddr(),
                locale,
                authEmail(authentication)
        ));
    }

    private String authEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
