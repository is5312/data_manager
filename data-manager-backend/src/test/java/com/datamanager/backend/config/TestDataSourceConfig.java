package com.datamanager.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Test configuration for datasources.
 * In tests, the embedded database provides the primary datasource.
 * This configuration creates the arrowDataSource as an alias to the primary datasource.
 */
@TestConfiguration
@Profile("test")
public class TestDataSourceConfig {

    /**
     * Arrow datasource for tests - just references the primary embedded datasource.
     * No need for binary transfer optimization in tests.
     */
    @Bean(name = "arrowDataSource")
    public DataSource arrowDataSource(@Qualifier("dataSource") DataSource dataSource) {
        return dataSource;
    }
}
