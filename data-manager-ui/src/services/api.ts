export interface TableMetadata {
    id: number;
    label: string;
    physicalName: string;
    description?: string;
    versionNo?: number;
    deploymentType?: string;
    createdAt?: string;
    createdBy?: string;
    updatedAt?: string;
    updatedBy?: string;
}

export interface ColumnMetadata {
    id: number;
    tableId: number;
    label: string;
    physicalName: string;
    tablePhysicalName: string;
    type?: string;
    description?: string;
    versionNo?: number;
    createdAt?: string;
    createdBy?: string;
    updatedAt?: string;
    updatedBy?: string;
}

export interface BatchUploadResponse {
    batchId: number;
    table: TableMetadata;
    message: string;
}

export interface BatchStatus {
    batchId: number;
    status: string;
    exitCode?: string;
    exitDescription?: string;
    startTime?: string;
    endTime?: string;
    readCount?: number;
    writeCount?: number;
    skipCount?: number;
    failureExceptions?: string[];
}

export interface MigrationResponse {
    status: string;
    message: string;
    shadowTableName?: string;
    targetSchema?: string;
    tableId?: number;
    details?: string;
}

export interface MigrationJobResponse {
    jobId: string;
    status: string;
    tableId: number;
    sourceSchema: string;
    targetSchema: string;
    message: string;
}

export interface JobDetails {
    jobId: string;
    status: 'ENQUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
    jobName: string;
    createdAt: string;
    updatedAt: string;
    result?: MigrationResponse;
    failureReason?: string;
}

export interface JobsListResponse {
    content: JobDetails[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface ActiveMigrationDto {
    hasActiveMigration: boolean;
    jobId?: string;
    status?: string;
}

const API_BASE_URL = '/api/schema';

/**
 * Standardized fetch wrapper with consistent error handling
 */
async function fetchWrapper<T>(
    url: string,
    options?: RequestInit,
    defaultErrorMessage?: string
): Promise<T> {
    try {
        const response = await fetch(url, options);

        if (!response.ok) {
            // Try to extract error message from response body
            let errorMessage = defaultErrorMessage || `Request failed with status ${response.status}`;

            try {
                const contentType = response.headers.get('content-type');
                if (contentType?.includes('application/json')) {
                    const errorData = await response.json();
                    errorMessage = errorData.message || errorData.error || errorMessage;
                } else {
                    const errorText = await response.text();
                    if (errorText) {
                        errorMessage = errorText;
                    }
                }
            } catch (parseError) {
                // If we can't parse the error, use the default message
                console.warn('Failed to parse error response:', parseError);
            }

            throw new Error(errorMessage);
        }

        // Handle void responses (e.g., DELETE)
        const contentType = response.headers.get('content-type');
        if (!contentType || response.status === 204) {
            return undefined as T;
        }

        // Parse JSON response
        if (contentType.includes('application/json')) {
            return await response.json();
        }

        // For other content types, return as text
        return (await response.text()) as T;
    } catch (error) {
        // Re-throw Error objects as-is
        if (error instanceof Error) {
            throw error;
        }
        // Wrap non-Error objects
        throw new Error(defaultErrorMessage || 'An unexpected error occurred');
    }
}

// Table Metadata Operations
export const fetchTables = async (schema?: string): Promise<TableMetadata[]> => {
    const url = schema 
        ? `${API_BASE_URL}/tables?schema=${encodeURIComponent(schema)}`
        : `${API_BASE_URL}/tables`;
    return fetchWrapper<TableMetadata[]>(
        url,
        undefined,
        'Failed to fetch tables'
    );
};

export const fetchTableById = async (id: number, schema?: string): Promise<TableMetadata> => {
    const url = schema 
        ? `${API_BASE_URL}/tables/${id}?schema=${encodeURIComponent(schema)}`
        : `${API_BASE_URL}/tables/${id}`;
    return fetchWrapper<TableMetadata>(
        url,
        undefined,
        'Failed to fetch table details'
    );
};

export const fetchTableColumns = async (tableId: number, schema?: string): Promise<ColumnMetadata[]> => {
    const url = schema
        ? `${API_BASE_URL}/tables/${tableId}/columns?schema=${encodeURIComponent(schema)}`
        : `${API_BASE_URL}/tables/${tableId}/columns`;
    return fetchWrapper<ColumnMetadata[]>(
        url,
        undefined,
        'Failed to fetch table columns'
    );
};

export const createTable = async (label: string, deploymentType: string = 'DESIGN_TIME'): Promise<TableMetadata> => {
    return fetchWrapper<TableMetadata>(
        `${API_BASE_URL}/tables?label=${encodeURIComponent(label)}&deploymentType=${encodeURIComponent(deploymentType)}`,
        { method: 'POST' },
        'Failed to create table'
    );
};

export const deleteTable = async (tableId: number): Promise<void> => {
    return fetchWrapper<void>(
        `${API_BASE_URL}/tables/${tableId}`,
        { method: 'DELETE' },
        'Failed to delete table'
    );
};

// CSV Upload Operations
export const uploadCsvTable = async (file: File, tableName: string): Promise<TableMetadata> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('tableName', tableName);

    return fetchWrapper<TableMetadata>(
        `${API_BASE_URL}/tables/upload`,
        { method: 'POST', body: formData },
        'Failed to upload CSV'
    );
};

export const uploadCsvTableWithTypes = async (
    file: File,
    tableName: string,
    columnTypes: string[]
): Promise<TableMetadata> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('tableName', tableName);
    formData.append('columnTypes', JSON.stringify(columnTypes));

    return fetchWrapper<TableMetadata>(
        `${API_BASE_URL}/tables/upload`,
        { method: 'POST', body: formData },
        'Failed to upload CSV with types'
    );
};

export const startBatchUpload = async (
    file: File,
    tableName: string,
    deploymentType: string = 'RUN_TIME',
    columnTypes?: string[],
    selectedColumnIndices?: number[],
    csvOptions?: { delimiter?: string; quoteChar?: string; escapeChar?: string }
): Promise<BatchUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('tableName', tableName);
    formData.append('deploymentType', deploymentType);
    if (columnTypes && columnTypes.length > 0) {
        formData.append('columnTypes', JSON.stringify(columnTypes));
    }
    if (selectedColumnIndices && selectedColumnIndices.length > 0) {
        formData.append('selectedColumnIndices', JSON.stringify(selectedColumnIndices));
    }
    if (csvOptions?.delimiter) formData.append('delimiter', csvOptions.delimiter);
    if (csvOptions?.quoteChar) formData.append('quoteChar', csvOptions.quoteChar);
    if (csvOptions?.escapeChar) formData.append('escapeChar', csvOptions.escapeChar);

    return fetchWrapper<BatchUploadResponse>(
        `${API_BASE_URL}/tables/upload/batch`,
        { method: 'POST', body: formData },
        'Failed to start batch upload'
    );
};

export const fetchBatchStatus = async (batchId: number): Promise<BatchStatus> => {
    return fetchWrapper<BatchStatus>(
        `${API_BASE_URL}/batches/${batchId}`,
        undefined,
        'Failed to fetch batch status'
    );
};

// Column Operations
export const addColumn = async (tableId: number, label: string, type: string): Promise<ColumnMetadata> => {
    return fetchWrapper<ColumnMetadata>(
        `${API_BASE_URL}/tables/${tableId}/columns?label=${encodeURIComponent(label)}&type=${encodeURIComponent(type)}`,
        { method: 'POST' },
        'Failed to add column'
    );
};

export const changeColumnType = async (tableId: number, columnId: number, type: string): Promise<ColumnMetadata> => {
    return fetchWrapper<ColumnMetadata>(
        `${API_BASE_URL}/tables/${tableId}/columns/${columnId}/type?type=${encodeURIComponent(type)}`,
        { method: 'PUT' },
        'Failed to change column type'
    );
};

// Data Operations
export const insertTableRow = async (tableId: number, rowData: Record<string, any>): Promise<{ id: number; message: string }> => {
    return fetchWrapper<{ id: number; message: string }>(
        `/api/data/tables/${tableId}/rows`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rowData),
        },
        'Failed to insert row'
    );
};

export const updateTableRow = async (tableId: number, rowId: number, rowData: Record<string, any>): Promise<{ message: string }> => {
    return fetchWrapper<{ message: string }>(
        `/api/data/tables/${tableId}/rows/${rowId}`,
        {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rowData),
        },
        'Failed to update row'
    );
};

export const deleteTableRow = async (tableId: number, rowId: number): Promise<void> => {
    return fetchWrapper<void>(
        `/api/data/tables/${tableId}/rows/${rowId}`,
        { method: 'DELETE' },
        'Failed to delete row'
    );
};

// Migration Operations
export const fetchAvailableSchemas = async (): Promise<string[]> => {
    return fetchWrapper<string[]>(
        `${API_BASE_URL}/migration/schemas`,
        undefined,
        'Failed to fetch available schemas'
    );
};

export const migrateTable = async (tableId: number, sourceSchema: string, targetSchema: string): Promise<MigrationJobResponse> => {
    return fetchWrapper<MigrationJobResponse>(
        `${API_BASE_URL}/migration/tables/${tableId}/migrate?sourceSchema=${encodeURIComponent(sourceSchema)}&targetSchema=${encodeURIComponent(targetSchema)}`,
        { method: 'POST' },
        'Failed to migrate table'
    );
};

export const fetchJobStatus = async (jobId: string): Promise<JobDetails> => {
    return fetchWrapper<JobDetails>(
        `${API_BASE_URL}/migration/jobs/${jobId}`,
        undefined,
        'Failed to fetch job status'
    );
};

export const fetchJobs = async (page = 0, size = 20, status?: string): Promise<JobsListResponse> => {
    const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
    });
    if (status) {
        params.append('status', status);
    }
    
    return fetchWrapper<JobsListResponse>(
        `${API_BASE_URL}/migration/jobs?${params.toString()}`,
        undefined,
        'Failed to fetch jobs list'
    );
};

export const checkActiveMigration = async (tableId: number, targetSchema: string): Promise<ActiveMigrationDto> => {
    return fetchWrapper<ActiveMigrationDto>(
        `${API_BASE_URL}/migration/tables/${tableId}/active?targetSchema=${encodeURIComponent(targetSchema)}`,
        undefined,
        'Failed to check for active migration'
    );
};
