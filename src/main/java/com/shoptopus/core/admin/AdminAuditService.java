package com.shoptopus.core.admin;

import com.shoptopus.core.logging.AuditTrail;
import com.shoptopus.core.logging.repository.AuditTrailRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminAuditService {

    private final AuditTrailRepository auditTrailRepository;
    private final UserRepository userRepository;

    public AdminAuditService(AuditTrailRepository auditTrailRepository, UserRepository userRepository) {
        this.auditTrailRepository = auditTrailRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true, transactionManager = "logsTransactionManager")
    public Map<String, Object> list() {
        List<Map<String, Object>> data = auditTrailRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "audits");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    public Map<String, Object> show(String id) {
        UUID auditId = uuid(id).orElse(null);
        if (auditId == null) {
            return Map.of("id", id);
        }
        return auditTrailRepository.findById(auditId)
                .map(this::toRow)
                .orElseGet(() -> Map.of("id", auditId));
    }

    private Map<String, Object> toRow(AuditTrail audit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", audit.getId());
        row.put("operation", audit.getOperation());
        row.put("entity_type", audit.getEntityType());
        row.put("entity_id", audit.getEntityId());
        row.put("before_state", audit.getBeforeState());
        row.put("after_state", audit.getAfterState());
        row.put("created_at", audit.getCreatedAt());
        User user = audit.getUserId() == null ? null : userRepository.findById(audit.getUserId()).orElse(null);
        row.put("user_id", audit.getUserId());
        if (user == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", user.getId());
            userRow.put("email", user.getEmail());
            userRow.put("first_name", user.getFirstName());
            userRow.put("last_name", user.getLastName());
            row.put("user", userRow);
        }
        return row;
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
