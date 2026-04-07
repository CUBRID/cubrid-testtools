<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# shell

## Purpose
Shell-based test suite orchestrator for CUBRID. Supports local single-node and remote multi-instance test execution via SSH. Handles test deployment, dispatch, execution, and result reporting.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source: Main entry, test factory, deploy, dispatch, result feedback (see `src/AGENTS.md`) |
| `lib/` | `cubridqa-shell.jar` + MANIFEST.MF |
| `init_path/` | Shell init assets, C libraries (`commonforc/`), and JDBC common source (`commonforjdbc_src/`) |
| `local/` | Local test utilities (`unittest.sh`) |
| `.sisyphus/` | Continuous test run configuration |

## For AI Agents

### Working In This Directory
- Entry point: `shell/src/.../main/Main.java` → validates config → `TestFactory` → `Test` → executes shell scripts
- Three-phase execution: **Deploy** (install CUBRID on nodes) → **Dispatch** (distribute test cases) → **Test** (concurrent execution with monitoring)
- Multi-instance: configured via `env.instance1.ssh.*` in `conf/shell.conf`
- Result reporting via `Feedback` interface: `FeedbackDB`, `FeedbackFile`, `FeedbackNull`
- `init_path/` contains init scripts sourced by test cases (`. $init_path/init.sh`)

### Testing Requirements
- After Java changes: `ant clean dist` then `bin/ctp.sh shell -c conf/shell.conf`
- Local test: uses `local/unittest.sh`
- Results in: `CTP/result/shell/current_runtime_logs/`

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — shared utilities, SSH, logging
- `common/script/` — test operation scripts

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
