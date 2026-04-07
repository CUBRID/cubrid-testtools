<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# bin

## Purpose
CTP entry point scripts. All test suite execution starts here via `ctp.sh`, which dispatches to the Java framework.

## Key Files
| File | Description |
|------|-------------|
| `ctp.sh` | Main launcher — sets `CTP_HOME`, invokes `com.navercorp.cubridqa.ctp.CTP` via Java, captures `#SCRIPTCONT` output lines into a temp script and executes them |
| `ini.sh` | INI config read/write utility — invokes `com.navercorp.cubridqa.ctp.IniCommand` |

## For AI Agents

### Working In This Directory
- `ctp.sh` uses bashisms (`PIPESTATUS`, `==`); ensure `/bin/sh` points to bash or invoke with `bash ctp.sh`
- `JAVA_HOME` must be set — the script exits immediately if unset
- The `#SCRIPTCONT` mechanism is **security-sensitive**: Java prints lines ending with `#SCRIPTCONT`, `ctp.sh` extracts them into a temp script and runs it. Any change to these printed commands should be reviewed carefully
- Classpath is minimal: only `common/lib/cubridqa-common.jar` — suite jars are loaded dynamically via URLClassLoader

### Testing Requirements
- After modifying, test with: `CTP/bin/ctp.sh sql -c CTP/conf/sample.conf`
- Verify `JAVA_HOME` error path: unset JAVA_HOME and confirm the script exits with a message

## Dependencies

### Internal
- `common/lib/cubridqa-common.jar` — runtime classpath for both scripts

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
