# CTP/cdc_repl/src - CDC Replication Testing Framework

## OVERVIEW
Change Data Capture (CDC) replication test framework; verifies data change streaming and replication.

## STRUCTURE
```
cdc_repl/src/com/navercorp/cubridqa/cdc_repl/
  Main.java                 Entry point for CDC tests
  Test.java                 1038 lines - Test orchestration (very large)
  TestReader.java           Test case file parser (deprecated @ line 167)
  TestMonitor.java          Test execution monitoring
  Context.java              Configuration management
  Feedback.java             Result feedback interface
  InstanceManager.java      CDC instance lifecycle
  HoldCasCheck.java         CAS checks
  CheckRequirement.java     Pre-flight checks
  CheckDiff.java            Data consistency checking
  CommonReader.java         Common file reading
  CdcReplUtils.java         CDC-specific utilities
  migrate/
    SQLFileReader.java      493 lines - SQL file migration reader
    Convert.java            Format conversion
  deploy/
    Deploy.java             CDC deployment orchestration
  dispatch/
    Dispatch.java           Test dispatching
  impl/
    FeedbackDB.java         468 lines - Database result storage
    FeedbackFile.java       File-based result storage
    FeedbackNull.java       No-op feedback
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `com/navercorp/cubridqa/cdc_repl/Main.java` |
| Test orchestration (largest file) | `com/navercorp/cubridqa/cdc_repl/Test.java` (1038 lines) |
| SQL migration | `com/navercorp/cubridqa/cdc_repl/migrate/SQLFileReader.java` (493 lines) |
| Configuration | `com/navercorp/cubridqa/cdc_repl/Context.java` |
| CDC deployment | `com/navercorp/cubridqa/cdc_repl/deploy/Deploy.java` |
| Dispatch | `com/navercorp/cubridqa/cdc_repl/dispatch/Dispatch.java` |
| Results | `com/navercorp/cubridqa/cdc_repl/impl/FeedbackDB.java` |

## CONVENTIONS
- Nearly identical structure to ha_repl/ (same author/pattern).
- Uses SQL test cases transformed for CDC validation.
- Same deploy/dispatch/impl pattern as shell/, isolation/, ha_repl/.

## ANTI-PATTERNS (This Module)
- Test.java: tbd workaround comment (line 206), TODO Auto-generated catch blocks (lines 718, 725)
- TestReader.java: @Deprecated at line 167
- FeedbackDB.java: 3 TODO Auto-generated stubs (lines 351, 356, 444)
- FeedbackNull.java: 12 TODO Auto-generated stubs (lines 41-108)

## NOTES
- Test.java is 1038 lines (tied for largest with ha_repl/Test.java).
- Mirror structure of ha_repl/ - changes to one likely need to be mirrored in the other.
- CDC is newer feature than HA replication; code is slightly more recent but follows same patterns.
