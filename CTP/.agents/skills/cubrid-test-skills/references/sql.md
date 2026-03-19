# SQL Test Cases

Complete reference for SQL/MEDIUM test cases in CUBRID CTP.

## CTP Implementation Details

SQL test execution is handled by `cqt` (CUBRID Query Tester) framework:
- **Entry point**: `CTP/sql/bin/run.sh` or `CTP/bin/ctp.sh sql`
- **Core orchestrator**: `CTP/sql/src/com/navercorp/cubridqa/cqt/console/bo/ConsoleBO.java`
- **JDBC operations**: `CTP/sql/src/com/navercorp/cubridqa/cqt/console/dao/ConsoleDAO.java`
- **SQL parsing**: `CTP/sql/src/com/navercorp/cubridqa/cqt/common/SQLParser.java`
- **Result comparison**: `diff_match_patch.java`
- **Web console**: `CTP/sql/src/com/navercorp/cubridqa/cqt/webconsole/Starter.java` (port 8888)

## File Structure

```
test_case_folder/
├── cases/
│   └── test_name.sql
│   └── test_name.queryPlan       (optional, for query plan output)
├── answers/
│   ├── test_name.answer
│   ├── test_name.answer_WIN      (if Windows results differ)
│   └── test_name.answer_cci      (if CCI results differ)
```

## Naming Conventions

### For Bug Fix Test Cases
- Location: `cubrid-testcases/sql/_13_issues/_{yy}_{1|2}h/cases/`
- Names: `cbrd_xxxxx.sql`, `cbrd_xxxxx_1.sql`, `cbrd_xxxxx_2.sql`

### For Feature Test Cases
- Location: `cubrid-testcases/sql/_{no}_{release_code}/cbrd_xxxxx_{feature}/cases/`
- Names: `{any_structured_name}.sql`

## SQL Test Case Template

```sql
-- Test Case: [Brief description]
-- Priority: [1-3]
-- Reference case: [related case if any]
-- Author: [name]

-- Drop table if exists (required before CREATE)
DROP TABLE IF EXISTS t1;

-- Create test objects
CREATE TABLE t1 (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    created_date DATE
);

-- Insert test data
INSERT INTO t1 VALUES (1, 'test1', '2023-01-01');
INSERT INTO t1 VALUES (2, 'test2', '2023-01-02');
INSERT INTO t1 VALUES (3, 'test3', '2023-01-03');

-- Test query with ORDER BY for stable results
SELECT * FROM t1 ORDER BY id;

-- Test with query plan hint (optional)
--@queryplan
SELECT /*+ recompile */ * FROM t1 WHERE id > 1 ORDER BY id;

-- Clean up
DROP TABLE IF EXISTS t1;

-- Reset any changed system parameters
SET SYSTEM PARAMETERS 'param_name=default_value';
```

## SQL Conventions

1. **Always use `DROP TABLE IF EXISTS` before CREATE TABLE**
2. **Add `ORDER BY` to all SELECT statements** for stable results
3. **Clean up all objects** created during the test
4. **Reset system parameters** to default values
5. **Avoid time-consuming queries**
6. **Split large tests** into multiple smaller files
7. **Use `--@queryplan`** before queries needing execution plan
8. **Create empty `.queryPlan` file** in cases/ to output all query plans

## Execution Commands

| Action | Command |
|--------|---------|
| Run SQL tests | `ctp.sh sql -c conf/sql.conf` |
| Run MEDIUM tests | `ctp.sh medium -c conf/medium.conf` |
| Run SQL_BY_CCI tests | `ctp.sh sql_by_cci -c conf/sql_by_cci.conf` |
| Interactive mode | `ctp.sh sql -c conf/sql.conf --interactive` |
| Webconsole start | `ctp.sh webconsole start` |

### Interactive Mode Commands

```bash
ctp.sh sql -c conf/sql.conf --interactive

# Available commands:
sql> run /path/to/test.sql           # Run single SQL test
sql> run /path/to/test/directory     # Run all tests in directory
sql> run_cci /path/to/test.sql       # Run with CCI interface
sql> quit                            # Exit interactive mode
```

## Creating Answer Files

```bash
# 1. Run CTP in interactive mode
ctp.sh sql -c ~/CTP/conf/sql.conf --interactive

# 2. Navigate to test case directory and run
cd /path/to/test/cases
run test_name.sql

# 3. Verify results are correct, then copy as answer
cp test_name.result ../answers/test_name.answer

# 4. Re-run to verify the test passes
run test_name.sql

# 5. For CCI differences, check with run_cci
run_cci test_name.sql
# If different and expected:
cp test_name.result ../answers/test_name.answer_cci

# 6. For Windows differences (test on Windows):
cp test_name.result ../answers/test_name.answer_WIN
```

## Result Analysis

### Test Results Location
- **Results**: `CTP/result/sql/current_runtime_logs/`
- **Answers**: `answers/*.answer` files
- **Generated**: `cases/*.result` files

### Comparing Results
```bash
# Compare expected vs actual
diff answers/test_name.answer cases/test_name.result

# For CCI results
diff answers/test_name.answer_cci cases/test_name.result
```

### Web Console Analysis
```bash
# Start web console
ctp.sh webconsole start

# Access at http://localhost:8888
# View: Test results, diffs, execution history
```

## Configuration

- **Default config**: `CTP/conf/sql.conf`
- **INI-style sections**: `[sql/cubrid.conf]`, `[sql/cubrid_broker.conf/%BROKER1]`
- **INI utility**: `CTP/bin/ini.sh -s <section> <file> <key> {value}`

### Required Environment Caveat

Add these to your shell profile before running SQL/MEDIUM tests:

```bash
export TZ='Asia/Seoul'
export LC_ALL=en_US
```

`Asia/Seoul` timezone is required by many existing SQL test cases (`doc/sql_guide.md`).

### Common Config Parameters
```ini
[sql]
scenario = ${HOME}/cubrid-testcases/sql

[sql/cubrid.conf]
cubrid_port_id = 1523

[sql/cubrid_broker.conf/%BROKER1]
BROKER_PORT = 33000
```

### MEDIUM and SQL_BY_CCI Config Notes

```ini
# MEDIUM example
[sql]
scenario = ${HOME}/cubrid-testcases/medium
data_file = ${HOME}/cubrid-testcases/medium/files/mdb.tar.gz

# SQL_BY_CCI example
[sql]
sql_interface_type = cci
scenario = ${HOME}/cubrid-testcases/sql
```

## CTP-Specific Notes

- **Java 6 baseline**: No Java 7+ features (source="1.6" target="1.6")
- **SQLParser**: Splits on semicolons; be careful with stored procedures
- **Web console**: Supports SQL and MEDIUM only (not SHELL or others)
- **Results**: Compared via diff_match_patch library
