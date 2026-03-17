# CTP/sql/src - SQL/MEDIUM Testing Framework

## OVERVIEW
SQL test execution engine with interactive console, web-based results viewer, and JDBC-based test orchestration.

## STRUCTURE
```
sql/src/
  com/navercorp/cubridqa/cqt/
    console/
      bo/
        ConsoleBO.java        1528 lines, 32 methods - Core business logic for test execution
      dao/
        ConsoleDAO.java       1039 lines - Data access, JDBC operations, connection pooling
      bean/
        CaseResult.java       Test case result data
        ProcessMonitor.java   Process monitoring
        Sql.java              SQL statement wrapper
        Summary.java          Test summary data
        Test.java             Test definition
        TestCaseSummary.java  Per-case summary
      util/
        TestUtil.java         1037 lines, 31 methods - Test execution utilities
        CommonFileUtile.java  614 lines - File operations
        FileUtil.java         408 lines - File I/O helpers
        StringUtil.java       418 lines - String manipulation
        SQLParser.java        416 lines - SQL script parsing
        (30+ more utilities)
      Executor.java           Test execution interface
      ConsoleAgent.java       Console agent for test control
    common/
      SQLParser.java          SQL parsing (splitting statements)
      LineScanner.java        Line-by-line file scanning
      SSHConnect.java         SSH wrapper
      CommonUtils.java        SQL-specific utilities
      ShellInput.java         Shell interaction
    model/
      Case.java               Test case model
      Resource.java           Resource model
    webconsole/
      Starter.java            Web console entry point
      WebServer.java          HTTP server for results
      WebModel.java           384 lines - Result display logic
      SummaryModel.java       Summary display
      SummaryItem.java        Summary item
      Compare.java            Result comparison
      Util.java               Web console utilities
  name/fraser/neil/plaintext/
    diff_match_patch.java     2471 lines - Third-party diff library
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| SQL test execution logic | `com/navercorp/cubridqa/cqt/console/bo/ConsoleBO.java` |
| JDBC/connection handling | `com/navercorp/cubridqa/cqt/console/dao/ConsoleDAO.java` |
| Test utilities | `com/navercorp/cubridqa/cqt/console/util/TestUtil.java` |
| SQL script parsing | `com/navercorp/cubridqa/cqt/common/SQLParser.java` |
| Web console | `com/navercorp/cubridqa/cqt/webconsole/` |
| Result comparison | `com/navercorp/cubridqa/cqt/webconsole/compare/` |

## CONVENTIONS
- ConsoleBO is the main orchestrator; it calls ConsoleDAO for DB operations and TestUtil for helpers.
- SQL files are parsed into individual statements by SQLParser; results compared via diff_match_patch.
- Web console runs as separate HTTP server on port 8888 (configurable).
- 35+ utility classes in console/util/ - check there before adding new helpers.

## ANTI-PATTERNS (This Module)
- ConsoleBO.java has 10 TODO comments (lines 130, 862, 870, 1155, 1161, 1180, 1186, 1223, 1243, 1482)
- ConsoleDAO.java uses @Deprecated methods (lines 230, 387, 410, 425, 454) and has BUG comment at line 629
- Multiple @SuppressWarnings("deprecation") in util/ (EnvSetter, MyDriverManager, MyDataSource, ErrorInterruptUtil, ConsoleAgent)
- ShellFileMaker.java, FileUtil.java, CommonFileUtile.java have empty TODO catch blocks

## NOTES
- ConsoleBO.java (1528 lines) is the most complex file; changes need careful testing.
- Web console only supports SQL and MEDIUM test types (not SHELL or others).
- SQLParser handles splitting SQL files on semicolons; be careful with stored procedures.
