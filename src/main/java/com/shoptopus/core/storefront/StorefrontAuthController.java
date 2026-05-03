package com.shoptopus.core.storefront;

import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.auth.dto.RegisterRequest;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/storefront/auth")
public class StorefrontAuthController {

    private final AuthService authService;
    private final StorefrontAuthService storefrontAuthService;

    public StorefrontAuthController(AuthService authService, StorefrontAuthService storefrontAuthService) {
        this.authService = authService;
        this.storefrontAuthService = storefrontAuthService;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok("Storefront registered", storefrontAuthService.authResponse(authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok("Storefront logged in", storefrontAuthService.authResponse(authService.login(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        authService.logout(authorization.replace("Bearer ", ""));
        return ApiResponse.ok("Storefront logged out", storefrontAuthService.logoutResponse());
    }

    @GetMapping("/me")
    public ApiResponse<?> me(Authentication authentication) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        return ApiResponse.ok("Storefront authenticated user", storefrontAuthService.meResponse(authentication.getName()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<?> forgotPassword(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : string(body.get("email"));
        return ApiResponse.ok("Storefront password reset email sent", authService.forgotPassword(email));
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> resetPassword(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : string(body.get("email"));
        String token = body == null ? null : string(body.get("token"));
        String password = body == null ? null : string(body.get("password"));
        String confirmation = body == null ? null : string(body.get("password_confirmation"));
        return ApiResponse.ok("Storefront password updated", authService.resetPassword(email, token, password, confirmation));
    }

    @GetMapping("/social-providers")
    public ApiResponse<?> socialProviders() {
        return ApiResponse.ok("Storefront social providers", storefrontAuthService.socialProviders());
    }

    @GetMapping("/social/{provider}")
    public ApiResponse<?> socialLoginUrl(@PathVariable String provider) {
        return ApiResponse.ok("Storefront social login URL", storefrontAuthService.socialProvider(provider));
    }

    @GetMapping("/social/{provider}/authorize")
    public ResponseEntity<Void> socialAuthorize(@PathVariable String provider) {
        URI location = storefrontAuthService.socialAuthorizationRedirect(provider);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/social/{provider}/callback")
    public ResponseEntity<Void> socialCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        URI location = storefrontAuthService.socialCallbackRedirect(provider, code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/social/complete")
    public ApiResponse<?> socialComplete(@RequestParam String token) {
        return ApiResponse.ok("Storefront social login completed", storefrontAuthService.socialLoginCompletion(token));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
