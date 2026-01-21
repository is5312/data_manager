import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { server } from '../../__tests__/msw-setup';
import { http, HttpResponse } from 'msw';
import {
  fetchTables,
  fetchTableById,
  fetchTableColumns,
  createTable,
  deleteTable,
  startBatchUpload,
  fetchBatchStatus,
  addColumn,
  changeColumnType,
  insertTableRow,
  updateTableRow,
  deleteTableRow,
} from '../api';

describe('API Service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('fetchWrapper', () => {
    it('handles successful responses', async () => {
      const tables = await fetchTables();
      expect(tables).toBeInstanceOf(Array);
      expect(tables.length).toBeGreaterThan(0);
    });

    it('handles JSON error responses', async () => {
      server.use(
        http.get('/api/schema/tables', () => {
          return HttpResponse.json({ message: 'Server error' }, { status: 500 });
        })
      );

      await expect(fetchTables()).rejects.toThrow();
      // Error message should contain "Server error" or status code
      try {
        await fetchTables();
      } catch (error: any) {
        expect(error.message).toMatch(/Server error|500/);
      }
    });

    it('handles text error responses', async () => {
      server.use(
        http.get('/api/schema/tables', () => {
          return new HttpResponse('Error occurred', { 
            status: 500,
            headers: { 'Content-Type': 'text/plain' }
          });
        })
      );

      await expect(fetchTables()).rejects.toThrow();
      try {
        await fetchTables();
      } catch (error: any) {
        expect(error.message).toMatch(/Error occurred|500/);
      }
    });

    it('handles 204 No Content', async () => {
      server.use(
        http.delete('/api/schema/tables/1', () => {
          return new HttpResponse(null, { status: 204 });
        })
      );

      const result = await deleteTable(1);
      expect(result).toBeUndefined();
    });

    it('handles network errors', async () => {
      server.use(
        http.get('/api/schema/tables', () => {
          return HttpResponse.error();
        })
      );

      await expect(fetchTables()).rejects.toThrow();
      // Network errors should throw an error
      try {
        await fetchTables();
      } catch (error: any) {
        expect(error).toBeDefined();
      }
    });
  });

  describe('Table Operations', () => {
    it('fetchTables returns table list', async () => {
      const tables = await fetchTables();
      expect(Array.isArray(tables)).toBe(true);
    });

    it('fetchTableById returns table metadata', async () => {
      const table = await fetchTableById(1);
      expect(table).toHaveProperty('id');
      expect(table).toHaveProperty('label');
      expect(table).toHaveProperty('physicalName');
    });

    it('fetchTableColumns returns column list', async () => {
      const columns = await fetchTableColumns(1);
      expect(Array.isArray(columns)).toBe(true);
    });

    it('createTable creates table', async () => {
      server.use(
        http.post('/api/schema/tables', async ({ request }) => {
          const url = new URL(request.url);
          const label = url.searchParams.get('label');
          const deploymentType = url.searchParams.get('deploymentType') || 'DESIGN_TIME';
          return HttpResponse.json({
            id: 2,
            label: label || 'New Table',
            physicalName: 'new_table',
            versionNo: 1,
            deploymentType: deploymentType,
          });
        })
      );

      const table = await createTable('New Table');
      expect(table.label).toBe('New Table');
      expect(table.deploymentType).toBe('DESIGN_TIME');
    });

    it('deleteTable deletes table', async () => {
      server.use(
        http.delete('/api/schema/tables/1', () => {
          return new HttpResponse(null, { status: 204 });
        })
      );

      await expect(deleteTable(1)).resolves.toBeUndefined();
    });
  });

  describe('CSV Upload', () => {
    it('startBatchUpload starts batch job', async () => {
      const file = new File(['name,age\nJohn,30'], 'test.csv', { type: 'text/csv' });
      const response = await startBatchUpload(file, 'test_table');

      expect(response).toHaveProperty('batchId');
      expect(response).toHaveProperty('table');
    });

    it('fetchBatchStatus returns batch status', async () => {
      const status = await fetchBatchStatus(1);
      expect(status).toHaveProperty('batchId');
      expect(status).toHaveProperty('status');
    });
  });

  describe('Column Operations', () => {
    it('addColumn adds column', async () => {
      server.use(
        http.post('/api/schema/tables/1/columns', () => {
          return HttpResponse.json({
            id: 2,
            tableId: 1,
            label: 'New Column',
            physicalName: 'new_column',
            tablePhysicalName: 'test_table',
            type: 'INTEGER',
            versionNo: 1,
          });
        })
      );

      const column = await addColumn(1, 'New Column', 'INTEGER');
      expect(column.label).toBe('New Column');
      expect(column.type).toBe('INTEGER');
    });

    it('changeColumnType changes type', async () => {
      server.use(
        http.put('/api/schema/tables/1/columns/1/type', () => {
          return HttpResponse.json({
            id: 1,
            tableId: 1,
            label: 'Name',
            physicalName: 'name',
            tablePhysicalName: 'test_table',
            type: 'INTEGER',
            versionNo: 1,
          });
        })
      );

      const column = await changeColumnType(1, 1, 'INTEGER');
      expect(column.type).toBe('INTEGER');
    });
  });

  describe('Data Operations', () => {
    it('insertTableRow inserts row', async () => {
      server.use(
        http.post('/api/data/tables/1/rows', async ({ request }) => {
          const body = await request.json();
          return HttpResponse.json({
            id: 1,
            message: 'Row inserted',
            ...body,
            add_usr: 'test_user',
            add_ts: new Date().toISOString(),
            upd_usr: 'test_user',
            upd_ts: new Date().toISOString(),
          });
        })
      );

      const result = await insertTableRow(1, { name: 'Test' });
      expect(result).toHaveProperty('id');
      expect(result).toHaveProperty('message');
    });

    it('updateTableRow updates row', async () => {
      server.use(
        http.put('/api/data/tables/1/rows/1', async ({ request }) => {
          const body = await request.json();
          return HttpResponse.json({
            message: 'Row updated',
            ...body,
            add_usr: 'test_user',
            add_ts: '2024-01-01T00:00:00Z',
            upd_usr: 'test_user',
            upd_ts: new Date().toISOString(),
          });
        })
      );

      const result = await updateTableRow(1, 1, { name: 'Updated' });
      expect(result).toHaveProperty('message');
    });

    it('deleteTableRow deletes row', async () => {
      server.use(
        http.delete('/api/data/tables/1/rows/1', () => {
          return new HttpResponse(null, { status: 204 });
        })
      );

      await expect(deleteTableRow(1, 1)).resolves.toBeUndefined();
    });
  });
});

