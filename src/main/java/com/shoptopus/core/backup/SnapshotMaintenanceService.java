package com.shoptopus.core.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class SnapshotMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotMaintenanceService.class);

    private final SnapshotService snapshotService;
    private final JdbcTemplate logsJdbc;
    private final TransactionTemplate logsTransactionTemplate;
    private final String logsDatabaseUrl;
    private final Duration snapshotRetention;

    public SnapshotMaintenanceService(
            SnapshotService snapshotService,
            @Qualifier("logsDataSource") DataSource logsDataSource,
            @Qualifier("logsTransactionManager") org.springframework.transaction.PlatformTransactionManager logsTransactionManager,
            @Qualifier("logsDataSourceProperties") DataSourceProperties logsDataSourceProperties,
            @org.springframework.beans.factory.annotation.Value("${app.snapshot.retention:PT168H}") Duration snapshotRetention
    ) {
        this.snapshotService = snapshotService;
        this.logsJdbc = new JdbcTemplate(logsDataSource);
        this.logsTransactionTemplate = new TransactionTemplate(logsTransactionManager);
        this.logsDatabaseUrl = logsDataSourceProperties.getUrl();
        this.snapshotRetention = snapshotRetention;
    }

    @Scheduled(
            fixedDelayString = "${app.snapshot.schedule.fixed-delay:PT24H}",
            initialDelayString = "${app.snapshot.schedule.initial-delay:PT15M}"
    )
    public void snapshotAndPurgeLogs() {
        try {
            Path archive = snapshotService.createSnapshot();
            clearLogsDatabase();
            int deletedSnapshots = snapshotService.deleteSnapshotsOlderThan(snapshotRetention);
            log.info(
                    "Completed scheduled snapshot {}, cleared logs database {}, deleted {} expired snapshots",
                    archive,
                    logsDatabaseUrl,
                    deletedSnapshots
            );
        } catch (Exception ex) {
            log.error("Scheduled snapshot maintenance failed", ex);
        }
    }

    private void clearLogsDatabase() {
        logsTransactionTemplate.executeWithoutResult(status -> {
            logsJdbc.execute("TRUNCATE TABLE audits, event_logs");
        });
    }
}
