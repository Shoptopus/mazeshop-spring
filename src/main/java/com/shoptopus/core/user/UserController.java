package com.shoptopus.core.user;

import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.user.dto.AccountUpdateRequest;
import com.shoptopus.core.user.dto.AddressRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/favorites")
    public ApiResponse<?> favorites(Authentication authentication) {
        var data = userService.favorites(authentication.getName());
        return ApiResponse.ok("Favorites", data);
    }

    @GetMapping("/user/account")
    public ApiResponse<?> getAccount(Authentication authentication) {
        return ApiResponse.ok("Account", userService.getAccount(authentication.getName()));
    }

    @PostMapping("/user/account")
    public ApiResponse<?> updateAccount(Authentication authentication, @Valid @RequestBody AccountUpdateRequest request) {
        return ApiResponse.ok("Account updated", userService.updateAccount(authentication.getName(), request));
    }

    @DeleteMapping("/user/account")
    public ApiResponse<?> deleteAccount(
            Authentication authentication,
            @RequestParam(name = "anonimize", required = false, defaultValue = "false") boolean anonimize
    ) {
        userService.deleteAccount(authentication.getName(), anonimize);
        return ApiResponse.ok("User account deleted", null);
    }

    @GetMapping("/addresses")
    public ApiResponse<?> getAddresses(
            Authentication authentication,
            @RequestParam(name = "filter[id]", required = false) String filterId
    ) {
        var data = userService.getAddresses(authentication.getName(), filterId);
        return ApiResponse.paged("Addresses", data, data.size(), (long) data.size(), null);
    }

    @PostMapping("/addresses")
    public ApiResponse<?> createAddress(Authentication authentication, @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address created", userService.createAddress(authentication.getName(), request));
    }

    @GetMapping("/address/{id}")
    public ApiResponse<?> getAddress(Authentication authentication, @PathVariable UUID id) {
        return ApiResponse.ok("Address", userService.getAddress(authentication.getName(), id));
    }

    @PatchMapping("/address/{id}")
    public ApiResponse<?> updateAddress(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address updated", userService.updateAddress(authentication.getName(), id, request));
    }

    @DeleteMapping("/address/{id}")
    public ApiResponse<?> deleteAddress(Authentication authentication, @PathVariable UUID id) {
        userService.deleteAddress(authentication.getName(), id);
        return ApiResponse.ok("Address deleted", null);
    }
}
