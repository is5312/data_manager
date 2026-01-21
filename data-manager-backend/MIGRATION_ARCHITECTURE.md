# Table Migration Architecture & Design Document

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Migration Flow](#migration-flow)
4. [Shadow Copy Strategy](#shadow-copy-strategy)
5. [Components](#components)
6. [Data Flow](#data-flow)
7. [Trigger Mechanism](#trigger-mechanism)
8. [Metadata Management](#metadata-management)
9. [Backup & Rollback](#backup--rollback)
10. [Error Handling](#error-handling)
11. [Performance Considerations](#performance-considerations)

---

## Overview

The Data Manager table migration system enables zero-downtime migration of tables between PostgreSQL schemas. It supports two migration strategies:

1. **Normal Migration**: When the target table doesn't exist, creates a new table and copies data
2. **Shadow Copy Migration**: When the target table already exists, creates a shadow table with ULID-generated name and uses database triggers to synchronize changes

### Key Features
- ✅ Zero-downtime migration
- ✅ Continuous data synchronization via triggers
- ✅ Metadata versioning and backup
- ✅ Automatic rollback capability
- ✅ Asynchronous job processing (JobRunr)
- ✅ Schema-aware operations

---

## Architecture

### High-Level Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        UI[Web UI]
        API[REST API]
        GRPC[gRPC Service]
    end
    
    subgraph "Application Layer"
        MC[Migration Controller]
        MS[Migration Service]
        MJ[Migration Job]
    end
    
    subgraph "Data Layer"
        SD[Schema DAO]
        TMS[Table Metadata Service]
        DS[Data Service]
    end
    
    subgraph "Database Layer"
        PS[(PostgreSQL)]
        PS1[(public schema)]
        PS2[(dmgr schema)]
        TRG[Database Triggers]
    end
    
    UI --> API
    API --> MC
    GRPC --> DS
    MC --> MS
    MS --> MJ
    MJ --> SD
    MJ --> TMS
    SD --> PS
    TMS --> PS
    DS --> PS
    PS --> PS1
    PS --> PS2
    TRG --> PS2
```

### Component Interaction

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant JobRunr
    participant MigrationJob
    participant SchemaDAO
    participant Database
    
    Client->>Controller: POST /api/schema/migration/tables/{id}/migrate
    Controller->>Service: migrateTable(tableId, sourceSchema, targetSchema)
    Service->>JobRunr: Enqueue migration job
    JobRunr-->>Controller: Return jobId (202 Accepted)
    Controller-->>Client: Return jobId
    
    JobRunr->>MigrationJob: Execute migration
    MigrationJob->>SchemaDAO: Check table existence
    SchemaDAO->>Database: Query table metadata
    Database-->>SchemaDAO: Return table info
    
    alt Shadow Copy Scenario
        MigrationJob->>SchemaDAO: Create shadow table
        MigrationJob->>SchemaDAO: Copy existing data
        MigrationJob->>SchemaDAO: Create triggers
        MigrationJob->>Database: Update metadata
    else Normal Migration
        MigrationJob->>SchemaDAO: Create new table
        MigrationJob->>SchemaDAO: Copy source data
        MigrationJob->>Database: Insert metadata
    end
    
    MigrationJob-->>JobRunr: Migration complete
    JobRunr-->>Client: Job status available
```

---

## Migration Flow

### Main Migration Flow

```mermaid
flowchart TD
    Start([Migration Request]) --> Validate{Validate Input}
    Validate -->|Invalid| Error1[Return Error]
    Validate -->|Valid| CheckSchema{Target Schema<br/>Exists?}
    
    CheckSchema -->|No| CreateSchema[Create Target Schema]
    CreateSchema --> EnsureMeta
    CheckSchema -->|Yes| EnsureMeta[Ensure Metadata Tables Exist]
    
    EnsureMeta --> UpgradeFK[Upgrade Foreign Key Constraints]
    UpgradeFK --> GetSourceMeta[Get Source Table Metadata]
    GetSourceMeta --> CheckSource{Source Table<br/>Exists?}
    
    CheckSource -->|No| Error2[Error: Table Not Found]
    CheckSource -->|Yes| CheckTarget{Target Table<br/>Exists?}
    
    CheckTarget -->|Yes| ShadowCopy[Shadow Copy Strategy]
    CheckTarget -->|No| NormalMig[Normal Migration Strategy]
    
    ShadowCopy --> CopyMeta[Copy Metadata]
    NormalMig --> CopyMeta
    
    CopyMeta --> Success([Migration Complete])
    
    Error1 --> End([End])
    Error2 --> End
    Success --> End
    
    style ShadowCopy fill:#e1f5ff
    style NormalMig fill:#fff4e1
    style Success fill:#e8f5e9
    style Error1 fill:#ffebee
    style Error2 fill:#ffebee
```

### Shadow Copy Flow (Detailed)

```mermaid
flowchart TD
    Start([Shadow Copy Migration]) --> GenULID[Generate ULID Shadow Table Name]
    GenULID --> GetStructure[Get Table Structure from Source]
    GetStructure --> CreateShadow[Create Shadow Table in Target Schema]
    
    CreateShadow --> CopyExisting[Copy Data from Existing Target Table<br/>to Shadow Table]
    CopyExisting --> CreateTriggers[Create Database Triggers]
    
    CreateTriggers --> TriggerInsert[Create INSERT Trigger]
    CreateTriggers --> TriggerUpdate[Create UPDATE Trigger]
    CreateTriggers --> TriggerDelete[Create DELETE Trigger]
    
    TriggerInsert --> CheckMeta{Metadata Exists<br/>in Target?}
    TriggerUpdate --> CheckMeta
    TriggerDelete --> CheckMeta
    
    CheckMeta -->|Yes| BackupMeta[Backup Existing Metadata]
    BackupMeta --> UpdateMeta[Update Metadata to Point<br/>to Shadow Table]
    UpdateMeta --> SyncColumns[Sync Column Metadata]
    
    CheckMeta -->|No| InsertMeta[Insert New Metadata]
    InsertMeta --> InsertColumns[Insert Column Metadata]
    
    SyncColumns --> Complete([Shadow Copy Complete])
    InsertColumns --> Complete
    
    style CreateShadow fill:#e1f5ff
    style CreateTriggers fill:#e1f5ff
    style UpdateMeta fill:#fff4e1
```

### Normal Migration Flow (Detailed)

```mermaid
flowchart TD
    Start([Normal Migration]) --> UseSourceName[Use Source Table Name]
    UseSourceName --> GetStructure[Get Table Structure from Source]
    GetStructure --> CreateTable[Create Table in Target Schema]
    
    CreateTable --> CheckData{Source Table<br/>Has Data?}
    CheckData -->|Yes| CopyData[Copy Data from Source<br/>to Target]
    CheckData -->|No| SkipCopy[Skip Data Copy]
    
    CopyData --> InsertMeta[Insert Metadata in Target Schema]
    SkipCopy --> InsertMeta
    
    InsertMeta --> InsertColumns[Insert Column Metadata]
    InsertColumns --> Complete([Normal Migration Complete])
    
    style CreateTable fill:#fff4e1
    style CopyData fill:#fff4e1
```

---

## Shadow Copy Strategy

### Why Shadow Copy?

When migrating from `public` to `dmgr` schema:
- The table may already exist in `dmgr` (e.g., from previous migration attempt)
- We need to preserve existing data in `dmgr`
- We need to merge data from `public` with `dmgr`
- Zero-downtime is critical

### Shadow Copy Architecture

```mermaid
graph LR
    subgraph "public schema"
        PT[public.tbl_users]
    end
    
    subgraph "dmgr schema"
        ET[dmgr.tbl_users<br/>Existing Table]
        ST[dmgr.tbl_01ARZ3NDEKTSV4VV4VV4VV4VV4V<br/>Shadow Table ULID]
    end
    
    PT -->|1. Copy Structure| ST
    ET -->|2. Copy Data| ST
    ET -->|3. Create Triggers| TR[Triggers on<br/>dmgr.tbl_users]
    TR -->|4. Sync Changes| ST
    
    ET -.->|5. Update Metadata| META[Metadata Points<br/>to Shadow Table]
    META -.->|6. Applications Use| ST
    
    style ST fill:#e1f5ff
    style TR fill:#fff4e1
    style META fill:#e8f5e9
```

### Shadow Copy Steps

1. **Generate Shadow Table Name**: ULID-based unique name (e.g., `tbl_01ARZ3NDEKTSV4VV4VV4VV4VV4V`)
2. **Create Shadow Table**: Copy DDL structure from source table
3. **Copy Existing Data**: Bulk copy from existing target table to shadow table
4. **Create Triggers**: INSERT, UPDATE, DELETE triggers on existing table to sync to shadow
5. **Update Metadata**: Point metadata `tbl_link` to shadow table name
6. **Sync Column Metadata**: Ensure column definitions match

---

## Components

### TableMigrationService

**Responsibility**: Orchestrates the migration process

**Key Methods**:
- `migrateTable(Long tableId, String sourceSchema, String targetSchema)`: Main migration entry point
- `getAvailableSchemas()`: Returns list of schemas available for migration

**Dependencies**:
- `SchemaDao`: Database operations
- `DSLContext`: JOOQ query builder
- `MigrationProperties`: Configuration

### SchemaDao

**Responsibility**: Database schema and table operations

**Key Methods**:
- `tableExistsInSchema(String tableName, String schema)`: Check table existence
- `getTableStructure(String tableName, String schema)`: Get table DDL
- `bulkCopyTableData(...)`: Copy data between tables
- `createTrigger(...)`: Create database triggers
- `createMetadataTablesInSchema(String schema)`: Create metadata tables

### TableMigrationJob

**Responsibility**: Asynchronous job execution

**Features**:
- Runs migration in background via JobRunr
- Provides job status tracking
- Handles job failures and retries

---

## Data Flow

### Data Synchronization During Migration

```mermaid
sequenceDiagram
    participant App as Application
    participant ET as Existing Table<br/>dmgr.tbl_users
    participant TR as Database Triggers
    participant ST as Shadow Table<br/>dmgr.tbl_01ARZ...
    participant Meta as Metadata
    
    Note over App,Meta: During Migration (Triggers Active)
    
    App->>ET: INSERT row
    ET->>TR: Trigger INSERT
    TR->>ST: INSERT into shadow table
    ST-->>TR: Success
    TR-->>ET: Continue
    
    App->>ET: UPDATE row
    ET->>TR: Trigger UPDATE
    TR->>ST: DELETE + INSERT in shadow
    ST-->>TR: Success
    TR-->>ET: Continue
    
    App->>ET: DELETE row
    ET->>TR: Trigger DELETE
    TR->>ST: DELETE from shadow
    ST-->>TR: Success
    TR-->>ET: Continue
    
    Note over App,Meta: After Migration (Metadata Updated)
    
    App->>Meta: Query table metadata
    Meta-->>App: Returns shadow table name
    App->>ST: Direct operations on shadow table
```

### Metadata Flow

```mermaid
flowchart LR
    subgraph "Source Schema: public"
        SM[base_reference_table<br/>id: 82<br/>tbl_link: tbl_users]
        SC[base_column_map<br/>tbl_id: 82]
    end
    
    subgraph "Target Schema: dmgr"
        TM[base_reference_table<br/>id: 82<br/>tbl_link: tbl_01ARZ...]
        TC[base_column_map<br/>tbl_id: 82]
        TB[base_reference_table_bak<br/>Backup of old metadata]
        CB[base_column_map_bak<br/>Backup of old columns]
    end
    
    SM -->|1. Read| Copy[Copy Metadata]
    SC -->|1. Read| Copy
    
    Copy -->|2. Backup| TB
    Copy -->|2. Backup| CB
    
    Copy -->|3. Update| TM
    Copy -->|3. Insert| TC
    
    style TB fill:#fff4e1
    style CB fill:#fff4e1
    style TM fill:#e8f5e9
```

---

## Trigger Mechanism

### Trigger Function Generation

For each operation type (INSERT, UPDATE, DELETE), a PostgreSQL function is created:

#### INSERT Trigger

```sql
CREATE OR REPLACE FUNCTION migrate_insert_handler_<trigger_name>()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO dmgr.tbl_shadow SELECT NEW.*;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_migrate_82_<timestamp>_insert
AFTER INSERT ON dmgr.tbl_users
FOR EACH ROW EXECUTE FUNCTION migrate_insert_handler_<trigger_name>();
```

#### UPDATE Trigger

```sql
CREATE OR REPLACE FUNCTION migrate_update_handler_<trigger_name>()
RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM dmgr.tbl_shadow WHERE id = NEW.id;
  INSERT INTO dmgr.tbl_shadow SELECT NEW.*;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_migrate_82_<timestamp>_update
AFTER UPDATE ON dmgr.tbl_users
FOR EACH ROW EXECUTE FUNCTION migrate_update_handler_<trigger_name>();
```

#### DELETE Trigger

```sql
CREATE OR REPLACE FUNCTION migrate_delete_handler_<trigger_name>()
RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM dmgr.tbl_shadow WHERE id = OLD.id;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_migrate_82_<timestamp>_delete
AFTER DELETE ON dmgr.tbl_users
FOR EACH ROW EXECUTE FUNCTION migrate_delete_handler_<trigger_name>();
```

### Trigger Lifecycle

```mermaid
stateDiagram-v2
    [*] --> MigrationStarted: Migration Job Starts
    MigrationStarted --> TriggersCreated: Create Triggers
    TriggersCreated --> Active: Triggers Active
    Active --> Syncing: Data Changes Occur
    Syncing --> Active: Changes Synced
    Active --> MetadataUpdated: Update Metadata
    MetadataUpdated --> TriggersRemoved: Remove Triggers (Optional)
    TriggersRemoved --> [*]: Migration Complete
    
    Active --> Error: Trigger Failure
    Error --> Rollback: Rollback Metadata
    Rollback --> [*]
```

---

## Metadata Management

### Metadata Tables Structure

```mermaid
erDiagram
    base_reference_table ||--o{ base_column_map : "has columns"
    base_reference_table ||--o{ base_reference_table_bak : "backed up to"
    base_column_map ||--o{ base_column_map_bak : "backed up to"
    
    base_reference_table {
        bigint id PK
        varchar tbl_label
        varchar tbl_link
        text description
        integer version_no
        timestamp add_ts
        varchar add_usr
        timestamp upd_ts
        varchar upd_usr
    }
    
    base_column_map {
        bigint id PK
        bigint tbl_id FK
        varchar tbl_link
        varchar col_label
        varchar col_link
        varchar data_type
        integer version_no
        timestamp add_ts
        varchar add_usr
        timestamp upd_ts
        varchar upd_usr
    }
    
    base_reference_table_bak {
        bigint id PK
        varchar tbl_label
        varchar tbl_link
        timestamp backup_ts
    }
    
    base_column_map_bak {
        bigint id PK
        bigint tbl_id FK
        varchar col_link
        timestamp backup_ts
    }
```

### Metadata Versioning

```mermaid
flowchart TD
    Start([Metadata Update]) --> Backup[Backup Current Metadata<br/>to *_bak tables]
    Backup --> Increment[Increment version_no]
    Increment --> Update[Update Metadata]
    Update --> Cleanup{Backup Count > 5?}
    Cleanup -->|Yes| DeleteOld[Delete Oldest Backups]
    Cleanup -->|No| Complete
    DeleteOld --> Complete([Update Complete])
    
    style Backup fill:#fff4e1
    style Update fill:#e8f5e9
```

---

## Backup & Rollback

### Backup Strategy

```mermaid
flowchart TD
    Start([Migration Starts]) --> CheckMeta{Metadata Exists<br/>in Target?}
    CheckMeta -->|Yes| BackupTable[Backup base_reference_table<br/>to base_reference_table_bak]
    CheckMeta -->|No| SkipBackup
    
    BackupTable --> BackupColumns[Backup base_column_map<br/>to base_column_map_bak]
    BackupColumns --> Proceed[Proceed with Migration]
    SkipBackup --> Proceed
    
    Proceed --> Success{Migration<br/>Success?}
    Success -->|Yes| KeepBackup[Keep Backup for<br/>Rollback Capability]
    Success -->|No| Rollback[Restore from Backup]
    
    Rollback --> RestoreTable[Restore base_reference_table]
    RestoreTable --> RestoreColumns[Restore base_column_map]
    RestoreColumns --> Cleanup[Cleanup Shadow Table]
    Cleanup --> End([Rollback Complete])
    
    KeepBackup --> CleanupOld{Backup Count > 5?}
    CleanupOld -->|Yes| DeleteOld[Delete Oldest Backups]
    CleanupOld -->|No| End
    DeleteOld --> End
    
    style BackupTable fill:#fff4e1
    style Rollback fill:#ffebee
    style RestoreTable fill:#e8f5e9
```

### Rollback Process

1. **Restore Metadata**: Copy from `*_bak` tables back to main tables
2. **Update `tbl_link`**: Point back to original table name
3. **Restore Columns**: Restore column metadata
4. **Cleanup**: Remove shadow table and triggers
5. **Version Rollback**: Decrement `version_no`

---

## Error Handling

### Error Scenarios & Handling

```mermaid
flowchart TD
    Start([Migration Request]) --> Validate{Validation}
    Validate -->|Invalid Schema| Err1[Return 400 Bad Request]
    Validate -->|Table Not Found| Err2[Return 404 Not Found]
    Validate -->|Valid| CheckDB{Database<br/>Connection}
    
    CheckDB -->|Failed| Err3[Return 503 Service Unavailable]
    CheckDB -->|OK| Migrate[Start Migration]
    
    Migrate --> CreateTable{Create Table<br/>Success?}
    CreateTable -->|Failed| Err4[Rollback & Return 500]
    CreateTable -->|Success| CopyData{Copy Data<br/>Success?}
    
    CopyData -->|Failed| Err5[Rollback & Return 500]
    CopyData -->|Success| CreateTriggers{Create Triggers<br/>Success?}
    
    CreateTriggers -->|Failed| Err6[Rollback & Return 500]
    CreateTriggers -->|Success| UpdateMeta{Update Metadata<br/>Success?}
    
    UpdateMeta -->|Failed| Err7[Restore Backup & Return 500]
    UpdateMeta -->|Success| Success([Migration Complete])
    
    Err1 --> End([End])
    Err2 --> End
    Err3 --> End
    Err4 --> End
    Err5 --> End
    Err6 --> End
    Err7 --> End
    Success --> End
    
    style Err1 fill:#ffebee
    style Err2 fill:#ffebee
    style Err3 fill:#ffebee
    style Err4 fill:#ffebee
    style Err5 fill:#ffebee
    style Err6 fill:#ffebee
    style Err7 fill:#ffebee
    style Success fill:#e8f5e9
```

### Transaction Management

- **Migration is transactional**: All steps succeed or all rollback
- **Metadata updates are atomic**: Backup → Update → Commit
- **Trigger creation is idempotent**: Can be safely retried

---

## Performance Considerations

### Optimization Strategies

1. **Bulk Data Copy**: Uses PostgreSQL `INSERT INTO ... SELECT` for efficient bulk copy
2. **Asynchronous Processing**: Migration runs in background via JobRunr
3. **Trigger Efficiency**: Triggers use simple INSERT/DELETE operations
4. **Index Preservation**: Shadow table inherits indexes from source structure
5. **Connection Pooling**: Uses HikariCP for database connections

### Performance Impact

```mermaid
graph LR
    subgraph "During Migration"
        A[Application Writes] --> B[Existing Table]
        B --> C[Trigger Execution]
        C --> D[Shadow Table Write]
        D --> E[Additional Latency: ~1-5ms]
    end
    
    subgraph "After Migration"
        F[Application Writes] --> G[Shadow Table Direct]
        G --> H[No Trigger Overhead]
    end
    
    style E fill:#fff4e1
    style H fill:#e8f5e9
```

### Monitoring Points

- Migration job duration
- Trigger execution time
- Data copy throughput
- Metadata update latency
- Backup/restore performance

---

## API Reference

### Migration Endpoints

#### Trigger Migration
```
POST /api/schema/migration/tables/{tableId}/migrate?sourceSchema={source}&targetSchema={target}
```

**Response**: `202 Accepted`
```json
{
  "jobId": "019bde3d-b350-75ab-ac12-26e804982685",
  "status": "QUEUED",
  "message": "Migration job queued"
}
```

#### Get Migration Job Status
```
GET /api/schema/migration/jobs/{jobId}
```

**Response**: `200 OK`
```json
{
  "jobId": "019bde3d-b350-75ab-ac12-26e804982685",
  "status": "SUCCEEDED",
  "tableId": 82,
  "sourceSchema": "public",
  "targetSchema": "dmgr",
  "shadowTableName": "tbl_01ARZ3NDEKTSV4VV4VV4VV4VV4V",
  "startedAt": "2026-01-20T20:49:04Z",
  "completedAt": "2026-01-20T20:49:16Z",
  "duration": "12s"
}
```

---

## Conclusion

The migration architecture provides:

✅ **Zero-downtime migration** via shadow copy strategy  
✅ **Data consistency** through database triggers  
✅ **Rollback capability** via metadata backups  
✅ **Scalability** through asynchronous job processing  
✅ **Reliability** through comprehensive error handling  

This design enables seamless schema migrations while maintaining data integrity and application availability.
