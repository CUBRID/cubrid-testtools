---
name: cubrid-test-skills
description: |
  Comprehensive CUBRID CTP testing skill for writing test cases and executing tests.
  Use this skill whenever the user needs to work with CUBRID database testing - whether
  writing SQL test files, shell scripts, isolation tests, or running test suites.
  This skill covers both test case creation and test execution workflows.
---

# CUBRID Test Skill

This skill provides comprehensive support for CUBRID CTP (CUBRID Test Program) testing, including both test case writing and test execution.

## Quick Start: Choose Your Workflow

| What you need to do | Module | Read This |
|---------------------|--------|-----------|
| **Install/setup CTP** | Installer | Read `modules/installer/SKILL.md` |
| **Write a test case** | Writer | Read `modules/writer/SKILL.md` |
| **Run/execute tests** | Runner | Read `modules/runner/SKILL.md` |
| **Both write and run** | Both | Read this file, then relevant module |

## CTP Architecture Overview

CTP is a Java 6 + Ant-built test harness for CUBRID functional testing:

- **Entry point**: `CTP/bin/ctp.sh` dispatches suite runners using `conf/*.conf`
- **Build system**: Ant compiles multiple `*/src` trees into `CTP/build/`, then creates jars per suite
- **Runtime**: Uses URLClassLoader + reflection to call suite `Main.exec(...)` from suite jars
- **Config**: INI-like format (`conf/*.conf`) with nested sections like `[sql/cubrid.conf]`
- **Java baseline**: Strict Java 6 (`source="1.6" target="1.6"`) - no Java 7+ features

### Test Suite Implementations

| Test Suite | Entry Point | Key Implementation |
|------------|-------------|-------------------|
| **SQL/MEDIUM** | `ctp.sh sql` | `CTP/sql/src/com/navercorp/cubridqa/cqt/` - cqt framework |
| **SQL_BY_CCI** | `ctp.sh sql_by_cci` | SQL suite with `sql_interface_type=cci` |
| **Shell** | `ctp.sh shell` | `CTP/shell/src/com/navercorp/cubridqa/shell/` - Quartz scheduler |
| **HA Shell** | `ctp.sh shell` (HA config) | Shell framework with `env.X.ssh.relatedhosts` for HA |
| **Isolation** | `ctp.sh isolation` | `CTP/isolation/src/com/navercorp/cubridqa/isolation/` - .ctl parser |
| **HA Replication** | `ctp.sh ha_repl` | `CTP/ha_repl/src/com/navercorp/cubridqa/ha_repl/` - SQL migration |
| **CDC Replication** | `ctp.sh cdc_repl` | `CTP/cdc_repl/src/com/navercorp/cubridqa/cdc_repl/` |
| **JDBC** | `ctp.sh jdbc` | `CTP/jdbc/bin/run.sh` - External JUnit runner |
| **CCI API tests** | `ctp.sh shell -c conf/cci.conf` | Shell-based CCI API scenarios in private test repo |

## Test Type Selection Guide

| Test Type | File Extension | For Testing... |
|-----------|---------------|----------------|
| SQL/MEDIUM | `.sql` | SQL queries, DDL, DML |
| Shell | `.sh` | CUBRID utilities, parameters |
| HA Shell | `.sh` | HA features on 2-node setup (master + slave) |
| Isolation | `.ctl` | MVCC, concurrency |
| HA Replication | `.test` | Data replication (auto-converted from .sql) |
| JDBC | `.java` | JDBC driver API |
| SQL_BY_CCI | `.sql` | SQL execution through CCI driver |
| CDC Replication | `.sql` | CDC replication checks |
| CCI API (shell-based) | `.sh` + `.c` | CCI API behavior via shell scripts |

## Domain-Specific References

### Test Type References

- **`references/sql.md`** — SQL/MEDIUM test cases and execution
- **`references/shell.md`** — Shell test scripts and execution
- **`references/isolation.md`** — MVCC concurrency tests and execution
- **`references/ha_repl.md`** — HA replication tests and execution
- **`references/ha_shell.md`** — HA shell tests (shell-based HA on 2 nodes)
- **`references/cdc_repl.md`** — CDC replication tests and execution
- **`references/jdbc.md`** — JUnit test cases and execution
- **`references/cci.md`** — C Call Interface tests and execution

### Platform References (for Installation)

- **`references/linux.md`** — Linux installation (CentOS, RHEL, Ubuntu)
- **`references/windows.md`** — Windows installation with Cygwin

## Module Structure

### Installer Module (`modules/installer/`)
For environment setup:
- Prerequisites and common configuration
- OS selection guidance (see `references/linux.md` or `references/windows.md`)
- Environment variables configuration
- Verification and troubleshooting

### Writer Module (`modules/writer/`)
For creating test cases:
- Test case templates
- Naming conventions
- Coding standards
- File structure guidelines

### Runner Module (`modules/runner/`)
For executing tests:
- CTP commands
- Interactive mode procedures
- Result analysis
- Debugging workflows

## CTP Development Commands

| Goal | Command |
|------|---------|
| Build jars after code changes | `cd CTP && ant clean dist` |
| Compile only (no jars) | `cd CTP && ant compile` |
| Run suite with explicit config | `CTP/bin/ctp.sh <suite> -c CTP/conf/<suite>.conf` |
| Run suite with default config | `CTP/bin/ctp.sh sql` (uses `CTP/conf/sql.conf`) |
| SQL/MEDIUM interactive debug | `CTP/bin/ctp.sh sql --interactive` |
| Webconsole for SQL results | `CTP/bin/ctp.sh webconsole start` |

## Configuration

- **Default configs**: `CTP/conf/<suite>.conf` used when `-c` omitted
- **INI sections**: Nested names like `[sql/cubrid.conf]`, `[jdbc/cubrid_broker.conf/%BROKER1]`
- **Environment**: `JAVA_HOME` mandatory; `CTP_HOME` should be set
- **INI utility**: `CTP/bin/ini.sh -s <section> <file> <key> {value}`

## Common Best Practices

### Test Case Documentation Template
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
