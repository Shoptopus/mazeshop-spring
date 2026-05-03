package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.admin.AdminRolePermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminUiRolePermissionService {

    private final JdbcTemplate jdbc;
    private final AdminRolePermissionService adminRolePermissionService;

    public AdminUiRolePermissionService(JdbcTemplate jdbc, AdminRolePermissionService adminRolePermissionService) {
        this.jdbc = jdbc;
        this.adminRolePermissionService = adminRolePermissionService;
    }

    public Map<String, Object> roles(String search, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<Map<String, Object>> filtered = jdbc.queryForList("select id, name, created_at from roles order by id").stream()
                .filter(row -> matchesSearch(String.valueOf(row.get("name")), search))
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("name"))))
                .map(this::roleListItem)
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of("search", search == null ? "" : search)
        );
    }

    public Map<String, Object> role(String roleRef) {
        long roleId = resolveRoleId(roleRef);
        String roleName = jdbc.queryForObject("select name from roles where id = ?", String.class, roleId);
        List<Map<String, Object>> permissions = jdbc.queryForList("""
                select p.id, p.name
                from permissions p
                join role_has_permissions rp on rp.permission_id = p.id
                where rp.role_id = ?
                order by p.name
                """, roleId).stream().map(row -> {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("id", row.get("id"));
            mapped.put("name", row.get("name"));
            return mapped;
        }).toList();
        List<Map<String, Object>> users = jdbc.queryForList("""
                select u.id, u.email, u.name, u.first_name, u.last_name
                from users u
                join model_has_roles mr on mr.user_id = u.id
                where mr.role_id = ?
                  and u.deleted_at is null
                order by u.created_at desc
                """, roleId).stream().map(this::userRow).toList();
        List<Map<String, Object>> availableUsers = jdbc.queryForList("""
                select u.id, u.email, u.name, u.first_name, u.last_name
                from users u
                where u.deleted_at is null
                  and not exists (
                      select 1 from model_has_roles mr where mr.user_id = u.id and mr.role_id = ?
                  )
                order by u.created_at desc
                limit 10
                """, roleId).stream().map(this::userRow).toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", roleId);
        row.put("name", roleName);
        row.put("metrics", Map.of(
                "permissions", permissions.size(),
                "users", users.size()
        ));
        row.put("permissions", permissions);
        row.put("users", users);
        row.put("available_users_preview", availableUsers);
        row.put("form_options", roleFormOptions());
        return row;
    }

    public Map<String, Object> roleFormOptions() {
        return Map.of(
                "permissions_catalog", jdbc.queryForList("select id, name from permissions order by name").stream()
                        .map(row -> Map.of(
                                "id", row.get("id"),
                                "name", row.get("name")
                        ))
                        .toList()
        );
    }

    public Map<String, Object> createRole(Map<String, Object> body) {
        Map<String, Object> created = adminRolePermissionService.createRole(body);
        return role(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateRole(String roleRef, Map<String, Object> body) {
        adminRolePermissionService.updateRole(roleRef, body);
        return role(roleRef);
    }

    public Map<String, Object> deleteRole(String roleRef) {
        adminRolePermissionService.deleteRole(roleRef);
        return Map.of("id", roleRef);
    }

    public Map<String, Object> assignPermission(String roleRef, String permissionRef) {
        adminRolePermissionService.assignPermissionToRole(roleRef, permissionRef);
        return role(roleRef);
    }

    public Map<String, Object> removePermission(String roleRef, String permissionRef) {
        adminRolePermissionService.removePermissionFromRole(roleRef, permissionRef);
        return role(roleRef);
    }

    public Map<String, Object> assignUser(String roleRef, String userRef) {
        adminRolePermissionService.assignRoleToUser(roleRef, userRef);
        return role(roleRef);
    }

    public Map<String, Object> removeUser(String roleRef, String userRef) {
        adminRolePermissionService.removeRoleFromUser(roleRef, userRef);
        return role(roleRef);
    }

    public Map<String, Object> permissions(String search, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 25 : Math.min(perPage, 200);

        List<Map<String, Object>> filtered = jdbc.queryForList("select id, name, created_at from permissions order by id").stream()
                .filter(row -> matchesSearch(String.valueOf(row.get("name")), search))
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("name"))))
                .map(this::permissionListItem)
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of("search", search == null ? "" : search)
        );
    }

    private Map<String, Object> roleListItem(Map<String, Object> row) {
        long roleId = ((Number) row.get("id")).longValue();
        int permissionsCount = jdbc.queryForObject("select count(*) from role_has_permissions where role_id = ?", Integer.class, roleId);
        int usersCount = jdbc.queryForObject("select count(*) from model_has_roles where role_id = ?", Integer.class, roleId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", roleId);
        item.put("name", row.get("name"));
        item.put("permissions_count", permissionsCount);
        item.put("users_count", usersCount);
        item.put("system", "super_admin".equals(String.valueOf(row.get("name"))));
        item.put("created_at", format(row.get("created_at")));
        return item;
    }

    private Map<String, Object> permissionListItem(Map<String, Object> row) {
        long permissionId = ((Number) row.get("id")).longValue();
        int rolesCount = jdbc.queryForObject("select count(*) from role_has_permissions where permission_id = ?", Integer.class, permissionId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", permissionId);
        item.put("name", row.get("name"));
        item.put("roles_count", rolesCount);
        item.put("group", permissionGroup(String.valueOf(row.get("name"))));
        item.put("created_at", format(row.get("created_at")));
        return item;
    }

    private String format(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof OffsetDateTime value) {
            return value.toString();
        }
        return String.valueOf(raw);
    }

    private String permissionGroup(String permission) {
        int index = permission.indexOf('.');
        int underscore = permission.indexOf('_');
        if (index > 0) {
            return permission.substring(0, index);
        }
        if (underscore > 0) {
            return permission.substring(0, underscore);
        }
        return "misc";
    }

    private Map<String, Object> userRow(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("email", row.get("email"));
        item.put("name", row.get("name"));
        item.put("first_name", row.get("first_name"));
        item.put("last_name", row.get("last_name"));
        return item;
    }

    private boolean matchesSearch(String value, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        return value.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> pageMeta(int page, int perPage, int total) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("per_page", perPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / perPage));
        return meta;
    }

    private long resolveRoleId(String roleRef) {
        try {
            long id = Long.parseLong(roleRef);
            Integer count = jdbc.queryForObject("select count(*) from roles where id = ?", Integer.class, id);
            if (count != null && count > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
        }
        Long roleId = jdbc.query("""
                select id from roles where name = ? limit 1
                """, rs -> rs.next() ? rs.getLong(1) : null, roleRef);
        if (roleId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Role not found", "Role not found");
        }
        return roleId;
    }
}
