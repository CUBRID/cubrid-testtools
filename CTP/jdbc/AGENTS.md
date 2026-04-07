<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# jdbc

## Purpose
JDBC test runner. No Java source — this module consists of shell scripts and JUnit for executing JDBC test cases from the `cubrid-testcases` repository.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `bin/` | `run.sh` — main runner: init → clean → prepare DB → compile tests → execute via JUnit |
| `lib/` | `junit-4.8.2.jar` — JUnit test framework |

## For AI Agents

### Working In This Directory
- `bin/run.sh` workflow: `do_init()` → `do_clean()` → `do_prepare()` (create DB, start services, compile Java) → `do_test()` (execute via `JdbcLocalTest`)
- Test cases are external: defined by `scenario` in `conf/jdbc.conf` (typically `cubrid-testcases-private/interface/JDBC/test_jdbc`)
- Tests are identified by `@Test` annotation and method names containing "test"; `@Ignore` skips cases
- No Ant compilation — `run.sh` compiles test source directly with javac
- Config uses nested INI sections: `[jdbc/cubrid.conf]`, `[jdbc/cubrid_broker.conf/%BROKER1]`

### Testing Requirements
- `bin/ctp.sh jdbc -c conf/jdbc.conf`
- Results in: `CTP/result/jdbc/current_runtime_logs/run_case_details.log`

## Dependencies

### External
- JUnit 4.8.2
- CUBRID JDBC driver (from CUBRID installation)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
