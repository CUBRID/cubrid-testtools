# Isolation Test Cases

Complete reference for Isolation (MVCC) test cases in CUBRID CTP.

## CTP Implementation Details

Isolation test execution is handled by the isolation framework:
- **Entry point**: `CTP/isolation/src/com/navercorp/cubridqa/isolation/Main.java`
- **Test orchestration**: `CTP/isolation/src/com/navercorp/cubridqa/isolation/Test.java`
- **.ctl file parsing**: `CTP/isolation/src/com/navercorp/cubridqa/isolation/TestReader.java`
- **ctltool**: `CTP/isolation/ctltool/` - External helper for control file processing
- **Single test runner**: `CTP/isolation/ctltool/runone.sh`

## File Structure

```
test_directory/
├── test_name.ctl
├── answer/
│   └── test_name.answer
└── result/
    └── (generated logs)
```

## Isolation Test Case Template

```ctl
/*
Test Case: [Brief description]
Priority: [1-3]
Reference case: [related case]
Author: [name]

Test Plan: 
[Describe what MVCC/isolation behavior is being tested]

Test Scenario:
C1: [first client actions]
C2: [second client actions]

Test Point:
1) [Expected behavior 1]
2) [Expected behavior 2]

NUM_CLIENTS = 2
*/

MC: setup NUM_CLIENTS = 2;

/* Initialization */
C1: set transaction lock timeout INFINITE;
C1: set transaction isolation level read committed;

C2: set transaction lock timeout INFINITE;
C2: set transaction isolation level read committed;

/* Preparation */
C1: DROP TABLE IF EXISTS t1;
C1: CREATE TABLE t1 (id INT PRIMARY KEY, val VARCHAR(100));
C1: INSERT INTO t1 VALUES (1, 'value1'), (2, 'value2');
C1: COMMIT;
MC: wait until C1 ready;

/* Test Scenario */
C1: UPDATE t1 SET val = 'updated1' WHERE id = 1;
MC: wait until C1 ready;

C2: SELECT * FROM t1 WHERE id = 1 ORDER BY id;
MC: wait until C2 ready;

C1: COMMIT;
MC: wait until C1 ready;

C2: COMMIT;
MC: wait until C2 ready;

/* Cleanup */
C1: DROP TABLE IF EXISTS t1;
C1: COMMIT;
MC: wait until C1 ready;

/* Exit */
C1: quit;
C2: quit;
```

## Isolation Conventions

1. **Start with** `MC: setup NUM_CLIENTS = N;` (N = 1-10)
2. **Set transaction properties** for each client (timeout, isolation level)
3. **Use `MC: wait until CX ready;`** after each client statement
4. **Use `MC: wait until CX blocked;`** to test blocking behavior
5. **Always clean up** (drop tables, commit)
6. **Always quit** all clients at the end

## MC (Main Controller) Commands

| Command | Description |
|---------|-------------|
| `MC: setup NUM_CLIENTS = N;` | Initialize N clients (N = 1-10) |
| `MC: wait until CX ready;` | Wait for client X to finish |
| `MC: wait until CX blocked;` | Wait for client X to be blocked |
| `MC: sleep N;` | Sleep N seconds |

## Client Commands

- `C1:`, `C2:`, ... `C10:` represent client sessions
- Each client executes SQL independently
- Use `login as 'username';` for different user contexts
- Isolation levels: `read committed`, `repeatable read`
- Lock timeouts: `INFINITE`, or numeric seconds

## Isolation Level Directories

Test cases are organized by isolation level combinations:
- `_01_ReadCommitted` - Read Committed tests
- `_02_RepeatableRead` - Repeatable Read tests
- `_04_RepeatableRead_ReadCommitted` - Mixed isolation tests
- `_05_ReadCommitted_RepeatableRead` - Mixed isolation tests

## Execution Commands

| Action | Command |
|--------|---------|
| Run Isolation tests | `ctp.sh isolation -c conf/isolation.conf` |
| Run single test | `sh $CTP_HOME/isolation/ctltool/runone.sh case.ctl 300` |
| Quick alias | `alias runone="sh $CTP_HOME/isolation/ctltool/runone.sh"` |

### Running Single Tests

```bash
# Method 1: Using full path
sh $CTP_HOME/isolation/ctltool/runone.sh ~/cubrid-testcases/isolation/_01_ReadCommitted/test.ctl 300

# Method 2: Using alias
alias runone="sh $CTP_HOME/isolation/ctltool/runone.sh"
cd ~/cubrid-testcases/isolation/_01_ReadCommitted/
runone test.ctl 300

# Timeout is in seconds (300 = 5 minutes)
```

## Result Analysis

### Test Results Location
- **Results**: `CTP/result/isolation/current_runtime_logs/`
- **Answers**: `answer/*.answer` files
- **Generated logs**: `result/*.log` files
- **Status files**: `*.result` files in test directory

### Comparing Results
```bash
# Compare expected vs actual
diff answer/test_name.answer result/test_name.log

# Check test status
cat test_name.result  # Shows "test_name:OK" or "test_name:NOK"
```

### Understanding Output
- **.ctl file**: Test case definition with MC/C1/C2 commands
- **.answer file**: Expected output
- **.log file (in result/)**: Actual output with execution details
- **.result file**: Pass/Fail status summary

## Configuration

- **Default config**: `CTP/conf/isolation.conf`
- **Scenario path**: `scenario=${HOME}/cubrid-testcases/isolation/`
- **Timeout**: `testcase_timeout_in_secs=300`

### Common Config Parameters
```ini
[common]
scenario = ${HOME}/cubrid-testcases/isolation/
testcase_timeout_in_secs = 300

# Multi-node execution (optional)
env.instance1.ssh.host = 192.168.1.100
env.instance1.ssh.user = isolation
env.instance1.ssh.pwd = password
```

## CTP-Specific Notes

- **Smaller module**: ~10 files vs sql/ and shell/
- **Mirrors shell/ architecture**: deploy/dispatch/impl structure
- **Multi-client syntax**: MC setup, C1/C2 transactions, wait/commit/quit
