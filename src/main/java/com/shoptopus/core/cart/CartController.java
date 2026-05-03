package com.shoptopus.core.cart;

import com.shoptopus.core.cart.dto.AddCartItemRequest;
import com.shoptopus.core.cart.dto.UpdateCartQuantityRequest;
import com.shoptopus.core.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{cartId}")
    public ApiResponse<?> show(@PathVariable UUID cartId, Locale locale) {
        return ApiResponse.ok("Cart", cartService.getCart(cartId, locale));
    }

    @PostMapping("/addItem")
    public ApiResponse<?> addItem(@Valid @RequestBody AddCartItemRequest request, HttpServletRequest httpRequest, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Cart updated", cartService.addItem(request, httpRequest.getRemoteAddr(), locale, authEmail(authentication)));
    }

    @DeleteMapping("/removeItem")
    public ApiResponse<?> remove(@RequestParam UUID cartId, @RequestParam UUID productId, @RequestParam(required = false) UUID productVariantId, Authentication authentication) {
        cartService.removeItem(cartId, productId, productVariantId, authEmail(authentication));
        return ApiResponse.ok("Cart updated", null);
    }

    @DeleteMapping("/all")
    public ApiResponse<?> clear(@RequestParam UUID cartId, Authentication authentication) {
        cartService.clear(cartId, authEmail(authentication));
        return ApiResponse.ok("Cart cleared", null);
    }

    @PatchMapping("/{cartId}/product/quantity")
    public ApiResponse<?> updateQuantity(@PathVariable UUID cartId, @Valid @RequestBody UpdateCartQuantityRequest request, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Cart updated", cartService.updateQuantity(cartId, request, locale, authEmail(authentication)));
    }

    @PatchMapping("/{cartId}")
    public ApiResponse<?> updateCart(@PathVariable UUID cartId, HttpServletRequest request, Locale locale, Authentication authentication) {
        return ApiResponse.ok("Cart updated", cartService.updateCart(cartId, request.getRemoteAddr(), locale, authEmail(authentication)));
    }

    private String authEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
