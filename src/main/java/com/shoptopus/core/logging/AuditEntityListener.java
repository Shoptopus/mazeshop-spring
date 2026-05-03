package com.shoptopus.core.logging;

import com.shoptopus.core.common.jpa.AuditSnapshotCarrier;
import com.shoptopus.core.common.spring.SpringContext;
import com.shoptopus.core.store.DefaultStoreResolver;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;

import java.lang.reflect.Field;
import java.util.Map;

public class AuditEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        try {
            assignDefaultStore(entity);
        } catch (Throwable ignored) {
        }
    }

    @PostLoad
    public void postLoad(Object entity) {
        try {
            if (!(entity instanceof AuditSnapshotCarrier carrier)) {
                return;
            }
            if (!supports(entity)) {
                return;
            }
            Map<String, Object> snapshot = captureService().snapshot(entity);
            carrier.setAuditSnapshot(snapshot);
        } catch (Throwable ignored) {
        }
    }

    @PostPersist
    public void postPersist(Object entity) {
        try {
            if (!supports(entity)) {
                return;
            }
            captureService().record("create", entity, null, captureService().snapshot(entity));
        } catch (Throwable ignored) {
        }
    }

    @PostUpdate
    public void postUpdate(Object entity) {
        try {
            if (!(entity instanceof AuditSnapshotCarrier carrier)) {
                return;
            }
            if (!supports(entity)) {
                return;
            }
            Map<String, Object> beforeState = carrier.getAuditSnapshot();
            Map<String, Object> afterState = captureService().snapshot(entity);
            captureService().record("update", entity, beforeState, afterState);
            carrier.setAuditSnapshot(afterState);
        } catch (Throwable ignored) {
        }
    }

    @PreRemove
    public void preRemove(Object entity) {
        try {
            if (!supports(entity)) {
                return;
            }
            Map<String, Object> beforeState = entity instanceof AuditSnapshotCarrier carrier && carrier.getAuditSnapshot() != null
                    ? carrier.getAuditSnapshot()
                    : captureService().snapshot(entity);
            captureService().record("delete", entity, beforeState, null);
        } catch (Throwable ignored) {
        }
    }

    private boolean supports(Object entity) {
        Package entityPackage = entity.getClass().getPackage();
        if (entityPackage == null) {
            return false;
        }
        String packageName = entityPackage.getName();
        return !packageName.startsWith("com.shoptopus.core.logging")
                && !packageName.startsWith("com.shoptopus.core.audit");
    }

    private void assignDefaultStore(Object entity) throws IllegalAccessException {
        if (entity == null || entity instanceof User || entity instanceof Store) {
            return;
        }
        Field storeField = storeField(entity.getClass());
        if (storeField == null) {
            return;
        }
        storeField.setAccessible(true);
        Object current = storeField.get(entity);
        if (current != null) {
            return;
        }
        Store defaultStore = SpringContext.getBean(DefaultStoreResolver.class).defaultStore();
        if (defaultStore != null) {
            storeField.set(entity, defaultStore);
        }
    }

    private Field storeField(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField("store");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private AuditTrailCaptureService captureService() {
        return SpringContext.getBean(AuditTrailCaptureService.class);
    }
}
