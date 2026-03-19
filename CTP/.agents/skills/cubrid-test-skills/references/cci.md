# CCI Test Cases

Complete reference for CCI (C Call Interface) test cases in CUBRID CTP.

## Important Distinction: CCI API tests vs SQL_BY_CCI

These are different workflows:

1. **CCI API tests** (this document's main focus)
   - Test CCI APIs directly from C code
   - Test cases are shell scripts that compile/execute C programs
   - Run with: `ctp.sh shell -c conf/cci.conf` (user-created config)
   - Test repository: `cubrid-testcases-private/interface/CCI/shell/`

2. **SQL_BY_CCI tests**
   - SQL suite executed through CCI interface (`sql_interface_type=cci`)
   - Run with: `ctp.sh sql_by_cci -c conf/sql_by_cci.conf`
   - Test repository: `cubrid-testcases/sql`
   - See `references/sql.md`

## CTP Implementation Details

CCI API tests are a **branch of shell tests** (`doc/cci_guide.md`):
- Executed by shell runner: `CTP/shell/src/com/navercorp/cubridqa/shell/main/Main.java`
- Use shell utilities from `CTP/shell/init_path/`
- C code is compiled in test scripts (often through `xgcc` helper)
- Common init script: `. $init_path/init.sh`

Related helper for C compilation:
- `CTP/sql_by_cci/compile.sh` can be used to compile standalone C files when needed

## Repository and Scenario Paths

### Test cases repository
```bash
git clone https://github.com/CUBRID/cubrid-testcases-private.git
```

### CCI scenario root
```text
${HOME}/cubrid-testcases-private/interface/CCI/shell/
```

## Configuration

Create CCI config file (example): `CTP/conf/cci.conf`

```ini
[common]
scenario = ${HOME}/cubrid-testcases-private/interface/CCI/shell/_20_cci/_13_enhancement/cci_execute_batch

# Optional common shell settings
test_platform = linux
test_category = cci
testcase_timeout_in_secs = 7200
```

> `cci.conf` is typically user-created for CCI test runs.

## Required Environment

```bash
export CTP_HOME=$HOME/CTP
export init_path=$CTP_HOME/shell/init_path
. ~/.cubrid.sh
```

`init_path` is required because CCI tests source shell init scripts.

## Execution Commands

| Action | Command |
|--------|---------|
| Run CCI API tests | `ctp.sh shell -c conf/cci.conf` |
| Run SQL_BY_CCI (SQL via CCI) | `ctp.sh sql_by_cci -c conf/sql_by_cci.conf` |
| Compile standalone C program | `sh $CTP_HOME/sql_by_cci/compile.sh test.c` |

## CCI Shell Test Template

```bash
#!/bin/sh
. $init_path/init.sh
init test

# Compile CCI program (xgcc from init scripts)
xgcc -o cci_case cci_case.c

# Run and compare output
./cci_case > cci_case.result 2>&1
if diff -u cci_case.answer cci_case.result; then
    write_ok
else
    write_nok "CCI output mismatch"
fi

finish
```

## Typical C Program Snippet

```c
#include <stdio.h>
#include "cas_cci.h"

int main(void)
{
    /* connect / prepare / execute / fetch / close */
    printf("cci test\n");
    return 0;
}
```

## Result Analysis

CCI API tests run under shell framework:
- **Result root**: `CTP/result/shell/current_runtime_logs/`
- **Pass/Fail**: driven by `write_ok` / `write_nok`
- **Logs**: `test_*.log`, dispatch logs, snapshot properties

Check failures:
```bash
grep -i "nok\|fail" CTP/result/shell/current_runtime_logs/*.log
```

## Debugging Tips

1. Ensure `init_path` is exported before running.
2. Verify CUBRID env is loaded (`. ~/.cubrid.sh`).
3. Ensure C compiler toolchain used by scripts is available (`xgcc`/gcc).
4. If C output differs by platform, normalize output in script before comparison.

## CTP-Specific Notes

- CCI API tests are shell-based integration tests, not a standalone Java suite.
- SQL_BY_CCI is valid and uses `conf/sql_by_cci.conf`, but belongs to SQL workflow.
- For SQL answer generation through CCI (`answer_cci`), use SQL interactive `run_cci` flow in `references/sql.md`.

(End of file)
