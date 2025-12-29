---
description: How to add a new REST endpoint to the backend
---

# Adding a New Backend Endpoint

Follow these steps when adding new REST endpoints to the Data Manager backend.

## Prerequisites
- Understand the two-layer architecture (see `.agent/CONTEXT.md`)
- Know which entity you're working with (Table metadata, Column metadata, etc.)

## Steps

### 1. Define the Endpoint in Controller
**File**: `src/main/java/com/datamanager/backend/schema/SchemaController.java`

```java
@GetMapping("/endpoint-name")
public ResponseType methodName(@RequestParam String param) {
    return service.methodName(param);
}
```

**Important**: 
- Use `@RestController` and `@RequestMapping("/api/schema")`
- Follow REST conventions (GET, POST, PUT, DELETE)
- Return appropriate DTOs, not internal entities

### 2. Implement Business Logic in Service
**File**: `src/main/java/com/datamanager/backend/schema/MetadataService.java`

```java
@Transactional
public ResponseType methodName(String param) {
    // 1. Validate input
    // 2. Interact with metadata layer
    // 3. Call SchemaRepository for DDL operations if needed
    // 4. Return result
}
```

**Critical Rules**:
- ALWAYS use `@Transactional` when modifying both metadata + physical layer
- Generate UUIDs via `System.currentTimeMillis() + UUID.randomUUID()`
- Sanitize ALL identifiers via `SchemaHelpers.sanitizeIdentifier()`

### 3. Add Repository Method (if DDL needed)
**File**: `src/main/java/com/datamanager/backend/schema/SchemaRepository.java`

```java
public void operationName(String tableName, String columnName) {
    String safeTableName = SchemaHelpers.sanitizeIdentifier(tableName);
    String safeColumnName = SchemaHelpers.sanitizeIdentifier(columnName);
    
    String sql = String.format("DDL STATEMENT", safeTableName, safeColumnName);
    jdbcTemplate.execute(sql);
}
```

**Important**:
- NEVER trust user input - always sanitize
- Use `jdbcTemplate.execute()` for DDL
- Use `jdbcTemplate.query()` for SELECT

### 4. Test the Endpoint

// turbo
```bash
./gradlew :data-manager-backend:build -x test
```

// turbo
```bash
curl -X POST http://localhost:8080/api/schema/endpoint-name?param=value
```

### 5. Update Frontend API Service
**File**: `data-manager-ui/src/services/api.ts`

```typescript
export const newApiMethod = async (param: string): Promise<ResponseType> => {
  const response = await fetch(`${API_BASE_URL}/endpoint-name?param=${param}`, {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error('Failed to...');
  }
  return response.json();
};
```

## Example: Adding "Get Columns for Table" Endpoint

### Step 1: Controller
```java
@GetMapping("/tables/{tableId}/columns")
public List<ColumnMetadata> getColumns(@PathVariable Long tableId) {
    return metadataService.getColumnsForTable(tableId);
}
```

### Step 2: Service
```java
public List<ColumnMetadata> getColumnsForTable(Long tableId) {
    String sql = "SELECT id, col_label, col_link FROM base_column_map WHERE tbl_id = ?";
    return jdbcTemplate.query(sql, (rs, rowNum) -> new ColumnMetadata(
        rs.getLong("id"),
        rs.getString("col_label"),
        rs.getString("col_link")
    ));
}
```

### Step 3: Frontend
```typescript
export const fetchColumns = async (tableId: number): Promise<ColumnMetadata[]> => {
  const response = await fetch(`${API_BASE_URL}/tables/${tableId}/columns`);
  return response.json();
};
```

## Common Mistakes to Avoid

1. ❌ Skipping transaction annotation when modifying both layers
2. ❌ Using unsanitized identifiers in SQL
3. ❌ Exposing physical table/column names to frontend
4. ❌ Forgetting to update CORS if adding new origins
5. ❌ Not handling SQL exceptions properly

## Related Files
- `.agent/CONTEXT.md` - Architecture overview
- `SchemaController.java` - Existing endpoints
- `MetadataService.java` - Business logic patterns
- `SchemaRepository.java` - DDL operation examples
