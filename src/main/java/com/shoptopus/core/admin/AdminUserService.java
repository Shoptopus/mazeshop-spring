package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StoreRepository storeRepository;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder, StoreRepository storeRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storeRepository = storeRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("users", data);
    }

    public Map<String, Object> show(String id) {
        UUID userId = uuid(id).orElse(null);
        if (userId == null) {
            return Map.of("id", id);
        }
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", userId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        String firstName = required(body, "first_name");
        String lastName = required(body, "last_name");
        String email = required(body, "email").toLowerCase();
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The email has already been taken.");
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName((firstName + " " + lastName).trim());
        user.setEmail(email);
        user.setPrefix(str(body, "prefix", null));
        user.setPhone(str(body, "phone", null));
        user.setSlug(str(body, "slug", "user-" + user.getId()));
        user.setTemporary(bool(body, "temporary", false));
        user.setFavorite(bool(body, "favorite", false));
        user.setPassword(passwordEncoder.encode(str(body, "password", UUID.randomUUID().toString())));
        userRepository.save(user);
        return Map.of("id", user.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID userId = uuid(id).orElse(null);
        if (userId == null) {
            return Map.of("id", id);
        }
        Optional<User> user = userRepository.findById(userId).filter(item -> item.getDeletedAt() == null);
        if (user.isEmpty()) {
            return Map.of("id", userId);
        }
        User entity = user.orElseThrow();
        if (body != null && body.containsKey("first_name")) {
            entity.setFirstName(required(body, "first_name"));
        }
        if (body != null && body.containsKey("last_name")) {
            entity.setLastName(required(body, "last_name"));
        }
        if (body != null && body.containsKey("prefix")) {
            entity.setPrefix(str(body, "prefix", entity.getPrefix()));
        }
        if (body != null && body.containsKey("phone")) {
            entity.setPhone(str(body, "phone", entity.getPhone()));
        }
        if (body != null && body.containsKey("favorite")) {
            entity.setFavorite(bool(body, "favorite", entity.isFavorite()));
        }
        if (body != null && body.containsKey("temporary")) {
            entity.setTemporary(bool(body, "temporary", entity.isTemporary()));
        }
        if (body != null && body.containsKey("store_id")) {
            UUID storeId = uuid(str(body, "store_id", null)).orElse(null);
            entity.setStore(storeId == null ? null : storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null));
        }
        if (body != null && body.containsKey("avatar") && body.get("avatar") instanceof Map<?, ?> avatar) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            avatar.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            entity.setAvatar(normalized);
        }
        entity.setName((entity.getFirstName() + " " + entity.getLastName()).trim());
        userRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID userId = uuid(id).orElse(null);
        if (userId == null) {
            return Map.of("id", id);
        }
        userRepository.findById(userId).filter(item -> item.getDeletedAt() == null).ifPresent(user -> {
            user.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            userRepository.save(user);
        });
        return Map.of("id", userId);
    }

    private Map<String, Object> toDetail(User user) {
        Map<String, Object> row = toRow(user);
        row.put("prefix", user.getPrefix());
        row.put("phone", user.getPhone());
        row.put("favorite", user.isFavorite());
        row.put("temporary", user.isTemporary());
        row.put("slug", user.getSlug());
        row.put("email_verified_at", user.getEmailVerifiedAt());
        return row;
    }

    private Map<String, Object> toRow(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("email", user.getEmail());
        row.put("name", user.getName());
        row.put("avatar", user.getAvatar());
        row.put("first_name", user.getFirstName());
        row.put("last_name", user.getLastName());
        row.put("prefix", user.getPrefix());
        return row;
    }

    private Map<String, Object> listResponse(String message, List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    private String required(Map<String, Object> body, String key) {
        String value = str(body, key, null);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return value;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private boolean bool(Map<String, Object> body, String key, boolean fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        return raw instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(raw));
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
