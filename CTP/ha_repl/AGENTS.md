<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# ha_repl

## Purpose
High Availability replication testing framework for CUBRID. Validates data consistency between master and slave nodes by transforming SQL test cases into HA replication verification scripts with `--test:` / `--check:` markers.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source: Main entry, InstanceManager, Deploy/DeployNode, Dispatch, SQL migration (Convert), feedback |
| `lib/` | `cubridqa-ha_repl.jar` + MANIFEST.MF + `common.inc` version-compatibility variants |

## For AI Agents

### Working In This Directory
- Entry point: `ha_repl/src/.../Main.java` — multi-phase: deploy → dispatch → concurrent test with monitors
- Requires **3+ accounts**: controller + HA pair nodes (master + slave)
- `InstanceManager.java` manages HA cluster nodes
- `migrate/` package: `Convert.java`, `SQLFileReader.java` transform SQL cases into HA replication checks
- Test cases are SQL scenarios with added `--test:` and `--check:` flags; primary keys are auto-added if missing
- `HoldCasCheck.java` — CAS process monitoring during replication tests
- Config: `conf/ha_repl.conf` with `env.instance1.master.ssh.*` and `env.instance1.slave.ssh.*`

### Testing Requirements
- After Java changes: `ant clean dist` then `bin/ctp.sh ha_repl -c conf/ha_repl.conf`
- Requires a configured HA cluster (master + slave nodes)
- Results in: `CTP/result/ha_repl/current_runtime_logs/`

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — shared utilities, SSH
- SQL test cases from `cubrid-testcases/sql` repository

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
