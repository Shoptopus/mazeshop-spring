package com.shoptopus.core.adminui;

import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.shoptopus.core.common.error.ApiException;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class AdminStoreContext {

    public static final String STORE_HEADER = "X-Admin-Store-Id";

    private final boolean multiStoreEnabled;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final JdbcTemplate jdbc;
    private final AdminRoleScopeService adminRoleScopeService;

    public AdminStoreContext(
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            UserRepository userRepository,
            StoreRepository storeRepository,
            JdbcTemplate jdbc,
            AdminRoleScopeService adminRoleScopeService
    ) {
        this.multiStoreEnabled = multiStoreEnabled;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.jdbc = jdbc;
        this.adminRoleScopeService = adminRoleScopeService;
    }

    public boolean multiStoreEnabled() {
        return multiStoreEnabled;
    }

    public Store activeStore(String email) {
        return activeStoreOptional(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available."));
    }

    public Optional<Store> activeStoreOptional(String email) {
        if (!multiStoreEnabled) {
            return Optional.empty();
        }

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));

        if (!canManageStores(email)) {
            UUID assignedStoreId = adminRoleScopeService.assignedStoreIds(email).stream().findFirst().orElse(null);
            if (assignedStoreId != null) {
                Optional<Store> assignedStore = storeRepository.findByIdAndDeletedAtIsNull(assignedStoreId);
                if (assignedStore.isPresent()) {
                    return assignedStore;
                }
            }
            if (user.getStore() != null) {
                return Optional.of(user.getStore());
            }
            return defaultStore();
        }

        UUID requestedStoreId = requestedStoreId().orElse(null);
        if (requestedStoreId != null) {
            Optional<Store> requestedStore = storeRepository.findByIdAndDeletedAtIsNull(requestedStoreId);
            if (requestedStore.isPresent()) {
                return requestedStore;
            }
        }

        if (user.getStore() != null) {
            return Optional.of(user.getStore());
        }
        UUID assignedStoreId = adminRoleScopeService.assignedStoreIds(email).stream().findFirst().orElse(null);
        if (assignedStoreId != null) {
            Optional<Store> assignedStore = storeRepository.findByIdAndDeletedAtIsNull(assignedStoreId);
            if (assignedStore.isPresent()) {
                return assignedStore;
            }
        }
        return defaultStore();
    }

    public Optional<Store> activeStoreForCurrentRequest() {
        String email = currentEmail().orElse(null);
        if (email == null || email.isBlank()) {
            return defaultStore();
        }
        return activeStoreOptional(email);
    }

    public boolean canManageStores(String email) {
        if (!multiStoreEnabled) {
            return false;
        }
        return adminRoleScopeService.hasGlobalStoreManagementAccess(email);
    }

    public boolean matchesActiveStore(String email, Store store) {
        if (!multiStoreEnabled || store == null) {
            return true;
        }
        return activeStoreOptional(email)
                .map(Store::getId)
                .map(id -> id.equals(store.getId()))
                .orElse(false);
    }

    public UUID currentStoreIdOrNull() {
        return activeStoreForCurrentRequest().map(Store::getId).orElse(null);
    }

    private Optional<String> currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String email = authentication.getName();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private Optional<UUID> requestedStoreId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Optional.empty();
        }
        String raw = request.getHeader(STORE_HEADER);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    public Optional<Store> defaultStore() {
        return storeRepository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc();
    }
}
