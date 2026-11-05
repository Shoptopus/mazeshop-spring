package com.shoptopus.core.logging;

import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.common.jpa.UuidEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditTrailCaptureService {

    private final AuditTrailBuffer auditTrailBuffer;
    private final AdminStoreContext adminStoreContext;

    public AuditTrailCaptureService(AuditTrailBuffer auditTrailBuffer, AdminStoreContext adminStoreContext) {
        this.auditTrailBuffer = auditTrailBuffer;
        this.adminStoreContext = adminStoreContext;
    }

    public void record(String operation, Object entity, Map<String, Object> beforeState, Map<String, Object> afterState) {
        if (entity == null) {
            return;
        }
        AuditTrail trail = new AuditTrail();
        trail.setStoreId(resolveStoreId(entity));
        trail.setUserId(currentUserId().orElse(null));
        trail.setOperation(operation);
        trail.setEntityType(entityType(entity.getClass()));
        trail.setEntityId(entityId(entity));
        trail.setBeforeState(beforeState == null ? null : new LinkedHashMap<>(beforeState));
        trail.setAfterState(afterState == null ? null : new LinkedHashMap<>(afterState));
        trail.setCreatedAt(OffsetDateTime.now());
        trail.setUpdatedAt(trail.getCreatedAt());
        auditTrailBuffer.enqueue(trail);
    }

    public Map<String, Object> snapshot(Object entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                if ("auditSnapshot".equals(field.getName())) {
                    continue;
                }
                if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
                    snapshot.putIfAbsent(field.getName(), "[collection relation omitted]");
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    if (value != null && (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class) || isEntityReference(value.getClass()))) {
                        snapshot.putIfAbsent(field.getName(), entityId(value));
                    } else {
                        snapshot.putIfAbsent(field.getName(), normalize(value));
                    }
                } catch (RuntimeException | IllegalAccessException ignored) {
                    snapshot.putIfAbsent(field.getName(), "[snapshot_unavailable]");
                }
            }
            current = current.getSuperclass();
        }
        return snapshot;
    }

    private Object normalize(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof UUID) {
                return value;
            }
            if (value instanceof TemporalAccessor temporal) {
                return temporal.toString();
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
                    Object item = entry.getValue();
                    normalized.put(String.valueOf(entry.getKey()), item == null || isScalar(item) ? item : safeReference(item));
                }
                return normalized;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> normalized = new ArrayList<>();
                for (Object item : new ArrayList<>(collection)) {
                    normalized.add(item == null || isScalar(item) ? item : safeReference(item));
                }
                return normalized;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> normalized = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    Object item = Array.get(value, index);
                    normalized.add(item == null || isScalar(item) ? item : safeReference(item));
                }
                return normalized;
            }
            if (isEntityReference(value.getClass())) {
                return entityId(value);
            }
            return String.valueOf(value);
        } catch (ConcurrentModificationException ex) {
            return "[concurrent_modification]";
        } catch (RuntimeException ex) {
            return "[snapshot_unavailable]";
        }
    }

    private boolean isScalar(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof TemporalAccessor;
    }

    private Object safeReference(Object value) {
        if (value == null) {
            return null;
        }
        if (isEntityReference(value.getClass())) {
            return entityId(value);
        }
        return String.valueOf(value);
    }

    private boolean isEntityReference(Class<?> type) {
        return type.isAnnotationPresent(Entity.class) || UuidEntity.class.isAssignableFrom(type);
    }

    private String entityType(Class<?> type) {
        String simpleName = type.getSimpleName();
        return simpleName.isBlank() ? type.getName() : simpleName;
    }

    private String entityId(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!"id".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object id = field.get(entity);
                    return id == null ? null : String.valueOf(id);
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object rawUserId = map.get("user_id");
            if (rawUserId != null) {
                try {
                    return Optional.of(UUID.fromString(String.valueOf(rawUserId)));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        String principalName = authentication.getName();
        if (principalName == null || principalName.isBlank() || Objects.equals(principalName, "anonymousUser")) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(principalName));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private UUID resolveStoreId(Object entity) {
        UUID currentStoreId = adminStoreContext.currentStoreIdForAuditOrNull();
        if (currentStoreId != null) {
            return currentStoreId;
        }
        Object storeRef = relationValue(entity, "store");
        if (storeRef != null) {
            String entityId = entityId(storeRef);
            if (entityId != null) {
                try {
                    return UUID.fromString(entityId);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Object relationValue(Object entity, String fieldName) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!fieldName.equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    return field.get(entity);
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
