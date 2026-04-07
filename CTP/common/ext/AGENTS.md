<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# common/ext

## Purpose
Suite-specific extended runner scripts. Each script handles the full test lifecycle for one test type: environment setup, CUBRID installation, test execution, result collection, and cleanup.

## Key Files
| File | Description |
|------|-------------|
| `run_sql.sh` | SQL test execution with database setup (10KB) |
| `run_shell.sh` | Shell command test execution (6.4KB) |
| `run_jdbc.sh` | JDBC API test runner (3.6KB) |
| `run_unittest.sh` | Unit test runner (3.0KB) |
| `run_isolation.sh` | Transaction isolation level testing (4.4KB) |
| `run_cci.sh` | CUBRID C Interface tests (8.7KB) |
| `run_coverage.sh` | Code coverage instrumentation runner (7.1KB) |
| `run_compat_jdbc.sh` | JDBC compatibility tests across versions (16KB) |
| `run_compat_cci.sh` | CCI compatibility tests (8.7KB) |
| `run_sql_by_cci.sh` | SQL tests executed via CCI layer (7.9KB) |
| `run_ha_repl.sh` | HA replication tests (7.2KB) |
| `run_cdc_repl.sh` | CDC replication tests (7.3KB) |

## For AI Agents

### Working In This Directory
- These scripts are invoked by the scheduler consumer after a build job is picked up from ActiveMQ
- Each script follows the pattern: install build → configure CUBRID → run suite → upload results
- `run_compat_jdbc.sh` is the largest (16KB) due to multi-version compatibility matrix logic
- Scripts source utilities from `common/script/` (e.g., `util_common.sh`, `util_compat_test.sh`)

### Testing Requirements
- Test in a scheduler-like environment with `CTP_HOME`, `CUBRID`, build URL set
- Verify end-to-end: build install → test run → result upload

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
