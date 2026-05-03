package com.shoptopus.core.admin;

import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminCustomerService {

    private final UserRepository userRepository;

    public AdminCustomerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        return listResponse("customers", data);
    }

    public Map<String, Object> show(String id) {
        UUID customerId = uuid(id).orElse(null);
        if (customerId == null) {
            return Map.of("id", id);
        }
        return userRepository.findById(customerId)
                .filter(user -> user.getDeletedAt() == null)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", customerId));
    }

    private Map<String, Object> toDetail(User user) {
        Map<String, Object> row = toRow(user);
        row.put("slug", user.getSlug());
        row.put("favorite", user.isFavorite());
        row.put("temporary", user.isTemporary());
        row.put("phone", user.getPhone());
        return row;
    }

    private Map<String, Object> toRow(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("email", user.getEmail());
        row.put("first_name", user.getFirstName());
        row.put("last_name", user.getLastName());
        row.put("name", user.getName());
        row.put("avatar", user.getAvatar());
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
