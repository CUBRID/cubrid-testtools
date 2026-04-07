<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/common/src - Shared Utilities & Dispatcher

## OVERVIEW
Common framework code shared across all CTP test suites: dispatch logic, utilities, INI parsing, SSH/invoker helpers, and the scheduler. Core entry point is `CTP.java` which uses URLClassLoader + reflection to dynamically load and invoke suite runners.

## STRUCTURE
```
common/src/
  com/navercorp/cubridqa/
    ctp/
      CTP.java              Main dispatcher; parses CLI, loads suite jars via URLClassLoader
      ComponentEnum.java    Enum of supported test types (sql, shell, isolation, ha_repl, cdc_repl...)
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
      SFTP.java, SFTPUpload.java, SFTPDownload.java  SFTP file transfer
      coreanalyzer/         Core dump analysis utilities
        AnalyzerMain.java, Analyzer.java, IssueMain.java
      grepo/                 Git repository service
        RepoService.java, UpgradeMain.java
  com/nhncorp/cubrid/common/
    grepo/                  Legacy package (NHN Corp) for git operations
common/sched/src/
  com/navercorp/cubridqa/scheduler/
    producer/               Build/schedule producers
      Main.java, Compatibility.java
      crontab/
        BuildMain.java, SchedularMain.java
    consumer/               Test execution consumers
    common/                 Shared scheduler utilities
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Add new test type to CTP | `ComponentEnum.java` (add enum) → `CTP.java` (add dispatch case) |
| URLClassLoader + reflection patterns | `CTP.java` (loads suite jars from `CTP/*/lib/*.jar`, calls `Main.exec()` reflectively) |
| INI section parsing | `IniData.java` (handles nested sections like `[sql/cubrid.conf]`) |
| SSH/SFTP operations | `SSHConnect.java`, `RunRemoteScript.java`, `SFTP*.java` |
| String/file/crypto utils | `CommonUtils.java` (55 methods: encoding, hashing, file ops, string manipulation) |
| Scheduler build triggers | `scheduler/producer/` (crontab-based build scheduling) |

## CONVENTIONS
- **URLClassLoader pattern**: Suite jars loaded at runtime from `CTP/*/lib/*.jar` based on ComponentEnum; no compile-time dependencies.
- **Reflection entry point**: All suites must implement `Main.exec(String configFilename)` for dynamic invocation.
- **INI nesting**: Sections like `[sql/cubrid.conf]` map to nested config structures.
- **CommonUtils**: Kitchen-sink utility; prefer suite-specific helpers for domain logic.

## COMMANDS
| Task | Command |
|---|---|
| Rebuild common + all suites | `cd CTP && ant clean dist` |
| Compile only (no jars) | `cd CTP && ant compile` |
| Test INI parsing | `CTP/bin/ini.sh -s <section> <file> <key>` |

## NOTES
- Changing `CTP.java` dispatch affects ALL suites; test with ≥2 suite types before commit.
- `CommonUtils.java` has 55 methods; check references before modifying signatures.
- Scheduler (`sched/`) builds to separate `cubridqa-scheduler.jar`; rarely modified unless changing build triggers.
- URLClassLoader + reflection allows dynamic suite loading without recompiling CTP core.
