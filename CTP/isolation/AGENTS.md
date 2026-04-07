<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# isolation

## Purpose
Transaction isolation level testing framework for CUBRID. Tests concurrent transaction behavior (READ COMMITTED, SERIALIZABLE, etc.) using multi-client `.ctl` test case files with MC (main controller) coordination.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source: Main entry, test factory, isolation helpers, deploy, dispatch, feedback |
| `lib/` | `cubridqa-isolation.jar` + MANIFEST.MF |
| `ctltool/` | Compiled C/C++ test control tools: `qactl`, `qacsql`, `qamccom` with drivers for CUBRID, MySQL, Oracle |

## For AI Agents

### Working In This Directory
- Entry point: `isolation/src/.../Main.java` → same three-phase pattern as shell (Deploy → Dispatch → Test)
- Test cases use `.ctl` format with `MC:` (main controller) and `C1:`/`C2:` (transaction clients)
- `IsolationHelper.java` and `IsolationScriptInput.java` handle isolation-specific logic
- `ctltool/` contains pre-compiled native binaries — not built by Ant; multi-database support (CUBRID, MySQL, Oracle)
- Multi-instance: configured via `env.instance1.ssh.*` in `conf/isolation.conf`

### Testing Requirements
- After Java changes: `ant clean dist` then `bin/ctp.sh isolation -c conf/isolation.conf`
- Results in: `CTP/result/isolation/current_runtime_logs/`

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — shared utilities
- `common/script/` — test operation scripts

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
