# Shell Test Cases

Complete reference for Shell test cases in CUBRID CTP.

## CTP Implementation Details

Shell test execution is orchestrated by the shell framework:
- **Entry point**: `CTP/shell/src/com/navercorp/cubridqa/shell/main/Main.java`
- **Main orchestration**: `CTP/shell/src/com/navercorp/cubridqa/shell/main/RunShellMain.java` - Uses Quartz scheduler
- **Configuration hub**: `CTP/shell/src/com/navercorp/cubridqa/shell/main/Context.java`
- **SSH operations**: `CTP/shell/src/com/navercorp/cubridqa/shell/common/SSHConnect.java`
- **Script utilities**: `CTP/shell/init_path/init.sh`

## File Structure

```
test_name/
└── cases/
    └── test_name.sh
```

## Naming Conventions

### For Bug Fix Test Cases
- Location: `cubrid-testcases-private-ex/shell/_06_issues/_{yy}_{1|2}h/`
- Names: `cbrd_xxxxx/cases/cbrd_xxxxx.sh`

### For Feature Test Cases
- Location: `cubrid-testcases-private-ex/shell/_{no}_{release_code}/cbrd_xxxxx_{feature}/`
- Names: `structured_name/cases/structured_name.sh`

## Shell Test Case Template

```bash
#!/bin/sh
# Test Case: [Brief description]
# Test Objective: [What this test verifies]
# Author: [name]

# Initialize environment
. $init_path/init.sh
init test

# Set test database name
dbname=tmpdb

# Create database
cubrid_createdb $dbname

# ============================================
# Test Steps
# ============================================

# Example: Test CUBRID utility
cubrid server start $dbname

# Example: Execute SQL and capture result
csql -u dba $dbname -c "SELECT 1" > result.log 2>&1

# Example: Check condition
if [ -s result.log ]; then
    write_ok
else
    write_nok "Expected output not found"
fi

# ============================================
# Cleanup
# ============================================

# Stop services
cubrid server stop $dbname
cubrid broker stop

# Delete database (also checks for core files)
cubrid deletedb $dbname

# Clean environment (reverts config, stops services)
finish
```

## Shell Conventions

1. **Always start with** `. $init_path/init.sh` and `init test`
2. **Always end with** `finish` to clean up
3. **Use `cubrid_createdb`** instead of `cubrid createdb` for compatibility
4. **Use `write_ok`/`write_nok`** for assertions
5. **Use `cubrid deletedb`** to clean up (checks for core files)
6. **Use provided functions** from init.sh

## Common Shell Functions

| Function | Usage |
|----------|-------|
| `init test` | Initialize test environment |
| `finish` | Clean up, revert configs, stop services |
| `cubrid_createdb $dbname` | Create database (legacy compatible) |
| `write_ok` | Mark test as passed |
| `write_nok [message]` | Mark test as failed |
| `compare_result_between_files f1 f2` | Compare two files |
| `change_db_parameter "param=value"` | Modify cubrid.conf |
| `change_broker_parameter "param=value"` | Modify cubrid_broker.conf |
| `xgcc -o out source.c` | Compile C/C++ (cross-platform) |
| `format_csql_output file` | Remove time from csql output |
| `xkill process_name` | Kill process (cross-platform) |

## Platform Exclusion Macros

```bash
# Add these anywhere in the script to skip on specific platforms:
WINDOWS_NOT_SUPPORTED    # Skip on Windows
LINUX_NOT_SUPPORTED      # Skip on Linux
```

## Execution Commands

| Action | Command |
|--------|---------|
| Run Shell tests | `ctp.sh shell -c conf/shell.conf` |
| Single node | Configure `env.instance1` only |
| Multi-node | Configure `env.instance1`, `env.instance2`, etc. |

### Execute a Single Shell Testcase

```bash
# Direct execution of one case
export init_path=$CTP_HOME/shell/init_path
cd /path/to/testcase/cases
sh case_name.sh
```

Notes:
- Case scripts typically start with `. $init_path/init.sh` and use `write_ok`/`write_nok`.
- For repeated single-case runs, you can use `CTP/shell/init_path/run_shell.sh`.

## Result Analysis

### Test Results Location
- **Results**: `CTP/result/shell/current_runtime_logs/`
- **Logs**: Console output captured per test
- **Status**: PASS (write_ok) or FAIL (write_nok)

### Checking Results
```bash
# View test logs
cat CTP/result/shell/current_runtime_logs/test_local.log

# Check for failures
grep -i "fail\|nok" CTP/result/shell/current_runtime_logs/*.log

# Check for core files (indicates crash)
find CTP/result/shell/ -name "core.*"
```

## Configuration

- **Default config**: `CTP/conf/shell.conf`
- **SSH config**: `env.instance1.ssh.host`, `env.instance1.ssh.user`, `env.instance1.ssh.pwd`
- **Test interface**: `test_interface_type=jdbc` (recommended) or `csql`

### Common Config Parameters
```ini
[common]
scenario = ${HOME}/cubrid-testcases-private-ex/shell/

# Single node
env.instance1.ssh.host = 192.168.1.100
env.instance1.ssh.user = shell
env.instance1.ssh.pwd = password

# Test settings
test_interface_type = jdbc
testcase_timeout_in_secs = 300
```

## CTP-Specific Notes

- **Quartz scheduler**: RunShellMain.java uses Quartz for job management
- **Multi-node support**: Supports both local and remote (SSH) test execution
- **Feedback classes**: DB, File, or Null (no-op) result reporting

## Anti-Patterns

- **Never** call cubrid commands directly; always use wrapper functions
- **Never** exit script directly; use `write_ok`/`write_nok` then `finish`
- **Don't** hardcode paths; use `$init_path` and environment variables
