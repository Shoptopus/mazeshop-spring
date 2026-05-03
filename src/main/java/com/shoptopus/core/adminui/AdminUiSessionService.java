package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminReportingService;
import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.AuthPayloadDto;
import com.shoptopus.core.auth.dto.AuthUserDto;
import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUiSessionService {

    private final AuthService authService;
    private final AdminReportingService adminReportingService;
    private final JdbcTemplate jdbc;
    private final boolean multiStoreEnabled;
    private final AdminUiStoreService adminUiStoreService;
    private final AdminStoreContext adminStoreContext;
    private final AdminRoleScopeService adminRoleScopeService;

    public AdminUiSessionService(
            AuthService authService,
            AdminReportingService adminReportingService,
            JdbcTemplate jdbc,
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            AdminUiStoreService adminUiStoreService,
            AdminStoreContext adminStoreContext,
            AdminRoleScopeService adminRoleScopeService
    ) {
        this.authService = authService;
        this.adminReportingService = adminReportingService;
        this.jdbc = jdbc;
        this.multiStoreEnabled = multiStoreEnabled;
        this.adminUiStoreService = adminUiStoreService;
        this.adminStoreContext = adminStoreContext;
        this.adminRoleScopeService = adminRoleScopeService;
    }

    public Map<String, Object> login(LoginRequest request) {
        AuthPayloadDto auth = authService.login(request);
        return Map.of(
                "auth", auth,
                "session", session(auth.user().email())
        );
    }

    public Map<String, Object> session(String email) {
        AuthUserDto user = authService.me(email);
        UUID activeStoreId = adminStoreContext.activeStoreOptional(email).map(com.shoptopus.core.store.Store::getId).orElse(null);
        List<String> roles = List.copyOf(adminRoleScopeService.effectiveRoles(email, activeStoreId));
        Set<String> permissions = adminRoleScopeService.effectivePermissions(email, activeStoreId);
        boolean superAdmin = roles.contains("super_admin");
        if (!superAdmin && permissions.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Admin access required.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", user);
        payload.put("roles", roles);
        payload.put("permissions", List.copyOf(permissions));
        payload.put("navigation", navigation(email, roles, superAdmin, permissions));
        payload.put("multi_store_enabled", multiStoreEnabled);
        payload.put("manageable_stores", canManageStores(email, superAdmin, permissions)
                ? List.copyOf(adminUiStoreService.manageableStoresFor(email))
                : List.of());
        payload.put("active_store", activeStoreRow(activeStoreId));
        return payload;
    }

    public List<Map<String, Object>> navigation(String email) {
        UUID activeStoreId = adminStoreContext.activeStoreOptional(email).map(com.shoptopus.core.store.Store::getId).orElse(null);
        List<String> roles = List.copyOf(adminRoleScopeService.effectiveRoles(email, activeStoreId));
        Set<String> permissions = adminRoleScopeService.effectivePermissions(email, activeStoreId);
        boolean superAdmin = roles.contains("super_admin");
        if (!superAdmin && permissions.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Admin access required.");
        }
        return navigation(email, roles, superAdmin, permissions);
    }

    public Map<String, Object> dashboard(String email) {
        requirePermission(email, "reports.view");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) adminReportingService.dashboard().get("data");
        return data;
    }

    public Set<String> permissions(String email) {
        session(email);
        UUID activeStoreId = adminStoreContext.activeStoreOptional(email).map(com.shoptopus.core.store.Store::getId).orElse(null);
        return adminRoleScopeService.effectivePermissions(email, activeStoreId);
    }

    public void requirePermission(String email, String permission) {
        UUID activeStoreId = adminStoreContext.activeStoreOptional(email).map(com.shoptopus.core.store.Store::getId).orElse(null);
        Set<String> roles = adminRoleScopeService.effectiveRoles(email, activeStoreId);
        if (roles.contains("super_admin")) {
            return;
        }
        if (!adminRoleScopeService.effectivePermissions(email, activeStoreId).contains(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Admin access required.");
        }
    }

    public void requireGlobalStoreManagement(String email) {
        if (!multiStoreEnabled || !adminRoleScopeService.hasGlobalStoreManagementAccess(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Admin access required.");
        }
    }

    public void requireGoogleMerchantAccess(String email) {
        UUID activeStoreId = adminStoreContext.activeStoreOptional(email)
                .map(com.shoptopus.core.store.Store::getId)
                .or(() -> adminStoreContext.defaultStore().map(com.shoptopus.core.store.Store::getId))
                .orElse(null);
        Set<String> roles = adminRoleScopeService.effectiveRoles(email, activeStoreId);
        if (roles.contains("super_admin") || roles.contains("store_manager")) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Admin access required.");
    }

    private List<Map<String, Object>> navigation(String email, List<String> roles, boolean superAdmin, Set<String> permissions) {
        List<Map<String, Object>> items = new ArrayList<>();
        addItem(items, superAdmin, permissions, "reports.view", "Dashboard", "/", "overview");
        addItem(items, superAdmin, permissions, "products.view", "Products", "/products", "catalog");
        addItem(items, superAdmin, permissions, "product_categories.view", "Categories", "/categories", "catalog");
        addItem(items, superAdmin, permissions, "product_tags.view", "Tags", "/tags", "catalog");
        addItem(items, superAdmin, permissions, "product_attributes.view", "Attributes", "/attributes", "catalog");
        addItem(items, superAdmin, permissions, "pages.view", "Pages", "/pages", "catalog");
        addItem(items, superAdmin, permissions, "orders.view", "Orders", "/orders", "sales");
        addItem(items, superAdmin, permissions, "messages.view", "Messages", "/messages", "sales");
        addItem(items, superAdmin, permissions, "payments.manage", "Payments", "/payments", "sales");
        addItem(items, superAdmin, permissions, "voucher_codes.view", "Vouchers", "/vouchers", "sales");
        addItem(items, superAdmin, permissions, "discount_rules.view", "Discount Rules", "/discount-rules", "sales");
        addItem(items, superAdmin, permissions, "delivery_types.view", "Delivery", "/delivery", "sales");
        addItem(items, superAdmin, permissions, "customers.view", "Customers", "/customers", "people");
        addItem(items, superAdmin, permissions, "users.view", "Users", "/users", "people");
        addItem(items, superAdmin, permissions, "carts.view", "Carts", "/carts", "people");
        addItem(items, superAdmin, permissions, "ratings.manage", "Ratings", "/ratings", "marketing");
        addItem(items, superAdmin, permissions, "banners.manage", "Banners", "/banners", "marketing");
        addItem(items, superAdmin, permissions, "roles.view", "Roles & Permissions", "/roles", "system");
        addItem(items, superAdmin, permissions, "roles.view", "Permissions", "/permissions", "system");
        addItem(items, superAdmin, permissions, "files.view", "Files", "/files", "system");
        addItem(items, superAdmin, permissions, "audits.view", "Audits", "/audits", "system");
        addItem(items, superAdmin, permissions, "emails.manage", "Email Users", "/emails", "system");
        addItem(items, superAdmin, permissions, "notifications.view", "Notifications", "/notifications", "system");
        addItem(items, superAdmin, permissions, "shop_settings.view", "Shop Settings", "/shop-settings", "system");
        if (multiStoreEnabled && canManageStores(email, superAdmin, permissions)) {
            addItem(items, superAdmin, permissions, "can.view.stores", "Stores", "/stores", "system");
        }
        if (roles.contains("super_admin") || roles.contains("store_manager")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", "Google Merchant");
            item.put("href", "/google-merchant");
            item.put("section", "system");
            item.put("permission", "google_merchant.manage");
            items.add(item);
        }
        return items;
    }

    private boolean canManageStores(String email, boolean superAdmin, Set<String> permissions) {
        return superAdmin || adminRoleScopeService.hasGlobalStoreManagementAccess(email);
    }

    private Map<String, Object> activeStoreRow(UUID activeStoreId) {
        if (activeStoreId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.query("""
                select id, name, slug, status
                from stores
                where id = ?::uuid
                  and deleted_at is null
        """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            UUID id = rs.getObject("id", UUID.class);
            row.put("id", id);
            row.put("name", multiStoreEnabled && com.shoptopus.core.store.DefaultStoreResolver.DEFAULT_STORE_ID.equals(id)
                    ? "Marketplace"
                    : rs.getString("name"));
            row.put("slug", rs.getString("slug"));
            row.put("status", rs.getString("status") == null ? null : rs.getString("status").toLowerCase());
            return row;
        }, activeStoreId.toString());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void addItem(
            List<Map<String, Object>> items,
            boolean superAdmin,
            Set<String> permissions,
            String permission,
            String label,
            String href,
            String section
    ) {
        if (!superAdmin && !permissions.contains(permission)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("href", href);
        item.put("section", section);
        item.put("permission", permission);
        items.add(item);
    }
}
