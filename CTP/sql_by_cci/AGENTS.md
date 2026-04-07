<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# sql_by_cci

## Purpose
Native C-based SQL test executor using CUBRID's CCI (Client-Server C Interface). Provides an alternative to JDBC for running SQL test cases, compiled against CUBRID CCI headers and libraries.

## Key Files
| File | Description |
|------|-------------|
| `compile.sh` | Build script: detects CUBRID version, compiles `execute` and `ccqt` binaries using gcc + CCI |
| `execute.c` | Primary SQL executor (~63KB): CCI API-based query execution with DBMS output buffer management |
| `ccqt.c` | Command-line query tool (~8KB) |
| `line_scanner.c` | Input parsing utility (~11KB) |
| `.gitignore` | Excludes compiled binaries |

## For AI Agents

### Working In This Directory
- Build: `./compile.sh` (requires `$CUBRID` environment variable set)
- `compile.sh` auto-detects CUBRID 11.2.0+ vs earlier for include/lib paths
- `execute.c` handles max line length of 5MB, DBMS output buffers (50KB default, 1MB max)
- `ADD_CAS_ERROR_HEADER` macro provides version compatibility
- Compiled binaries are not tracked in git
- To run: `bin/ctp.sh sql_by_cci -c conf/sql_by_cci.conf` (requires `sql_interface_type=cci` in config)

### Testing Requirements
- Compile: set `$CUBRID` then run `./compile.sh`
- Test: `bin/ctp.sh sql_by_cci -c conf/sql_by_cci.conf`

## Dependencies

### External
- CUBRID CCI headers (`cas_cci.h`) and library (`libcascci.a`)
- gcc compiler
- CUBRID installation with `$CUBRID` set

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
