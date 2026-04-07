<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/isolation/src - Isolation Testing Framework

## OVERVIEW
Concurrency test framework for verifying database isolation levels and transaction behavior under multi-client scenarios.

## STRUCTURE
```
isolation/src/com/navercorp/cubridqa/isolation/
  Main.java                 Entry point
  Test.java                 Test orchestration
  TestReader.java           .ctl file parser
  Context.java              Configuration
  deploy/
    Deploy.java             Test deployment
  dispatch/
    Dispatch.java           Test dispatching
  impl/
    FeedbackDB.java         Database result storage
    FeedbackFile.java       File-based result storage
    FeedbackNull.java       No-op feedback
  ctltool/                  Control file tools
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Entry point | `Main.java` |
| Test orchestration | `Test.java` |
| .ctl file parsing | `TestReader.java` |
| Configuration | `Context.java` |
| Deployment | `deploy/Deploy.java` |
| Dispatch | `dispatch/Dispatch.java` |
| Results | `impl/Feedback*.java` |

## CONVENTIONS
- Test cases: `.ctl` (control) files with MC (main controller), C1, C2 notation for multi-client scenarios.
- Follows shell/ pattern: deploy/dispatch/impl structure.
- ctltool/ is external helper for control file processing.

## ANTI-PATTERNS (This Module)
- FeedbackDB.java: 3 TODO stubs (lines 351, 356, 444)
- FeedbackFile.java: 5 TODO stubs (lines 187, 203, 209, 215, 221)
- FeedbackNull.java: 13 TODO stubs (lines 34-111)

## NOTES
- Smaller module (~10 files) vs sql/ and shell/.
- Mirrors shell/ architecture (deploy/dispatch/impl).
- Multi-client test syntax: MC setup, C1/C2 transactions, wait/commit/quit.
