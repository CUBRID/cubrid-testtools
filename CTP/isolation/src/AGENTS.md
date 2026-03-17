# CTP/isolation/src - Isolation Testing Framework

## OVERVIEW
Isolation (concurrency) test framework for verifying database isolation levels and transaction behavior.

## STRUCTURE
```
isolation/src/com/navercorp/cubridqa/isolation/
  Main.java                 Entry point for isolation tests
  Test.java                 Test orchestration
  TestReader.java           Test case file parser (.ctl files)
  TestMonitor.java          Test execution monitoring
  Context.java              Configuration management
  Feedback.java             Result feedback interface
  InstanceManager.java      Test instance lifecycle
  HoldCasCheck.java         CAS (CUBRID Application Server) checks
  CheckRequirement.java     Pre-flight requirement checks
  CheckDiff.java            Result diff checking
  CommonReader.java         Common file reading utilities
  deploy/
    Deploy.java             Test deployment
  dispatch/
    Dispatch.java           Test dispatching
  impl/
    FeedbackDB.java         Database result storage
    FeedbackFile.java       File-based result storage
    FeedbackNull.java       No-op feedback
  ctltool/                  Control file tools (external)
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `com/navercorp/cubridqa/isolation/Main.java` |
| Test orchestration | `com/navercorp/cubridqa/isolation/Test.java` |
| Test case parsing | `com/navercorp/cubridqa/isolation/TestReader.java` |
| Configuration | `com/navercorp/cubridqa/isolation/Context.java` |
| Deployment | `com/navercorp/cubridqa/isolation/deploy/Deploy.java` |
| Dispatch | `com/navercorp/cubridqa/isolation/dispatch/Dispatch.java` |
| Results | `com/navercorp/cubridqa/isolation/impl/Feedback*.java` |

## CONVENTIONS
- Test cases are `.ctl` (control) files with special syntax for multi-client scenarios.
- Uses same pattern as shell/ for deploy/dispatch/feedback structure.
- ctl tool in `ctltool/` directory is external helper for control file processing.

## ANTI-PATTERNS (This Module)
- FeedbackDB.java: 3 TODO Auto-generated stubs (lines 351, 356, 444)
- FeedbackFile.java: 5 TODO Auto-generated stubs (lines 187, 203, 209, 215, 221)
- FeedbackNull.java: 13 TODO Auto-generated stubs (lines 34-111)

## NOTES
- Smaller module (~10 files) compared to sql/ and shell/.
- Follows same architectural pattern as shell/ (deploy/dispatch/impl structure).
- Test cases use MC (main controller), C1, C2 notation for multi-client scenarios.
