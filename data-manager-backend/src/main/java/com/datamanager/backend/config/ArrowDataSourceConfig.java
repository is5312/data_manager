package com.datamanager.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuration for a separate DataSource optimized for Arrow binary streaming.
 * Uses binary transfer mode for better performance with large result sets.
 */
@Configuration
public class ArrowDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    /**
     * Create a dedicated DataSource for Arrow streaming with binary protocol enabled.
     * This is separate from the main datasource to avoid interfering with JPA/Hibernate.
     */
    @Bean(name = "arrowDataSource")
    public DataSource arrowDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Use binary transfer protocol for better performance
        String binaryUrl = jdbcUrl.contains("?") 
            ? jdbcUrl + "&binaryTransfer=true&prepareThreshold=0" 
            : jdbcUrl + "?binaryTransfer=true&prepareThreshold=0";
        
        config.setJdbcUrl(binaryUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Optimized for streaming large result sets
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // Allow large result sets without timeout
        config.addDataSourceProperty("socketTimeout", "0");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        
        // Disable auto-commit for fetch size to work properly
        config.setAutoCommit(false);
        
        config.setPoolName("ArrowStreamingPool");
        
        return new HikariDataSource(config);
    }
}

