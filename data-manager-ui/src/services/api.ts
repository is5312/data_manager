export interface TableMetadata {
    id: number;
    label: string;
    physicalName: string;
    description?: string;
    versionNo?: number;
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
export const fetchTables = async (): Promise<TableMetadata[]> => {
    return fetchWrapper<TableMetadata[]>(
        `${API_BASE_URL}/tables`,
        undefined,
        'Failed to fetch tables'
    );
};

export const fetchTableById = async (id: number): Promise<TableMetadata> => {
    return fetchWrapper<TableMetadata>(
        `${API_BASE_URL}/tables/${id}`,
        undefined,
        'Failed to fetch table details'
    );
};

export const fetchTableColumns = async (tableId: number): Promise<ColumnMetadata[]> => {
    return fetchWrapper<ColumnMetadata[]>(
        `${API_BASE_URL}/tables/${tableId}/columns`,
        undefined,
        'Failed to fetch table columns'
    );
};

export const createTable = async (label: string): Promise<TableMetadata> => {
    return fetchWrapper<TableMetadata>(
        `${API_BASE_URL}/tables?label=${encodeURIComponent(label)}`,
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
    columnTypes?: string[],
    selectedColumnIndices?: number[]
): Promise<BatchUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('tableName', tableName);
    if (columnTypes && columnTypes.length > 0) {
        formData.append('columnTypes', JSON.stringify(columnTypes));
    }
    if (selectedColumnIndices && selectedColumnIndices.length > 0) {
        formData.append('selectedColumnIndices', JSON.stringify(selectedColumnIndices));
    }

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
