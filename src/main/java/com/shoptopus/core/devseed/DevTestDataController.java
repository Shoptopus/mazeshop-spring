package com.shoptopus.core.devseed;

import com.shoptopus.core.admin.AdminAccessService;
import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin-ui/dev/test-data")
@ConditionalOnProperty(value = "app.dev.test-data.enabled", havingValue = "true")
public class DevTestDataController {

    private final DevTestDataService devTestDataService;
    private final AdminAccessService adminAccessService;

    public DevTestDataController(DevTestDataService devTestDataService, AdminAccessService adminAccessService) {
        this.devTestDataService = devTestDataService;
        this.adminAccessService = adminAccessService;
    }

    @GetMapping
    public ApiResponse<?> status(Authentication authentication) {
        adminAccessService.requireSuperAdmin(authentication);
        return ApiResponse.ok("Development test-data status", devTestDataService.status());
    }

    @PostMapping("/seed")
    public ApiResponse<?> seed(Authentication authentication) {
        adminAccessService.requireSuperAdmin(authentication);
        return ApiResponse.ok("Development test data seeded", devTestDataService.seed());
    }

    @DeleteMapping("/seed")
    public ApiResponse<?> cleanup(Authentication authentication) {
        adminAccessService.requireSuperAdmin(authentication);
        return ApiResponse.ok("Development test data cleaned", devTestDataService.cleanup());
    }
}
