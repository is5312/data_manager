package com.datamanager.performance.config;

import com.datamanager.client.DataOperationsClient;
import com.datamanager.performance.data.TestDataGenerator;
import com.datamanager.performance.setup.TestSetupService;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Spring configuration for performance tests
 */
@Configuration
@Profile("performance")
public class PerformanceTestConfig {

    @Bean
    public TestSetupService testSetupService(DataSource dataSource) {
        return new TestSetupService(dataSource);
    }

    @Bean
    public TestDataGenerator testDataGenerator() {
        return new TestDataGenerator();
    }

    @Bean
    @ConfigurationProperties(prefix = "performance")
    public PerformanceProperties performanceProperties() {
        PerformanceProperties props = new PerformanceProperties();
        // Initialize migration config with defaults
        props.setMigration(new PerformanceProperties.MigrationTestConfig());
        return props;
    }

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    /**
     * Performance test configuration properties
     */
    public static class PerformanceProperties {
        private String schema = "dmgr";
        private String tablePrefix = "perf_test";
        private SpikeConfig spike = new SpikeConfig();
        private DataConfig data = new DataConfig();
        private CleanupConfig cleanup = new CleanupConfig();
        private MigrationTestConfig migration = new MigrationTestConfig();

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        public SpikeConfig getSpike() {
            return spike;
        }

        public void setSpike(SpikeConfig spike) {
            this.spike = spike;
        }

        public DataConfig getData() {
            return data;
        }

        public void setData(DataConfig data) {
            this.data = data;
        }

        public CleanupConfig getCleanup() {
            return cleanup;
        }

        public void setCleanup(CleanupConfig cleanup) {
            this.cleanup = cleanup;
        }

        public MigrationTestConfig getMigration() {
            return migration;
        }

        public void setMigration(MigrationTestConfig migration) {
            this.migration = migration;
        }

        public static class SpikeConfig {
            private int normalRps = 10;
            private int spikeRps = 500;
            private int normalDurationSeconds = 5;
            private int spikeDurationSeconds = 20;

            public int getNormalRps() {
                return normalRps;
            }

            public void setNormalRps(int normalRps) {
                this.normalRps = normalRps;
            }

            public int getSpikeRps() {
                return spikeRps;
            }

            public void setSpikeRps(int spikeRps) {
                this.spikeRps = spikeRps;
            }

            public int getNormalDurationSeconds() {
                return normalDurationSeconds;
            }

            public void setNormalDurationSeconds(int normalDurationSeconds) {
                this.normalDurationSeconds = normalDurationSeconds;
            }

            public int getSpikeDurationSeconds() {
                return spikeDurationSeconds;
            }

            public void setSpikeDurationSeconds(int spikeDurationSeconds) {
                this.spikeDurationSeconds = spikeDurationSeconds;
            }
        }

        public static class DataConfig {
            private int namePoolSize = 1000;
            private String emailDomain = "perftest.example.com";
            private int minAge = 18;
            private int maxAge = 80;

            public int getNamePoolSize() {
                return namePoolSize;
            }

            public void setNamePoolSize(int namePoolSize) {
                this.namePoolSize = namePoolSize;
            }

            public String getEmailDomain() {
                return emailDomain;
            }

            public void setEmailDomain(String emailDomain) {
                this.emailDomain = emailDomain;
            }

            public int getMinAge() {
                return minAge;
            }

            public void setMinAge(int minAge) {
                this.minAge = minAge;
            }

            public int getMaxAge() {
                return maxAge;
            }

            public void setMaxAge(int maxAge) {
                this.maxAge = maxAge;
            }
        }

        public static class CleanupConfig {
            private boolean enabled = true;
            private boolean deleteTableAfterTest = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isDeleteTableAfterTest() {
                return deleteTableAfterTest;
            }

            public void setDeleteTableAfterTest(boolean deleteTableAfterTest) {
                this.deleteTableAfterTest = deleteTableAfterTest;
            }
        }

        public static class MigrationTestConfig {
            private int initialRows = 1000;
            private int writeRps = 50;
            private int readRps = 20;
            private int warmupDurationSeconds = 10;
            private int migrationTriggerDelaySeconds = 15;
            private int verificationTimeoutSeconds = 300;
            private String backendUrl = "http://localhost:8080";

            public int getInitialRows() {
                return initialRows;
            }

            public void setInitialRows(int initialRows) {
                this.initialRows = initialRows;
            }

            public int getWriteRps() {
                return writeRps;
            }

            public void setWriteRps(int writeRps) {
                this.writeRps = writeRps;
            }

            public int getReadRps() {
                return readRps;
            }

            public void setReadRps(int readRps) {
                this.readRps = readRps;
            }

            public int getWarmupDurationSeconds() {
                return warmupDurationSeconds;
            }

            public void setWarmupDurationSeconds(int warmupDurationSeconds) {
                this.warmupDurationSeconds = warmupDurationSeconds;
            }

            public int getMigrationTriggerDelaySeconds() {
                return migrationTriggerDelaySeconds;
            }

            public void setMigrationTriggerDelaySeconds(int migrationTriggerDelaySeconds) {
                this.migrationTriggerDelaySeconds = migrationTriggerDelaySeconds;
            }

            public int getVerificationTimeoutSeconds() {
                return verificationTimeoutSeconds;
            }

            public void setVerificationTimeoutSeconds(int verificationTimeoutSeconds) {
                this.verificationTimeoutSeconds = verificationTimeoutSeconds;
            }

            public String getBackendUrl() {
                return backendUrl;
            }

            public void setBackendUrl(String backendUrl) {
                this.backendUrl = backendUrl;
            }
        }
    }
}
