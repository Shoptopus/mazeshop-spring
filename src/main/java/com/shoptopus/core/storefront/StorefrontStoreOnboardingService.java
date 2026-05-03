package com.shoptopus.core.storefront;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.mail.ShopMailService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StorefrontStoreOnboardingService {

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "media", "actuator", "studio",
            "shop", "product", "page", "login", "register", "forgot-password", "reset-password",
            "auth", "cart", "checkout", "account", "order", "create-store"
    );

    private final boolean multiStoreEnabled;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ShopMailService shopMailService;
    private final ObjectMapper objectMapper;

    public StorefrontStoreOnboardingService(
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            StoreRepository storeRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            ShopMailService shopMailService,
            ObjectMapper objectMapper
    ) {
        this.multiStoreEnabled = multiStoreEnabled;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.shopMailService = shopMailService;
        this.objectMapper = objectMapper;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> createStore(String authenticatedEmail, Map<String, Object> body) {
        requireMultiStoreEnabled();
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required.");
        }
        User owner = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(authenticatedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required."));
        if (owner.getStore() != null || alreadyOwnsStore(owner.getId()) || alreadyAssignedToStore(owner.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "You already have a store.");
        }

        String name = required(body, "name", "Store name is required.");
        String slug = normalizeSlug(string(body.get("slug")));
        if (slug.isBlank()) {
            slug = normalizeSlug(name);
        }
        validateSlug(slug);
        if (storeRepository.findBySlugAndDeletedAtIsNull(slug).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Namespace is already in use.");
        }

        String description = required(body, "description", "Description is required.");
        String ownerEmail = required(body, "owner_email", "Owner email is required.");
        String ownerPhone = required(body, "owner_phone", "Owner phone is required.");
        String tagline = required(body, "tagline", "Tagline is required.");
        Map<String, Object> logo = map(body.get("logo"));
        if (logo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Logo is required.");
        }

        String businessDetails = string(body.get("business_details"));
        UUID storeId = UUID.randomUUID();
        jdbcTemplate.update("""
                        insert into stores(
                            id,
                            name,
                            slug,
                            description,
                            business_details,
                            logo,
                            tagline,
                            owner_user_id,
                            owner_email,
                            owner_phone,
                            status,
                            social_platforms,
                            theme,
                            rating_average,
                            rating_count,
                            created_at,
                            updated_at
                        )
                        values (
                            ?::uuid,
                            ?,
                            ?,
                            ?,
                            ?,
                            ?::jsonb,
                            ?,
                            ?::uuid,
                            ?,
                            ?,
                            ?,
                            '[]'::jsonb,
                            '{}'::jsonb,
                            0,
                            0,
                            now(),
                            now()
                        )
                        """,
                storeId.toString(),
                name,
                slug,
                description,
                businessDetails,
                json(logo),
                tagline,
                owner.getId().toString(),
                ownerEmail.toLowerCase(Locale.ROOT),
                ownerPhone,
                StoreStatus.PENDING.name()
        );

        if (owner.getStore() == null) {
            jdbcTemplate.update("""
                            update users
                            set store_id = ?::uuid,
                                updated_at = now()
                            where id = ?::uuid
                              and store_id is null
                            """,
                    storeId.toString(),
                    owner.getId().toString()
            );
        }

        Integer storeManagerRoleId = jdbcTemplate.query(
                "select id from roles where name = 'store_manager' limit 1",
                rs -> rs.next() ? rs.getInt(1) : null
        );
        if (storeManagerRoleId != null) {
            jdbcTemplate.update("""
                            insert into user_store_roles(role_id, user_id, store_id)
                            values (?, ?::uuid, ?::uuid)
                            on conflict do nothing
                            """,
                    storeManagerRoleId,
                    owner.getId().toString(),
                    storeId.toString()
            );
        }

        Store store = new Store();
        store.setId(storeId);
        store.setName(name);
        store.setSlug(slug);
        store.setDescription(description);
        store.setBusinessDetails(businessDetails);
        store.setLogo(logo);
        store.setTagline(tagline);
        store.setOwnerEmail(ownerEmail.toLowerCase(Locale.ROOT));
        store.setOwnerPhone(ownerPhone);
        store.setStatus(StoreStatus.PENDING);

        shopMailService.sendStoreApplicationReceived(store);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", store.getId());
        payload.put("name", store.getName());
        payload.put("slug", store.getSlug());
        payload.put("status", store.getStatus().name().toLowerCase(Locale.ROOT));
        payload.put("status_label", store.getStatus().label());
        payload.put("owner_email", store.getOwnerEmail());
        return payload;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "Failed to serialize store payload.");
        }
    }

    public boolean multiStoreEnabled() {
        return multiStoreEnabled;
    }

    private boolean alreadyOwnsStore(UUID userId) {
        Integer existing = jdbcTemplate.query("""
                select count(*)
                from stores
                where owner_user_id = ?::uuid
                  and deleted_at is null
                """, rs -> rs.next() ? rs.getInt(1) : 0, userId.toString());
        return existing != null && existing > 0;
    }

    private boolean alreadyAssignedToStore(UUID userId) {
        Integer existing = jdbcTemplate.query("""
                select count(*)
                from user_store_roles
                where user_id = ?::uuid
                """, rs -> rs.next() ? rs.getInt(1) : 0, userId.toString());
        return existing != null && existing > 0;
    }

    private void requireMultiStoreEnabled() {
        if (!multiStoreEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found", "Multi-store mode is disabled.");
        }
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Namespace is required.");
        }
        if (slug.length() < 2 || slug.length() > 160) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Namespace must be between 2 and 160 characters.");
        }
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Namespace may only contain lowercase letters, numbers, and hyphens.");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Namespace is reserved.");
        }
    }

    private String required(Map<String, Object> body, String key, String message) {
        String value = string(body.get(key));
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
        return value.trim();
    }

    private String string(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        values.forEach((key, value) -> mapped.put(String.valueOf(key), value));
        return mapped;
    }

    private String normalizeSlug(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        return normalized;
    }
}
