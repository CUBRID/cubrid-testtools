<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# common/grepo

## Purpose
Git repository service using JGit (pure Java Git implementation). Manages git cloning, branching, and source code version control for CUBRID build management.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source: `RepoServiceImpl`, `RepoUtil`, `PackageInf`, `EmptyCache` |
| `lib/` | JGit 4.3.1 JAR + OSGi core dependency |

## For AI Agents

### Working In This Directory
- Pure Java git operations via JGit — no native git dependency
- `RepoServiceImpl.java` is the main service implementation
- Used by `common/script/start_grepo_server.sh` and `run_grepo_fetch`

## Dependencies

### External
- org.eclipse.jgit 4.3.1 (pure Java Git)
- org.osgi.core 4.3.0

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
