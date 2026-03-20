# CTP (CUBRID Test Program) - AGENTS

## OVERVIEW
Java 6 + Ant-built test harness for CUBRID functional testing; dispatches suite runners from `bin/ctp.sh` using `conf/*.conf`.

## STRUCTURE
```text
CTP/
  build.xml                 Ant: compile all suite sources -> jar per suite
  bin/
    ctp.sh                  Entry point; runs com.navercorp.cubridqa.ctp.CTP
    ini.sh                  INI utility; runs com.navercorp.cubridqa.ctp.IniCommand
  conf/                     INI-style configs per suite (default if -c omitted)
  common/
    src/                    Shared framework + dispatcher (CTP.java)
    lib/                    cubridqa-common.jar + runtime deps (mail/, etc)
    script/                 35 shell scripts for test operations
    sched/
      src/                  Scheduler sources
      lib/                  cubridqa-scheduler.jar
    tpl/                    Templates used by reporting/issue tooling
  sql/
    src/                    SQL/MEDIUM harness + webconsole (cqt/*)
    bin/                    run.sh, run_memory.sh, interactive.sh
    lib/                    cubridqa-cqt.jar
    webconsole/             Web root served by webconsole starter
  shell/
    src/                    Shell suite orchestrator (local/remote dispatch)
    lib/                    cubridqa-shell.jar
    init_path/              Shell init assets + commonforjdbc_src (built into jar)
  isolation/                Isolation suite (jar: isolation/lib/cubridqa-isolation.jar)
  ha_repl/                  HA replication suite (jar: ha_repl/lib/cubridqa-ha_repl.jar)
  cdc_repl/                 CDC replication suite (jar: cdc_repl/lib/cubridqa-cdc_repl.jar)
  jdbc/                     JDBC runner scripts/deps (invoked by jdbc/bin/run.sh)
  sql_by_cci/               Native C executor (gcc + CUBRID CCI headers/libs)
  build/                    Ant compile output (untracked)
```

## WHERE TO LOOK
| What you're doing | Files / entry points |
|---|---|
| Add/modify suite dispatch (task names, wiring) | `CTP/common/src/com/navercorp/cubridqa/ctp/CTP.java`, `CTP/common/src/com/navercorp/cubridqa/ctp/ComponentEnum.java` |
| Understand what `bin/ctp.sh <suite>` actually runs | `CTP/bin/ctp.sh`, `CTP/common/src/com/navercorp/cubridqa/ctp/CTP.java` |
| SQL/MEDIUM execution logic (shell side) | `CTP/sql/bin/run.sh`, `CTP/sql/bin/run_memory.sh`, `CTP/sql/bin/interactive.sh` |
| Webconsole start/stop | `CTP/common/src/com/navercorp/cubridqa/ctp/CTP.java` (WEBCONSOLE), `CTP/sql/src/com/navercorp/cubridqa/cqt/webconsole/Starter.java` |
| INI config semantics (sections, translation) | `CTP/common/src/com/navercorp/cubridqa/common/IniData.java`, `CTP/common/src/com/navercorp/cubridqa/ctp/IniCommand.java` |
| Per-suite main classes (reflective loading) | `CTP/shell/src/.../Main.java`, `CTP/isolation/src/.../Main.java`, `CTP/ha_repl/src/.../Main.java`, `CTP/cdc_repl/src/.../Main.java` |
| Ant jar packaging filters / classpath | `CTP/build.xml`, `CTP/*/lib/MANIFEST.MF` |
| Native CCI executor build | `CTP/sql_by_cci/compile.sh` |
| Shell script utilities | `CTP/common/script/` (see AGENTS.md there) |

## CONVENTIONS
- Ant compiles multiple `*/src` trees into a single `CTP/build/` output, then jars by package include filters (not a per-module build).
- Runtime uses URLClassLoader + reflection to call suite `Main.exec(...)` from suite jars (no direct compile-time dependencies).
- Config is INI-like (`conf/*.conf`) with nested section names like `[sql/cubrid.conf]`; default config is `conf/<suite>.conf` when `-c` is omitted.
- Java language baseline is strict: `source="1.6" target="1.6"` in `CTP/build.xml` (no Java 7+ features).
- `jdbc/` and `sql_by_cci/` have no Java src/ directories; they are test runners only.

## COMMANDS
| Goal | Command |
|---|---|
| Build jars after code changes | `cd CTP && ant clean dist` |
| Compile only (no jars) | `cd CTP && ant compile` |
| Run suite with explicit config | `CTP/bin/ctp.sh <sql|medium|shell|isolation|ha_repl|cdc_repl|jdbc|unittest> -c CTP/conf/<suite>.conf` |
| Run suite with default config | `CTP/bin/ctp.sh sql` (uses `CTP/conf/sql.conf`) |
| SQL/MEDIUM interactive debug | `CTP/bin/ctp.sh sql --interactive` |
| Webconsole for SQL/MEDIUM results | `CTP/bin/ctp.sh webconsole start` (config: `CTP/conf/webconsole.conf`) |
| Read/modify INI values | `CTP/bin/ini.sh -s <section> <file> <key> {value}` |

## NOTES
| Gotcha / workflow | Details |
|---|---|
| `JAVA_HOME` is mandatory | `CTP/bin/ctp.sh` exits if unset; it only sets classpath to `common/lib/cubridqa-common.jar` and dispatches from there. |
| `/bin/sh` vs bashisms | `CTP/bin/ctp.sh` uses `PIPESTATUS` and `==`; if `/bin/sh` is not bash-compatible, run `bash CTP/bin/ctp.sh ...`. |
| `#SCRIPTCONT` execution path | Java prints lines ending `#SCRIPTCONT` (see `CTP/common/src/com/navercorp/cubridqa/ctp/CTP.java`); `ctp.sh` extracts them into a temp script and executes it. Treat any changes to those printed commands as security-sensitive. |
| Fast TDD loop (ultrawork-ready) | 1) Repro with smallest suite command (often `CTP/bin/ctp.sh sql --interactive`) 2) Change code 3) `cd CTP && ant clean dist` 4) Re-run same suite command and compare produced logs/results. |
| Atomic commit strategy | 1) `docs(ctp): add/update CTP/AGENTS.md` 2) `build(ctp): adjust Ant compile/jar packaging` 3) `fix(ctp): <suite> runner/dispatcher/config handling` (each commit keeps `CTP/bin/ctp.sh <suite>` runnable). |
