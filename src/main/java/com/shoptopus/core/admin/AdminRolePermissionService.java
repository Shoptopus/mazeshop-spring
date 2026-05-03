package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminRolePermissionService {

    private final JdbcTemplate jdbc;

    public AdminRolePermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> roles() {
        List<Map<String, Object>> data = jdbc.queryForList("select id, name from roles order by id");
        return collection("roles", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> permissions() {
        List<Map<String, Object>> data = jdbc.queryForList("select id, name from permissions order by id");
        return collection("permissions", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rolePermissions(String roleRef) {
        long roleId = resolveRoleId(roleRef);
        List<Map<String, Object>> data = jdbc.queryForList("""
                select p.id, p.name
                from permissions p
                join role_has_permissions rp on rp.permission_id = p.id
                where rp.role_id = ?
                order by p.id
                """, roleId);
        return Map.of("role", roleRef, "data", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> roleUsers(String roleRef) {
        long roleId = resolveRoleId(roleRef);
        return Map.of("role", roleRef, "data", usersForRole(roleId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> availableUsers(String roleRef) {
        long roleId = resolveRoleId(roleRef);
        List<Map<String, Object>> data = jdbc.queryForList("""
                select u.id, u.email, u.name, u.first_name, u.last_name
                from users u
                where u.deleted_at is null
                  and not exists (
                      select 1 from model_has_roles mr where mr.user_id = u.id and mr.role_id = ?
                  )
                order by u.created_at desc
                """, roleId).stream().map(this::userRowWithoutRoles).toList();
        return Map.of("role", roleRef, "data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> createRole(Map<String, Object> body) {
        String normalized = normalizeRoleName(requiredName(body));
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", normalized);
        Long roleId = jdbc.queryForObject("select id from roles where name = ?", Long.class, normalized);
        return Map.of("id", roleId, "name", normalized);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateRole(String roleRef, Map<String, Object> body) {
        long roleId = resolveRoleId(roleRef);
        String normalized = normalizeRoleName(requiredName(body));
        jdbc.update("update roles set name = ?, updated_at = now() where id = ?", normalized, roleId);
        return Map.of("id", roleId, "name", normalized);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> deleteRole(String roleRef) {
        long roleId = resolveRoleId(roleRef);
        String roleName = jdbc.queryForObject("select name from roles where id = ?", String.class, roleId);
        if ("super_admin".equals(roleName)) {
            return Map.of("id", roleId, "name", roleName);
        }
        jdbc.update("delete from roles where id = ?", roleId);
        return Map.of("id", roleId, "name", roleName);
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> assignPermissionToRole(String roleRef, String permissionRef) {
        long roleId = resolveRoleId(roleRef);
        long permissionId = resolvePermissionId(permissionRef);
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", roleId, permissionId);
        return listData(rolePermissions(roleRef));
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> removePermissionFromRole(String roleRef, String permissionRef) {
        long roleId = resolveRoleId(roleRef);
        long permissionId = resolvePermissionId(permissionRef);
        jdbc.update("delete from role_has_permissions where role_id = ? and permission_id = ?", roleId, permissionId);
        return listData(rolePermissions(roleRef));
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> assignRoleToUser(String roleRef, String userRef) {
        long roleId = resolveRoleId(roleRef);
        UUID userId = resolveUserId(userRef);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?) on conflict do nothing", roleId, userId);
        return usersForRole(roleId);
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> removeRoleFromUser(String roleRef, String userRef) {
        long roleId = resolveRoleId(roleRef);
        UUID userId = resolveUserId(userRef);
        jdbc.update("delete from model_has_roles where role_id = ? and user_id = ?", roleId, userId);
        return usersForRole(roleId);
    }

    private Map<String, Object> collection(String message, List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listData(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("data");
    }

    private List<Map<String, Object>> usersForRole(long roleId) {
        return jdbc.queryForList("""
                select u.id, u.email, u.name, u.first_name, u.last_name
                from users u
                join model_has_roles mr on mr.user_id = u.id
                where mr.role_id = ?
                  and u.deleted_at is null
                order by u.created_at desc
                """, roleId).stream().map(this::userRowWithRoles).toList();
    }

    private Map<String, Object> userRowWithRoles(Map<String, Object> row) {
        UUID userId = UUID.fromString(String.valueOf(row.get("id")));
        Map<String, Object> mapped = userRowWithoutRoles(row);
        List<String> roles = jdbc.queryForList("""
                select r.name
                from roles r
                join model_has_roles mr on mr.role_id = r.id
                where mr.user_id = ?
                order by r.id
                """, String.class, userId);
        mapped.put("roles", roles);
        return mapped;
    }

    private Map<String, Object> userRowWithoutRoles(Map<String, Object> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", row.get("id"));
        mapped.put("email", row.get("email"));
        mapped.put("name", row.get("name"));
        mapped.put("first_name", row.get("first_name"));
        mapped.put("last_name", row.get("last_name"));
        return mapped;
    }

    private long resolveRoleId(String roleRef) {
        Optional<Long> numeric = longId(roleRef);
        Long roleId = numeric.orElseGet(() -> jdbc.query("""
                select id from roles where name = ? limit 1
                """, rs -> rs.next() ? rs.getLong(1) : null, roleRef));
        if (roleId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "Role not found.");
        }
        return roleId;
    }

    private long resolvePermissionId(String permissionRef) {
        Optional<Long> numeric = longId(permissionRef);
        Long permissionId = numeric.orElseGet(() -> jdbc.query("""
                select id from permissions where name = ? limit 1
                """, rs -> rs.next() ? rs.getLong(1) : null, permissionRef));
        if (permissionId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "Permission not found.");
        }
        return permissionId;
    }

    private UUID resolveUserId(String userRef) {
        try {
            UUID userId = UUID.fromString(userRef);
            Integer count = jdbc.queryForObject("select count(*) from users where id = ? and deleted_at is null", Integer.class, userId);
            if (count == null || count == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "User not found.");
            }
            return userId;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid user id.");
        }
    }

    private String requiredName(Map<String, Object> body) {
        if (body == null || body.get("name") == null || String.valueOf(body.get("name")).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
        }
        return String.valueOf(body.get("name"));
    }

    private String normalizeRoleName(String value) {
        return value.trim().toLowerCase().replace(' ', '_');
    }

    private Optional<Long> longId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
