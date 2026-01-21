package com.datamanager.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Configuration for multiple datasources.
 * The primary datasource is used by JPA/Hibernate, Flyway, and Spring Batch.
 * The arrow datasource is dedicated for Arrow streaming with binary transfer
 * optimization.
 * 
 * Not loaded in test profile - tests use embedded database auto-configuration.
 */
@Configuration
@Profile("!test")
public class DataSourceConfig {

    @Value("${spring.primary-datasource.url}")
    private String primaryUrl;

    @Value("${spring.primary-datasource.username}")
    private String primaryUsername;

    @Value("${spring.primary-datasource.password}")
    private String primaryPassword;

    /**
     * Primary datasource for JPA/Hibernate, Flyway, and Spring Batch.
     * This MUST be marked as @Primary to ensure Spring Boot auto-configurations use
     * it.
     * Configured manually to ensure proper jdbcUrl mapping for HikariCP.
     */
    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(primaryUrl);
        config.setUsername(primaryUsername);
        config.setPassword(primaryPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Standard pool settings
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }

    /**
     * Dedicated datasource for Arrow streaming.
     * Optimized with binary transfer protocol to eliminate UTF-8 encoding overhead.
     * Configured manually to avoid @ConfigurationProperties binding issues.
     */
    @Bean(name = "arrowDataSource")
    public DataSource arrowDataSource() {
        HikariConfig config = new HikariConfig();

        // Use primary datasource connection but with binary transfer enabled
        String arrowUrl = primaryUrl.contains("?")
                ? primaryUrl + "&binaryTransfer=true&binaryTransferEnable=bytea,text,varchar"
                : primaryUrl + "?binaryTransfer=true&binaryTransferEnable=bytea,text,varchar";

        config.setJdbcUrl(arrowUrl);
        config.setUsername(primaryUsername);
        config.setPassword(primaryPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Optimized pool settings for streaming
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("ArrowStreamingPool");

        return new HikariDataSource(config);
    }
}
