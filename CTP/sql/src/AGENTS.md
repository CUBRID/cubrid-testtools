# CTP/sql/src - SQL/MEDIUM Testing Framework

## OVERVIEW
SQL test execution engine with interactive console, web-based results viewer, and JDBC-based test orchestration.

## STRUCTURE
```
sql/src/
  com/navercorp/cubridqa/cqt/
    console/
      bo/
        ConsoleBO.java        1528 lines - Core orchestrator for test execution
      dao/
        ConsoleDAO.java       1039 lines - JDBC operations, connection pooling
      bean/
        CaseResult.java, ProcessMonitor.java, Sql.java, Summary.java, Test.java, TestCaseSummary.java
        DefTestDB.java, SqlParam.java, SummaryInfo.java, SystemModel.java
      util/
        TestUtil.java         1037 lines - Test execution utilities
        CommonFileUtile.java, FileUtil.java, StringUtil.java, SQLParser.java
        CubridConnManager.java, CubridConnection.java, CubridDBCenter.java, CubridUtil.java
        EnvSetter.java, EnvGetter.java, EnvironmentCheck.java
        ConfigurationXMLReader.java, DatabaseXMLReader.java, ConfigureUtil.java
        CommandExecutor.java, CommandUtil.java, ShellFileMaker.java
        ErrorInterrupt.java, ErrorInterruptUtil.java, LogUtil.java
        MyDataSource.java, MyDriverManager.java, PropertiesUtil.java
        RepositoryPathUtil.java, StdOutJob.java, StreamGobbler.java, SystemConst.java
      Executor.java, ConsoleAgent.java
    common/
      SQLParser.java, LineScanner.java, SSHConnect.java, CommonUtils.java, ShellInput.java
      RunRemoteScript.java, SFTP.java, SFTPDownload.java, SFTPUpload.java
    model/
      Case.java, Resource.java
    webconsole/
      Starter.java, WebServer.java, WebModel.java, SummaryModel.java, SummaryItem.java, Compare.java, Util.java
  name/fraser/neil/plaintext/
    diff_match_patch.java     2471 lines - Third-party diff library
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| SQL test execution | `console/bo/ConsoleBO.java` |
| JDBC/connection handling | `console/dao/ConsoleDAO.java` |
| Test utilities | `console/util/TestUtil.java` |
| SQL script parsing | `common/SQLParser.java` |
| Web console | `webconsole/` |
| Result comparison | `webconsole/Compare.java` |

## CONVENTIONS
- ConsoleBO orchestrates; calls ConsoleDAO for DB ops and TestUtil for helpers.
- SQL files parsed into statements by SQLParser; results compared via diff_match_patch.
- Web console runs as HTTP server on port 8888 (configurable).
- 40+ utility classes in console/util/ — check before adding new helpers.

## ANTI-PATTERNS (This Module)
- ConsoleBO.java: 10 TODO comments (lines 130, 862, 870, 1155, 1161, 1180, 1186, 1223, 1243, 1482)
- ConsoleDAO.java: @Deprecated methods (lines 230, 387, 410, 425, 454); BUG comment at line 629
- Multiple @SuppressWarnings("deprecation") in util/ (EnvSetter, MyDriverManager, MyDataSource, ErrorInterruptUtil, ConsoleAgent)
- ShellFileMaker.java, FileUtil.java, CommonFileUtile.java: empty TODO catch blocks

## NOTES
- ConsoleBO.java (1528 lines) is most complex; changes need careful testing.
- Web console supports SQL and MEDIUM only (not SHELL or others).
- SQLParser splits on semicolons; be careful with stored procedures.
