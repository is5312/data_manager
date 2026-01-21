# gRPC Data Operations Service

This document describes how to use the gRPC service for data operations (insert, update, delete) in the Data Manager application.

## Overview

The gRPC service provides a high-performance alternative to REST APIs for performing data operations. It uses Protocol Buffers for efficient serialization and supports schema-aware operations.

## Architecture

```
┌─────────────────────┐          ┌─────────────────────┐
│  data-manager-      │  gRPC    │  data-manager-      │
│  client             │─────────▶│  backend            │
│                     │  :9090   │                     │
│  DataOperations     │          │  DataOperations     │
│  Client             │          │  GrpcService        │
└─────────────────────┘          └──────────┬──────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │  DataService        │
                                  │  (Existing)         │
                                  └──────────┬──────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │  DataDao / JOOQ     │
                                  └──────────┬──────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │  PostgreSQL         │
                                  └─────────────────────┘
```

## Server Setup

### 1. Configuration

The gRPC server is configured in `application.yml`:

```yaml
grpc:
  server:
    port: 9090
    enable-reflection: true
```

- **Port 9090**: The gRPC server listens on this port (separate from HTTP port 8080)
- **Reflection enabled**: Allows tools like `grpcurl` and `grpcui` to introspect the service

### 2. Start the Backend

```bash
./gradlew :data-manager-backend:bootRun
```

The gRPC server starts automatically alongside the Spring Boot application.

### 3. Verify Server is Running

Check that port 9090 is listening:

```bash
lsof -i :9090
```

Or use grpcurl (if installed):

```bash
grpcurl -plaintext localhost:9090 list
# Should output: datamanager.DataOperationsService

grpcurl -plaintext localhost:9090 describe datamanager.DataOperationsService
# Shows service methods
```

## Client Usage

The client uses [grpc-spring-boot-starter](https://grpc-ecosystem.github.io/grpc-spring/en/) for automatic channel management and Spring Boot integration.

### Configuration

Edit `data-manager-client/src/main/resources/application.yml`:

```yaml
grpc:
  client:
    dataOperations:
      address: 'static://localhost:9090'
      negotiationType: PLAINTEXT
```

### Using the Java Client

The `DataOperationsClient` is a Spring `@Service` that uses `@GrpcClient` annotation for automatic connection management:

```java
import com.datamanager.client.DataOperationsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class MyService {
    
    @Autowired
    private DataOperationsClient client;
    
    public void performOperations() {
        // Insert a row
        Map<String, String> data = new HashMap<>();
        data.put("name", "John Doe");
        data.put("email", "john@example.com");
        
        InsertRowResponse response = client.insertRow(
            1L,           // table ID
            "public",     // schema name
            data          // row data
        );
        
        System.out.println("Inserted row ID: " + response.getId());
        System.out.println("Added by: " + response.getAudit().getAddUsr());
        
        // Update the row
        Map<String, String> updates = new HashMap<>();
        updates.put("email", "newemail@example.com");
        
        UpdateRowResponse updateResp = client.updateRow(
            1L,                    // table ID
            response.getId(),      // row ID
            "public",              // schema name
            updates                // updated data
        );
        
        System.out.println("Updated at: " + updateResp.getAudit().getUpdTs());
        
        // Delete the row
        DeleteRowResponse deleteResp = client.deleteRow(
            1L,                    // table ID
            response.getId(),      // row ID
            "public"               // schema name
        );
        
        System.out.println(deleteResp.getMessage());
    }
}
```

### Convenience Methods

The client provides simplified methods for common operations:

```java
// Insert with varargs (key-value pairs)
long rowId = client.insertRowSimple(
    1L, 
    "public",
    "name", "John Doe",
    "email", "john@example.com",
    "age", "30"
);

// Update with varargs
client.updateRowSimple(
    1L,
    rowId,
    "public",
    "email", "newemail@example.com",
    "age", "31"
);
```

### Running the Example

A complete Spring Boot example is provided that demonstrates all operations:

```bash
# Run with Gradle
./gradlew :data-manager-client:bootRun

# Or build and run the JAR
./gradlew :data-manager-client:bootJar
java -jar data-manager-client/build/libs/data-manager-client-0.0.1-SNAPSHOT.jar
```

The example includes:
- Insert operations with audit trail
- Update operations
- Delete operations
- Convenience methods
- Error handling demonstrations

**Note**: Ensure the backend server is running on `localhost:9090` before starting the client.

## API Reference

### InsertRow

Inserts a new row into a table.

**Request:**
- `table_id` (int64): Logical table ID from `base_reference_table`
- `schema_name` (string): Schema name (e.g., "public", "dmgr")
- `row_data` (map<string, string>): Column name to value mapping

**Response:**
- `id` (int64): ID of the inserted row
- `message` (string): Success message
- `audit` (AuditInfo): Audit trail (add_usr, add_ts, upd_usr, upd_ts)

### UpdateRow

Updates an existing row in a table.

**Request:**
- `table_id` (int64): Logical table ID
- `row_id` (int64): ID of the row to update
- `schema_name` (string): Schema name
- `row_data` (map<string, string>): Column name to value mapping (only columns to update)

**Response:**
- `message` (string): Success message
- `audit` (AuditInfo): Audit trail

### DeleteRow

Deletes a row from a table.

**Request:**
- `table_id` (int64): Logical table ID
- `row_id` (int64): ID of the row to delete
- `schema_name` (string): Schema name

**Response:**
- `message` (string): Success message

## Testing with grpcurl

If you have [grpcurl](https://github.com/fullstorydev/grpcurl) installed, you can test the service from the command line:

```bash
# List services
grpcurl -plaintext localhost:9090 list

# Describe a service
grpcurl -plaintext localhost:9090 describe datamanager.DataOperationsService

# Insert a row
grpcurl -plaintext -d '{
  "table_id": 1,
  "schema_name": "public",
  "row_data": {
    "name": "Test User",
    "email": "test@example.com"
  }
}' localhost:9090 datamanager.DataOperationsService/InsertRow

# Update a row
grpcurl -plaintext -d '{
  "table_id": 1,
  "row_id": 123,
  "schema_name": "public",
  "row_data": {
    "email": "updated@example.com"
  }
}' localhost:9090 datamanager.DataOperationsService/UpdateRow

# Delete a row
grpcurl -plaintext -d '{
  "table_id": 1,
  "row_id": 123,
  "schema_name": "public"
}' localhost:9090 datamanager.DataOperationsService/DeleteRow
```

## Error Handling

The gRPC service returns standard gRPC status codes:

- **INVALID_ARGUMENT**: Invalid table ID, missing required fields, or table not found
- **INTERNAL**: Server-side error (database issues, etc.)

Example error handling in Java:

```java
try {
    InsertRowResponse response = client.insertRow(999L, "public", data);
} catch (StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
        System.err.println("Invalid request: " + e.getStatus().getDescription());
    } else {
        System.err.println("Server error: " + e.getStatus().getDescription());
    }
}
```

## Proto File

The service definition is in `src/main/proto/data_service.proto` and is shared between backend and client modules.

## Building

To regenerate protobuf stubs after modifying the `.proto` file:

```bash
# Generate for backend
./gradlew :data-manager-backend:generateProto

# Generate for client
./gradlew :data-manager-client:generateProto

# Or both at once
./gradlew generateProto
```

## Performance Notes

- gRPC uses HTTP/2 and Protocol Buffers for efficient binary serialization
- Consider using async stubs for high-throughput scenarios
- The current implementation uses blocking stubs for simplicity
- No TLS encryption (plaintext) as configured - suitable for internal networks only

## Troubleshooting

### Server not starting on port 9090

Check if another process is using the port:
```bash
lsof -i :9090
```

Change the port in `application.yml` if needed.

### "UNAVAILABLE: io exception"

- Verify the backend is running
- Check firewall settings
- Ensure you're using the correct host and port

### "INVALID_ARGUMENT: Table not found"

- Verify the table ID exists in `base_reference_table`
- Check that you're querying the correct schema
- Use REST API to list tables: `GET http://localhost:8080/api/tables?schema=public`

## Testing

### Running Tests

#### Backend Tests

```bash
# Run all gRPC service tests
./gradlew :data-manager-backend:test --tests "com.datamanager.backend.grpc.*"

# Run only unit tests
./gradlew :data-manager-backend:test --tests "DataOperationsGrpcServiceTest"

# Run only integration tests
./gradlew :data-manager-backend:test --tests "DataOperationsGrpcServiceIntegrationTest"
```

#### Client Tests

```bash
# Run all client tests
./gradlew :data-manager-client:test --tests "com.datamanager.client.*"

# Run specific client test
./gradlew :data-manager-client:test --tests "DataOperationsClientTest"
```

### Test Coverage

#### Backend Unit Tests (`DataOperationsGrpcServiceTest`)

Tests the gRPC service layer with mocked dependencies:

- ✅ `insertRow_Success` - Successful row insertion
- ✅ `insertRow_InvalidTableId` - Invalid table ID error handling
- ✅ `insertRow_InternalError` - Internal error handling
- ✅ `insertRow_WithEmptyRowData` - Empty data handling
- ✅ `updateRow_Success` - Successful row update
- ✅ `updateRow_InvalidRowId` - Invalid row ID error handling
- ✅ `updateRow_WithMultipleFields` - Multiple field updates
- ✅ `deleteRow_Success` - Successful row deletion
- ✅ `deleteRow_InvalidTableId` - Invalid table ID error handling

**Total: 9 tests**

#### Backend Integration Tests (`DataOperationsGrpcServiceIntegrationTest`)

End-to-end tests using in-process gRPC server:

- ✅ `insertRow_EndToEnd_Success` - Complete insert flow
- ✅ `insertRow_EndToEnd_InvalidTableId` - Error propagation
- ✅ `insertRow_WithDifferentSchema` - Multi-schema support
- ✅ `updateRow_EndToEnd_Success` - Complete update flow
- ✅ `updateRow_EndToEnd_InvalidRowId` - Error handling
- ✅ `updateRow_WithMultipleFields` - Complex updates
- ✅ `deleteRow_EndToEnd_Success` - Complete delete flow
- ✅ `deleteRow_EndToEnd_InvalidTableId` - Error handling

**Total: 8 tests**

#### Client Tests (`DataOperationsClientTest`)

Tests the client wrapper with mock gRPC service:

- ✅ `testInsertRow_Success` - Client insert operation
- ✅ `testInsertRow_InvalidTableId` - Client error handling
- ✅ `testUpdateRow_Success` - Client update operation
- ✅ `testUpdateRow_InvalidRowId` - Client error handling
- ✅ `testDeleteRow_Success` - Client delete operation
- ✅ `testDeleteRow_InvalidTableId` - Client error handling

**Total: 6 tests**

### Test Architecture

```
┌─────────────────────────────────────────┐
│   DataOperationsGrpcServiceTest         │
│   (Unit Tests)                          │
│   - Mocks DataService                   │
│   - Tests service logic & error handling│
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│   DataOperationsGrpcServiceIntegrationTest│
│   (Integration Tests)                   │
│   - Uses in-process gRPC server         │
│   - Tests full request/response cycle   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│   DataOperationsClientTest              │
│   (Client Tests)                        │
│   - Mocks gRPC service                  │
│   - Tests client wrapper functionality  │
└─────────────────────────────────────────┘
```

### View Test Reports

After running tests, view detailed HTML reports:

```bash
# Backend test report
open data-manager-backend/build/reports/tests/test/index.html

# Client test report
open data-manager-client/build/reports/tests/test/index.html
```

## Next Steps

- Add authentication/authorization
- Implement streaming operations for bulk inserts
- Add more service methods (batch operations, queries)
- Configure TLS for production use
- Add performance/load tests