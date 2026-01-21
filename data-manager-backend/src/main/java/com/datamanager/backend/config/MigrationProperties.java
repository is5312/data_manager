package com.datamanager.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for migration feature
 */
@Component
@ConfigurationProperties(prefix = "migration")
@Data
public class MigrationProperties {

    private String defaultSchema = "public";
    private List<String> availableSchemas = new ArrayList<>();

    public MigrationProperties() {
        // Default values
        availableSchemas.add("public");
        availableSchemas.add("dmgr");
    }
}
