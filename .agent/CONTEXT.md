# Project Context & Architecture

## Project Overview
**Name**: Data Manager  
**Type**: Baserow-like metadata-driven data management system  
**Architecture**: Microfrontend (React + Spring Boot)

## Core Architecture Principles

### 1. **Two-Layer Table System**
- **Physical Layer**: Actual PostgreSQL tables with UUID-based names
  - Pattern: `tbl_<uuid>` (e.g., `tbl_a1b2c3d4`)
  - Columns: `col_<uuid>` (e.g., `col_m3n4o5p6`)
  
- **Metadata Layer**: User-facing logical names
  - `base_reference_table`: Maps logical table names → physical table names
  - `base_column_map`: Maps logical column names → physical column names

### 2. **User Experience**
- Users ONLY see logical names (e.g., "Customers", "Email")
- Physical names are completely abstracted away
- Similar to Baserow's data editing features

### 3. **UUID Generation**
- Tables: Generated via `tbl_<timestamp>_<uuid>` in `MetadataService`
- Columns: Generated via `col_<timestamp>` in `MetadataService`
- All identifiers are sanitized via `SchemaHelpers.sanitizeIdentifier()`

### 4. Client-Side Change Buffering
- **Pending Changes**: Edits (Inserts, Updates, Deletes) are tracked in memory using Zustand store
- **Optimistic UI**: Changes are reflected immediately in the grid
- **Safety**: Hard limit of 50 pending changes prevents memory issues
- **Commit**: Batch save operation persists changes to backend in one go

### 5. Frontend Optimizations
- **Smart Refresh**: `TableDataView` queries live row counts from DuckDB (`SELECT COUNT(*)`) to ensure new rows appear immediately.
- **Efficient Caching**: `batchSaveChanges` uses `purge: true` to force grid refresh from local DuckDB (fast) instead of redundant backend fetches.
- **Backend Sync**: `insertRow`/`updateRow` return full row data (including audit columns) to avoid extra round-trips.

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.4.1
- **Java Version**: 21 (LTS) - DO NOT use Java 25 (causes ASM compatibility issues)
- **Database**: PostgreSQL 18.1 (Docker)
- **Credentials**: postgres / changeme
- **Database Name**: datamanager
- **Build Tool**: Gradle 9.2.1

### Frontend
- **Framework**: React 18.2 + TypeScript 5.2
- **Build Tool**: Vite 5.2
- **UI Library**: Material UI (MUI) v5 - Dark theme with customization
- **State Management**: Zustand + Immer (Change buffering)
- **Data Grid**: AG Grid Community (requires ModuleRegistry.registerModules)
- **Client DB**: DuckDB WASM (Primary data source for grid)
- **Styling**: MUI theming + custom theme with dark mode, glassmorphism, gradients

### Frontend-Backend Communication
- **Proxy**: Vite proxies `/api/*` to `http://localhost:8080`
- **CORS**: Configured in `WebConfig.java` for localhost:5173, localhost:3000
- **API Base**: `/api/schema`
- **Protocol**: REST + JSON

## Key Design Decisions

### Decision 1: Flyway → Spring SQL Init
**Reason**: Flyway 10.x doesn't support PostgreSQL 18.1 yet  
**Solution**: Using `schema.sql` + `data.sql` with Spring Boot SQL initialization  
**Config**: `spring.sql.init.mode=always`

### Decision 2: Java 21 (not 25)
**Reason**: Spring Boot 3.4.1's ASM doesn't support Java 25 bytecode (class version 69)  
**Solution**: Forced Java 21 via Gradle toolchain in root `build.gradle`  
**Impact**: All subprojects must use Java 21

### Decision 3: AG Grid Module Registration
**Reason**: AG Grid v32+ requires explicit module registration  
**Solution**: `ModuleRegistry.registerModules([ClientSideRowModelModule])`  
**Location**: Top of `LandingPage.tsx`

### Decision 4: UUID Naming Convention
**Reason**: Avoid identifier conflicts and enable dynamic schema changes  
**Pattern**: 
- Tables: `tbl_<timestamp>_<uuid_fragment>`
- Columns: `col_<timestamp>`

### Decision 5: Material UI (MUI) for UI Components
**Reason**: Provides a comprehensive, accessible component library with excellent TypeScript support  
**Solution**: Using MUI v5 with custom dark theme, integrating with AG Grid for data tables  
**Impact**: Consistent component API, built-in theming, responsive design out of the box  
**Theme**: Dark mode with glassmorphism effects, custom color palette (blues/purples)

### Decision 6: Local DuckDB as Source of Truth for Grid
**Reason**: To enable instant filtering, sorting, and pagination without hitting backend repeatedly.
**Solution**: 
- Load data from backend -> Insert into DuckDB
- Grid queries DuckDB via Server-Side Row Model (SSRM)
- On Save: Update Backend -> Update DuckDB -> Refresh Grid (from DuckDB)

## Critical Files

### Backend
- `SchemaController.java`: REST endpoints for table/column operations
- `MetadataService.java`: Business logic, UUID generation, transaction coordination
- `SchemaRepository.java`: DDL operations on physical tables
- `SchemaHelpers.java`: SQL identifier sanitization
- `application.yml`: Database config (postgres/changeme)
- `DataDaoImpl.java`: Dynamic CRUD operations with optimized SQL

### Frontend
- `LandingPage.tsx`: Main UI with AG Grid
- `TableDataView.tsx`: Data grid logic, SSRM datasource, DuckDB queries
- `useTableMutations.ts`: Optimized batch saving and refresh logic
- `api.ts`: REST client
- `duckdb.ts`: DuckDB WASM initialization
- `vite.config.ts`: Proxy + COOP/COEP headers for DuckDB

### Database
- `schema.sql`: Metadata tables (base_reference_table, base_column_map) with audit columns
- `data.sql`: Seed data (3 tables: Customers, Orders, Products)
- Physical Tables: `tbl_xxx` with audit columns (`add_usr`, `add_ts`, `upd_usr`, `upd_ts`)

## Development Workflows

### Starting the Application
1. Ensure Docker Postgres container is running on port 5432
2. Backend: `./gradlew :data-manager-backend:bootRun` (from project root)
3. Frontend: `npm run dev` (from data-manager-ui)

### Adding a New Table (via API)
```bash
POST /api/schema/tables?label=TableName
```
Backend will:
1. Generate `tbl_<uuid>`
2. Create physical table with `id`, `created_at` columns
3. Insert metadata into `base_reference_table`

### Adding a Column (via API)
```bash
POST /api/schema/tables/{tableId}/columns?label=ColumnName&type=VARCHAR(255)
```
Backend will:
1. Look up physical table name from metadata
2. Generate `col_<uuid>`
3. Execute `ALTER TABLE ADD COLUMN`
4. Insert metadata into `base_column_map`

## Common Pitfalls

### ❌ DON'T
- Use Java 25 (Spring Boot incompatible)
- Enable Flyway with PostgreSQL 18.1
- Use physical table/column names in user-facing code
- Forget AG Grid module registration
- Skip identifier sanitization

### ✅ DO
- Always use Java 21 toolchain
- Use Spring SQL init for schema management
- Abstract physical names via metadata layer
- Register AG Grid modules before use
- Sanitize all SQL identifiers

## Future Enhancements (Backlog)
- [ ] Table data editing (CRUD on rows)
- [ ] Column type management UI
- [ ] DuckDB integration for client-side analytics
- [ ] Table/column deletion with cascade
- [ ] User authentication & multi-tenancy
- [ ] Export/import functionality

## Questions to Ask Me
When working on this project, always consider:
1. Does this change respect the two-layer architecture?
2. Are physical names properly abstracted?
3. Is SQL injection prevented via sanitization?
4. Are transactions used for metadata + physical changes?
5. Does the UI follow the glassmorphism design system?
