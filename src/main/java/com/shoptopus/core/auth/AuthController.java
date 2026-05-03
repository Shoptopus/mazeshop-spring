package com.shoptopus.core.auth;

import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.auth.dto.RegisterRequest;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Registered", new java.util.HashMap<>() {{ put("auth", authService.register(request)); }});
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Logged in", new java.util.HashMap<>() {{ put("auth", authService.login(request)); }});
    }

    @GetMapping("/details")
    public ApiResponse<?> details(Authentication authentication) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        return ApiResponse.ok("Authenticated user", new java.util.HashMap<>() {{
            put("auth", java.util.Map.of("user", authService.me(authentication.getName())));
        }});
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
        var token = authorization.replace("Bearer ", "");
        authService.logout(token);
        return ApiResponse.ok("Logged out", null);
    }

    @PostMapping("/me")
    public ApiResponse<?> me(Authentication authentication) {
        return ApiResponse.ok("Authenticated user", new java.util.HashMap<>() {{
            put("auth", java.util.Map.of("user", authService.me(authentication.getName())));
        }});
    }

    @PostMapping("/verify/resend")
    public ApiResponse<?> resendVerification(
            @RequestBody(required = false) java.util.Map<String, Object> body,
            Authentication authentication
    ) {
        String email = body == null ? null : (body.get("email") == null ? null : String.valueOf(body.get("email")));
        String authenticatedEmail = authentication == null ? null : authentication.getName();
        return ApiResponse.ok("Verification email sent", authService.resendVerification(email, authenticatedEmail));
    }

    @GetMapping("/verify/{id}")
    public ApiResponse<?> verify(@PathVariable String id, @RequestParam(required = false) String hash) {
        return ApiResponse.ok("Email verified", authService.verifyEmail(id, hash));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<?> forgotPassword(@RequestBody java.util.Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : (body.get("email") == null ? null : String.valueOf(body.get("email")));
        return ApiResponse.ok("Password reset email sent", authService.forgotPassword(email));
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> resetPassword(@RequestBody java.util.Map<String, Object> body, Authentication authentication) {
        if (authentication != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Authenticated users cannot reset password.");
        }
        String email = body == null ? null : (body.get("email") == null ? null : String.valueOf(body.get("email")));
        String token = body == null ? null : (body.get("token") == null ? null : String.valueOf(body.get("token")));
        String password = body == null ? null : (body.get("password") == null ? null : String.valueOf(body.get("password")));
        String passwordConfirmation = body == null ? null : (body.get("password_confirmation") == null
                ? null
                : String.valueOf(body.get("password_confirmation")));
        return ApiResponse.ok("Password updated", authService.resetPassword(email, token, password, passwordConfirmation));
    }

    @GetMapping("/login/{provider}")
    public ApiResponse<?> oauthProviderUrl(@PathVariable String provider) {
        if (!"facebook".equalsIgnoreCase(provider) && !"google".equalsIgnoreCase(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Provider is invalid.");
        }
        return ApiResponse.ok("OAuth URL", java.util.Map.of("targetUrl", "/oauth/" + provider.toLowerCase()));
    }

    @PostMapping("/login/{provider}/callback")
    public ApiResponse<?> oauthCallback(@PathVariable String provider, @RequestBody(required = false) java.util.Map<String, Object> body) {
        if (!"facebook".equalsIgnoreCase(provider) && !"google".equalsIgnoreCase(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Provider is invalid.");
        }
        return ApiResponse.ok("OAuth callback accepted", java.util.Map.of("provider", provider));
    }

    @PostMapping("/register-by-invite/{token}")
    public ApiResponse<?> registerByInvite(@PathVariable String token, @RequestBody(required = false) java.util.Map<String, Object> body) {
        String firstName = body == null ? null : (body.get("first_name") == null ? null : String.valueOf(body.get("first_name")));
        String lastName = body == null ? null : (body.get("last_name") == null ? null : String.valueOf(body.get("last_name")));
        String password = body == null ? null : (body.get("password") == null ? null : String.valueOf(body.get("password")));
        String passwordConfirmation = body == null ? null : (body.get("password_confirmation") == null
                ? null
                : String.valueOf(body.get("password_confirmation")));
        return ApiResponse.ok("Registered by invite", java.util.Map.of("auth", authService.registerByInvite(token, firstName, lastName, password, passwordConfirmation)));
    }
}
