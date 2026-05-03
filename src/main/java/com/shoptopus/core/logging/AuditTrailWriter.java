package com.shoptopus.core.logging;

import com.shoptopus.core.logging.repository.AuditTrailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditTrailWriter {

    private final AuditTrailRepository auditTrailRepository;

    public AuditTrailWriter(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    @Transactional("logsTransactionManager")
    public void write(List<AuditTrail> trails) {
        auditTrailRepository.saveAll(trails);
    }
}
