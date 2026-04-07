<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/cdc_repl/src - CDC Replication Testing Framework

## OVERVIEW
Change Data Capture (CDC) replication test framework; verifies data change streaming and replication. **Nearly identical to ha_repl/ structure.**

## STRUCTURE
```
cdc_repl/src/com/navercorp/cubridqa/cdc_repl/
  Main.java                 Entry point
  Test.java                 1038 lines - Test orchestration (very large)
  TestReader.java           Test case parser (@Deprecated line 167)
  TestMonitor.java          Execution monitoring
  Context.java              Configuration
  Feedback.java             Result interface
  InstanceManager.java      CDC instance lifecycle
  CheckDiff.java            Data consistency checking
  CdcReplUtils.java         CDC utilities
  migrate/
    SQLFileReader.java      493 lines - SQL migration reader
  deploy/
    Deploy.java             Deployment orchestration
  dispatch/
    Dispatch.java           Test dispatching
  impl/
    FeedbackDB.java         468 lines - Database result storage
    FeedbackFile.java       File-based storage
    FeedbackNull.java       No-op feedback
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `Main.java` |
| Test orchestration | `Test.java` (1038 lines) |
| SQL migration | `migrate/SQLFileReader.java` (493 lines) |
| Configuration | `Context.java` |
| Results storage | `impl/FeedbackDB.java` (468 lines) |

## ANTI-PATTERNS (Known Issues)
- **Test.java**: tbd workaround (line 206), TODO catch blocks (lines 718, 725)
- **TestReader.java**: @Deprecated (line 167)
- **FeedbackDB.java**: 3 TODO stubs (lines 351, 356, 444)
- **FeedbackNull.java**: 12 TODO stubs (lines 41-108)

## CRITICAL NOTES
- **Mirror structure**: cdc_repl/ mirrors ha_repl/ exactly. Changes to ha_repl/ often need mirroring here.
- **Shared patterns**: deploy/dispatch/impl structure identical to shell/, isolation/, ha_repl/.
- **Test.java size**: 1038 lines (tied with ha_repl/Test.java for largest in suite).
