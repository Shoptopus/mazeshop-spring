package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAccessService {

    private final JdbcTemplate jdbc;

    public AdminAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireSuperAdmin(Authentication authentication) {
        requireAuthenticated(authentication);
        if (!hasRole(authentication.getName(), "super_admin")) {
            unauthorized();
        }
    }

    public void requirePermission(Authentication authentication, String permission) {
        requireAnyPermission(authentication, permission);
    }

    public void requireAnyPermission(Authentication authentication, String... permissions) {
        requireAuthenticated(authentication);
        if (hasRole(authentication.getName(), "super_admin")) {
            return;
        }
        Set<String> requested = Arrays.stream(permissions)
                .filter(permission -> permission != null && !permission.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            unauthorized();
        }
        String placeholders = requested.stream().map(permission -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[requested.size() + 1];
        args[0] = authentication.getName();
        int index = 1;
        for (String permission : requested) {
            args[index++] = permission;
        }
        Integer count = jdbc.queryForObject("""
                select count(*)
                from users u
                join model_has_roles mr on mr.user_id = u.id
                join role_has_permissions rp on rp.role_id = mr.role_id
                join permissions p on p.id = rp.permission_id
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                  and p.name in (""" + placeholders + ")",
                Integer.class,
                args
        );
        if (count == null || count == 0) {
            unauthorized();
        }
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required");
        }
    }

    private boolean hasRole(String email, String roleName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from users u
                join model_has_roles mr on mr.user_id = u.id
                join roles r on r.id = mr.role_id
                where lower(u.email) = lower(?)
                  and u.deleted_at is null
                  and r.name = ?
                """, Integer.class, email, roleName);
        return count != null && count > 0;
    }

    private void unauthorized() {
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Sorry there was a system error, the administrator has been informed.",
                "This action is unauthorized.",
                1403
        );
    }
}
