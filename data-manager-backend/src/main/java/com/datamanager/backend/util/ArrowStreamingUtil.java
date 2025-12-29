package com.datamanager.backend.util;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for converting JDBC ResultSet to Apache Arrow IPC Stream format.
 * Provides efficient binary streaming of database results to frontend.
 */
public class ArrowStreamingUtil {

    private static final Logger log = LoggerFactory.getLogger(ArrowStreamingUtil.class);
    private static final int FIRST_BATCH_SIZE = 10000; // Rows for first batch (quick initial display)
    private static final int SUBSEQUENT_BATCH_SIZE = 50000; // Rows for subsequent batches (efficient bulk loading)

    /**
     * Stream a JDBC ResultSet to an OutputStream in Arrow IPC format.
     * Automatically handles batching and memory management.
     * Uses 10,000 rows for the first batch and 50,000 rows for subsequent batches.
     *
     * @param resultSet    The JDBC ResultSet to stream
     * @param outputStream The output stream to write Arrow data to
     * @return Number of rows streamed
     * @throws SQLException If database error occurs
     * @throws IOException  If I/O error occurs
     */
    public static long streamResultSetAsArrow(ResultSet resultSet, OutputStream outputStream)
            throws SQLException, IOException {

        long startTime = System.currentTimeMillis();
        long totalRows = 0;

        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {

            // Build Arrow schema from ResultSet metadata
            ResultSetMetaData metaData = resultSet.getMetaData();
            Schema schema = buildArrowSchema(metaData);

            log.info("Created Arrow schema with {} columns", schema.getFields().size());

            // Create vector schema root for batching
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
                    ArrowStreamWriter writer = new ArrowStreamWriter(root, null, outputStream)) {

                writer.start();

                List<FieldVector> vectors = root.getFieldVectors();
                int batchRowCount = 0;
                int batchNumber = 0;

                // Process ResultSet in batches
                while (resultSet.next()) {

                    // Write row data to vectors
                    for (int i = 0; i < vectors.size(); i++) {
                        int columnIndex = i + 1; // JDBC is 1-indexed
                        writeValueToVector(vectors.get(i), batchRowCount, resultSet, columnIndex);
                    }

                    batchRowCount++;
                    totalRows++;

                    // Determine current batch size: first batch uses smaller size for quick initial
                    // display
                    int currentBatchSize = (batchNumber == 0) ? FIRST_BATCH_SIZE : SUBSEQUENT_BATCH_SIZE;

                    // Write batch when full
                    if (batchRowCount >= currentBatchSize) {
                        root.setRowCount(batchRowCount);
                        writer.writeBatch();

                        log.debug("Wrote Arrow batch #{}: {} rows (total: {})", batchNumber + 1, batchRowCount,
                                totalRows);

                        // Clear vectors for next batch
                        for (FieldVector vector : vectors) {
                            vector.clear();
                        }
                        batchRowCount = 0;
                        batchNumber++;
                    }
                }

                // Write final partial batch
                if (batchRowCount > 0) {
                    root.setRowCount(batchRowCount);
                    writer.writeBatch();
                    log.debug("Wrote final Arrow batch #{}: {} rows", batchNumber + 1, batchRowCount);
                }

                writer.end();

                long duration = System.currentTimeMillis() - startTime;
                log.info("Streamed {} rows in Arrow format in {}ms ({} rows/sec)",
                        totalRows, duration, totalRows * 1000 / Math.max(1, duration));
            }
        }

        return totalRows;
    }

    /**
     * Build an Arrow Schema from JDBC ResultSet metadata
     */
    private static Schema buildArrowSchema(ResultSetMetaData metaData) throws SQLException {
        List<Field> fields = new ArrayList<>();

        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnName(i);
            int sqlType = metaData.getColumnType(i);

            ArrowType arrowType = mapSqlTypeToArrow(sqlType, metaData, i);
            Field field = new Field(columnName, FieldType.nullable(arrowType), null);
            fields.add(field);
        }

        return new Schema(fields);
    }

    /**
     * Map SQL types to Arrow types
     */
    private static ArrowType mapSqlTypeToArrow(int sqlType, ResultSetMetaData metaData, int columnIndex)
            throws SQLException {

        return switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> new ArrowType.Bool();

            case Types.TINYINT -> new ArrowType.Int(8, true);
            case Types.SMALLINT -> new ArrowType.Int(16, true);
            case Types.INTEGER -> new ArrowType.Int(32, true);
            case Types.BIGINT -> new ArrowType.Int(64, true);

            case Types.REAL -> new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
            case Types.FLOAT, Types.DOUBLE -> new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);

            case Types.DECIMAL, Types.NUMERIC -> {
                int precision = metaData.getPrecision(columnIndex);
                int scale = metaData.getScale(columnIndex);
                yield new ArrowType.Decimal(precision, scale, 128);
            }

            case Types.DATE -> new ArrowType.Date(org.apache.arrow.vector.types.DateUnit.DAY);
            case Types.TIME -> new ArrowType.Time(org.apache.arrow.vector.types.TimeUnit.MILLISECOND, 32);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                new ArrowType.Timestamp(org.apache.arrow.vector.types.TimeUnit.MILLISECOND, null);

            // Default to UTF8 string for text types and unknown types
            default -> new ArrowType.Utf8();
        };
    }

    /**
     * Write a value from ResultSet to an Arrow vector at the specified index
     */
    private static void writeValueToVector(FieldVector vector, int index, ResultSet rs, int columnIndex)
            throws SQLException {

        Object value = rs.getObject(columnIndex);

        // Handle NULL values
        if (value == null) {
            vector.setNull(index);
            return;
        }

        // Write based on vector type
        switch (vector) {
            case BitVector v -> v.setSafe(index, (Boolean) value ? 1 : 0);
            case TinyIntVector v -> v.setSafe(index, ((Number) value).byteValue());
            case SmallIntVector v -> v.setSafe(index, ((Number) value).shortValue());
            case IntVector v -> v.setSafe(index, ((Number) value).intValue());
            case BigIntVector v -> v.setSafe(index, ((Number) value).longValue());
            case Float4Vector v -> v.setSafe(index, ((Number) value).floatValue());
            case Float8Vector v -> v.setSafe(index, ((Number) value).doubleValue());
            case DecimalVector v -> {
                BigDecimal decimal = (value instanceof BigDecimal) ? (BigDecimal) value
                        : new BigDecimal(value.toString());
                v.setSafe(index, decimal);
            }
            case DateDayVector v -> {
                LocalDate date = value instanceof LocalDate ? (LocalDate) value
                        : rs.getDate(columnIndex).toLocalDate();
                v.setSafe(index, (int) date.toEpochDay());
            }
            case TimeStampMilliVector v -> {
                if (value instanceof LocalDateTime ldt) {
                    long epochMilli = Timestamp.valueOf(ldt).getTime();
                    v.setSafe(index, epochMilli);
                } else if (value instanceof Timestamp ts) {
                    v.setSafe(index, ts.getTime());
                } else {
                    v.setSafe(index, rs.getTimestamp(columnIndex).getTime());
                }
            }
            case VarCharVector v -> {
                String str = value.toString();
                v.setSafe(index, str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            default -> {
                // Fallback to string representation
                if (vector instanceof VarCharVector v) {
                    String str = value.toString();
                    v.setSafe(index, str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    log.warn("Unsupported vector type: {}, treating as string", vector.getClass().getSimpleName());
                    vector.setNull(index);
                }
            }
        }
    }
}
