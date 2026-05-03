package com.shoptopus.core.store;

public enum StoreStatus {
    PENDING,
    ACTIVE,
    DEACTIVATED,
    SUSPENDED;

    public String label() {
        return switch (this) {
            case PENDING -> "Pending";
            case ACTIVE -> "Active";
            case DEACTIVATED -> "Deactivated";
            case SUSPENDED -> "Suspended";
        };
    }

    public static StoreStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return StoreStatus.valueOf(value.trim().toUpperCase());
    }
}
