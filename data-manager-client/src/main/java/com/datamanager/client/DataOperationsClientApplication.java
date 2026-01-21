package com.datamanager.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Data Manager gRPC Client
 */
@SpringBootApplication
public class DataOperationsClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataOperationsClientApplication.class, args);
    }
}
