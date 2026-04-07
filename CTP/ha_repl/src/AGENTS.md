<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/ha_repl/src - HA Replication Testing Framework

## OVERVIEW
High Availability (HA) replication test framework; verifies data consistency between master and slave CUBRID servers via SQL test case transformation.

## STRUCTURE
```
ha_repl/src/com/navercorp/cubridqa/ha_repl/
  Main.java                 287 lines - Entry point for HA replication tests
  Test.java                 1037 lines - Test orchestration & HA checking
  TestReader.java           266 lines - Test case file parser (@Deprecated)
  TestMonitor.java          187 lines - Test execution monitoring
  Context.java              290 lines - Configuration management
  Feedback.java             57 lines - Result feedback interface
  InstanceManager.java      325 lines - HA instance lifecycle
  HoldCasCheck.java         50 lines - CAS checks
  CheckRequirement.java     169 lines - Pre-flight checks
  CheckDiff.java            72 lines - Data consistency checking
  CommonReader.java         118 lines - Common file reading
  HaReplUtils.java          177 lines - HA-specific utilities
  common/
    Constants.java          51 lines - Constants
  migrate/
    SQLFileReader.java      502 lines - SQL file migration reader
    LineScanner.java        461 lines - Line scanning for migration
    Convert.java            78 lines - Format conversion
  deploy/
    Deploy.java             82 lines - HA deployment orchestration
    DeployNode.java         260 lines - Per-node deployment
  dispatch/
    Dispatch.java           216 lines - Test dispatching to HA nodes
  impl/
    FeedbackDB.java         468 lines - Database result storage
    FeedbackFile.java       255 lines - File-based result storage
    FeedbackNull.java       121 lines - No-op feedback
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `Main.java` (287 lines) |
| Test orchestration | `Test.java` (1037 lines) — HC_CHECK_FOR_DML, HA flags |
| SQL migration | `migrate/SQLFileReader.java` (502 lines) |
| Configuration | `Context.java` (290 lines) |
| HA deployment | `deploy/Deploy.java` (82 lines) |
| Test dispatch | `dispatch/Dispatch.java` (216 lines) |
| Results storage | `impl/FeedbackDB.java` (468 lines) |

## CONVENTIONS
- Transforms SQL test cases with HA checking flags: `$HC_CHECK_FOR_DML`, `@HC_CHECK_FOR_EACH_STATEMENT`.
- Adds primary keys to tables if missing (required for HA replication).
- Shares test cases with sql/ module; applies --test: and --check: transformations.
- Same architectural pattern as shell/ and isolation/.

## ANTI-PATTERNS (This Module)
- Test.java: tbd workaround (line 207), TODO Auto-generated catch blocks (lines 719, 726)
- TestReader.java: @Deprecated at line 167
- FeedbackDB.java: tbdNum variable usage (lines 56, 219, 222, 225, 258, 267, 276, 277, 298, 311)
- FeedbackNull.java: 12 TODO Auto-generated stubs (lines 41-108)

## NOTES
- Test.java (1037 lines) is one of the largest files in CTP; handles test orchestration and HA consistency checks.
- SQLFileReader.java (502 lines) + LineScanner.java (461 lines) = 963 lines for SQL migration pipeline.
- HA replication requires primary keys; CTP auto-adds them if missing from test cases.
