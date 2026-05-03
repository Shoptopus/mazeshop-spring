package com.shoptopus.core.storefront;

import com.shoptopus.core.adminui.AdminUiMediaService;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/storefront/stores")
public class StorefrontStoreController {

    private final StorefrontStoreOnboardingService storefrontStoreOnboardingService;
    private final AdminUiMediaService adminUiMediaService;

    public StorefrontStoreController(
            StorefrontStoreOnboardingService storefrontStoreOnboardingService,
            AdminUiMediaService adminUiMediaService
    ) {
        this.storefrontStoreOnboardingService = storefrontStoreOnboardingService;
        this.adminUiMediaService = adminUiMediaService;
    }

    @PostMapping
    public ApiResponse<?> createStore(@RequestBody(required = false) Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(
                "Store application created",
                storefrontStoreOnboardingService.createStore(requireEmail(authentication), body == null ? Map.of() : body)
        );
    }

    @PostMapping("/logo")
    public ApiResponse<?> uploadLogo(@RequestParam("file") MultipartFile file, Authentication authentication) {
        requireEmail(authentication);
        return ApiResponse.ok("Store logo uploaded", adminUiMediaService.uploadImage(file, "stores"));
    }

    private String requireEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required.");
        }
        return authentication.getName();
    }
}
