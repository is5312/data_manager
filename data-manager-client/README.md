# Data Manager gRPC Client

A Spring Boot-based gRPC client for Data Manager data operations, built with [grpc-spring-boot-starter](https://grpc-ecosystem.github.io/grpc-spring/en/).

## Features

- 🚀 **Spring Boot Integration** - Automatic channel lifecycle management
- 🔌 **Zero Boilerplate** - `@GrpcClient` annotation handles all connection logic
- ⚙️ **Configuration-Based** - Simple YAML configuration for server endpoints
- 🛡️ **Type-Safe** - Strongly typed protobuf messages
- 📝 **Convenience Methods** - Simplified APIs for common operations
- 🧪 **Fully Tested** - Comprehensive test coverage with in-process gRPC

## Quick Start

### 1. Configuration

Edit `src/main/resources/application.yml`:

```yaml
grpc:
  client:
    dataOperations:
      address: 'static://localhost:9090'
      negotiationType: PLAINTEXT
```

### 2. Use in Your Service

```java
import com.datamanager.client.DataOperationsClient;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    private final DataOperationsClient client;
    
    public MyService(DataOperationsClient client) {
        this.client = client;
    }
    
    public void insertData() {
        // No channel management needed - it's automatic!
        long rowId = client.insertRowSimple(
            1L, "public",
            "name", "John Doe",
            "email", "john@example.com"
        );
        System.out.println("Inserted row: " + rowId);
    }
}
```

### 3. Run the Example

```bash
# Start the backend server first (on port 9090)
cd /path/to/data-manager
./gradlew :data-manager-backend:bootRun

# Then run the client example
./gradlew :data-manager-client:bootRun
```

## API Overview

### Insert Row

```java
Map<String, String> data = new HashMap<>();
data.put("name", "John Doe");
data.put("email", "john@example.com");

InsertRowResponse response = client.insertRow(1L, "public", data);
System.out.println("Row ID: " + response.getId());
System.out.println("Created by: " + response.getAudit().getAddUsr());
```

### Update Row

```java
Map<String, String> updates = new HashMap<>();
updates.put("email", "newemail@example.com");

UpdateRowResponse response = client.updateRow(1L, rowId, "public", updates);
System.out.println("Updated at: " + response.getAudit().getUpdTs());
```

### Delete Row

```java
DeleteRowResponse response = client.deleteRow(1L, rowId, "public");
System.out.println(response.getMessage());
```

### Convenience Methods

```java
// Insert with varargs
long id = client.insertRowSimple(1L, "public", "name", "Jane", "age", "25");

// Update with varargs
client.updateRowSimple(1L, id, "public", "age", "26");
```

## Configuration Options

### Basic Configuration

```yaml
grpc:
  client:
    dataOperations:
      address: 'static://localhost:9090'
      negotiationType: PLAINTEXT
```

### Advanced Configuration

```yaml
grpc:
  client:
    dataOperations:
      address: 'static://localhost:9090'
      negotiationType: PLAINTEXT
      max-inbound-message-size: 10485760  # 10MB
      deadline: 30s
      keepAliveTime: 30s
      keepAliveTimeout: 10s
      # For production with TLS:
      # negotiationType: TLS
      # security:
      #   authorityOverride: localhost
```

## Benefits of grpc-spring-boot-starter

### Before (Manual Channel Management)

```java
// ❌ Manual channel creation
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

DataOperationsServiceBlockingStub stub = 
    DataOperationsServiceGrpc.newBlockingStub(channel);

try {
    // Use stub...
} finally {
    // ❌ Manual cleanup required
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
}
```

### After (Spring Boot)

```java
// ✅ Automatic injection
@Autowired
private DataOperationsClient client;

// ✅ Ready to use - no manual management
client.insertRow(1L, "public", data);

// ✅ Automatic cleanup on shutdown
```

## Testing

Run tests with:

```bash
./gradlew :data-manager-client:test
```

The test suite uses in-process gRPC servers for fast, isolated testing without network dependencies.

## Building

```bash
# Compile
./gradlew :data-manager-client:compileJava

# Build JAR
./gradlew :data-manager-client:bootJar

# Run
java -jar build/libs/data-manager-client-0.0.1-SNAPSHOT.jar
```

## Error Handling

```java
try {
    InsertRowResponse response = client.insertRow(999L, "public", data);
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case INVALID_ARGUMENT:
            System.err.println("Invalid input: " + e.getStatus().getDescription());
            break;
        case NOT_FOUND:
            System.err.println("Table not found: " + e.getStatus().getDescription());
            break;
        case UNAVAILABLE:
            System.err.println("Server unavailable - is it running?");
            break;
        default:
            System.err.println("gRPC error: " + e.getStatus());
    }
}
```

## Documentation

For complete documentation, see [GRPC_USAGE.md](../GRPC_USAGE.md) in the project root.

## Requirements

- Java 21+
- Spring Boot 3.4.1
- Backend server running on port 9090

## License

Same as parent project.
