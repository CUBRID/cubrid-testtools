<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/shell/src - Shell Testing Framework

## OVERVIEW
Shell script test orchestrator supporting local execution and distributed multi-node SSH-based testing.

## STRUCTURE
```
shell/src/com/navercorp/cubridqa/shell/
  main/
    Main.java               Entry point: `shell` suite dispatcher
    RunShellMain.java       1124 lines - Main orchestration with Quartz scheduling
    Test.java               Test case lifecycle management
    TestFactory.java        Test case factory
    TestMonitor.java        Test execution monitoring
    Context.java            560 lines, 80 methods - Configuration management
    ShellHelper.java        Shell-specific helpers
    GeneralLocalTest.java   Local test execution
    JdbcLocalTest.java      JDBC-based local tests
    ManualReportJob.java    Manual reporting
    CheckRequirement.java   Pre-flight checks
  common/
    SSHConnect.java         SSH connection wrapper
    ShellScriptInput.java   Shell script parameter handling
    ScriptInput.java        Generic script input
    GeneralScriptInput.java General script parameters
    Log.java                Logging utilities
    HttpUtil.java           HTTP utilities
    LocalInvoker.java       Local process invocation
    SyncException.java      Synchronization exceptions
  deploy/
    Deploy.java             Deployment orchestration
    DeployOneNode.java      Single-node deployment
    DeployHA.java           HA deployment
    TestCaseSVN.java        SVN test case retrieval
    TestCaseGithub.java     GitHub test case retrieval
  dispatch/
    Dispatch.java           Test dispatching to nodes
  result/
    Feedback.java           Result feedback interface
    FeedbackDB.java         527 lines - Database result storage
    FeedbackFile.java       469 lines - File-based result storage
    FeedbackNull.java       124 lines - No-op feedback
  service/
    ShellService.java       RMI service interface
    ShellServiceImpl.java    RMI service implementation
    Server.java             Service server
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Main shell test orchestration | `com/navercorp/cubridqa/shell/main/RunShellMain.java` (1124 lines) |
| Configuration/context | `com/navercorp/cubridqa/shell/main/Context.java` (560 lines, 80 methods) |
| Test case execution | `com/navercorp/cubridqa/shell/main/Test.java` |
| SSH operations | `com/navercorp/cubridqa/shell/common/SSHConnect.java` |
| Multi-node dispatch | `com/navercorp/cubridqa/shell/dispatch/Dispatch.java` |
| Test deployment | `com/navercorp/cubridqa/shell/deploy/` |
| Result handling | `com/navercorp/cubridqa/shell/result/FeedbackDB.java` (527 lines) |

## CONVENTIONS
- Context.java manages ALL configuration with 80 methods - it's the central config hub.
- RunShellMain.java uses Quartz scheduler for job management.
- Supports both local and remote (SSH) test execution.
- Feedback classes handle result reporting to DB, file, or null (no-op).

## ANTI-PATTERNS (This Module)
- FeedbackDB.java: 3 TODO Auto-generated stubs (lines 431, 436, 525)
- FeedbackFile.java: 5 TODO Auto-generated stubs (lines 248, 266, 272, 278, 284)
- FeedbackNull.java: 13 TODO Auto-generated stubs (lines 35-120)

## NOTES
- Context.java has the highest method count (80) in the entire CTP codebase; check for existing methods before adding.
- RunShellMain.java is the second-largest file (1124 lines) after sql/ConsoleBO.java.
- Supports SVN and GitHub for test case retrieval (TestCaseSVN.java, TestCaseGithub.java).
