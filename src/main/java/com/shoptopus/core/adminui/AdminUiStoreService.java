package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.mail.ShopMailService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.store.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUiStoreService {

    private final StoreRepository storeRepository;
    private final boolean multiStoreEnabled;
    private final AdminRoleScopeService adminRoleScopeService;
    private final ShopMailService shopMailService;
    private final JdbcTemplate jdbcTemplate;

    public AdminUiStoreService(
            StoreRepository storeRepository,
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            AdminRoleScopeService adminRoleScopeService,
            ShopMailService shopMailService,
            JdbcTemplate jdbcTemplate
    ) {
        this.storeRepository = storeRepository;
        this.multiStoreEnabled = multiStoreEnabled;
        this.adminRoleScopeService = adminRoleScopeService;
        this.shopMailService = shopMailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Map<String, Object> list(Integer page, Integer perPage, String search) {
        requireMultiStoreEnabled();
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPerPage = perPage == null ? 20 : Math.min(Math.max(perPage, 1), 100);
        PageRequest pageable = PageRequest.of(resolvedPage - 1, resolvedPerPage);
        Page<Store> result = search == null || search.isBlank()
                ? storeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                : storeRepository.findByDeletedAtIsNullAndNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), pageable);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", result.getContent().stream().map(this::listRow).toList());
        payload.put("meta", Map.of(
                "page", resolvedPage,
                "per_page", resolvedPerPage,
                "total", result.getTotalElements(),
                "total_pages", result.getTotalPages()
        ));
        payload.put("filters", Map.of(
                "search", search == null ? "" : search,
                "statuses", List.of(
                        statusOption(StoreStatus.PENDING),
                        statusOption(StoreStatus.ACTIVE),
                        statusOption(StoreStatus.DEACTIVATED),
                        statusOption(StoreStatus.SUSPENDED)
                )
        ));
        return payload;
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Map<String, Object> detail(UUID storeId) {
        requireMultiStoreEnabled();
        return detailRow(store(storeId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateStatus(UUID storeId, Map<String, Object> body) {
        requireMultiStoreEnabled();
        Store store = store(storeId);
        StoreStatus previousStatus = store.getStatus();
        String statusReason = str(body.get("status_reason"));
        String reviewNotes = str(body.get("review_notes"));
        StoreStatus nextStatus;
        try {
            nextStatus = StoreStatus.fromValue(String.valueOf(body.get("status")));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid store status.");
        }
        jdbcTemplate.update("""
                        update stores
                        set status = ?,
                            status_reason = ?,
                            review_notes = ?,
                            updated_at = now()
                        where id = ?::uuid
                          and deleted_at is null
                        """,
                nextStatus.name(),
                statusReason,
                reviewNotes,
                storeId.toString()
        );
        Store refreshed = store(storeId);
        if (previousStatus != refreshed.getStatus()) {
            shopMailService.sendStoreStatusUpdated(refreshed);
        }
        return detailRow(refreshed);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateDetails(UUID storeId, Map<String, Object> body) {
        requireMultiStoreEnabled();
        Store store = store(storeId);
        store.setTagline(str(body.get("tagline")));
        store.setDescription(str(body.get("description")));
        store.setBusinessDetails(str(body.get("business_details")));
        store.setOwnerEmail(str(body.get("owner_email")));
        store.setOwnerPhone(str(body.get("owner_phone")));
        store.setContactPhone(str(body.get("contact_phone")));
        store.setContactAddress(str(body.get("contact_address")));
        store.setReviewNotes(str(body.get("review_notes")));
        store.setStatusReason(str(body.get("status_reason")));
        store.setLogo(map(body.get("logo")));
        store.setTheme(map(body.get("theme")));
        store.setSocialPlatforms(listOfMaps(body.get("social_platforms")));
        storeRepository.save(store);
        return detailRow(store);
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public List<Map<String, Object>> manageableStores() {
        if (!multiStoreEnabled) {
            return List.of();
        }
        return storeRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(store -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", store.getId());
                    row.put("name", store.getName());
                    row.put("slug", store.getSlug());
                    row.put("status", store.getStatus().name().toLowerCase());
                    return row;
                })
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public List<Map<String, Object>> manageableStoresFor(String email) {
        if (!multiStoreEnabled) {
            return List.of();
        }
        if (adminRoleScopeService.hasGlobalStoreManagementAccess(email)) {
            return manageableStores();
        }
        var allowed = adminRoleScopeService.assignedStoreIds(email);
        return storeRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(store -> allowed.contains(store.getId()))
                .map(store -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", store.getId());
                    row.put("name", store.getName());
                    row.put("slug", store.getSlug());
                    row.put("status", store.getStatus().name().toLowerCase());
                    return row;
                })
                .toList();
    }

    public boolean multiStoreEnabled() {
        return multiStoreEnabled;
    }

    private void requireMultiStoreEnabled() {
        if (!multiStoreEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "Multi-store mode is disabled.");
        }
    }

    private Store store(UUID storeId) {
        return storeRepository.findByIdAndDeletedAtIsNull(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Store not found."));
    }

    private Map<String, Object> listRow(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("slug", store.getSlug());
        row.put("tagline", store.getTagline());
        row.put("contact_phone", store.getContactPhone());
        row.put("contact_address", store.getContactAddress());
        row.put("social_platforms", store.getSocialPlatforms() == null ? List.of() : store.getSocialPlatforms());
        row.put("theme", store.getTheme() == null ? Map.of() : store.getTheme());
        row.put("status", store.getStatus().name().toLowerCase());
        row.put("status_label", store.getStatus().label());
        row.put("owner_name", store.getOwnerUser() == null ? null : store.getOwnerUser().getName());
        row.put("owner_email", store.getOwnerEmail());
        row.put("rating_average", store.getRatingAverage());
        row.put("rating_count", store.getRatingCount());
        row.put("created_at", store.getCreatedAt());
        return row;
    }

    private Map<String, Object> detailRow(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("slug", store.getSlug());
        row.put("description", store.getDescription());
        row.put("business_details", store.getBusinessDetails());
        row.put("logo", store.getLogo());
        row.put("tagline", store.getTagline());
        row.put("status", store.getStatus().name().toLowerCase());
        row.put("status_label", store.getStatus().label());
        row.put("review_notes", store.getReviewNotes());
        row.put("status_reason", store.getStatusReason());
        row.put("rating_average", store.getRatingAverage());
        row.put("rating_count", store.getRatingCount());
        row.put("owner", ownerRow(store));
        row.put("owner_email", store.getOwnerEmail());
        row.put("owner_phone", store.getOwnerPhone());
        row.put("contact_phone", store.getContactPhone());
        row.put("contact_address", store.getContactAddress());
        row.put("social_platforms", store.getSocialPlatforms() == null ? List.of() : store.getSocialPlatforms());
        row.put("theme", store.getTheme() == null ? Map.of() : store.getTheme());
        row.put("available_statuses", List.of(
                statusOption(StoreStatus.PENDING),
                statusOption(StoreStatus.ACTIVE),
                statusOption(StoreStatus.DEACTIVATED),
                statusOption(StoreStatus.SUSPENDED)
        ));
        row.put("created_at", store.getCreatedAt());
        row.put("updated_at", store.getUpdatedAt());
        return row;
    }

    private Map<String, Object> statusOption(StoreStatus status) {
        return Map.of(
                "value", status.name().toLowerCase(),
                "label", status.label()
        );
    }

    private Map<String, Object> ownerRow(Store store) {
        if (store.getOwnerUser() == null) {
            return null;
        }
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("id", store.getOwnerUser().getId());
        owner.put("name", store.getOwnerUser().getName());
        owner.put("email", store.getOwnerUser().getEmail());
        owner.put("phone", store.getOwnerUser().getPhone());
        return owner;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            return new LinkedHashMap<>();
        }
        return values.entrySet().stream().collect(Collectors.toMap(
                entry -> String.valueOf(entry.getKey()),
                Map.Entry::getValue,
                (left, right) -> right,
                LinkedHashMap::new
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(value -> map(value))
                .toList();
    }

    private String str(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }
}
