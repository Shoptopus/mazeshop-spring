package com.shoptopus.core.admin;

import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.auth.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminPeopleController {

    private final AdminSupportService adminSupportService;
    private final AuthService authService;
    private final AdminAccessService adminAccessService;
    private final AdminRolePermissionService adminRolePermissionService;
    private final AdminBulkMutationService adminBulkMutationService;
    private final AdminNoteService adminNoteService;
    private final AdminFileService adminFileService;
    private final AdminAuditService adminAuditService;
    private final AdminRatingService adminRatingService;
    private final AdminBannerService adminBannerService;
    private final AdminUserService adminUserService;
    private final AdminCustomerService adminCustomerService;
    private final AdminCartService adminCartService;
    private final AdminSupportChannelsService adminSupportChannelsService;
    private final AdminUserAvatarService adminUserAvatarService;
    private final AdminAiService adminAiService;

    public AdminPeopleController(
            AdminSupportService adminSupportService,
            AuthService authService,
            AdminAccessService adminAccessService,
            AdminRolePermissionService adminRolePermissionService,
            AdminBulkMutationService adminBulkMutationService,
            AdminNoteService adminNoteService,
            AdminFileService adminFileService,
            AdminAuditService adminAuditService,
            AdminRatingService adminRatingService,
            AdminBannerService adminBannerService,
            AdminUserService adminUserService,
            AdminCustomerService adminCustomerService,
            AdminCartService adminCartService,
            AdminSupportChannelsService adminSupportChannelsService,
            AdminUserAvatarService adminUserAvatarService,
            AdminAiService adminAiService
    ) {
        this.adminSupportService = adminSupportService;
        this.authService = authService;
        this.adminAccessService = adminAccessService;
        this.adminRolePermissionService = adminRolePermissionService;
        this.adminBulkMutationService = adminBulkMutationService;
        this.adminNoteService = adminNoteService;
        this.adminFileService = adminFileService;
        this.adminAuditService = adminAuditService;
        this.adminRatingService = adminRatingService;
        this.adminBannerService = adminBannerService;
        this.adminUserService = adminUserService;
        this.adminCustomerService = adminCustomerService;
        this.adminCartService = adminCartService;
        this.adminSupportChannelsService = adminSupportChannelsService;
        this.adminUserAvatarService = adminUserAvatarService;
        this.adminAiService = adminAiService;
    }

    @GetMapping("/users")
    public Map<String, Object> users(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "users.view");
        return adminUserService.list();
    }

    @PostMapping("/user")
    public ApiResponse<?> createUser(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "users.manage");
        throw new com.shoptopus.core.common.error.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Validation failed", "Users must be invited before they can register.");
    }

    @PatchMapping("/user/avatar")
    public ApiResponse<?> updateAvatar(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "users.manage");
        return ApiResponse.ok("Updated", adminUserAvatarService.updateAvatar(authentication.getName(), body));
    }

    @PostMapping("/user/avatar")
    public ApiResponse<?> updateAvatarViaPost(Authentication authentication, @RequestBody Map<String, Object> body) { return updateAvatar(authentication, body); }

    @PostMapping("/user/invite")
    public ApiResponse<?> invite(@RequestBody Map<String, Object> body, org.springframework.security.core.Authentication authentication) {
        adminAccessService.requirePermission(authentication, "users.can.invite");
        String email = body.get("email") == null ? null : String.valueOf(body.get("email"));
        String role = body.get("role") == null ? null : String.valueOf(body.get("role"));
        return ApiResponse.ok("Invited", authService.inviteSystemUser(authentication.getName(), email, role));
    }

    @GetMapping("/user/{user}")
    public ApiResponse<?> showUser(Authentication authentication, @PathVariable String user) {
        adminAccessService.requirePermission(authentication, "users.view");
        return ApiResponse.ok("User", adminUserService.show(user));
    }

    @PatchMapping("/user/{user}")
    public ApiResponse<?> updateUser(Authentication authentication, @PathVariable String user, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "users.manage");
        return ApiResponse.ok("Updated", adminUserService.update(user, body));
    }

    @PostMapping("/user/{user}")
    public ApiResponse<?> updateUserViaPost(Authentication authentication, @PathVariable String user, @RequestBody Map<String, Object> body) { return updateUser(authentication, user, body); }

    @DeleteMapping("/user/{user}")
    public ApiResponse<?> deleteUser(Authentication authentication, @PathVariable String user) {
        adminAccessService.requirePermission(authentication, "users.manage");
        return ApiResponse.ok("Deleted", adminUserService.delete(user));
    }

    @GetMapping("/customers")
    public Map<String, Object> customers(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "customers.view");
        return adminCustomerService.list();
    }

    @GetMapping("/customer/{customer}")
    public ApiResponse<?> showCustomer(Authentication authentication, @PathVariable String customer) {
        adminAccessService.requirePermission(authentication, "customers.view");
        return ApiResponse.ok("Customer", adminCustomerService.show(customer));
    }

    @GetMapping("/carts")
    public Map<String, Object> carts(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "carts.view");
        return adminCartService.list();
    }

    @PostMapping("/cart")
    public ApiResponse<?> createCart(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "carts.manage");
        return ApiResponse.ok("Created", adminCartService.create(body));
    }

    @GetMapping("/cart/{cart}")
    public ApiResponse<?> showCart(Authentication authentication, @PathVariable String cart) {
        adminAccessService.requirePermission(authentication, "carts.view");
        return ApiResponse.ok("Cart", adminCartService.show(cart));
    }

    @PatchMapping("/cart/{cart}")
    public ApiResponse<?> updateCart(Authentication authentication, @PathVariable String cart, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "carts.manage");
        return ApiResponse.ok("Updated", adminCartService.update(cart, body));
    }

    @DeleteMapping("/cart/{cart}")
    public ApiResponse<?> deleteCart(Authentication authentication, @PathVariable String cart) {
        adminAccessService.requirePermission(authentication, "carts.manage");
        return ApiResponse.ok("Deleted", adminCartService.delete(cart));
    }

    @GetMapping("/ratings")
    public Map<String, Object> ratings(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "ratings.manage");
        return adminRatingService.list();
    }

    @PostMapping("/ratings/bulk/availability")
    public ApiResponse<?> ratingsAvailability(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "ratings.manage");
        return ApiResponse.ok("Updated", adminBulkMutationService.ratingsAvailability(body));
    }

    @PostMapping("/ratings/bulk/verified-status")
    public ApiResponse<?> ratingsVerified(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "ratings.manage");
        return ApiResponse.ok("Updated", adminBulkMutationService.ratingsVerified(body));
    }

    @GetMapping("/rating/{rating}")
    public ApiResponse<?> showRating(Authentication authentication, @PathVariable String rating) {
        adminAccessService.requirePermission(authentication, "ratings.manage");
        return ApiResponse.ok("Rating", adminRatingService.show(rating));
    }

    @DeleteMapping("/rating/{rating}")
    public ApiResponse<?> deleteRating(Authentication authentication, @PathVariable String rating) {
        adminAccessService.requirePermission(authentication, "ratings.manage");
        return ApiResponse.ok("Deleted", adminRatingService.delete(rating));
    }

    @GetMapping("/banners")
    public Map<String, Object> banners(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return adminBannerService.list();
    }

    @PostMapping("/banners/bulk/availability")
    public ApiResponse<?> bannersAvailability(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Updated", adminBulkMutationService.bannersAvailability(body));
    }

    @DeleteMapping("/banners/bulk")
    public ApiResponse<?> bannersBulkDelete(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Deleted", adminBulkMutationService.bannersDelete(body));
    }

    // Laravel route parity aliases:
    // /api/admin/banner/bulk/availability and /api/admin/banner/bulk
    @PostMapping("/banner/bulk/availability")
    public ApiResponse<?> bannerAvailabilityAlias(Authentication authentication, @RequestBody Map<String, Object> body) { return bannersAvailability(authentication, body); }

    @DeleteMapping("/banner/bulk")
    public ApiResponse<?> bannerBulkDeleteAlias(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) { return bannersBulkDelete(authentication, body); }

    @PostMapping("/banner")
    public ApiResponse<?> createBanner(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Created", adminBannerService.create(body));
    }

    @GetMapping("/banner/{banner}")
    public ApiResponse<?> showBanner(Authentication authentication, @PathVariable String banner) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Banner", adminBannerService.show(banner));
    }

    @PatchMapping("/banner/{banner}")
    public ApiResponse<?> updateBanner(Authentication authentication, @PathVariable String banner, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Updated", adminBannerService.update(banner, body));
    }

    @PostMapping("/banner/{banner}")
    public ApiResponse<?> updateBannerViaPost(Authentication authentication, @PathVariable String banner, @RequestBody Map<String, Object> body) { return updateBanner(authentication, banner, body); }

    @DeleteMapping("/banner/{banner}")
    public ApiResponse<?> deleteBanner(Authentication authentication, @PathVariable String banner) {
        adminAccessService.requirePermission(authentication, "banners.manage");
        return ApiResponse.ok("Deleted", adminBannerService.delete(banner));
    }

    @GetMapping("/files")
    public Map<String, Object> files(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "files.view");
        return adminFileService.list();
    }

    @DeleteMapping("/files/bulk")
    public ApiResponse<?> filesBulkDelete(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "files.manage");
        return ApiResponse.ok("Deleted", adminBulkMutationService.filesDelete(body));
    }

    @PostMapping("/file")
    public ApiResponse<?> createFile(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "files.manage");
        return ApiResponse.ok("Created", adminFileService.create(body));
    }

    @GetMapping("/file/{file}")
    public ApiResponse<?> showFile(Authentication authentication, @PathVariable String file) {
        adminAccessService.requirePermission(authentication, "files.view");
        return ApiResponse.ok("File", adminFileService.show(file));
    }

    @PatchMapping("/file/{file}")
    public ApiResponse<?> updateFile(Authentication authentication, @PathVariable String file, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "files.manage");
        return ApiResponse.ok("Updated", adminFileService.update(file, body));
    }

    @PostMapping("/file/{file}")
    public ApiResponse<?> updateFileViaPost(Authentication authentication, @PathVariable String file, @RequestBody Map<String, Object> body) { return updateFile(authentication, file, body); }

    @DeleteMapping("/files/{file}")
    public ApiResponse<?> deleteFileAlias(Authentication authentication, @PathVariable String file) { return deleteFile(authentication, file); }

    @PostMapping("/file/{file}/download")
    public ApiResponse<?> downloadFile(Authentication authentication, @PathVariable String file) {
        adminAccessService.requirePermission(authentication, "files.view");
        return ApiResponse.ok("Download", adminFileService.download(file));
    }

    @DeleteMapping("/file/{file}")
    public ApiResponse<?> deleteFile(Authentication authentication, @PathVariable String file) {
        adminAccessService.requirePermission(authentication, "files.manage");
        return ApiResponse.ok("Deleted", adminFileService.delete(file));
    }

    @GetMapping("/notes")
    public Map<String, Object> notes(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "notes.view");
        return adminNoteService.list();
    }

    @PostMapping("/note")
    public ApiResponse<?> createNote(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "notes.manage");
        return ApiResponse.ok("Created", adminNoteService.create(body));
    }

    @GetMapping("/note/{note}")
    public ApiResponse<?> showNote(Authentication authentication, @PathVariable String note) {
        adminAccessService.requirePermission(authentication, "notes.view");
        return ApiResponse.ok("Note", adminNoteService.show(note));
    }

    @PatchMapping("/note/{note}")
    public ApiResponse<?> updateNote(Authentication authentication, @PathVariable String note, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "notes.manage");
        return ApiResponse.ok("Updated", adminNoteService.update(note, body));
    }

    @PostMapping("/note/{note}")
    public ApiResponse<?> updateNoteViaPost(Authentication authentication, @PathVariable String note, @RequestBody Map<String, Object> body) { return updateNote(authentication, note, body); }

    @DeleteMapping("/note/{note}")
    public ApiResponse<?> deleteNote(Authentication authentication, @PathVariable String note) {
        adminAccessService.requirePermission(authentication, "notes.manage");
        return ApiResponse.ok("Deleted", adminNoteService.delete(note));
    }

    @GetMapping("/audits")
    public Map<String, Object> audits(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "audits.view");
        return adminAuditService.list();
    }

    @GetMapping("/audit/{audit}")
    public ApiResponse<?> showAudit(Authentication authentication, @PathVariable String audit) {
        adminAccessService.requirePermission(authentication, "audits.view");
        return ApiResponse.ok("Audit", adminAuditService.show(audit));
    }

    @GetMapping("/roles")
    public Map<String, Object> roles(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "roles.view");
        return adminRolePermissionService.roles();
    }

    @PostMapping("/role")
    public ApiResponse<?> createRole(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Created", adminRolePermissionService.createRole(body));
    }

    @GetMapping("/role/{role}/permissions")
    public ApiResponse<?> rolePermissions(Authentication authentication, @PathVariable String role) {
        adminAccessService.requirePermission(authentication, "roles.view");
        return ApiResponse.ok("Role permissions", adminRolePermissionService.rolePermissions(role));
    }

    @GetMapping("/role/{role}/users")
    public ApiResponse<?> roleUsers(Authentication authentication, @PathVariable String role) {
        adminAccessService.requirePermission(authentication, "roles.view");
        return ApiResponse.ok("Role users", adminRolePermissionService.roleUsers(role));
    }

    @GetMapping("/role/{role}/available-users")
    public ApiResponse<?> availableUsers(Authentication authentication, @PathVariable String role) {
        adminAccessService.requirePermission(authentication, "roles.view");
        return ApiResponse.ok("Available users", adminRolePermissionService.availableUsers(role));
    }

    @DeleteMapping("/role/{role}")
    public ApiResponse<?> deleteRole(Authentication authentication, @PathVariable String role) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Deleted", adminRolePermissionService.deleteRole(role));
    }

    @DeleteMapping("/role/{role}/permission/{permission}")
    public ApiResponse<?> removePermissionFromRole(Authentication authentication, @PathVariable String role, @PathVariable String permission) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Detached", adminRolePermissionService.removePermissionFromRole(role, permission));
    }

    @PostMapping("/role/{role}/permission/{permission}")
    public ApiResponse<?> assignPermissionToRole(Authentication authentication, @PathVariable String role, @PathVariable String permission) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Attached", adminRolePermissionService.assignPermissionToRole(role, permission));
    }

    @PatchMapping("/role/{role}")
    public ApiResponse<?> updateRole(Authentication authentication, @PathVariable String role, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Updated", adminRolePermissionService.updateRole(role, body));
    }

    @PatchMapping("/role/{role}/users/{user}")
    public ApiResponse<?> assignRoleToUser(Authentication authentication, @PathVariable String role, @PathVariable String user) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Assigned", adminRolePermissionService.assignRoleToUser(role, user));
    }

    @DeleteMapping("/role/{role}/users/{user}")
    public ApiResponse<?> removeRoleFromUser(Authentication authentication, @PathVariable String role, @PathVariable String user) {
        adminAccessService.requirePermission(authentication, "roles.manage");
        return ApiResponse.ok("Removed", adminRolePermissionService.removeRoleFromUser(role, user));
    }

    @GetMapping("/permissions")
    public Map<String, Object> permissions(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "roles.view");
        return adminRolePermissionService.permissions();
    }

    @GetMapping("/get-users")
    public ApiResponse<?> emailUsers(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "emails.manage");
        return ApiResponse.ok("Users", adminSupportChannelsService.emailUsers());
    }

    @PostMapping("/send-email")
    public ApiResponse<?> sendEmail(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "emails.manage");
        return ApiResponse.ok("Email queued", adminSupportChannelsService.sendEmail(body));
    }

    @PostMapping("/ai/translations")
    public ApiResponse<?> aiTranslations(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "ai.manage");
        return ApiResponse.ok("Translations", adminAiService.translations(body));
    }

    @PostMapping("/ai/optimise-rewrite")
    public ApiResponse<?> aiRewrite(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "ai.manage");
        return ApiResponse.ok("Rewrite", adminAiService.optimiseRewrite(body));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> adminNotifications(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "notifications.view");
        return ApiResponse.ok("Notifications", adminSupportChannelsService.notifications(authentication.getName()));
    }

    @PostMapping("/notifications")
    public ApiResponse<?> clearAdminNotifications(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "notifications.manage");
        return ApiResponse.ok("Cleared", adminSupportChannelsService.clearNotifications(authentication.getName()));
    }
}
