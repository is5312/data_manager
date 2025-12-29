---
description: How to add a new React component to the frontend
---

# Adding a New Frontend Component

Follow these steps when creating new UI components for the Data Manager frontend.

## Design Principles
- **Dark Mode**: Use CSS variables from the design system
- **Glassmorphism**: Translucent backgrounds with backdrop blur
- **Premium Feel**: Gradients, smooth animations, rich aesthetics
- **No Placeholders**: Use actual data or generate assets

## Steps

### 1. Create Component File
**Location**: `data-manager-ui/src/components/ComponentName.tsx`

```typescript
import React from 'react';
import './ComponentName.css';

interface ComponentNameProps {
  // Define props
}

export const ComponentName: React.FC<ComponentNameProps> = ({ /* props */ }) => {
  return (
    <div className="component-wrapper">
      {/* Component content */}
    </div>
  );
};
```

### 2. Create Component Styles
**Location**: `data-manager-ui/src/components/ComponentName.css`

**Use the design system**:
```css
.component-wrapper {
  background: var(--card-bg);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 1rem;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.component-title {
  background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
```

**Available CSS Variables** (from LandingPage.css):
- `--bg-color`: Background color
- `--card-bg`: Card background (translucent)
- `--text-primary`: Primary text color
- `--text-secondary`: Secondary text color
- `--accent-color`: Accent color
- `--grid-border`: Grid border color

### 3. Add API Integration (if needed)
**Location**: `data-manager-ui/src/services/api.ts`

```typescript
export interface ComponentData {
  id: number;
  // ... other fields
}

export const fetchComponentData = async (): Promise<ComponentData[]> => {
  const response = await fetch(`${API_BASE_URL}/endpoint`);
  if (!response.ok) {
    throw new Error('Failed to fetch data');
  }
  return response.json();
};
```

### 4. Use in Parent Component
**Example**: Adding to `App.tsx`

```typescript
import { ComponentName } from './components/ComponentName';

function App() {
  return (
    <div>
      <ComponentName prop="value" />
    </div>
  );
}
```

### 5. Test the Component
The dev server auto-reloads. Check:
- Visual appearance matches design system
- API calls work correctly
- No console errors
- Responsive on different screen sizes

## AG Grid Integration

If using AG Grid in your component:

```typescript
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, ClientSideRowModelModule } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';

// Register modules (do this once per file)
ModuleRegistry.registerModules([ClientSideRowModelModule]);

export const GridComponent: React.FC = () => {
  const [rowData, setRowData] = useState([]);
  
  const colDefs = useMemo(() => [
    { field: 'id', headerName: 'ID' },
    { field: 'name', headerName: 'Name', flex: 1 },
  ], []);
  
  return (
    <div className="ag-theme-quartz-dark" style={{ height: 600 }}>
      <AgGridReact rowData={rowData} columnDefs={colDefs} />
    </div>
  );
};
```

## DuckDB WASM Integration

If using DuckDB for client-side operations:

```typescript
import { initDuckDB } from '../utils/duckdb';

useEffect(() => {
  initDuckDB().then(db => {
    // Use DuckDB instance
    const conn = await db.connect();
    // Run queries...
  });
}, []);
```

## Design System Guidelines

### Typography
```css
h1 {
  font-size: 3rem;
  font-weight: 700;
  background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
```

### Cards
```css
.card {
  background: var(--card-bg);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}
```

### Buttons
```css
.button {
  background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
  border: none;
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  color: white;
  cursor: pointer;
  transition: transform 0.2s;
}

.button:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(56, 189, 248, 0.3);
}
```

## Common Mistakes to Avoid

1. ❌ Using inline styles instead of CSS files
2. ❌ Not using CSS variables from design system
3. ❌ Forgetting AG Grid module registration
4. ❌ Not handling loading/error states
5. ❌ Creating plain, unstyled components (must be premium!)

## Example: Table Detail Component

```typescript
import React, { useEffect, useState } from 'react';
import { fetchColumns } from '../services/api';
import './TableDetail.css';

interface TableDetailProps {
  tableId: number;
  tableName: string;
}

export const TableDetail: React.FC<TableDetailProps> = ({ tableId, tableName }) => {
  const [columns, setColumns] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchColumns(tableId)
      .then(data => {
        setColumns(data);
        setLoading(false);
      })
      .catch(err => console.error(err));
  }, [tableId]);

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <div className="table-detail">
      <h2 className="table-title">{tableName}</h2>
      <div className="columns-list">
        {columns.map(col => (
          <div key={col.id} className="column-card">
            <span>{col.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
```

## Related Files
- `.agent/CONTEXT.md` - Architecture and design principles
- `LandingPage.tsx` - Example component with AG Grid
- `api.ts` - API client patterns
- `LandingPage.css` - Design system variables
