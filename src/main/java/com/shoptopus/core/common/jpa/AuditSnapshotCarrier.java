package com.shoptopus.core.common.jpa;

import java.util.Map;

public interface AuditSnapshotCarrier {

    Map<String, Object> getAuditSnapshot();

    void setAuditSnapshot(Map<String, Object> snapshot);
}
