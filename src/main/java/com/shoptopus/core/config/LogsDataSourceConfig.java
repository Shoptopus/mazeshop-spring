package com.shoptopus.core.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.shoptopus.core.logging",
        entityManagerFactoryRef = "logsEntityManagerFactory",
        transactionManagerRef = "logsTransactionManager"
)
public class LogsDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.logs")
    DataSourceProperties logsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.logs.hikari")
    DataSource logsDataSource(@Qualifier("logsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean logsEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("logsDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages("com.shoptopus.core.logging")
                .persistenceUnit("logs")
                .build();
    }

    @Bean
    PlatformTransactionManager logsTransactionManager(
            @Qualifier("logsEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
    ) {
        return new JpaTransactionManager(emf.getObject());
    }
}
