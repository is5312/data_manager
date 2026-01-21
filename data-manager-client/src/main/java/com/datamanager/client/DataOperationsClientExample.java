package com.datamanager.client;

import com.datamanager.grpc.DeleteRowResponse;
import com.datamanager.grpc.InsertRowResponse;
import com.datamanager.grpc.UpdateRowResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

/**
 * Example Spring Boot application demonstrating gRPC client usage
 * with grpc-spring-boot-starter.
 * 
 * To run: ./gradlew :data-manager-client:bootRun
 */
@SpringBootApplication
public class DataOperationsClientExample {

    public static void main(String[] args) {
        SpringApplication.run(DataOperationsClientExample.class, args);
    }

    @Bean
    public CommandLineRunner run(DataOperationsClient client) {
        return args -> {
            System.out.println("=".repeat(80));
            System.out.println("Data Manager gRPC Client Example");
            System.out.println("=".repeat(80));
            
            try {
                // Example 1: Insert a new row
                System.out.println("\n1. Inserting a new row...");
                Map<String, String> insertData = new HashMap<>();
                insertData.put("name", "John Doe");
                insertData.put("email", "john@example.com");
                insertData.put("age", "30");
                
                InsertRowResponse insertResponse = client.insertRow(1L, "public", insertData);
                System.out.println("   ✓ Row inserted successfully!");
                System.out.println("   - New Row ID: " + insertResponse.getId());
                System.out.println("   - Message: " + insertResponse.getMessage());
                System.out.println("   - Created by: " + insertResponse.getAudit().getAddUsr());
                System.out.println("   - Created at: " + insertResponse.getAudit().getAddTs());
                
                long newRowId = insertResponse.getId();
                
                // Example 2: Update the row
                System.out.println("\n2. Updating the row...");
                Map<String, String> updateData = new HashMap<>();
                updateData.put("email", "john.doe@newdomain.com");
                updateData.put("age", "31");
                
                UpdateRowResponse updateResponse = client.updateRow(1L, newRowId, "public", updateData);
                System.out.println("   ✓ Row updated successfully!");
                System.out.println("   - Message: " + updateResponse.getMessage());
                System.out.println("   - Updated by: " + updateResponse.getAudit().getUpdUsr());
                System.out.println("   - Updated at: " + updateResponse.getAudit().getUpdTs());
                
                // Example 3: Delete the row
                System.out.println("\n3. Deleting the row...");
                DeleteRowResponse deleteResponse = client.deleteRow(1L, newRowId, "public");
                System.out.println("   ✓ Row deleted successfully!");
                System.out.println("   - Message: " + deleteResponse.getMessage());
                
                // Example 4: Using simple convenience methods
                System.out.println("\n4. Using simple convenience methods...");
                long simpleRowId = client.insertRowSimple(
                    1L, 
                    "public",
                    "name", "Jane Doe",
                    "email", "jane@example.com",
                    "age", "25"
                );
                System.out.println("   ✓ Row inserted with simple method!");
                System.out.println("   - New Row ID: " + simpleRowId);
                
                client.updateRowSimple(
                    1L,
                    simpleRowId,
                    "public",
                    "age", "26"
                );
                System.out.println("   ✓ Row updated with simple method!");
                
                // Example 5: Error handling
                System.out.println("\n5. Demonstrating error handling...");
                try {
                    client.insertRow(999L, "public", insertData);
                } catch (StatusRuntimeException e) {
                    System.out.println("   ✓ Expected error caught:");
                    System.out.println("   - Status: " + e.getStatus().getCode());
                    System.out.println("   - Message: " + e.getStatus().getDescription());
                }
                
            } catch (StatusRuntimeException e) {
                System.err.println("\n❌ gRPC Error:");
                System.err.println("   Status: " + e.getStatus().getCode());
                System.err.println("   Message: " + e.getStatus().getDescription());
                System.err.println("\nMake sure the backend server is running on localhost:9090");
            } catch (Exception e) {
                System.err.println("\n❌ Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Example completed. Application will now exit.");
            System.out.println("=".repeat(80));
        };
    }
}
