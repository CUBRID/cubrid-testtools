<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# common

## Purpose
Shared framework used by all CTP test suites. Contains the main dispatcher (`CTP.java`), utility libraries, SSH/SFTP operations, INI config parsing, shell test scripts, issue templates, a message-queue-based scheduler, and a git repository service.

## Key Files
| File | Description |
|------|-------------|

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Core Java source: CTP dispatcher, shared utilities, core analyzer, grepo client (see `src/AGENTS.md`) |
| `lib/` | Compiled `cubridqa-common.jar` + runtime JARs (Apache Commons, JSch, Log4j, ini4j, dom4j) |
| `script/` | 37 shell scripts for test operations, build install, result upload, scheduling (see `script/AGENTS.md`) |
| `ext/` | 11 suite-specific runner scripts (run_sql.sh, run_shell.sh, run_jdbc.sh, etc.) (see `ext/AGENTS.md`) |
| `sched/` | Scheduler: ActiveMQ producer/consumer + Quartz cron for build job orchestration (see `sched/AGENTS.md`) |
| `tpl/` | Jira/GitHub issue creation templates (3 JSON templates) |
| `gcov/` | GNU gcov binary for C/C++ code coverage collection |
| `grepo/` | Git repository service using JGit (see `grepo/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- All Java code compiles with `source="1.6" target="1.6"` — no Java 7+ features allowed
- `cubridqa-common.jar` is the single classpath entry for `ctp.sh`; suite jars are loaded dynamically via URLClassLoader + reflection
- Changes to `src/` require rebuild: `cd CTP && ant clean dist`

### Testing Requirements
- After any Java change: `ant clean dist` then `bin/ctp.sh sql -c conf/sample.conf`
- For script changes: test the specific operation (e.g., run_cubrid_install, start_consumer.sh)

## Dependencies

### External
- Apache Commons (cli, collections, dbcp, io, lang, logging, pool)
- JSch 0.1.55 (SSH/SFTP)
- Log4j 1.2.16, SLF4J
- ini4j (INI file parsing)
- dom4j 1.6.1 (XML)
- javax.mail 1.5.5 + Aspirin 0.11.01 (email)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
