<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# conf

## Purpose
Centralized INI-style configuration files for all CTP test suites. Each `.conf` file defines test scenario paths, CUBRID server parameters, broker settings, and SSH connection details for remote test instances.

## Key Files
| File | Description |
|------|-------------|
| `sql.conf` | SQL test configuration (scenario path, cubrid.conf overrides, broker settings) |
| `medium.conf` | MEDIUM test configuration (adds `data_file` for initial DB loading) |
| `medium_dev.conf` | MEDIUM development/debug variant |
| `shell.conf` | Shell test configuration (SSH instances, CUBRID ports, scenario path) |
| `shell_ci.conf` | Shell CI pipeline variant |
| `isolation.conf` | Isolation test configuration (multi-instance SSH, scenario path) |
| `ha_repl.conf` | HA replication test config (master/slave SSH pairs, HA port IDs) |
| `cdc_repl.conf` | CDC replication test configuration |
| `ha_shell.conf` | HA shell test variant |
| `jdbc.conf` | JDBC test configuration (scenario path, broker ports) |
| `sql_by_cci.conf` | CCI-based SQL test configuration |
| `sample.conf` | Minimal example for quick-start testing |
| `webconsole.conf` | Webconsole server settings (port, web root) |

## For AI Agents

### Working In This Directory
- Config format is INI with nested section names: `[sql]`, `[sql/cubrid.conf]`, `[sql/cubrid_broker.conf/%BROKER1]`
- Environment variables like `${HOME}`, `${CTP_HOME}` are expanded at runtime
- Default config is `conf/<suite>.conf` when `-c` is omitted from `ctp.sh`
- Use `bin/ini.sh -s <section> <file> <key>` to read/verify config values programmatically

### Common Patterns
- Multi-instance configs use `env.instance1.ssh.host`, `env.instance1.ssh.user`, etc.
- CUBRID params follow the pattern: `default.cubrid.<param>` or section-based `[suite/cubrid.conf]`
- Broker config uses `%` prefix for broker names: `[sql/cubrid_broker.conf/%BROKER1]`

## Dependencies

### Internal
- Read by `common/src/com/navercorp/cubridqa/common/IniData.java`
- Consumed by each suite's `Context.java` class

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
