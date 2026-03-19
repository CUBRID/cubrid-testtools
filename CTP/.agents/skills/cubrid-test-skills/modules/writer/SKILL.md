# Writer Module: Test Case Creation

This module provides guidance for writing CUBRID CTP test cases.

> **Note**: For test execution commands, see the Runner module (`modules/runner/SKILL.md`).

## Quick Start: Select Your Test Type

| Test Type | For Writing... | See |
|-----------|---------------|-----|
| **SQL** | `.sql` test files | `references/sql.md` |
| **Shell** | `.sh` test scripts | `references/shell.md` |
| **Isolation** | `.ctl` concurrency tests | `references/isolation.md` |
| **HA Replication** | `.sql` (auto-converts to `.test`) | `references/ha_repl.md` |
| **JDBC** | `.java` JUnit tests | `references/jdbc.md` |
| **CCI** | `.c` native tests | `references/cci.md` |

## Test Type Overview

| Test Type | File Extension | Test Location | Answer/Expected Output |
|-----------|---------------|---------------|----------------------|
| SQL/MEDIUM | `.sql` | `cases/` | `answers/*.answer` |
| Shell | `.sh` | `cases/` | Console output + logs |
| Isolation | `.ctl` | Test directory | `answer/*.answer` |
| HA Replication | `.test` | Test directory | `.master.dump`, `.slave1.dump` |
| JDBC | `.java` | `src/` | JUnit assertions |
| CCI | `.c` | Test directory | `*.answer` |

## Writing Workflow

### Step 1: Determine Test Type
Identify what you're testing:
- SQL queries, DDL, DML → **SQL** test
- CUBRID utilities, parameters → **Shell** test
- Concurrency, MVCC → **Isolation** test
- Data replication → **HA Replication** test (write SQL)
- JDBC API → **JDBC** test
- Native C interface → **CCI** test

### Step 2: Read Domain Reference
Consult the appropriate `references/*.md` file for:
- File structure
- Naming conventions
- Test case templates
- Coding conventions

### Step 3: Write Test Case
Follow the templates and conventions from the reference.

### Step 4: Create Answer File
After writing, use the Runner module to:
1. Execute the test
2. Verify output is correct
3. Copy result to answer file

## Common Writing Conventions (All Test Types)

### Test Case Documentation Template
Include at the top:
```
Test Case: [Descriptive name]
Priority: [1-3, where 1 is highest]
Reference case: [If derived from another case]
Author: [Name/Handle]
Objective: [What is being tested]
Expected Result: [What should happen]
```

### General Guidelines
1. **Keep tests atomic** - One test case per file
2. **Make tests deterministic** - Same input → Same output
3. **Include cleanup** - Leave environment clean
4. **Document the test** - Comments explain the purpose
5. **Handle edge cases** - NULL values, empty results, errors
6. **Use stable ordering** - Always ORDER BY for SQL queries
7. **Test positive and negative** cases

## CTP Test Case Repositories

Test cases are stored in separate repositories:
- **Public**: `https://github.com/CUBRID/cubrid-testcases` (sql/, medium/, isolation/)
- **Private Extended**: `https://github.com/CUBRID/cubrid-testcases-private-ex` (shell/)
- **Private Interface**: `https://github.com/CUBRID/cubrid-testcases-private`
  - JDBC: `interface/JDBC/test_jdbc/`
  - CCI API: `interface/CCI/shell/`

## Cross-Module Reference

After writing test cases:
- **For execution**: See `modules/runner/SKILL.md`
- **For answer file creation**: See Runner module's result analysis section
