<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# sql

## Purpose
SQL/MEDIUM test execution framework for CUBRID. Provides JDBC-based test orchestration, an interactive debug console, memory profiling via Valgrind, a web-based result viewer, and support for multiple charset configurations.

## Key Files
| File | Description |
|------|-------------|
| `README.md` | (empty placeholder) |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `bin/` | Execution scripts: `run.sh` (main runner), `run_memory.sh` (Valgrind), `interactive.sh` (REPL debug) |
| `src/` | Java source: ConsoleAgent, ConsoleBO, ConsoleDAO, webconsole, diff library (see `src/AGENTS.md`) |
| `lib/` | `cubridqa-cqt.jar` + Jetty, XStream, JUnit, JSP runtime JARs |
| `configuration/` | XML configs: database setups (`Function_Db/`) and test execution configs (`test_config/`) with 17 charset variants |
| `webconsole/` | JSP views + static assets for HTTP result viewer (port 8888) |
| `memory/` | C stubs (`cub_server.c`, `cub_cas.c`) and Valgrind suppression file for memory profiling |
| `sample/` | Example test case (`cases/1001.sql`) and expected result (`answers/1001.answer`) |
| `function/stored_procedure/` | Java source for stored procedure test implementations (SpTest*.java) |
| `sql_by_cci/` | Native C executor using CCI API (see `../sql_by_cci/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Test execution flow: `bin/run.sh` → Java `ConsoleAgent` → `ConsoleBO` orchestrates → `ConsoleDAO` manages JDBC
- Result comparison uses Google `diff_match_patch.java` (Apache 2.0 licensed, in `src/name/fraser/neil/`)
- XML configs in `configuration/test_config/` define charset permutations (euckr, iso88591, utf8)
- `ConsoleBO.java` (1528 lines) is the core test orchestrator — read carefully before modifying
- Interactive mode: `bin/ctp.sh sql --interactive` for single-case debugging

### Testing Requirements
- After Java changes: `ant clean dist` then `bin/ctp.sh sql -c conf/sample.conf`
- Webconsole: `bin/ctp.sh webconsole start` then check `http://127.0.0.1:8888`
- Memory mode: `bin/ctp.sh sql -c conf/sql.conf` with Valgrind-configured environment

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — loaded at runtime by CTP dispatcher
- `common/script/` — shell scripts sourced during test execution

### External
- Jetty 8.1.9 (embedded web server for webconsole)
- XStream 1.2.2 (XML serialization)
- Jaxen 1.1.6 (XPath)
- Jasper JSP engine (JSP compilation)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
