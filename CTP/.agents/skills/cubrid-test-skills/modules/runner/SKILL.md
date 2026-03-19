# Runner Module: Test Execution

This module provides guidance for executing CUBRID CTP tests.

> **Note**: For test case writing, see the Writer module (`modules/writer/SKILL.md`).

## Quick Start: Select Your Test Type to Execute

| Test Suite | Command | Details In |
|------------|---------|------------|
| SQL tests | `ctp.sh sql -c conf/sql.conf` | `references/sql.md` |
| MEDIUM tests | `ctp.sh medium -c conf/medium.conf` | `references/sql.md` |
| SQL_BY_CCI tests (SQL via CCI interface) | `ctp.sh sql_by_cci -c conf/sql_by_cci.conf` | `references/sql.md` |
| Shell tests | `ctp.sh shell -c conf/shell.conf` | `references/shell.md` |
| HA Shell tests | `ctp.sh shell -c conf/shell.conf` | `references/ha_shell.md` |
| CDC Replication tests | `ctp.sh cdc_repl -c conf/cdc_repl.conf` | `references/cdc_repl.md` |
| Isolation tests | `ctp.sh isolation -c conf/isolation.conf` | `references/isolation.md` |
| HA Replication tests | `ctp.sh ha_repl -c conf/ha_repl.conf` | `references/ha_repl.md` |
| JDBC tests | `ctp.sh jdbc -c conf/jdbc.conf` | `references/jdbc.md` |
| CCI API tests (shell-based) | `ctp.sh shell -c conf/cci.conf` | `references/cci.md` |

## CTP Execution Quick Reference

### Full Suite Execution
```bash
# SQL tests
ctp.sh sql -c conf/sql.conf

# MEDIUM tests
ctp.sh medium -c conf/medium.conf

# SQL_BY_CCI tests (SQL suite with CCI interface)
ctp.sh sql_by_cci -c conf/sql_by_cci.conf

# Shell tests
ctp.sh shell -c conf/shell.conf

# CDC Replication tests
ctp.sh cdc_repl -c conf/cdc_repl.conf

# Isolation tests
ctp.sh isolation -c conf/isolation.conf

# HA Replication tests
ctp.sh ha_repl -c conf/ha_repl.conf

# JDBC tests
ctp.sh jdbc -c conf/jdbc.conf

# CCI API tests (shell-based scenarios)
ctp.sh shell -c conf/cci.conf
```

### Interactive Mode (SQL/MEDIUM only)
```bash
ctp.sh sql -c conf/sql.conf --interactive

# Available commands:
sql> run /path/to/test.sql
sql> run /path/to/test/directory
sql> run_cci /path/to/test.sql
sql> quit
```

## Execution Workflow

### Step 1: Determine Test Type
Identify which test suite to run:
- SQL queries → `sql`
- SQL via CCI driver → `sql_by_cci`
- Shell scripts → `shell`
- CCI API shell scripts (.sh that compile/run C code) → `shell -c conf/cci.conf`
- CDC replication tests → `cdc_repl`
- Concurrency tests → `isolation`
- Replication tests → `ha_repl`
- JDBC API tests → `jdbc`

### Step 2: Check/Create Configuration
Ensure `CTP/conf/<suite>.conf` exists with correct settings:
- `scenario` - Path to test cases
- `env.instance*.ssh.*` - For shell/isolation multi-node
- `env.instance1.master.*` and `env.instance1.slave.*` - For HA/CDC replication suites

### Step 3: Execute Tests
Run the appropriate command from the Quick Reference above.

### Step 4: Analyze Results
See "Result Analysis" section below for each test type.

## Single Test Execution

### SQL (Interactive Mode)
```bash
ctp.sh sql -c conf/sql.conf --interactive
sql> run /path/to/test.sql
```

### Shell (single testcase)
```bash
# Method 1: run one shell case directly
export init_path=$CTP_HOME/shell/init_path
cd /path/to/testcase/cases
sh case_name.sh

# Method 2: set scenario to a narrow target and run shell suite
# (use a dedicated config, e.g. conf/cci.conf or custom shell conf)
ctp.sh shell -c conf/shell.conf
```

### Isolation (runone.sh)
```bash
sh $CTP_HOME/isolation/ctltool/runone.sh case.ctl 300

# Or with alias:
alias runone="sh $CTP_HOME/isolation/ctltool/runone.sh"
runone case.ctl 300
```

### SQL_BY_CCI standalone compile/run helper
```bash
sh $CTP_HOME/sql_by_cci/compile.sh test.c
./test
```

For CCI API test cases, follow shell workflow (`ctp.sh shell -c conf/cci.conf`) and compile inside case scripts using shell helpers such as `xgcc`.

## Result Analysis

### SQL/MEDIUM Results
- **Location**: `CTP/result/sql/current_runtime_logs/`
- **Expected**: `answers/*.answer`
- **Actual**: `cases/*.result`
- **Compare**: `diff answers/test.answer cases/test.result`

### Shell Results
- **Location**: `CTP/result/shell/current_runtime_logs/`
- **Check**: `write_ok` = PASS, `write_nok` = FAIL
- **Logs**: `test_local.log` files

### Isolation Results
- **Location**: `CTP/result/isolation/current_runtime_logs/`
- **Expected**: `answer/*.answer`
- **Actual**: `result/*.log`
- **Status**: `*.result` file (OK/NOK)

### HA Replication Results
- **Location**: Test directory
- **Master**: `*.master.dump`
- **Slave**: `*.slave1.dump`
- **Compare**: `diff test.master.dump test.slave1.dump`
- **Diff file**: Create `*.master.slave1.diff_1` for legitimate differences

### CDC Replication Results
- **Location**: `CTP/result/cdc_repl/current_runtime_logs/`
- **Check**: Controller/instance logs and replication consistency output
- **Config**: `conf/cdc_repl.conf`

### JDBC Results
- **Location**: `CTP/result/jdbc/current_runtime_logs/`
- **Details**: `run_case_details.log`
- **Check**: OK/FAIL status per test

### SQL_BY_CCI Results
- **Location**: `CTP/result/sql_by_cci/` (schedule/result directories)
- **Expected**: `*.answer`
- **Actual**: SQL case `*.result` output (SQL suite executed through CCI)

### CCI API (shell-based) Results
- **Location**: `CTP/result/shell/current_runtime_logs/`
- **Expected**: shell assertion outcomes (`write_ok` / `write_nok`) and C output comparisons

## Debugging Test Failures

### General Steps
1. **Check logs** in `CTP/result/<suite>/current_runtime_logs/`
2. **Compare** expected vs actual output
3. **Use webconsole** (SQL/MEDIUM): `ctp.sh webconsole start`
4. **Use interactive mode** (SQL/MEDIUM): `--interactive` flag

### Common Issues
| Issue | Solution |
|-------|----------|
| JAVA_HOME not set | `export JAVA_HOME=/path/to/java` |
| Config file missing | Create `CTP/conf/<suite>.conf` |
| Permission denied | Check SSH keys/passwords in config |
| Test timeout | Increase `testcase_timeout_in_secs` |

## CTP Development Commands

| Goal | Command |
|------|---------|
| Build jars | `cd CTP && ant clean dist` |
| Compile only | `cd CTP && ant compile` |
| Read INI value | `CTP/bin/ini.sh -s <section> <file> <key>` |
| Set INI value | `CTP/bin/ini.sh -s <section> <file> <key> <value>` |

## Web Console

For SQL/MEDIUM test result visualization:
```bash
ctp.sh webconsole start
# Access at http://localhost:8888
```

Features:
- View test results
- Compare diffs
- Execution history

## Cross-Module Reference

Before executing tests:
- **For writing test cases**: See `modules/writer/SKILL.md`
- **For answer file creation**: Execute test and copy result to answer file
