package com.shoptopus.core.backup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SnapshotMaintenanceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(application -> application.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(SnapshotMaintenanceService.class)
            .withBean(SnapshotService.class, () -> mock(SnapshotService.class))
            .withBean("logsDataSource", DataSource.class, () -> mock(DataSource.class))
            .withBean("logsTransactionManager", PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class))
            .withBean("logsDataSourceProperties", DataSourceProperties.class, DataSourceProperties::new);

    @Test
    void scheduled_snapshot_maintenance_remains_enabled_by_default() {
        context.run(application -> assertThat(application).hasSingleBean(SnapshotMaintenanceService.class));
    }

    @Test
    void external_backup_installations_can_disable_snapshot_and_log_purge() {
        context.withPropertyValues("app.snapshot.maintenance-enabled=false")
                .run(application -> assertThat(application).doesNotHaveBean(SnapshotMaintenanceService.class));
    }
}
