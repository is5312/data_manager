# Data Manager Performance Tests

Gatling-based performance testing suite for the Data Manager gRPC service. This module tests the system's ability to handle aggressive spike loads while continuously inserting data into PostgreSQL tables via gRPC.

## Overview

The performance tests simulate real-world load scenarios:
- **Normal Load**: 10 requests/second baseline
- **Spike Load**: 500 requests/second aggressive spike
- **Duration**: 30 seconds total (5s normal → 20s spike → 5s normal)
- **Expected Inserts**: ~10,000+ rows into the `dmgr` schema

## Architecture

```
┌─────────────────────┐
│  Gatling Test       │
│  (Java)             │
└──────────┬──────────┘
           │ gRPC
           ▼
┌─────────────────────┐
│  DataOperationsClient│
│  (Spring gRPC)      │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  gRPC Server        │
│  (Backend)          │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  PostgreSQL         │
│  (dmgr schema)      │
└─────────────────────┘
```

## Prerequisites

### 1. Running Services

**Backend Server** (with gRPC enabled):
```bash
cd /Users/jeffthampy/antigravity_workspace/data_manager
./gradlew :data-manager-backend:bootRun
```

Verify gRPC server is running on port 9090.

**PostgreSQL Database**:
- Host: `localhost:5432`
- Database: `datamanager`
- User: `postgres`
- Password: `changeme`

Or set environment variables:
```bash
export DB_URL=jdbc:postgresql://your-host:5432/your-db
export DB_USER=your-user
export DB_PASSWORD=your-password
```

### 2. Database Schema

The test automatically creates:
- `dmgr` schema (if not exists)
- Metadata tables: `base_reference_table`, `base_column_map`
- Test table with columns: `id`, `name`, `email`, `age`, `created_at`

## Running the Tests

### Option 1: Gradle Task (Recommended)

```bash
# From project root
./gradlew :data-manager-performance-tests:gatlingRun

# Or from performance tests module
cd data-manager-performance-tests
../gradlew gatlingRun
```

### Option 2: Java Main Class

```bash
./gradlew :data-manager-performance-tests:testClasses
java -cp "$(./gradlew :data-manager-performance-tests:printTestClasspath -q)" \
    com.datamanager.performance.simulations.GrpcInsertSpikeSimulation
```

### Option 3: Direct Gatling Execution

```bash
./gradlew :data-manager-performance-tests:testClasses
java -cp "build/classes/java/test:..." io.gatling.app.Gatling \
    --simulation com.datamanager.performance.simulations.GrpcInsertSpikeSimulation \
    --results-folder build/gatling-results
```

## Configuration

Configuration file: `src/main/resources/application-performance.yml`

### Database Connection
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/datamanager}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:changeme}
```

### gRPC Client
```yaml
grpc:
  client:
    dataOperations:
      address: ${GRPC_ADDRESS:static://localhost:9090}
      negotiationType: PLAINTEXT
```

### Performance Test Parameters
```yaml
performance:
  schema: dmgr
  table-prefix: perf_test
  
  spike:
    normal-rps: 10              # Normal load: requests per second
    spike-rps: 500              # Spike load: requests per second
    normal-duration: 5s         # Duration format: 5s, 1m, etc.
    spike-duration: 20s
  
  cleanup-on-finish: true       # Auto-delete test table after test
```

### Environment Variables

Override defaults with environment variables:
```bash
export DB_URL=jdbc:postgresql://prod-host:5432/datamanager
export DB_USER=test_user
export DB_PASSWORD=secure_password
export GRPC_ADDRESS=static://prod-host:9090
```

## Test Results

### Fail-Fast Behavior

The test includes fail-fast logic:
- **Warmup Phase**: Single insert before main test to verify connectivity
- **Assertions**: Test stops after 10 consecutive failures
- **Early Exit**: Prevents wasted execution time if backend is down

### Viewing Reports

After running tests, Gatling generates an HTML report:

```bash
# Location
data-manager-performance-tests/build/gatling-results/grpcinsertspikesimulation-<timestamp>/index.html

# Open in browser (macOS)
open build/gatling-results/grpcinsertspikesimulation-*/index.html

# Open in browser (Linux)
xdg-open build/gatling-results/grpcinsertspikesimulation-*/index.html
```

### Expected Metrics

**Normal Load Phase (10 req/s)**:
- Response Time: < 100ms (p50), < 150ms (p95)
- Success Rate: 100%
- Throughput: ~10 req/s

**Spike Load Phase (500 req/s)**:
- Response Time: < 500ms (p50), < 2000ms (p95)
- Success Rate: > 95%
- Throughput: ~500 req/s

**Recovery Phase**:
- Response Time: Returns to normal (< 100ms)
- Success Rate: 100%

**Overall**:
- Total Requests: ~10,000+
- Total Duration: 30 seconds
- Total Inserts: All successful rows persisted in `dmgr` schema

### Sample Report Sections

1. **Global Statistics**: Overall success rate, response times, requests per second
2. **Response Time Distribution**: Histogram showing latency distribution
3. **Response Time Percentiles**: 50th, 75th, 95th, 99th percentiles over time
4. **Active Users**: Concurrent user simulation graph
5. **Requests per Second**: Throughput over time (shows spike clearly)

## Troubleshooting

### Issue: Connection Refused (gRPC)
```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
Caused by: java.net.ConnectException: Connection refused
```

**Solution**: Ensure backend server is running with gRPC enabled on port 9090:
```bash
./gradlew :data-manager-backend:bootRun
# Check logs for: "gRPC Server started, listening on port 9090"
```

**Note**: The test includes a warmup phase that will fail fast if the gRPC server is not reachable, preventing wasted test execution time.

### Issue: Connection Refused (PostgreSQL)
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```

**Solution**: Ensure PostgreSQL is running and accessible:
```bash
# Check if PostgreSQL is running
pg_isready -h localhost -p 5432

# Or start PostgreSQL
brew services start postgresql  # macOS with Homebrew
sudo systemctl start postgresql  # Linux with systemd
```

### Issue: Authentication Failed
```
PSQLException: password authentication failed for user "postgres"
```

**Solution**: Check credentials in `application-performance.yml` or set environment variables:
```bash
export DB_USER=correct_user
export DB_PASSWORD=correct_password
```

### Issue: Schema Permission Denied
```
PSQLException: permission denied for schema dmgr
```

**Solution**: Grant schema creation permissions:
```sql
GRANT CREATE ON DATABASE datamanager TO postgres;
GRANT ALL ON SCHEMA dmgr TO postgres;
```

### Issue: Low Success Rate During Spike
```
Success Rate: 85% (below 95% threshold)
```

**Possible Causes**:
- Backend server under-resourced (increase heap size)
- Database connection pool too small (increase `hikari.maximum-pool-size`)
- Database I/O bottleneck (check disk performance)
- Network latency (check network conditions)

**Solutions**:
```yaml
# Increase backend JVM heap
# In backend application.yml or environment:
JAVA_OPTS="-Xmx4g -Xms2g"

# Increase connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Increase from default
```

### Issue: Gatling Fails to Start
```
ClassNotFoundException: io.gatling.app.Gatling
```

**Solution**: Ensure dependencies are resolved:
```bash
./gradlew :data-manager-performance-tests:dependencies --configuration testRuntimeClasspath
./gradlew :data-manager-performance-tests:clean build
```

## Cleanup

### Manual Cleanup

If tests fail or you want to manually clean up:

```sql
-- Connect to PostgreSQL
psql -h localhost -U postgres -d datamanager

-- List test tables
SELECT * FROM dmgr.base_reference_table WHERE tbl_label LIKE 'perf_test%';

-- Drop test tables
DROP TABLE IF EXISTS dmgr.tbl_perf_test_<timestamp> CASCADE;

-- Delete metadata
DELETE FROM dmgr.base_column_map WHERE tbl_id IN (
    SELECT id FROM dmgr.base_reference_table WHERE tbl_label LIKE 'perf_test%'
);
DELETE FROM dmgr.base_reference_table WHERE tbl_label LIKE 'perf_test%';

-- Optional: Drop entire schema
DROP SCHEMA IF EXISTS dmgr CASCADE;
```

### Automatic Cleanup

Enable automatic cleanup in `application-performance.yml`:
```yaml
performance:
  cleanup-on-finish: true  # Set to true to auto-delete test table
```

## Advanced Usage

### Custom Load Profile

Modify `GrpcInsertSpikeSimulation.java` to create custom load profiles:

```java
// Example: Ramp up gradually
OpenInjectionStep rampUp = rampUsersPerSec(10).to(500).during(Duration.ofSeconds(30));

// Example: Constant high load
OpenInjectionStep constantHigh = constantUsersPerSec(1000).during(Duration.ofSeconds(60));

// Example: Staircase pattern
OpenInjectionStep staircase = 
    incrementUsersPerSec(100)
        .times(5)
        .eachLevelLasting(Duration.ofSeconds(10))
        .separatedByRampsLasting(Duration.ofSeconds(5))
        .startingFrom(10);
```

### Multiple Scenarios

Test different operations simultaneously:

```java
ScenarioBuilder insertScenario = scenario("Inserts").exec(...);
ScenarioBuilder updateScenario = scenario("Updates").exec(...);
ScenarioBuilder deleteScenario = scenario("Deletes").exec(...);

setUp(
    insertScenario.injectOpen(constantUsersPerSec(400).during(30)),
    updateScenario.injectOpen(constantUsersPerSec(80).during(30)),
    deleteScenario.injectOpen(constantUsersPerSec(20).during(30))
);
```

### Custom Assertions

Add more specific assertions:

```java
setUp(...)
.assertions(
    global().responseTime().mean().lt(200),
    global().responseTime().percentile4().lt(3000),  // 99th percentile
    global().successfulRequests().count().gt(9000),
    forAll().failedRequests().percent().lt(5.0)
);
```

## Contributing

When adding new performance tests:

1. Create new simulation class extending `Simulation`
2. Follow naming convention: `<Operation><LoadType>Simulation.java`
3. Document expected metrics in class Javadoc
4. Add configuration to `application-performance.yml`
5. Update this README with new test details

## References

- [Gatling Documentation](https://gatling.io/docs/gatling/)
- [Gatling Java DSL](https://gatling.io/docs/gatling/reference/current/core/dsl/)
- [gRPC Spring Boot Starter](https://github.com/grpc-ecosystem/grpc-spring)
- [Data Manager Architecture](../ARCHITECTURE.md)
- [gRPC Usage Guide](../GRPC_USAGE.md)

## Migration Load Test

### Overview

The migration load test verifies that table migration from `public` to `dmgr` schema works correctly during peak load with concurrent reads and writes, ensuring:
- **No data loss**: All rows inserted before/during/after migration are preserved
- **No interruption**: Reads and writes continue successfully throughout migration
- **Data integrity**: All data correctly migrated to shadow table with no duplicates

### Test Flow

1. **Setup Phase**: Creates table in `public` schema and pre-populates with initial data
2. **Warmup Phase**: Baseline load (writes + reads) for configurable duration
3. **Migration Trigger**: Triggers migration from `public` to `dmgr` schema via REST API
4. **Migration Execution**: Continues load generation during migration
5. **Verification**: After migration completes, verifies data integrity

### Running Migration Load Test

```bash
# From project root
./gradlew :data-manager-performance-tests:migrationLoadTest
```

### Configuration

Migration test configuration in `application-performance.yml`:

```yaml
performance:
  migration:
    initial-rows: 1000              # Pre-populate table with N rows
    write-rps: 50                   # Write requests per second
    read-rps: 20                    # Read requests per second
    warmup-duration-seconds: 10     # Warmup phase duration
    migration-trigger-delay-seconds: 15  # Delay before triggering migration
    verification-timeout-seconds: 300    # Max time to wait for migration
    backend-url: http://localhost:8080  # Backend REST API URL
```

### Test Results

The test generates:
- **Gatling HTML Report**: Performance metrics for reads/writes during migration
- **Integrity Report**: Console output showing:
  - Expected vs actual row counts
  - Missing row IDs (if any)
  - Duplicate detection
  - Insert statistics (before/during/after migration)

### Success Criteria

- ✅ Migration completes successfully during concurrent load
- ✅ Zero data loss (all inserts captured)
- ✅ Zero read/write errors during migration
- ✅ Response times remain acceptable (< 2s p95)
- ✅ Data integrity verified (row counts match, no duplicates)

## Support

For issues or questions:
1. Check logs in `build/gatling-results/performance-test.log`
2. Review Gatling HTML report for detailed metrics
3. Verify backend logs for gRPC service errors
4. Check PostgreSQL logs for database issues
