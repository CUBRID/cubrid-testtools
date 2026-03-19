# CDC Replication Test Cases

Reference for CDC replication (`cdc_repl`) execution in CUBRID CTP.

## Overview

- Suite command: `ctp.sh cdc_repl -c conf/cdc_repl.conf`
- Config file: `CTP/conf/cdc_repl.conf`
- Scenario source: SQL scenarios (`scenario=${HOME}/cubrid-testcases/sql`)
- Architecture: HA-style multi-node configuration with master/slave roles

## Core Configuration Pattern

```ini
# CTP/conf/cdc_repl.conf
env.instance1.master.ssh.host=<master ip>
env.instance1.master.ssh.user=<ssh user>
env.instance1.slave.ssh.host=<slave ip>
env.instance1.slave.ssh.user=<ssh user>

scenario=${HOME}/cubrid-testcases/sql
cubrid_download_url=http://.../CUBRID-<version>-Linux.x86_64.sh
```

## Execution

```bash
ctp.sh cdc_repl -c conf/cdc_repl.conf
```

## Result Location

- Runtime logs: `CTP/result/cdc_repl/current_runtime_logs/`
- Use controller/instance logs to diagnose replication checks and failures.

## Notes

- Use same SSH/env conventions as HA-style replication suites.
- Keep scenario paths reachable from the controller context used in `cdc_repl.conf`.

(End of file)
