package com.shoptopus.core.logging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuditTrailBuffer {
    private static final ThreadLocal<List<AuditTrail>> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REGISTERED = ThreadLocal.withInitial(() -> false);

    private final AuditTrailWriter auditTrailWriter;

    public AuditTrailBuffer(AuditTrailWriter auditTrailWriter) {
        this.auditTrailWriter = auditTrailWriter;
    }

    public void enqueue(AuditTrail trail) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeSafely(trail);
            return;
        }
        pending().add(trail);
        registerSynchronizationIfNeeded();
    }

    private void writeSafely(AuditTrail trail) {
        try {
            auditTrailWriter.write(List.of(trail));
        } catch (Throwable ignored) {
        }
    }

    private void writeSafely(List<AuditTrail> trails) {
        if (trails == null || trails.isEmpty()) {
            return;
        }
        try {
            auditTrailWriter.write(List.copyOf(trails));
        } catch (Throwable ignored) {
        }
    }

    private List<AuditTrail> pending() {
        List<AuditTrail> trails = PENDING.get();
        if (trails == null) {
            trails = new ArrayList<>();
            PENDING.set(trails);
        }
        return trails;
    }

    private void registerSynchronizationIfNeeded() {
        if (Boolean.TRUE.equals(REGISTERED.get())) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writeSafely(PENDING.get());
            }

            @Override
            public void afterCompletion(int status) {
                PENDING.remove();
                REGISTERED.remove();
            }
        });
        REGISTERED.set(true);
    }
}
