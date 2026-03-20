# CTP (CUBRID Test Program) - init-deep Configuration Prompt

## Project Overview

**Project Name**: CUBRID Test Program (CTP)  
**Repository**: cubrid-testtools  
**Language**: Java  
**Purpose**: Functional testing framework for CUBRID open-source database  
**Location**: `/home/dev/cubrid-testtools/CTP/`

## Recommended max-depth: 10

**Rationale**:
- The source code follows standard Java package hierarchy: `CTP/{module}/src/com/navercorp/cubridqa/{module}/{subpackage}/`
- Deepest observed directory nesting: 10 levels from CTP root
- Example path depth: `CTP/shell/src/com/navercorp/cubridqa/shell/main/Test.java` (9 levels)
- Using max-depth=10 ensures complete coverage of all nested packages and subdirectories

## Project Structure

```
/home/dev/cubrid-testtools/
├── CTP/                          # Main source directory
│   ├── bin/                      # Executable scripts
│   ├── build/                    # Build output
│   ├── build.xml                 # Ant build configuration
│   ├── common/                   # Shared components
│   │   ├── ext/                  # External libraries
│   │   ├── gcov/                 # Code coverage tools
│   │   ├── grepo/                # Git repository service
│   │   ├── lib/                  # Common libraries
│   │   ├── sched/                # Scheduling service
│   │   ├── script/               # Shell scripts
│   │   ├── src/                  # Common Java source
│   │   └── tpl/                  # Templates
│   ├── conf/                     # Configuration files
│   ├── cdc_repl/                 # CDC Replication tests
│   ├── ha_repl/                  # HA Replication tests
│   ├── isolation/                # Isolation tests
│   ├── jdbc/                     # JDBC tests
│   ├── shell/                    # Shell script tests
│   │   ├── init_path/            # Initialization paths
│   │   ├── local/                # Local test support
│   │   └── src/                  # Shell module Java source
│   ├── sql/                      # SQL tests (primary module)
│   │   ├── configuration/        # Test configurations
│   │   ├── function/             # Stored procedure functions
│   │   ├── memory/               # Memory test resources
│   │   ├── sample/               # Sample test cases
│   │   ├── src/                  # SQL module Java source
│   │   └── webconsole/           # Web console for results
│   └── sql_by_cci/               # SQL via CCI interface
├── doc/                          # Documentation (18 guides)
└── README.md                     # Project README
```

## Key Source Directories to Analyze

The actual Java source code is located in:

1. **CTP/common/src/** - Common utilities and shared components
2. **CTP/shell/src/** - Shell test execution framework
   - `com.navercorp.cubridqa.shell.main` - Main entry points
   - `com.navercorp.cubridqa.shell.common` - Common utilities
   - `com.navercorp.cubridqa.shell.deploy` - Deployment logic
   - `com.navercorp.cubridqa.shell.dispatch` - Test dispatching
   - `com.navercorp.cubridqa.shell.result` - Result handling
   - `com.navercorp.cubridqa.shell.service` - Service layer

3. **CTP/sql/src/** - SQL test execution framework
   - `com.navercorp.cubridqa.cqt.console` - Console utilities
   - `com.navercorp.cubridqa.cqt.model` - Data models
   - `com.navercorp.cubridqa.cqt.webconsole` - Web console
   - `name.fraser.neil.plaintext` - Third-party diff library

4. **CTP/jdbc/src/** - JDBC testing framework
5. **CTP/isolation/src/** - Isolation testing framework
6. **CTP/ha_repl/src/** - HA replication testing
7. **CTP/cdc_repl/src/** - CDC replication testing
8. **CTP/sql_by_cci/src/** - SQL via CCI testing

## Architecture Patterns

### Module Organization
- Each test type (sql, shell, jdbc, etc.) is a separate module
- Common code shared via `common/` module
- Module structure follows pattern: `{module}/src/com/navercorp/cubridqa/{module}/`

### Key Components
1. **Main Classes**: Entry points for each test type (e.g., `Main.java`, `Test.java`)
2. **Context Classes**: Configuration and environment management (`Context.java`)
3. **Service Layer**: Remote execution services (`*Service.java`)
4. **Utility Classes**: Common operations (SSH, file handling, logging)
5. **Result Handling**: Feedback and reporting mechanisms

### Configuration System
- INI-style configuration files in `conf/` directory
- Each test type has dedicated `.conf` file
- Common configuration in `common.conf`

## Documentation References

Located in `/home/dev/cubrid-testtools/doc/`:

1. **ctp_install_guide.md** - Installation instructions
2. **sql_guide.md** - SQL/MEDIUM/SQL_BY_CCI testing
3. **shell_guide.md** - Shell testing (Linux)
4. **shell_win_guide.md** - Shell testing (Windows)
5. **shell_heavy_guide.md** - Heavy load shell tests
6. **shell_long_guide.md** - Long-running shell tests
7. **shell_ext_guide.md** - Extended shell tests
8. **ha_shell_guide.md** - HA shell tests
9. **ha_repl_guide.md** - HA replication tests
10. **isolation_guide.md** - Isolation tests
11. **jdbc_guide.md** - JDBC tests
12. **jdbc_compatibility_guide.md** - JDBC compatibility
13. **cci_guide.md** - CCI tests
14. **cci_compatibility_guide.md** - CCI compatibility
15. **unittest_guide.md** - Unit tests
16. **rqg_guide.md** - Random Query Generator
17. **memoryleak_guide.md** - Memory leak tests
18. **code_coverage_guide.md** - Code coverage

## Command to Execute

```bash
/init-deep /home/dev/cubrid-testtools/CTP --max-depth=10 focus="src/,conf/,bin/,common/script/"
```

## Notes for AI Analysis

1. **Build System**: Uses Apache Ant (`build.xml` at CTP root)
2. **Entry Point**: `bin/ctp.sh` script
3. **Test Categories**: SQL, MEDIUM, SHELL, ISOLATION, HA_REPL, JDBC, SQL_BY_CCI
4. **Multi-platform**: Supports Linux and Windows (with Cygwin)
5. **Dependencies**: Java 6+, CUBRID database
6. **Remote Execution**: Supports distributed testing via SSH
