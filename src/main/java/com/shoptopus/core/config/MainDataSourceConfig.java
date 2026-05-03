package com.shoptopus.core.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = {
                "com.shoptopus.core.auth",
                "com.shoptopus.core.catalog",
                "com.shoptopus.core.cart",
                "com.shoptopus.core.ordering",
                "com.shoptopus.core.payment",
                "com.shoptopus.core.user",
                "com.shoptopus.core.engagement",
                "com.shoptopus.core.voucher",
                "com.shoptopus.core.discount",
                "com.shoptopus.core.delivery",
                "com.shoptopus.core.invoice",
                "com.shoptopus.core.email",
                "com.shoptopus.core.note",
                "com.shoptopus.core.settings",
                "com.shoptopus.core.content",
                "com.shoptopus.core.files",
                "com.shoptopus.core.audit",
                "com.shoptopus.core.page",
                "com.shoptopus.core.messaging",
                "com.shoptopus.core.store"
        },
        entityManagerFactoryRef = "mainEntityManagerFactory",
        transactionManagerRef = "mainTransactionManager"
)
public class MainDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.main")
    DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.main.hikari")
    DataSource mainDataSource(@Qualifier("mainDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean mainEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mainDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(
                        "com.shoptopus.core.auth",
                        "com.shoptopus.core.catalog",
                        "com.shoptopus.core.cart",
                        "com.shoptopus.core.ordering",
                        "com.shoptopus.core.payment",
                        "com.shoptopus.core.user",
                        "com.shoptopus.core.engagement",
                        "com.shoptopus.core.voucher",
                        "com.shoptopus.core.discount",
                        "com.shoptopus.core.delivery",
                        "com.shoptopus.core.invoice",
                        "com.shoptopus.core.email",
                        "com.shoptopus.core.note",
                        "com.shoptopus.core.settings",
                        "com.shoptopus.core.content",
                        "com.shoptopus.core.files",
                        "com.shoptopus.core.audit",
                        "com.shoptopus.core.page",
                        "com.shoptopus.core.messaging",
                        "com.shoptopus.core.store"
                )
                .persistenceUnit("main")
                .build();
    }

    @Bean
    @Primary
    PlatformTransactionManager mainTransactionManager(
            @Qualifier("mainEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
    ) {
        return new JpaTransactionManager(emf.getObject());
    }
}
