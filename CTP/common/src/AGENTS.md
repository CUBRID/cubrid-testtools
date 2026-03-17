# CTP/common/src - Shared Utilities

## OVERVIEW
Common framework code shared across all CTP test suites: dispatch logic, utilities, INI parsing, SSH/invoker helpers, and the scheduler.

## STRUCTURE
```
common/src/
  com/navercorp/cubridqa/
    ctp/
      CTP.java              Main dispatcher; parses CLI, loads suite jars reflectively
      ComponentEnum.java    Enum of supported test types (sql, shell, isolation...)
      IniCommand.java       CLI tool for INI read/write (used by ini.sh)
      Version.java          Version constants
    common/
      CommonUtils.java      55-method utility library (strings, files, crypto, encoding)
      IniData.java          INI file parser with nested section support
      LocalInvoker.java     Local process invocation helpers
      MailSender.java       Email notification utilities
      MakeFile.java         File creation helpers
      RunRemoteScript.java  SSH-based remote execution wrapper
      SSHConnect.java       SSH connection management
      ShellInput.java       Shell interaction abstractions
      ScriptInput.java      Script parameter handling
      SparseFsMain.java     Filesystem maintenance tool
      coreanalyzer/         Core dump analysis utilities
        AnalyzerMain.java
        IssueMain.java
      grepo/                 Git repository service
        RepoService.java
        UpgradeMain.java
  com/nhncorp/cubrid/common/
    grepo/                  Legacy package (NHN Corp) for git operations
common/sched/src/
  com/navercorp/cubridqa/scheduler/
    producer/               Build/schedule producers
      Main.java
      Compatibility.java
      crontab/
        BuildMain.java
        SchedularMain.java
    consumer/               Test execution consumers
    common/                 Shared scheduler utilities
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Add new test type to CTP | `com/navercorp/cubridqa/ctp/ComponentEnum.java` → add enum → `CTP.java` → add dispatch logic |
| Change how suites are loaded/invoked | `com/navercorp/cubridqa/ctp/CTP.java` lines 100-200 (URLClassLoader + reflection) |
| INI section parsing quirks | `com/navercorp/cubridqa/common/IniData.java` (handles `[suite/cubrid.conf]` nesting) |
| SSH connection handling | `com/navercorp/cubridqa/common/SSHConnect.java`, `RunRemoteScript.java` |
| Shared string/file/encoding utils | `com/navercorp/cubridqa/common/CommonUtils.java` (55 methods) |
| Scheduler build triggers | `com/navercorp/cubridqa/scheduler/producer/` |

## CONVENTIONS
- URLClassLoader loads suite jars at runtime from `CTP/*/lib/*.jar` based on ComponentEnum.
- `Main.exec(String configFilename)` is the reflective entry point expected by all suites.
- INI sections can be nested: `[sql/cubrid.conf]` means "cubrid.conf section inside sql".
- `CommonUtils.java` is the kitchen-sink utility; prefer adding domain-specific helpers to suite-specific utils.

## COMMANDS
| Task | Command |
|---|---|
| Rebuild only common (then rebuild downstream jars) | `cd CTP && ant compile` (compiles common + all) → `ant dist` |
| Test INI parsing | `CTP/bin/ini.sh -s <section> <file> <key>` |

## NOTES
- Changing `CTP.java` dispatch logic affects ALL suites; test with at least 2 different suite types before commit.
- `CommonUtils.java` has 55 methods; use `lsp_find_references` before modifying signatures.
- Scheduler (`sched/`) is built into separate `cubridqa-scheduler.jar`; rarely needs changes unless modifying build triggers.
