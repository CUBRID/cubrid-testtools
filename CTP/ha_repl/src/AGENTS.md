# CTP/ha_repl/src - HA Replication Testing Framework

## OVERVIEW
High Availability (HA) replication test framework; verifies data consistency between master and slave CUBRID servers.

## STRUCTURE
```
ha_repl/src/com/navercorp/cubridqa/ha_repl/
  Main.java                 Entry point for HA replication tests
  Test.java                 1037 lines - Test orchestration
  TestReader.java           Test case file parser (deprecated @ line 167)
  TestMonitor.java          Test execution monitoring
  Context.java              Configuration management
  Feedback.java             Result feedback interface
  InstanceManager.java      HA instance lifecycle
  HoldCasCheck.java         CAS checks
  CheckRequirement.java     Pre-flight checks
  CheckDiff.java            Data consistency checking
  CommonReader.java         Common file reading
  HaReplUtils.java          HA-specific utilities
  migrate/
    SQLFileReader.java      502 lines - SQL file migration reader
    LineScanner.java        Line scanning for migration
    Convert.java            Format conversion
  deploy/
    Deploy.java             HA deployment orchestration
    DeployNode.java         Per-node deployment
  dispatch/
    Dispatch.java           Test dispatching to HA nodes
  impl/
    FeedbackDB.java         468 lines - Database result storage
    FeedbackFile.java       File-based result storage
    FeedbackNull.java       No-op feedback
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `com/navercorp/cubridqa/ha_repl/Main.java` |
| Test orchestration (large file) | `com/navercorp/cubridqa/ha_repl/Test.java` (1037 lines) |
| SQL migration | `com/navercorp/cubridqa/ha_repl/migrate/SQLFileReader.java` (502 lines) |
| Configuration | `com/navercorp/cubridqa/ha_repl/Context.java` |
| HA deployment | `com/navercorp/cubridqa/ha_repl/deploy/Deploy.java` |
| Dispatch | `com/navercorp/cubridqa/ha_repl/dispatch/Dispatch.java` |
| Results | `com/navercorp/cubridqa/ha_repl/impl/FeedbackDB.java` |

## CONVENTIONS
- Uses SQL test cases (from sql/ scenarios) but transforms them with HA checking flags.
- Adds primary keys to tables if missing (required for HA replication).
- Same architectural pattern as shell/ and isolation/.

## ANTI-PATTERNS (This Module)
- Test.java: tbd workaround comment (line 207), TODO Auto-generated catch blocks (lines 719, 726)
- TestReader.java: @Deprecated at line 167
- FeedbackDB.java: 3 TODO Auto-generated stubs (lines 351, 356, 444)
- FeedbackNull.java: 12 TODO Auto-generated stubs (lines 41-108)

## NOTES
- Test.java is 1037 lines (one of the largest files in CTP).
- SQLFileReader.java (502 lines) handles migration of SQL test cases to HA format.
- Shares test cases with sql/ module but transforms them with --test: and --check: flags.
