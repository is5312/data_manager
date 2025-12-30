# Backend Architecture Refactoring

## Overview

The backend has been refactored to follow a cleaner architecture with proper separation of concerns:
- **Spring JPA** for metadata table operations (base_reference_table, base_column_map)
- **JOOQ** for dynamic schema operations (physical tables)
- **Repository Pattern** for data access
- **Service Layer** for business logic
- **DAO Pattern** for dynamic queries

## Architecture Layers

```
┌─────────────────────────────────────────┐
│          Controller Layer               │
│  (REST endpoints, request handling)     │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│          Service Layer                  │
│  (Business logic, orchestration)        │
└─────┬───────────────────────────────┬───┘
      │                               │
      ▼                               ▼
┌─────────────────┐         ┌─────────────────┐
│  JPA Repository │         │   JOOQ DAO      │
│   (Metadata)    │         │  (Physical DB)  │
└─────────────────┘         └─────────────────┘
      │                               │
      ▼                               ▼
┌─────────────────────────────────────────┐
│         PostgreSQL Database              │
│  (Metadata Tables + Physical Tables)     │
└─────────────────────────────────────────┘
```

## Directory Structure

```
src/main/java/com/datamanager/backend/
├── controller/               # REST Controllers
│   └── SchemaController.java
│
├── service/                  # Service Interfaces
│   ├── TableMetadataService.java
│   └── impl/                # Service Implementations
│       └── TableMetadataServiceImpl.java
│
├── repository/              # Spring Data JPA Repositories
│   ├── BaseReferenceTableRepository.java
│   └── BaseColumnMapRepository.java
│
├── dao/                     # JOOQ DAO Interfaces
│   ├── SchemaDao.java
│   └── impl/               # DAO Implementations
│       └── SchemaDaoImpl.java
│
├── entity/                  # JPA Entities (Metadata)
│   ├── BaseReferenceTable.java
│   └── BaseColumnMap.java
│
├── dto/                     # Data Transfer Objects
│   ├── TableMetadataDto.java
│   └── ColumnMetadataDto.java
│
├── mapper/                  # Entity-DTO Mappers
│   └── MetadataMapper.java
│
└── config/                  # Configuration Classes
    └── WebConfig.java

src/main/resources/
├── application.yml          # Application configuration
└── db/migration/           # Flyway migration scripts
    └── V1__init_meta_tables.sql
```

## Technology Stack

### Core Technologies
- **Spring Boot 3.x** - Application framework
- **Spring Data JPA** - Repository abstraction for metadata
- **JOOQ 3.19** - Type-safe SQL builder for dynamic queries
- **PostgreSQL** - Database
- **Flyway** - Database migrations
- **Lombok** - Reduce boilerplate code

### Key Dependencies

```gradle
// Spring Boot Starters
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-jooq'

// Database
implementation 'org.postgresql:postgresql'
implementation 'org.flywaydb:flyway-core'

// JOOQ
id 'nu.studer.jooq' version '9.0'

// Lombok
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

## Layer Responsibilities

### 1. Controller Layer
**Location:** `controller/`
**Purpose:** Handle HTTP requests and responses
**Technology:** Spring MVC

**Example:**
```java
@RestController
@RequestMapping("/api/schema")
public class SchemaController {
    @GetMapping("/tables")
    public ResponseEntity<List<TableMetadataDto>> getAllTables() {
        return ResponseEntity.ok(tableMetadataService.getAllTables());
    }
}
```

### 2. Service Layer
**Location:** `service/` and `service/impl/`
**Purpose:** Business logic and transaction management
**Technology:** Spring Transactions

**Key Responsibilities:**
- Orchestrate JPA repositories and JOOQ DAOs
- Manage transactions
- Convert entities to DTOs
- Validate business rules

**Example:**
```java
@Service
@Transactional
public class TableMetadataServiceImpl implements TableMetadataService {
    // Uses both JPA repositories and JOOQ DAO
    public TableMetadataDto createTable(String tableLabel) {
        // 1. Create physical table using JOOQ DAO
        schemaDao.createTable(physicalTableName);
        
        // 2. Save metadata using JPA repository
        tableRepository.save(entity);
    }
}
```

### 3. Repository Layer (JPA)
**Location:** `repository/`
**Purpose:** Data access for metadata tables
**Technology:** Spring Data JPA

**Why JPA for Metadata?**
- Structured, well-defined schema
- Complex relationships (one-to-many)
- CRUD operations
- Transaction management
- Object-relational mapping

**Example:**
```java
@Repository
public interface BaseReferenceTableRepository 
        extends JpaRepository<BaseReferenceTable, Long> {
    Optional<BaseReferenceTable> findByTblLink(String tblLink);
}
```

### 4. DAO Layer (JOOQ)
**Location:** `dao/` and `dao/impl/`
**Purpose:** Dynamic schema operations on physical tables
**Technology:** JOOQ DSL

**Why JOOQ for Dynamic Queries?**
- Type-safe DDL operations
- Dynamic table creation
- Dynamic column management
- No need for ORM mapping
- Direct SQL control

**Optimizations:**
- **Single-Query Operations**: `insertRow` and `updateRow` now explicitly set audit columns (`add_ts`, `upd_ts`, etc.) and return the result in a single query loop, eliminating redundant `SELECT` fetches.

**Example:**
```java
@Repository
public class SchemaDaoImpl implements SchemaDao {
    public void createTable(String tableName) {
        dsl.createTableIfNotExists(table(name(tableName)))
            .column(field(name("id")), SQLDataType.BIGINT.identity(true))
            .column(field(name("add_ts")), SQLDataType.TIMESTAMP.defaultValue(DSL.currentTimestamp())) 
            // ... other audit columns
            .execute();
    }
}
```

### 5. Entity Layer
**Location:** `entity/`
**Purpose:** JPA entities for metadata tables
**Technology:** JPA/Hibernate + Lombok

**Features:**
- Automatic timestamp management (@PrePersist, @PreUpdate)
- Bidirectional relationships
- Lombok annotations for boilerplate reduction

### 6. DTO Layer
**Location:** `dto/`
**Purpose:** Data transfer between layers
**Technology:** Lombok Builders

**Benefits:**
- Decouple API from internal entities
- Control what data is exposed
- Immutable data structures

## API Endpoints

### Tables

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/schema/tables` | Get all tables |
| GET | `/api/schema/tables/{id}` | Get table by ID |
| POST | `/api/schema/tables?label={label}` | Create new table |
| PUT | `/api/schema/tables/{id}/rename?newLabel={label}` | Rename table |
| DELETE | `/api/schema/tables/{id}` | Delete table |

### Columns

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/schema/tables/{id}/columns` | Get all columns for table |
| POST | `/api/schema/tables/{id}/columns?label={label}&type={type}` | Add column |
| DELETE | `/api/schema/tables/{tableId}/columns/{columnId}` | Remove column |

## Database Schema

### Metadata Tables (Managed by JPA)

**base_reference_table**
- Stores logical table metadata
- One-to-many relationship with base_column_map
- Managed by Spring JPA

**base_column_map**
- Stores logical column metadata
- Many-to-one relationship with base_reference_table
- Managed by Spring JPA

### Physical Tables (Managed by JOOQ)
- Dynamic tables created at runtime
- Named with pattern: `tbl_{timestamp}_{uuid}`
- Managed by JOOQ DAO

## Configuration

### application.yml

```yaml
spring:
  # JPA Configuration
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Flyway manages schema
  
  # Flyway Configuration
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

# JOOQ Configuration
jooq:
  sql-dialect: POSTGRES
```

## Building the Project

### 1. Build with Gradle

```bash
cd data-manager-backend
./gradlew clean build
```

### 2. Generate JOOQ Classes (Optional)

```bash
./gradlew generateJooq
```

This generates type-safe JOOQ classes from your database schema.

### 3. Run the Application

```bash
./gradlew bootRun
```

Or from the parent directory:

```bash
cd data_manager
./gradlew :data-manager-backend:bootRun
```

## Transaction Management

The service layer uses Spring's `@Transactional` annotation to ensure:
- Atomic operations across JPA and JOOQ
- Rollback on exceptions
- Proper isolation levels

**Example:**
```java
@Transactional
public TableMetadataDto createTable(String tableLabel) {
    // Both operations are in the same transaction
    schemaDao.createTable(physicalName);  // JOOQ
    tableRepository.save(entity);          // JPA
    // If either fails, both rollback
}
```

## Error Handling

The controller includes exception handlers:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
}
```

## Best Practices Applied

✅ **Separation of Concerns** - Clear layer boundaries
✅ **Dependency Injection** - Constructor injection
✅ **Interface Segregation** - Service and DAO interfaces
✅ **Transaction Management** - Declarative transactions
✅ **DTO Pattern** - Decouple API from entities
✅ **Repository Pattern** - Abstract data access
✅ **Logging** - SLF4J with Lombok
✅ **Error Handling** - Centralized exception handling
✅ **SQL Injection Prevention** - JOOQ type safety + sanitization

## Migration from Old Code

### Old Structure
```
schema/
├── SchemaController.java (mixed concerns)
├── MetadataService.java (JDBC template)
├── SchemaRepository.java (JDBC template)
├── SchemaHelpers.java
└── TableMetadata.java (record)
```

### New Structure
```
controller/ → REST API
service/ → Business logic
repository/ → JPA for metadata
dao/ → JOOQ for dynamic queries
entity/ → JPA entities
dto/ → API contracts
mapper/ → Conversions
```

### Key Changes

1. **Split Responsibilities:**
   - Metadata operations → JPA repositories
   - Dynamic schema → JOOQ DAO

2. **Added Transaction Management:**
   - @Transactional service methods
   - Automatic rollback

3. **Improved Type Safety:**
   - JOOQ DSL instead of string concatenation
   - JPA entities instead of RowMapper

4. **Better Error Handling:**
   - Validation at each layer
   - Proper exception hierarchy

## Testing

### Unit Tests
Test service layer with mocked repositories and DAOs:

```java
@ExtendWith(MockitoExtension.class)
class TableMetadataServiceImplTest {
    @Mock private BaseReferenceTableRepository tableRepository;
    @Mock private SchemaDao schemaDao;
    @InjectMocks private TableMetadataServiceImpl service;
}
```

### Integration Tests
Test full stack with test database:

```java
@SpringBootTest
@Transactional
class SchemaControllerIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
}
```

## Troubleshooting

### JOOQ Code Generation Issues
If JOOQ fails to generate, ensure:
1. Database is running
2. Flyway migrations have run
3. Credentials in build.gradle match your DB

### Lombok Not Working
Ensure your IDE has Lombok plugin:
- IntelliJ: Enable annotation processing
- Eclipse: Install Lombok agent

### Transaction Issues
Check that methods are:
- Public (private methods don't proxy)
- Called from outside the class (self-invocation doesn't work)

## Future Enhancements

1. **Add Caching** - Redis for metadata
2. **Add Validation** - JSR-303 Bean Validation
3. **Add Pagination** - Spring Data pagination
4. **Add Search** - JPA Specifications
5. **Add Auditing** - Spring Data Auditing
6. **Add Security** - Spring Security
7. **Add API Docs** - OpenAPI/Swagger

## References

- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [JOOQ Documentation](https://www.jooq.org/doc/latest/manual/)
- [Flyway](https://flywaydb.org/documentation/)
- [Lombok](https://projectlombok.org/)
