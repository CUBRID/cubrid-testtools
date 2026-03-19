# CTP Installation - Linux

Complete guide for installing CTP on Linux platforms.

## Prerequisites

| Component | Requirement | Notes |
|-----------|-------------|-------|
| Java | 6 or higher | OpenJDK or Oracle JDK |
| CUBRID Database | Installed | Separate installation required |
| OS | Linux (any distro) | Tested on CentOS, RHEL, Ubuntu |
| Git | For cloning repos | Usually pre-installed |

## Step 1: Install Java

### CentOS/RHEL
```bash
sudo yum install java-1.8.0-openjdk-devel
```

### Ubuntu/Debian
```bash
sudo apt-get update
sudo apt-get install openjdk-8-jdk
```

### Verify Java Installation
```bash
java -version
# Expected: java version "1.8.0_xxx" or higher
echo $JAVA_HOME
# Should show: /usr/lib/jvm/java-1.8.0-openjdk
```

### Set JAVA_HOME

Add to `~/.bash_profile`:
```bash
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
export PATH=$JAVA_HOME/bin:$PATH
```

Apply changes:
```bash
source ~/.bash_profile
```

## Step 2: Install CUBRID

CUBRID must be installed separately. Download from http://www.cubrid.org/

### Set CUBRID Environment

Add to `~/.bash_profile`:
```bash
export CUBRID=/path/to/cubrid
export CUBRID_DATABASES=$CUBRID/databases
export PATH=$CUBRID/bin:$PATH
```

Verify:
```bash
cubrid --version
cubrid_rel
```

## Step 3: Install CTP

```bash
cd ~
git clone https://github.com/CUBRID/cubrid-testtools.git
cd ~/cubrid-testtools
git checkout develop
cp -rf CTP ~/
```

### Set CTP Environment

Add to `~/.bash_profile`:
```bash
export CTP_HOME=$HOME/CTP
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH

# Optional: CTP update settings
export CTP_SKIP_UPDATE=0        # Set to 1 to skip updates
export CTP_BRANCH_NAME=develop  # Branch for updates
```

Apply changes:
```bash
source ~/.bash_profile
```

## Step 4: Verify Installation

```bash
# Check CTP help
ctp.sh -h
```

Expected output:
```
Welcome to use CUBRID Test Program (CTP)
usage: ctp.sh <sql|medium|shell|ha_repl|isolation|cdc_repl|jdbc|unittest> -c <config_file>
   -c,--config <arg>   provide a configuration file
   -h,--help           show help
       --interactive    interactive mode
   -v,--version        show version

utility: ctp.sh webconsole <start|stop>
```

### Shell Compatibility Caveat

On platforms where `/bin/sh` is not bash-compatible (for example Ubuntu with `dash`), run:

```bash
bash $CTP_HOME/bin/ctp.sh sql -c $CTP_HOME/conf/sql.conf
```

## Environment Variables Summary

Complete `~/.bash_profile`:
```bash
# Java
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# CUBRID
export CUBRID=/opt/cubrid
export CUBRID_DATABASES=$CUBRID/databases
export PATH=$CUBRID/bin:$PATH

# CTP
export CTP_HOME=$HOME/CTP
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH
export CTP_SKIP_UPDATE=0
export CTP_BRANCH_NAME=develop
```

## Test Case Repositories

Download test cases:
```bash
cd ~
git clone https://github.com/CUBRID/cubrid-testcases.git

# Private test cases (if access available)
git clone https://github.com/CUBRID/cubrid-testcases-private.git
```

## Common Configuration (Regression Testing)

Create `~/CTP/conf/common.conf` for shared settings:
```ini
# Git credentials
git_user=<git user>
git_pwd=<git password>
git_email=<email address>

# SSH defaults
default_ssh_pwd=<password>
default_ssh_port=22

# Build repository
cubrid_build_list_url=http://192.168.1.91:8080/REPO_ROOT/list.jsp

# Email notifications (optional)
mail_from_nickname=CUBRIDQA
mail_from_address=qa@example.com
```

## Troubleshooting

### JAVA_HOME not set
**Error**: `JAVA_HOME is not set`
**Solution**: Add `export JAVA_HOME=/path/to/jdk` to `~/.bash_profile`

### Java command not found
**Error**: `java: command not found`
**Solution**: Add `$JAVA_HOME/bin` to PATH

### Wrong Java version
**Error**: Unsupported class version
**Solution**: Install Java 6+ and update JAVA_HOME

### CUBRID not found
**Error**: `cubrid: command not found`
**Solution**: Install CUBRID and set `CUBRID` environment variable

### Database creation fails
**Error**: Cannot create database
**Solution**: Check `CUBRID_DATABASES` is set and directory exists

### ctp.sh not found
**Error**: `ctp.sh: command not found`
**Solution**: Add `$HOME/CTP/bin` to PATH

### Permission denied
**Error**: Permission denied on ctp.sh
**Solution**: `chmod +x $CTP_HOME/bin/*`

## Quick Verification Checklist

```bash
# 1. Verify Java
java -version
echo $JAVA_HOME

# 2. Verify CUBRID
cubrid --version
echo $CUBRID
echo $CUBRID_DATABASES

# 3. Verify CTP
ctp.sh -h
which ctp.sh

# 4. Check environment
env | grep -E "^(JAVA_HOME|CUBRID|CTP_HOME)"

# 5. Check test cases
ls $HOME/cubrid-testcases/sql/cases/ 2>/dev/null || echo "Test cases not found - run: git clone https://github.com/CUBRID/cubrid-testcases.git"
```

## Post-Installation

1. Configure test suite-specific `.conf` files in `$CTP_HOME/conf/`
2. Set `scenario` path to point to your test cases
3. Run a single test to verify setup: `ctp.sh sql -c $CTP_HOME/conf/sql.conf`
4. Review test-specific guides in `references/*.md`

(End of file - total 175 lines)
