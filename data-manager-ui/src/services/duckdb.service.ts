import * as duckdb from '@duckdb/duckdb-wasm';
import duckdb_wasm from '@duckdb/duckdb-wasm/dist/duckdb-mvp.wasm?url';
import duckdb_wasm_eh from '@duckdb/duckdb-wasm/dist/duckdb-eh.wasm?url';
import * as arrow from 'apache-arrow';
import { Kysely, PostgresAdapter, PostgresIntrospector, PostgresQueryCompiler, sql } from 'kysely';

// Define DB interface
// Since tables are dynamic, we use 'any' for the row type
export interface Database {
    [tableName: string]: any;
}

// Singleton instances
let dbInstance: duckdb.AsyncDuckDB | null = null;
let connection: duckdb.AsyncDuckDBConnection | null = null;
let initPromise: Promise<duckdb.AsyncDuckDB> | null = null;

// Initialize Kysely for SQL generation
// We use the Postgres dialect components because DuckDB's SQL syntax is very similar to Postgres
// This allows us to use the query builder without needing a full DuckDB driver implementation
const db = new Kysely<Database>({
    dialect: {
        createAdapter: () => new PostgresAdapter(),
        createDriver: () => ({
            init: async () => { },
            acquireConnection: async () => { throw new Error('Not implemented'); },
            beginTransaction: async () => { },
            commitTransaction: async () => { },
            rollbackTransaction: async () => { },
            releaseConnection: async () => { },
            destroy: async () => { }
        }),
        createIntrospector: (db) => new PostgresIntrospector(db),
        createQueryCompiler: () => new PostgresQueryCompiler(),
    },
});

// Track tables currently being loaded to prevent concurrent loads
const loadingTables = new Set<string>();

/**
 * Initialize DuckDB WASM instance (singleton)
 */
export async function initDuckDB(): Promise<duckdb.AsyncDuckDB> {
    if (dbInstance) return dbInstance;
    if (initPromise) return initPromise;

    initPromise = (async () => {
        console.time('⏱️ DuckDB initialization');

        const MANUAL_BUNDLES: duckdb.DuckDBBundles = {
            mvp: {
                mainModule: duckdb_wasm,
                mainWorker: new URL('@duckdb/duckdb-wasm/dist/duckdb-browser-mvp.worker.js', import.meta.url).toString(),
            },
            eh: {
                mainModule: duckdb_wasm_eh,
                mainWorker: new URL('@duckdb/duckdb-wasm/dist/duckdb-browser-eh.worker.js', import.meta.url).toString(),
            },
        };

        const bundle = await duckdb.selectBundle(MANUAL_BUNDLES);
        const worker = new Worker(bundle.mainWorker!);
        const logger = new duckdb.ConsoleLogger();

        const db = new duckdb.AsyncDuckDB(logger, worker);
        await db.instantiate(bundle.mainModule, bundle.pthreadWorker);

        console.timeEnd('⏱️ DuckDB initialization');
        console.log('✅ DuckDB initialized');

        dbInstance = db;
        return db;
    })();

    return initPromise;
}

/**
 * Get DuckDB connection (singleton)
 */
export async function getDuckDBConnection(): Promise<duckdb.AsyncDuckDBConnection> {
    if (connection) return connection;

    const db = await initDuckDB();
    connection = await db.connect();
    console.log('✅ DuckDB connection established');

    return connection;
}

/**
 * Progressive streaming callback type
 */
type ProgressCallback = (
    loaded: number,
    total: number | null,
    phase: 'downloading' | 'inserting' | 'complete',
    currentRows?: number
) => void;

/**
 * Load Arrow IPC data from backend into DuckDB table with progressive streaming
 * Shows UI after first batch, continues loading in background
 */
export async function loadTableDataIntoDuckDBArrow(
    tableId: number,
    tableName: string,
    onProgress?: ProgressCallback,
    schema?: string
): Promise<{ rowCount: number; columnCount: number }> {
    // Prevent concurrent loads of the same table
    if (loadingTables.has(tableName)) {
        console.log(`⚠️ Table ${tableName} is already being loaded, skipping duplicate request`);
        await new Promise(resolve => setTimeout(resolve, 500));
        const conn = await getDuckDBConnection();
        const stats = await getTableStats(conn, tableName);
        return stats;
    }

    loadingTables.add(tableName);

    try {
        console.time(`⏱️ Load table ${tableName} (Arrow streaming)`);

        const conn = await getDuckDBConnection();

        // Clean up old tables
        await cleanupOldTempTables(conn, tableName);

        // Drop existing table if present
        try {
            await conn.query(`DROP TABLE IF EXISTS "${tableName}"`);
        } catch (e) {
            console.warn('Drop table warning:', e);
        }

        // Download and insert Arrow stream progressively
        const stats = await downloadAndInsertArrowStream(tableId, tableName, conn, onProgress, schema);

        console.timeEnd(`⏱️ Load table ${tableName} (Arrow streaming)`);
        console.log(`✅ Loaded ${stats.rowCount.toLocaleString()} rows, ${stats.columnCount} columns`);

        if (onProgress) {
            onProgress(stats.rowCount, stats.rowCount, 'complete', stats.rowCount);
        }

        return stats;

    } catch (error) {
        console.error('❌ Failed to load Arrow data:', error);
        throw error;
    } finally {
        loadingTables.delete(tableName);
    }
}

/**
 * Download Arrow IPC stream and insert PROGRESSIVELY in chunks.
 * Uses low memory as it processes one batch at a time.
 */
async function downloadAndInsertArrowStream(
    tableId: number,
    tableName: string,
    conn: duckdb.AsyncDuckDBConnection,
    onProgress?: ProgressCallback,
    schema?: string
): Promise<{ rowCount: number; columnCount: number }> {
    const url = schema 
        ? `/api/data/tables/${tableId}/rows/arrow?schema=${encodeURIComponent(schema)}`
        : `/api/data/tables/${tableId}/rows/arrow`;
    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(`Failed to fetch Arrow data: ${response.statusText}`);
    }

    const contentLength = response.headers.get('content-length');
    const totalBytes = contentLength ? parseInt(contentLength) : null;

    // Read total rows from header
    const totalRowsHeader = response.headers.get('x-total-rows');
    const totalRows = totalRowsHeader ? parseInt(totalRowsHeader) : null;

    if (totalRows) {
        console.log(`📊 Total rows to load: ${totalRows.toLocaleString()}`);
    }

    if (!response.body) {
        throw new Error('Response body is not readable');
    }

    // Pass the web stream directly to Arrow's RecordBatchReader
    // This allows true streaming without buffering the whole file!
    const reader = await arrow.RecordBatchReader.from(response.body);

    let totalRowsInserted = 0;
    let batchNumber = 0;
    let columnCount = 0;
    let tableCreated = false;

    // Process each batch as it arrives from the network
    for await (const batch of reader) {
        const batchRowCount = batch.numRows;
        columnCount = batch.numCols;
        batchNumber++;

        // For empty batches, still create the table structure if not created yet
        if (batchRowCount === 0) {
            if (!tableCreated && columnCount > 0) {
                // Create empty table with schema from the batch
                const table = new arrow.Table(batch);
                const batchBuffer = arrow.tableToIPC(table);

                await conn.insertArrowFromIPCStream(batchBuffer, {
                    name: tableName,
                    create: true
                });
                tableCreated = true;
                console.log(`📋 Created empty table structure: ${columnCount} columns`);

                if (onProgress) {
                    onProgress(0, totalBytes, 'inserting', 0);
                }
            }
            continue;
        }

        // Write this single batch to an IPC buffer so DuckDB can read it
        // We create a temporary table with just this batch to serialize it
        const table = new arrow.Table(batch);
        const batchBuffer = arrow.tableToIPC(table);

        console.log(`🚀 Processing Batch ${batchNumber}: ${batchRowCount.toLocaleString()} rows`);

        try {
            if (!tableCreated) {
                // First batch: Create the table
                await conn.insertArrowFromIPCStream(batchBuffer, {
                    name: tableName,
                    create: true
                });
                tableCreated = true;

                // Notify UI immediately after first batch!
                if (onProgress) {
                    totalRowsInserted += batchRowCount;
                    onProgress(0, totalBytes, 'inserting', totalRowsInserted);
                }
            } else {
                // Subsequent batches: Append
                await conn.insertArrowFromIPCStream(batchBuffer, {
                    name: tableName,
                    create: false
                });

                totalRowsInserted += batchRowCount;
                if (onProgress) {
                    onProgress(0, totalBytes, 'inserting', totalRowsInserted);
                }
            }
        } catch (err) {
            console.error(`❌ Error inserting batch ${batchNumber}:`, err);
            throw err;
        }
    }

    console.log(`✅ Stream Complete. Total: ${totalRowsInserted.toLocaleString()} rows.`);
    return { rowCount: totalRowsInserted, columnCount };
}

/**
 * Get table statistics
 */
async function getTableStats(
    conn: duckdb.AsyncDuckDBConnection,
    tableName: string
): Promise<{ rowCount: number; columnCount: number }> {
    const countResult = await conn.query(`SELECT COUNT(*) as count FROM "${tableName}"`);
    const rowCount = Number(countResult.toArray()[0].count);

    const firstRow = await conn.query(`SELECT * FROM "${tableName}" LIMIT 1`);
    const columnCount = firstRow.schema.fields.length;

    return { rowCount, columnCount };
}

/**
 * Clean up old temporary tables (older than 5 minutes)
 */
async function cleanupOldTempTables(
    conn: duckdb.AsyncDuckDBConnection,
    baseTableName: string
): Promise<void> {
    try {
        const tempPrefix = `${baseTableName}_temp_`;
        const fiveMinutesAgo = Date.now() - 5 * 60 * 1000;

        const tables = await conn.query(`
            SELECT table_name 
            FROM information_schema.tables 
            WHERE table_schema = 'main' 
            AND table_name LIKE '${tempPrefix}%'
        `);

        for (const row of tables.toArray()) {
            const tblName = row.table_name as string;
            const timestampStr = tblName.replace(tempPrefix, '');
            const timestamp = parseInt(timestampStr);

            if (!isNaN(timestamp) && timestamp < fiveMinutesAgo) {
                await conn.query(`DROP TABLE IF EXISTS ${tblName}`);
                console.log(`🗑️ Cleaned up: ${tblName}`);
            }
        }
    } catch (error) {
        console.log('ℹ️ Temp table cleanup skipped');
    }
}

/**
 * Execute SQL query
 */
export async function queryDuckDB(sql: string): Promise<any[]> {
    const conn = await getDuckDBConnection();
    const result = await conn.query(sql);
    return result.toArray();
}

/**
 * Drop a table from DuckDB
 */
export async function dropTable(tableName: string): Promise<void> {
    const conn = await getDuckDBConnection();
    await conn.query(`DROP TABLE IF EXISTS "${tableName}"`);
    loadingTables.delete(tableName); // Ensure we clear loading state if it was stuck
    console.log(`🗑️ Dropped table: ${tableName}`);
}

/**
 * Check if table exists
 */
export async function tableExistsInDuckDB(tableName: string): Promise<boolean> {
    try {
        const conn = await getDuckDBConnection();
        const result = await conn.query(`
            SELECT COUNT(*) as count 
            FROM information_schema.tables 
            WHERE table_name = '${tableName}'
        `);
        return Number(result.toArray()[0].count) > 0;
    } catch {
        return false;
    }
}

/**
 * Get table schema
 */
export async function getTableSchema(tableName: string): Promise<Array<{ name: string; type: string }>> {
    const conn = await getDuckDBConnection();
    const result = await conn.query(`
        SELECT column_name as name, data_type as type 
        FROM information_schema.columns 
        WHERE table_name = '${tableName}'
        ORDER BY ordinal_position
    `);
    return result.toArray() as Array<{ name: string; type: string }>;
}

/**
 * Execute INSERT query for a single row
 */
export async function executeInsert(tableName: string, row: Record<string, any>): Promise<void> {
    const conn = await getDuckDBConnection();

    // Compile Kysely query to SQL string
    const compiledQuery = db
        .insertInto(tableName)
        .values(row)
        .compile();

    console.log('📝 Generated INSERT SQL:', compiledQuery.sql, compiledQuery.parameters);

    // Kysely uses parameters ($1, $2), but DuckDB WASM query() typically takes interpolated strings or prepared statements
    // We can use the helper below to interpolate safely or implement prepared statement support
    // For now, let's use a safe interpolation helper since we are doing 1 row
    const finalSql = interpolateSql(compiledQuery.sql, compiledQuery.parameters);

    await conn.query(finalSql);
}

/**
 * Execute UPDATE query for a single cell
 */
export async function executeUpdate(tableName: string, rowId: number, colName: string, value: any): Promise<void> {
    const conn = await getDuckDBConnection();

    const compiledQuery = db
        .updateTable(tableName)
        .set({ [colName]: value })
        .where('id', '=', rowId)
        .compile();

    console.log('📝 Generated UPDATE SQL:', compiledQuery.sql, compiledQuery.parameters);

    const finalSql = interpolateSql(compiledQuery.sql, compiledQuery.parameters);

    await conn.query(finalSql);
}

/**
 * Execute DELETE query for a single row
 */
export async function executeDelete(tableName: string, rowId: number): Promise<void> {
    const conn = await getDuckDBConnection();

    const compiledQuery = db
        .deleteFrom(tableName)
        .where('id', '=', rowId)
        .compile();

    console.log('📝 Generated DELETE SQL:', compiledQuery.sql, compiledQuery.parameters);

    const finalSql = interpolateSql(compiledQuery.sql, compiledQuery.parameters);

    await conn.query(finalSql);
}

/**
 * Helper to interpolate Kysely parameters into SQL string for DuckDB WASM
 * Note: Real prepared statements are better, but this handles types safely for simple queries
 */
function interpolateSql(sqlStr: string, params: readonly unknown[]): string {
    let i = 0;
    return sqlStr.replace(/\$(\d+)/g, () => {
        const val = params[i++];
        if (val === null || val === undefined) return 'NULL';
        if (typeof val === 'string') return `'${val.replace(/'/g, "''")}'`;
        if (typeof val === 'boolean') return val ? 'TRUE' : 'FALSE';
        if (val instanceof Date) return `'${val.toISOString()}'`;
        return String(val);
    });
}
