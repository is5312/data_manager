import { http, HttpResponse } from 'msw';

export const handlers = [
  // Table operations
  http.get('/api/schema/tables', () => {
    return HttpResponse.json([
      {
        id: 1,
        label: 'Test Table',
        physicalName: 'test_table',
        description: 'Test description',
        versionNo: 1,
        deploymentType: 'DESIGN_TIME',
        createdAt: '2024-01-01T00:00:00Z',
        createdBy: 'test_user',
        updatedAt: '2024-01-01T00:00:00Z',
        updatedBy: 'test_user',
      },
    ]);
  }),

  http.get('/api/schema/tables/:id', ({ params }) => {
    return HttpResponse.json({
      id: Number(params.id),
      label: 'Test Table',
      physicalName: 'test_table',
      description: 'Test description',
      versionNo: 1,
      deploymentType: 'DESIGN_TIME',
      createdAt: '2024-01-01T00:00:00Z',
      createdBy: 'test_user',
      updatedAt: '2024-01-01T00:00:00Z',
      updatedBy: 'test_user',
    });
  }),

  http.get('/api/schema/tables/:id/columns', ({ params }) => {
    return HttpResponse.json([
      {
        id: 1,
        tableId: Number(params.id),
        label: 'Name',
        physicalName: 'name',
        tablePhysicalName: 'test_table',
        type: 'VARCHAR',
        versionNo: 1,
        createdAt: '2024-01-01T00:00:00Z',
        createdBy: 'test_user',
        updatedAt: '2024-01-01T00:00:00Z',
        updatedBy: 'test_user',
      },
    ]);
  }),

  http.post('/api/schema/tables', async ({ request }) => {
    const url = new URL(request.url);
    const label = url.searchParams.get('label');
    const deploymentType = url.searchParams.get('deploymentType') || 'DESIGN_TIME';
    return HttpResponse.json({
      id: 1,
      label: label || 'New Table',
      physicalName: 'new_table',
      description: '',
      versionNo: 1,
      deploymentType: deploymentType,
    });
  }),

  http.delete('/api/schema/tables/:id', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Column operations
  http.post('/api/schema/tables/:tableId/columns', async ({ request }) => {
    const url = new URL(request.url);
    const label = url.searchParams.get('label');
    const type = url.searchParams.get('type');
    return HttpResponse.json({
      id: 1,
      tableId: 1,
      label: label || 'New Column',
      physicalName: 'new_column',
      tablePhysicalName: 'test_table',
      type: type || 'VARCHAR',
      versionNo: 1,
    });
  }),

  http.put('/api/schema/tables/:tableId/columns/:columnId/type', () => {
    return HttpResponse.json({
      id: 1,
      tableId: 1,
      label: 'Name',
      physicalName: 'name',
      tablePhysicalName: 'test_table',
      type: 'INTEGER',
      versionNo: 1,
    });
  }),

  // Batch upload
  http.post('/api/schema/tables/upload/batch', async ({ request }) => {
    const formData = await request.formData();
    const tableName = formData.get('tableName') as string;
    return HttpResponse.json({
      batchId: 1,
      table: {
        id: 1,
        label: tableName,
        physicalName: tableName.toLowerCase().replace(/\s+/g, '_'),
        versionNo: 1,
      },
      message: 'Batch upload started',
    });
  }),

  http.get('/api/schema/batches/:batchId', ({ params }) => {
    return HttpResponse.json({
      batchId: Number(params.batchId),
      status: 'COMPLETED',
      exitCode: 'COMPLETED',
      exitDescription: 'Success',
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:01:00Z',
      readCount: 100,
      writeCount: 100,
      skipCount: 0,
      failureExceptions: [],
    });
  }),

  // Data operations
  http.get('/api/data/tables/:id/rows/arrow', () => {
    // Return empty response for Arrow stream (would need proper Arrow format in real implementation)
    return new HttpResponse(new ArrayBuffer(0), {
      headers: {
        'Content-Type': 'application/octet-stream',
        'x-total-rows': '0',
      },
    });
  }),

  http.post('/api/data/tables/:id/rows', async ({ request }) => {
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
  }),

  http.put('/api/data/tables/:id/rows/:rowId', async ({ request }) => {
    const body = await request.json();
    return HttpResponse.json({
      message: 'Row updated',
      ...body,
      add_usr: 'test_user',
      add_ts: '2024-01-01T00:00:00Z',
      upd_usr: 'test_user',
      upd_ts: new Date().toISOString(),
    });
  }),

  http.delete('/api/data/tables/:id/rows/:rowId', () => {
    return new HttpResponse(null, { status: 204 });
  }),
];

