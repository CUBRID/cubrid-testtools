<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# CTP/common/script - Shell Script Utilities

## OVERVIEW
35 shell scripts providing shared test operations for all CTP test suites: database lifecycle, broker management, file operations, and test utilities.

## STRUCTURE
```
common/script/
  init.sh                 Test initialization (sourced by all test cases)
  common_functions.sh     Common test utilities
  cubrid_scripts.sh       CUBRID server lifecycle (createdb, deletedb, etc)
  broker_scripts.sh       Broker management (start/stop/status)
  compat_scripts.sh       Compatibility testing helpers
  ha_scripts.sh           HA (High Availability) operations
  jdbc_scripts.sh         JDBC-specific test support
  interface*.sh           Interface test utilities
  util*.sh                Various utility functions
  (20+ additional scripts)
```

## WHERE TO LOOK
| Task | Location |
|---|---|
| Test initialization | `init.sh` - sourced at start of every test case |
| Database operations | `cubrid_scripts.sh` - cubrid_createdb, cubrid_deletedb, etc |
| Broker operations | `broker_scripts.sh` - broker start/stop/status |
| Write test results | `common_functions.sh` - write_ok, write_nok |
| HA-specific operations | `ha_scripts.sh` - HA setup and verification |
| JDBC test support | `jdbc_scripts.sh` - JDBC environment setup |

## CONVENTIONS
- All scripts use `#!/bin/bash` or `#!/bin/sh` with BSD 3-clause license header
- Functions are named with underscores: `cubrid_createdb`, `write_ok`, `init_test`
- Scripts are sourced (`. $init_path/init.sh`), not executed directly
- Test cases must call `init` function before database operations
- Test cases must call `finish` for cleanup
- Results written via `write_ok` (PASS) or `write_nok` (FAIL)

## ANTI-PATTERNS
- Never call cubrid commands directly; always use wrapper functions from `cubrid_scripts.sh`
- Never exit script directly; use `write_ok`/`write_nok` then `finish`
- Don't hardcode paths; use `$init_path` and environment variables

## NOTES
- Scripts are copied into test environment during deployment
- `init.sh` sets up CUBRID environment and sources other required scripts
- For RQG tests, also source `rqg_init.sh` after `init.sh`
