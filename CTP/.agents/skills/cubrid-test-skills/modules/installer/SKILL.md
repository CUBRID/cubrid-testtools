# Installer Module: Environment Setup

This module guides CTP (CUBRID Test Program) installation and environment configuration.

> **Note**: For test execution, see Runner module. For test writing, see Writer module.

## Quick Start: Choose Your OS

| Operating System | Reference File | For... |
|------------------|----------------|--------|
| **Linux** (CentOS, RHEL, Ubuntu) | `references/linux.md` | All Linux distributions |
| **Windows** (with Cygwin) | `references/windows.md` | Windows 10/11 with Cygwin64 |

## Prerequisites (All Platforms)

| Component | Requirement | Environment Variable |
|-----------|-------------|---------------------|
| Java | 6 or higher | `JAVA_HOME` |
| CUBRID Database | Installed and configured | `CUBRID`, `CUBRID_DATABASES` |
| Git | For cloning repos | - |

## Quick Verification Checklist

```bash
# 1. Verify Java
java -version
# Expected: Java 6+ (check version string)

# 2. Verify CUBRID
cubrid --version
# Expected: CUBRID version info

# 3. Check environment variables
echo $JAVA_HOME
echo $CUBRID
echo $CUBRID_DATABASES

# 4. Verify CTP (after installation)
ctp.sh -h
# Expected: CTP help message
```

## Installation Overview

### Common Steps (Both Platforms)

1. **Install Java** (6 or higher)
2. **Install CUBRID** database separately
3. **Clone and install CTP** from GitHub
4. **Configure environment variables**
5. **Download test cases**
6. **Verify installation**

### Platform-Specific Steps

| Step | Linux | Windows |
|------|-------|---------|
| Package manager | `yum` / `apt-get` | Cygwin setup.exe |
| Special requirements | None | Visual Studio 2017, specific Cygwin package versions |
| Path format | `/home/user/...` | `/cygdrive/c/...` or Cygwin paths |
| Line endings | Unix (LF) | Must use LF (configured in Git) |

## Environment Variables (All Platforms)

Add these to your shell profile (`~/.bash_profile` for Linux, `~/.bashrc` for Cygwin):

```bash
# Java
export JAVA_HOME=/path/to/jdk
export PATH=$JAVA_HOME/bin:$PATH

# CUBRID (must be installed separately)
export CUBRID=/path/to/cubrid
export CUBRID_DATABASES=$CUBRID/databases
export PATH=$CUBRID/bin:$PATH

# CTP
export CTP_HOME=$HOME/CTP
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH

# CTP Update Settings (optional)
export CTP_SKIP_UPDATE=0        # Set to 1 to skip updates
export CTP_BRANCH_NAME=develop  # Branch for updates
```

## Test Case Repositories

Download test cases separately:
```bash
cd ~
git clone https://github.com/CUBRID/cubrid-testcases.git

# Private test cases (if access available)
git clone https://github.com/CUBRID/cubrid-testcases-private.git
```

## Common Configuration (Regression Testing)

For regression test platform setup, create `~/CTP/conf/common.conf`:

```ini
# Git credentials
git_user=<git user>
git_pwd=<git password>
git_email=<email address>

# SSH defaults
default_ssh_pwd=<password for ssh connect>
default_ssh_port=<port for ssh connect>

# CTP update service (optional)
grepo_service_url=rmi://192.168.1.91:11099

# Build repository
cubrid_build_list_url=http://192.168.1.91:8080/REPO_ROOT/list.jsp

# QA database (optional)
qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
qahome_db_user=dba

# Email notifications (optional)
mail_from_nickname=CUBRIDQA_BJ
mail_from_address=qa@cubrid.com
```

## Troubleshooting Summary

### Shell Compatibility Note

`CTP/bin/ctp.sh` may fail on systems where `/bin/sh` is not bash-compatible.
If that happens, run it explicitly with bash:

```bash
bash $CTP_HOME/bin/ctp.sh -h
```

### Common Issues (All Platforms)

| Issue | Solution |
|-------|----------|
| `JAVA_HOME not set` | Add `export JAVA_HOME=/path/to/jdk` to shell profile |
| `java: command not found` | Add `$JAVA_HOME/bin` to PATH |
| Wrong Java version | Install Java 6+ and update JAVA_HOME |
| `CUBRID not found` | Install CUBRID and set `CUBRID` environment variable |
| `ctp.sh: command not found` | Add `$HOME/CTP/bin` to PATH |
| Permission denied | `chmod +x $CTP_HOME/bin/*` |

### Linux-Specific Issues

See `references/linux.md` for:
- Package installation errors
- Environment setup issues
- Distribution-specific problems

### Windows-Specific Issues

See `references/windows.md` for:
- Cygwin package version problems
- Line ending issues (`\r` in output)
- Visual Studio setup
- Git configuration issues

## Cross-Module Reference

- **For Linux details**: See `references/linux.md`
- **For Windows details**: See `references/windows.md`
- **For writing test cases**: See `modules/writer/SKILL.md`
- **For executing tests**: See `modules/runner/SKILL.md`
- **For test type details**: See `references/*.md`

## Post-Installation Steps

1. Read the OS-specific reference (`references/linux.md` or `references/windows.md`)
2. Configure test suite-specific `.conf` files in `$CTP_HOME/conf/`
3. Set `scenario` path to point to your test cases
4. Run a single test to verify setup
5. Review test-specific guides in `references/` directory

(End of file - total 128 lines)
