<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# cdc_repl

## Purpose
Change Data Capture (CDC) replication testing framework for CUBRID. Validates CDC functionality and data consistency across replication nodes, similar in architecture to ha_repl but specialized for CDC-specific verification.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source: Main entry, InstanceManager, Deploy/DeployNode, Dispatch, SQL migration, CDC utilities |
| `lib/` | `cubridqa-cdc_repl.jar` + MANIFEST.MF + `common.inc` version-compatibility variants |
| `cdc_test_helper/` | Native C helper tool (`cdc_test_helper.c` + `build.sh`) for CDC-specific test operations |

## For AI Agents

### Working In This Directory
- Entry point: `cdc_repl/src/.../Main.java` — same multi-phase pattern as ha_repl
- `CheckDiff.java` — CDC-specific data consistency verification (not present in ha_repl)
- `CdcReplUtils.java` — CDC-specific utility functions
- `cdc_test_helper/` contains a native C tool; build with `cdc_test_helper/build.sh`
- Config: `conf/cdc_repl.conf`

### Testing Requirements
- After Java changes: `ant clean dist` then `bin/ctp.sh cdc_repl -c conf/cdc_repl.conf`
- Requires configured CDC replication environment
- Results in: `CTP/result/cdc_repl/current_runtime_logs/`

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — shared utilities, SSH
- SQL test cases from `cubrid-testcases/sql` repository

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
