# Data Manager - Baserow-like Architecture

## Overview
This application implements a Baserow-like metadata-driven data management system where users can dynamically create tables and columns through the UI.

## Architecture

### Two-Layer Table System

#### 1. Metadata Layer (Logical Tables)
- **Table**: `base_reference_table`
  - `id`: Unique identifier for the logical table
  - `tbl_label`: User-friendly table name (e.g., "Customers", "Orders")
  - `tbl_link`: Physical table name reference (e.g., "tbl_a1b2c3d4")
  - Timestamps and user tracking fields

- **Table**: `base_column_map`
  - `id`: Unique identifier for the column mapping
  - `tbl_id`: Foreign key to `base_reference_table.id`
  - `tbl_link`: Physical table name (denormalized for quick lookup)
  - `col_label`: User-friendly column name (e.g., "Name", "Email")
  - `col_link`: Physical column name (e.g., "col_m3n4o5p6")
  - Timestamps and user tracking fields

#### 2. Physical Layer (Actual Tables)
- **Naming Convention**: `tbl_<uuid>` 
  - Example: `tbl_a1b2c3d4`, `tbl_e5f6g7h8`
  - Each physical table has:
    - `id`: SERIAL PRIMARY KEY
    - `created_at`: Auto-timestamp
    - Dynamic columns with names like `col_<uuid>`

- **Column Naming Convention**: `col_<uuid>`
  - Example: `col_m3n4o5p6`, `col_q7r8s9t0`
  - User never sees these physical names

## Data Flow

### Creating a Table (via UI)
1. User provides logical table name: "Customers"
2. Backend generates physical table name: `tbl_<uuid>`
3. Physical table is created with base columns (id, created_at)
4. Metadata is stored in `base_reference_table`:
   ```
   id=1, tbl_label="Customers", tbl_link="tbl_a1b2c3d4"
   ```

### Adding a Column (via UI)
1. User selects table "Customers" and adds column "Email" of type VARCHAR(255)
2. Backend generates physical column name: `col_<uuid>`
3. Physical column is added to `tbl_a1b2c3d4`:
   ```sql
   ALTER TABLE tbl_a1b2c3d4 ADD COLUMN col_q7r8s9t0 VARCHAR(255);
   ```
4. Metadata is stored in `base_column_map`:
   ```
   tbl_id=1, tbl_link="tbl_a1b2c3d4", 
   col_label="Email", col_link="col_q7r8s9t0"
   ```

## Current Implementation

### Backend Components
- **SchemaController**: REST endpoints for table/column management
  - `GET /api/schema/tables` - List all tables
  - `POST /api/schema/tables` - Create new table
  - `POST /api/schema/tables/{id}/columns` - Add column to table

- **MetadataService**: Business logic for metadata operations
  - Generates UUID-based physical names
  - Coordinates metadata and physical schema changes
  - Uses transactions to ensure consistency

- **SchemaRepository**: DDL operations on physical layer
  - Creates physical tables
  - Adds/removes columns
  - Sanitizes identifiers for SQL injection protection

### Frontend Components (Microfrontend - React + TypeScript)
- **LandingPage**: Displays all available tables using AG Grid
- **API Service**: Fetches table metadata from backend
- **DuckDB WASM**: Initialized for future client-side data operations

## Tech Stack
- **Backend**: Spring Boot, PostgreSQL, JDBC
- **Frontend**: React, TypeScript, Vite, AG Grid, DuckDB WASM
- **Database**: PostgreSQL 18.1 (Docker)

## Example Seed Data
The system currently has 3 pre-seeded tables:

1. **Customers** (tbl_a1b2c3d4)
   - Name (col_m3n4o5p6)
   - Email (col_q7r8s9t0)
   - Phone (col_u1v2w3x4)

2. **Orders** (tbl_e5f6g7h8)
   - Customer ID (col_y5z6a7b8)
   - Total Amount (col_c9d0e1f2)
   - Order Date (col_g3h4i5j6)

3. **Products** (tbl_i9j0k1l2)
   - Product Name (col_k7l8m9n0)
   - Description (col_o1p2q3r4)
   - Price (col_s5t6u7v8)

## Running the Application

### Backend
```bash
cd /Users/jeffthampy/antigravity_workspace/data_manager
./gradlew :data-manager-backend:bootRun
```
Backend runs on: http://localhost:8080

### Frontend
```bash
cd data-manager-ui
npm run dev
```
Frontend runs on: http://localhost:5173

The frontend proxies `/api/*` requests to the backend automatically.
