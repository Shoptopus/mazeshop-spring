package com.shoptopus.core.adminui;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminRoleScopeService {

    private final JdbcTemplate jdbc;

    public AdminRoleScopeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> globalRoles(String email) {
        return new LinkedHashSet<>(jdbc.query("""
                select distinct r.name
                from users u
                join model_has_roles mr on mr.user_id = u.id
                join roles r on r.id = mr.role_id
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                order by r.name
                """, (rs, rowNum) -> rs.getString("name"), email));
    }

    public Set<String> scopedRoles(String email, UUID storeId) {
        if (storeId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.query("""
                select distinct r.name
                from users u
                join user_store_roles usr on usr.user_id = u.id
                join roles r on r.id = usr.role_id
                where lower(u.email) = lower(?)
                  and usr.store_id = ?::uuid
                  and u.deleted_at is null
                order by r.name
                """, (rs, rowNum) -> rs.getString("name"), email, storeId.toString()));
    }

    public Set<String> effectiveRoles(String email, UUID storeId) {
        Set<String> roles = new LinkedHashSet<>(globalRoles(email));
        roles.addAll(scopedRoles(email, storeId));
        return roles;
    }

    public Set<String> globalPermissions(String email) {
        return new LinkedHashSet<>(jdbc.query("""
                select distinct p.name
                from users u
                join model_has_roles mr on mr.user_id = u.id
                join role_has_permissions rp on rp.role_id = mr.role_id
                join permissions p on p.id = rp.permission_id
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                order by p.name
                """, (rs, rowNum) -> rs.getString("name"), email));
    }

    public Set<String> scopedPermissions(String email, UUID storeId) {
        if (storeId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.query("""
                select distinct p.name
                from users u
                join user_store_roles usr on usr.user_id = u.id
                join role_has_permissions rp on rp.role_id = usr.role_id
                join permissions p on p.id = rp.permission_id
                where lower(u.email) = lower(?)
                  and usr.store_id = ?::uuid
                  and u.deleted_at is null
                order by p.name
                """, (rs, rowNum) -> rs.getString("name"), email, storeId.toString()));
    }

    public Set<String> effectivePermissions(String email, UUID storeId) {
        Set<String> permissions = new LinkedHashSet<>(globalPermissions(email));
        permissions.addAll(scopedPermissions(email, storeId));
        return permissions;
    }

    public Set<UUID> assignedStoreIds(String email) {
        Set<UUID> ids = new LinkedHashSet<>(jdbc.query("""
                select distinct usr.store_id
                from users u
                join user_store_roles usr on usr.user_id = u.id
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                """, (rs, rowNum) -> UUID.fromString(rs.getString(1)), email));
        ids.addAll(jdbc.query("""
                select distinct u.store_id
                from users u
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                  and u.store_id is not null
                """, (rs, rowNum) -> UUID.fromString(rs.getString(1)), email));
        return ids;
    }

    public boolean hasGlobalStoreManagementAccess(String email) {
        Set<String> roles = globalRoles(email);
        if (roles.contains("super_admin")) {
            return true;
        }
        Set<String> permissions = globalPermissions(email);
        return permissions.contains("can.view.stores")
                || permissions.contains("can.edit.stores")
                || permissions.contains("can.delete.stores");
    }

    public List<String> userRoles(UUID userId, UUID storeId) {
        if (storeId == null) {
            return jdbc.query("""
                    select r.name
                    from model_has_roles mr
                    join roles r on r.id = mr.role_id
                    where mr.user_id = ?::uuid
                    order by r.name
                    """, (rs, rowNum) -> rs.getString("name"), userId.toString());
        }
        return jdbc.query("""
                select distinct role_name from (
                    select r.name as role_name
                    from model_has_roles mr
                    join roles r on r.id = mr.role_id
                    where mr.user_id = ?::uuid
                    union
                    select r.name as role_name
                    from user_store_roles usr
                    join roles r on r.id = usr.role_id
                    where usr.user_id = ?::uuid
                      and usr.store_id = ?::uuid
                ) roles
                order by role_name
                """, (rs, rowNum) -> rs.getString("role_name"), userId.toString(), userId.toString(), storeId.toString());
    }
}
