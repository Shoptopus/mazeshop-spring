package com.shoptopus.core.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean
    Flyway mainFlyway(@Qualifier("mainDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/main")
                .table("flyway_schema_history_main")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    Flyway logsFlyway(@Qualifier("logsDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/logs")
                .table("flyway_schema_history_logs")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
        };
    }
}
