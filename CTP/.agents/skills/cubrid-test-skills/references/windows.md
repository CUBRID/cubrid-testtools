# CTP Installation - Windows (Cygwin)

Complete guide for installing CTP on Windows using Cygwin.

## Prerequisites

| Component | Requirement | Notes |
|-----------|-------------|-------|
| Visual Studio 2017 | Required | For CUBRID/bin/make_locale.bat |
| Cygwin | 64-bit | Specific package versions required |
| Java | 6 or higher | Windows JDK |
| CUBRID Database | Installed | Separate installation required |
| Git for Windows | Required | Specific options needed |

## Step 1: Install Visual Studio 2017

### Download
Download Visual Studio 2017 Community from Microsoft.

### Installation Options
1. Launch installer
2. Select **Workloads** tab
3. In **Windows (3)** section, choose **Desktop development with C++**
4. Click **Install** or **Modify**

### Verify Environment Variable
After installation, check system variable:
```
VS140COMNTOOLS=C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\
```

**If not set automatically**, add manually:
```
VS140COMNTOOLS=C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\
```

## Step 2: Install Cygwin

### Download
Download `setup-x86_64.exe` from https://cygwin.com/

### Required Packages
Install these packages (DO NOT install gcc or MinGW):
- `wget`
- `zip`
- `unzip`
- `dos2unix`
- `bc`
- `expect`

### CRITICAL: Package Version Requirements

**Versions to AVOID** (cause test failures):
```
grep: 3.0-2
gawk: 4.1.4-3
sed: 4.4-1
```

**Correct versions** (must use these):
```
grep: 3.0-1
gawk: 4.1.3-1
sed: 4.2.2-3
```

### Why Specific Versions Matter

Newer grep (3.0-2+) modifies behavior to no longer force text mode on binary-mounted file descriptors. This causes `\r` (carriage return) to be appended to text on Windows.

Since test cases are shared between Linux and Windows platforms, we cannot modify the test cases. We must use the older package versions that maintain Linux-compatible behavior.

### Installing Specific Versions

#### Method 1: From Internet
1. Start `setup-x86_64.exe`
2. In **Choose A Download Source**, select **Install from Internet**
3. In **Select Packages**:
   - Set **View** to **Full**
   - In **Search**, type package name (e.g., `grep`)
   - Find the line: `grep: search for regular expressions in text files`
   - Click the **New** column (second column) until version shows `3.0-1`
4. **IMPORTANT**: Before continuing:
   - Select **View** → **Pending**
   - Verify only intended packages are in the pending list
   - If other packages show updates, click their **New** column to mark as **Keep**
   - **Note**: Install gawk, grep, and sed together in one session
5. Complete installation with default options

#### Method 2: From Local Directory (if Method 1 fails)
If required versions aren't available online:
1. Find your previous download path (e.g., `C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f`)
2. Download the previous version package
3. Place in correct subdirectory (e.g., `x86_64\release\grep`)
4. Edit `setup.ini` to add `[prev]` section with version info
5. Run `setup-x86_64.exe`, choose **Install from Local Directory**
6. Specify the local package directory path

## Step 3: Configure Cygwin

### Update Windows PATH
Add to Windows environment variables:
```
PATH=C:\cygwin64\bin;...existing paths...
```

### Configure /etc/fstab
Edit `/etc/fstab` in Cygwin:
```
none / cygdrive binary,noacl,posix=0,user 0 0
```

**Note**: This is required for SHELL tests on Windows.

### Set JAVA_HOME in Cygwin

Add to `~/.bashrc` or `~/.bash_profile`:
```bash
# Java (adjust path as needed)
export JAVA_HOME=/cygdrive/c/Program\ Files/Java/jdk1.8.0_xxx
export PATH=$JAVA_HOME/bin:$PATH
```

Or if using Windows Java:
```bash
export JAVA_HOME=$(cygpath -u "C:\Program Files\Java\jdk1.8.0_xxx")
export PATH=$JAVA_HOME/bin:$PATH
```

## Step 4: Install Git for Windows

### Download
Download from https://git-for-windows.github.io/

### Installation Options
1. **Adjusting your PATH environment**:
   - Select **Use Git from the Windows Command Prompt**

2. **Configuring the line ending conversions**:
   - Select **Checkout as-is, commit as-is** (critical!)

## Step 5: Install CTP

Follow the same steps as Linux installation:

```bash
cd ~
git clone https://github.com/CUBRID/cubrid-testtools.git
cd ~/cubrid-testtools
git checkout develop
cp -rf CTP ~/
```

### Set CTP Environment

Add to `~/.bashrc` or `~/.bash_profile`:
```bash
export CTP_HOME=$HOME/CTP
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH
export CTP_SKIP_UPDATE=0
export CTP_BRANCH_NAME=develop
```

Apply changes:
```bash
source ~/.bashrc
```

## Environment Variables Summary

Complete `~/.bash_profile`:
```bash
# Java
export JAVA_HOME=/cygdrive/c/Program\ Files/Java/jdk1.8.0_xxx
export PATH=$JAVA_HOME/bin:$PATH

# CUBRID (adjust path)
export CUBRID=/cygdrive/c/CUBRID
export CUBRID_DATABASES=$CUBRID/databases
export PATH=$CUBRID/bin:$PATH

# CTP
export CTP_HOME=$HOME/CTP
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH
export CTP_SKIP_UPDATE=0
export CTP_BRANCH_NAME=develop

# Visual Studio (if needed)
export VS140COMNTOOLS="C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\"
```

## Windows-Specific Troubleshooting

### Line ending problems (\r in output)
**Symptom**: Tests fail due to carriage returns in output
**Cause**: Wrong grep/gawk/sed versions
**Solution**: Reinstall Cygwin packages with correct versions (see Step 2)

### VS140COMNTOOLS not set
**Symptom**: CUBRID locale build fails
**Solution**: Add `VS140COMNTOOLS` to Windows environment variables

### Path not found in Cygwin
**Symptom**: Commands not found despite installation
**Solution**: Ensure `C:\cygwin64\bin` is in Windows PATH

### fstab issues
**Symptom**: SHELL tests fail on file operations
**Solution**: Verify `/etc/fstab` has correct cygdrive settings (see Step 3)

### Git line ending issues
**Symptom**: Test cases have `\r\n` instead of `\n`
**Cause**: Wrong Git for Windows settings
**Solution**: Reinstall Git with **Checkout as-is, commit as-is** option

### Java not found in Cygwin
**Symptom**: `java: command not found`
**Solution**: Use `cygpath` to convert Windows path:
```bash
export JAVA_HOME=$(cygpath -u "C:\Program Files\Java\jdk1.8.0_xxx")
```

### Permission denied
**Symptom**: Cannot execute ctp.sh
**Solution**: 
```bash
chmod +x $CTP_HOME/bin/*
dos2unix $CTP_HOME/bin/*
```

## Verification Commands

### Verify Cygwin Setup
```bash
# Check Cygwin version
uname -a

# Verify package versions
grep --version   # Should show 3.0-1 or earlier
gawk --version   # Should show 4.1.3 or earlier
sed --version    # Should show 4.2.2 or earlier

# Check line endings
echo $PATH | od -c | head -1
# Should NOT show \r (carriage return)
```

### Verify Java
```bash
java -version
echo $JAVA_HOME
```

### Verify CUBRID
```bash
cubrid --version
echo $CUBRID
```

### Verify CTP
```bash
ctp.sh -h
which ctp.sh
```

## Quick Verification Checklist

```bash
# 1. Verify Cygwin packages
echo "grep version:" && grep --version | head -1
echo "gawk version:" && gawk --version | head -1
echo "sed version:" && sed --version | head -1

# 2. Check for line ending issues
echo "Checking for \\r in PATH..."
echo $PATH | grep -q $'\r' && echo "ERROR: \\r found!" || echo "OK: No \\r"

# 3. Verify Java
java -version

# 4. Verify CUBRID
cubrid_rel 2>/dev/null || echo "CUBRID not in PATH"

# 5. Verify CTP
ctp.sh -h | head -5

# 6. Check environment
env | grep -E "^(JAVA_HOME|CUBRID|CTP_HOME|VS140COMNTOOLS)" | head -10
```

## Post-Installation

1. Configure test suite-specific `.conf` files in `$CTP_HOME/conf/`
2. Set `scenario` path using Cygwin paths (e.g., `/home/user/cubrid-testcases/sql`)
3. Run a single test to verify setup: `ctp.sh sql -c $CTP_HOME/conf/sql.conf`
4. Review test-specific guides in `references/*.md`

(End of file - total 236 lines)
