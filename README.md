# Data Manager - Baserow-like Microfrontend Application

A dynamic data management system with a Baserow-like metadata architecture, built as a microfrontend application.

## 🚀 Quick Start

### Prerequisites
- **Java 21** (LTS)
- **Node.js 18+** & npm
- **PostgreSQL** (via Docker)
- **Docker** running with Postgres container

### 1. Start PostgreSQL (if not running)
```bash
docker run -d \
  --name postgres_container \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 \
  postgres:18
```

### 2. Start Backend
```bash
cd /Users/jeffthampy/antigravity_workspace/data_manager
./gradlew :data-manager-backend:bootRun
```
Backend will run on: **http://localhost:8080**

### 3. Start Frontend
```bash
cd data-manager-ui
npm run dev
```
Frontend will run on: **http://localhost:5173**

## 🎯 Features

### Current Implementation
- ✅ **Dynamic Table Creation**: Metadata-driven table management with UUID-based physical tables
- ✅ **Dynamic Column Creation**: Add columns with type validation
- ✅ **REST API**: Full CRUD endpoints for table and column management
- ✅ **Modern UI**: React + TypeScript + AG Grid with dark theme
- ✅ **DuckDB WASM**: High-performance client-side SQL engine for:
  - Local data caching and querying
  - Instant sorting/filtering without backend hits
  - Live row counts for accurate pagination
- ✅ **Table Data Editing**: In-memory editing with inserts, updates, and deletes
- ✅ **Audit Columns**: Automatic tracking of `add_usr`, `add_ts`, `upd_usr`, `upd_ts`
- ✅ **Pending Changes**: Visual feedback for unsaved changes (Green/Yellow/Red)
- ✅ **CORS Support**: Frontend-backend communication configured

### Architecture Highlights
- **Physical Tables**: `tbl_<uuid>` (e.g., `tbl_a1b2c3d4`)
  - Includes audit columns: `add_usr`, `add_ts`, `upd_usr`, `upd_ts`
- **Physical Columns**: `col_<uuid>` (e.g., `col_m3n4o5p6`)
- **Logical Layer**: Stored in `base_reference_table` and `base_column_map`
- **User Experience**: Users only see logical names (e.g., "Customers", "Email")

## 📁 Project Structure

```
data_manager/
├── data-manager-backend/       # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/datamanager/backend/
│   │       ├── schema/         # Table/column management
│   │       ├── dao/            # Dynamic SQL (JOOQ)
│   │       └── config/         # CORS & config
│   └── src/main/resources/
│       ├── application.yml     # Database config
│       ├── schema.sql          # Metadata tables
│       └── data.sql            # Seed data
├── data-manager-ui/            # React microfrontend
│   ├── src/
│   │   ├── components/         # LandingPage with AG Grid
│   │   ├── services/           # API client
│   │   ├── hooks/              # Data mutation logic
│   │   └── utils/              # DuckDB WASM setup
│   └── vite.config.ts          # Proxy & CORS headers
└── ARCHITECTURE.md             # Detailed architecture docs
```

## 🛠️ Tech Stack

### Backend
- **Framework**: Spring Boot 3.4.1
- **Language**: Java 21 (LTS)
- **Database**: PostgreSQL 18.1
- **Build**: Gradle 9.2.1

### Frontend
- **Framework**: React 18
- **Language**: TypeScript 5
- **Build**: Vite 5
- **Grid**: AG Grid Community
- **Database**: DuckDB WASM
- **Styling**: Vanilla CSS with glassmorphism

## 📊 API Endpoints

### Tables
- `GET /api/schema/tables` - List all tables
- `POST /api/schema/tables?label=<name>` - Create table
- `POST /api/schema/tables/{id}/columns?label=<name>&type=<type>` - Add column

### Example Response
```json
[
  {
    "id": 1,
    "label": "Customers",
    "physicalName": "tbl_a1b2c3d4",
    "description": "Customer records",
    "versionNo": 1
  }
]
```

## 🗄️ Seed Data

The application comes with 3 pre-configured tables:

1. **Customers** (`tbl_a1b2c3d4`)
   - Name, Email, Phone

2. **Orders** (`tbl_e5f6g7h8`)
   - Customer ID, Total Amount, Order Date

3. **Products** (`tbl_i9j0k1l2`)
   - Product Name, Description, Price

## 🔧 Configuration

### Database (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/datamanager
    username: postgres
    password: changeme
```

### Frontend Proxy (vite.config.ts)
```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  }
}
```

## 📖 Documentation

For detailed architecture information, see [ARCHITECTURE.md](./ARCHITECTURE.md)

## 🎨 Design Philosophy

The UI follows modern web design best practices:
- **Dark Mode**: Sleek, eye-friendly color palette
- **Glassmorphism**: Translucent cards with backdrop blur
- **Micro-animations**: Smooth hover effects and transitions
- **Premium Feel**: Gradient typography and rich aesthetics

## 🚧 Next Steps

- [x] Implement table data editing (rows/cells)
- [x] Integrate DuckDB for client-side analytics
- [x] Add pagination and filtering to the grid interface
- [x] Implement table deletion functionality
- [ ] Implement column management for existing tables (add/remove/edit)
- [ ] Implement user authentication

## 📝 License

This is a demonstration project.
