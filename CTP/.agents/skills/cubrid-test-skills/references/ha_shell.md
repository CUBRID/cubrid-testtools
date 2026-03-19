# HA Shell Test Cases

Complete reference for HA (High Availability) Shell test cases in CUBRID CTP.

## Overview

HA Shell tests are **shell-based HA tests** that verify CUBRID HA (High Availability) features:
- **Test type**: Shell scripts (.sh) that run on 2-node HA setup (master + slave)
- **Command**: Uses same `ctp.sh shell` as regular shell tests, but with HA-specific config
- **Repository**: `cubrid-testcases-private/HA/shell/`
- **Category**: `test_category = ha_shell`

## HA Shell vs HA Replication vs Shell

| Aspect | HA Shell | HA Replication | Regular Shell |
|--------|----------|----------------|---------------|
| **Test format** | Shell scripts (.sh) | SQL files (.sql) | Shell scripts (.sh) |
| **Command** | `ctp.sh shell -c shell.conf` | `ctp.sh ha_repl -c ha_repl.conf` | `ctp.sh shell -c shell.conf` |
| **Nodes** | 2 nodes (master + slave) | 2+ nodes (master + slave(s)) | 1 or more nodes |
| **Config key** | `env.X.ssh.relatedhosts` | `env.ha1.master/slave` | `env.instanceX` |
| **Repository** | cubrid-testcases-private | cubrid-testcases (sql/) | cubrid-testcases-private-ex |
| **Purpose** | Test HA features via shell | Test data replication | General utility tests |

## CTP Implementation Details

HA Shell uses the **shell framework** with HA-specific deployment:
- **Entry point**: `CTP/shell/src/com/navercorp/cubridqa/shell/main/Main.java`
- **HA deployment**: `CTP/shell/src/com/navercorp/cubridqa/shell/deploy/DeployHA.java`
- **SSH operations**: Uses `relatedhosts` to deploy to slave nodes
- **Same runner**: Shares shell test orchestration and utilities

## Repository and Scenario Paths

### Test cases repository
```bash
git clone https://github.com/CUBRID/cubrid-testcases-private.git
# Sparse checkout for HA only:
git config core.sparseCheckout true
echo 'HA/*' > .git/info/sparse-checkout
git checkout develop
```

### HA Shell scenario root
```
${HOME}/cubrid-testcases-private/HA/shell/
```

### File Structure
```
cubrid-testcases-private/HA/shell/
├── _12_bts_issue/_{yy}_{1|2}h/          # Bug fix cases
│   └── cbrd_xxxxx/cases/cbrd_xxxxx.sh
├── _{no}_{release_code}/cbrd_xxxxx_{feature}/  # Feature cases
│   └── structured_name/cases/structured_name.sh
└── config/
    └── daily_regression_test_excluded_list_linux.conf
```

## Configuration

HA Shell uses **shell.conf** with special `relatedhosts` parameter:

```ini
# ~/CTP/conf/shell.conf for HA Shell

# Default CUBRID parameters
default.cubrid.cubrid_port_id=1568
default.broker1.BROKER_PORT=10090
default.broker1.APPL_SERVER_SHM_ID=10090
default.broker2.BROKER_PORT=13091
default.broker2.APPL_SERVER_SHM_ID=13091
default.ha.ha_port_id=19909

# HA node pairs: env.<id>.ssh.host (master) + relatedhosts (slave)
env.83.ssh.host=192.168.1.83
env.83.ssh.relatedhosts=192.168.1.93

env.84.ssh.host=192.168.1.84
env.84.ssh.relatedhosts=192.168.1.94

# Scenario and exclusions
scenario=${HOME}/cubrid-testcases-private/HA/shell
testcase_exclude_from_file=${HOME}/cubrid-testcases-private/HA/shell/config/daily_regression_test_excluded_list_linux.conf

# Test settings
test_continue_yn=false
testcase_timeout_in_secs=604800
testcase_retry_num=0
delete_testcase_after_each_execution_yn=false
enable_check_disk_space_yn=true

# Test category
feedback_type=file
test_platform=linux
test_category=ha_shell
owner_email=your.email@example.com
```

### Key HA Configuration Parameters

| Parameter | Description |
|-----------|-------------|
| `env.X.ssh.host` | Master node IP |
| `env.X.ssh.relatedhosts` | Slave node IP (1 or more) |
| `default.ha.ha_port_id` | HA port for replication |
| `test_category` | Set to `ha_shell` for reporting |

## Execution Commands

### Run HA Shell Tests
```bash
# Run with HA configuration
ctp.sh shell -c ~/CTP/conf/shell.conf

# Background execution
nohup ctp.sh shell -c ~/CTP/conf/shell.conf &
```

### Run Single HA Shell Testcase
```bash
# Export init_path
export init_path=$CTP_HOME/shell/init_path

# Navigate to case and execute
cd ~/cubrid-testcases-private/HA/shell/_12_bts_issue/_19_2h/cbrd_22207/cases
sh cbrd_22207.sh
```

### Regression Test (with consumer)
```bash
# Start listener for HA shell queue
start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_HA_LINUX -exec run_shell

# Or combined with isolation (shares controller)
start_consumer.sh -q QUEUE_CUBRID_QA_CC_BASIC,QUEUE_CUBRID_QA_SHELL_HA_LINUX -exec run_isolation,run_shell
```

## Test Case Template

HA Shell follows **standard shell test conventions**:

```bash
#!/bin/sh
# Test Case: [Brief description of HA feature]
# Test Objective: [What HA behavior is verified]
# Author: [name]

# Initialize environment
. $init_path/init.sh
init test

# Set database name
dbname=ha_test_db

# Create database on master
cubrid_createdb $dbname

# Start CUBRID HA
cubrid hb start $dbname

# ============================================
# Test Steps - HA-specific operations
# ============================================

# Example: Check HA status
cubrid hb status $dbname > ha_status.log 2>&1
if grep -q "HA-mode" ha_status.log; then
    write_ok
else
    write_nok "HA mode not active"
fi

# Example: Test failover (may use expect scripts)
# ... failover test logic ...

# ============================================
# Cleanup
# ============================================

# Stop HA
cubrid hb stop $dbname

# Delete database
cubrid deletedb $dbname

# Clean environment
finish
```

## Naming Conventions

### For Bug Fix Test Cases
- Location: `cubrid-testcases-private/HA/shell/_12_bts_issue/_{yy}_{1|2}h/`
- Names: `cbrd_xxxxx/cases/cbrd_xxxxx.sh`

### For Feature Test Cases
- Location: `cubrid-testcases-private/HA/shell/_{no}_{release_code}/cbrd_xxxxx_{feature}/`
- Names: `structured_name/cases/structured_name.sh`

### Examples
```
HA/shell/_31_cherry/issue_21506_online_index/cbrd_21506_01/cases/cbrd_21506_01.sh
HA/shell/_12_bts_issue/_19_2h/cbrd_22207/cases/cbrd_22207.sh
```

## Result Analysis

### Test Results Location
- **Results**: `CTP/result/shell/current_runtime_logs/` (same as regular shell)
- **Category**: `ha_shell` (distinct from `shell` in QA reporting)
- **Logs**: Console output captured per test
- **Status**: PASS (`write_ok`) or FAIL (`write_nok`)

### Checking Results
```bash
# View test logs
cat CTP/result/shell/current_runtime_logs/test_local.log

# Check for failures
grep -i "fail\|nok" CTP/result/shell/current_runtime_logs/*.log

# Check HA-specific issues
grep -i "ha\|replication\|failover" CTP/result/shell/current_runtime_logs/*.log
```

## Code Coverage Integration

HA Shell tests are part of **code coverage testing**:

```bash
# Worker node environment for coverage
export GCOV_PREFIX=/home/shell
export GCOV_PREFIX_STRIP=2

# Coverage build message
sender.sh QUEUE_CUBRID_QA_SHELL_HA_LINUX \
  "http://.../CUBRID-10.2.0.xxxx-gcov-Linux.x86_64.tar.gz,http://.../cubrid-10.2.0.xxxx-gcov-src-Linux.x86_64.tar.gz" \
  ha_shell default
```

## Environment Requirements

### Controller Node
```bash
# ~/.bash_profile
export CTP_HOME=$HOME/CTP
export PATH=$JAVA_HOME/bin:$CTP_HOME/common/script:$PATH
export CTP_BRANCH_NAME="develop"
export CTP_SKIP_UPDATE=0
```

### Worker Nodes (Master + Slave)
```bash
# ~/.bash_profile
export CTP_HOME=$HOME/CTP
export init_path=$CTP_HOME/shell/init_path
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH
export CTP_BRANCH_NAME="develop"
export CTP_SKIP_UPDATE=0
. ~/.cubrid.sh

# For code coverage
export GCOV_PREFIX=/home/shell
export GCOV_PREFIX_STRIP=2
ulimit -c unlimited
```

## Cross-Module Reference

- **For shell test standards**: See `references/shell.md`
- **For HA replication (SQL-based)**: See `references/ha_repl.md`
- **For general shell utilities**: See `modules/runner/SKILL.md` Shell section
- **For installation**: See `modules/installer/SKILL.md`

## CTP-Specific Notes

- **Shares shell framework**: Same `init.sh`, `finish`, `write_ok/write_nok` patterns
- **HA-specific deployment**: Uses `relatedhosts` for slave node deployment
- **Code coverage capable**: Supports gcov-instrumented CUBRID builds
- **Regression scheduled**: Part of daily HA regression test queue
- **Controller sharing**: Controller node can be shared with isolation tests

(End of file - total 215 lines)
