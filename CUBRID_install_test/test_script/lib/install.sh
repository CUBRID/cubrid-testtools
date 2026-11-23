# shellcheck shell=bash
#
# Install/remove per package type. Sourced by install_test.sh.
# Globals used: OS_FAMILY (deb|rpm), RESULT_DIR, HOME
# Globals set: INSTALL_DIR (the path that becomes $CUBRID), RUN_USER (account that runs the tests), INSTALL_NOTE (note text)
#

RPM_FORCE_DEBIAN=0    # 1 if --force-debian was used for the rpm install on the deb family (also used on removal)
# Upper limit of the yum install tried when rpm -ivh --nodeps fails (in case it hangs because it cannot reach the repository).
YUM_TIMEOUT_SEC=300

# Install method from the file name: sh | tar | rpm | unknown
pkg_kind() {
    case "$1" in
        *.rpm) echo rpm ;;
        *.tar.gz|*.tgz) echo tar ;;
        *.sh) echo sh ;;
        *) echo unknown ;;
    esac
}

# CUBRID-11.4.0.1768-87b1b31-Linux.x86_64-debug.sh -> 11.4.0.1768-87b1b31
pkg_version() {
    basename "$1" | sed -n 's/^CUBRID-\([0-9][0-9.]*-[0-9a-f][0-9a-f]*\)-Linux.*/\1/p'
}

pkg_is_debug() {
    case "$(basename "$1")" in
        *-debug.*) return 0 ;;
        *) return 1 ;;
    esac
}

# Decide the install path and the run account (can be called before installing).
pkg_plan() {
    case "$(pkg_kind "$1")" in
        sh)  INSTALL_DIR="$HOME/CUBRID"; RUN_USER=root ;;
        tar) INSTALL_DIR="$HOME/CUBRID"; RUN_USER=root ;;
        rpm) INSTALL_DIR=/opt/cubrid;    RUN_USER=cubrid ;;
        *)   INSTALL_DIR="";             RUN_USER=root ;;
    esac
    # Env file: ~/.cubrid.sh created by the package (for rpm, the cubrid account home = install path)
    case "$(pkg_kind "$1")" in
        rpm) ENV_SH="$INSTALL_DIR/.cubrid.sh" ;;
        *)   ENV_SH="$HOME/.cubrid.sh" ;;
    esac
}

# .sh: non-interactive answers - accept license (y), do not create a subdirectory (n), continue (y)
#      -> installs into the current directory ($INSTALL_DIR). The installer creates ~/.cubrid.sh and ~/.bash_profile.
pkg_install_sh() {
    local file=$1
    mkdir -p "$INSTALL_DIR" || return 1
    log "설치 경로: $INSTALL_DIR (응답: license=y, include-subdir=n, continue=y)"
    log "\$ cd $INSTALL_DIR && printf 'y\\nn\\ny\\n' | sh $file"
    ( cd "$INSTALL_DIR" && printf 'y\nn\ny\n' | sh "$file" )
}

# .tar.gz: extract, then set the environment variables with share/scripts/setup.sh (CBRD-24402)
pkg_install_tar() {
    local file=$1 top setup rc errline
    top=$(tar -tzf "$file" 2>/dev/null | head -n 1 | cut -d/ -f1)
    if [ -z "$top" ]; then
        INSTALL_NOTE="압축 파일 목록을 읽을 수 없음"
        return 1
    fi
    INSTALL_DIR="$HOME/$top"
    log "\$ cd $HOME && tar -xzf $file"
    ( cd "$HOME" && tar -xzf "$file" )
    rc=$?
    if [ $rc -ne 0 ]; then
        INSTALL_NOTE="tar 종료 코드 $rc"
        return $rc
    fi
    setup="$INSTALL_DIR/share/scripts/setup.sh"
    if [ ! -f "$setup" ]; then
        INSTALL_NOTE="setup.sh 없음(CBRD-24402)"
        return 1
    fi
    log "\$ cd $INSTALL_DIR && printf 'y\\n' | sh $setup $INSTALL_DIR"
    ( cd "$INSTALL_DIR" && printf 'y\n' | sh "$setup" "$INSTALL_DIR" ) 2>&1 | tee "$RESULT_DIR/setup_sh.log"
    rc=${PIPESTATUS[0]}
    [ $rc -eq 0 ] || INSTALL_NOTE="setup.sh 종료 코드 $rc"
    # setup.sh may exit 0 even when it fails, so error-like output lines go to the Note (the result is not changed)
    errline=$(grep -Ei 'cannot|can.t |fail|error|no such|not found|unable|permission|denied' "$RESULT_DIR/setup_sh.log" | head -n 1)
    if [ -n "$errline" ]; then
        errline=$(printf '%s' "$errline" | tr '\n|' '  ' | cut -c1-120)
        INSTALL_NOTE="${INSTALL_NOTE:+$INSTALL_NOTE; }setup.sh 출력에 오류: $errline"
        log "setup.sh 출력에서 오류성 줄 발견(setup_sh.log): $errline"
    fi
    return $rc
}

# Diagnostics right after install (rpm package): conf listing, broken symlinks, files/config files/verify from the rpm DB + dpkg registration check on the deb family
pkg_diag_after_install() {
    local name unmet n=0 deps=() d
    [ "$(pkg_kind "$PKG_FILE")" = rpm ] || return 0
    log "\$ ls -l $INSTALL_DIR/conf  (-> $RESULT_DIR/conf_listing.txt)"
    {
        ls -l "$INSTALL_DIR/conf" 2>&1
        echo "--- 끊어진 심볼릭 링크 (find -xtype l):"
        find "$INSTALL_DIR/conf" -xtype l -exec ls -l {} \; 2>&1
    } > "$RESULT_DIR/conf_listing.txt"
    sed 's/^/    /' "$RESULT_DIR/conf_listing.txt"
    # Files/config files/verify from the rpm DB (rpm and deb families). rpm_files.txt is also used to find files left after removal.
    name=$(pkg_registered_name)
    if [ -n "$name" ]; then
        rpm -ql "$name" > "$RESULT_DIR/rpm_files.txt" 2>&1
        rpm -qc "$name" > "$RESULT_DIR/rpm_conffiles.txt" 2>&1
        rpm -V "$name" > "$RESULT_DIR/rpm_verify.txt" 2>&1
        log "rpm -ql $name: $(wc -l < "$RESULT_DIR/rpm_files.txt")줄 (rpm_files.txt), rpm -qc: $(wc -l < "$RESULT_DIR/rpm_conffiles.txt")줄 (rpm_conffiles.txt), rpm -V: $(wc -l < "$RESULT_DIR/rpm_verify.txt")줄 (rpm_verify.txt)"
        head -n 20 "$RESULT_DIR/rpm_verify.txt" | sed 's/^/    /'
        if [ "$OS_FAMILY" != deb ]; then
            # Installed with --nodeps, so record unmet dependencies (only in the Note, not used for the result)
            rpm -V --nofiles "$name" 2>&1 | awk '/^Unsatisfied dependencies/ { f = 1; next } f' > "$RESULT_DIR/rpm_unmet_deps.txt"
            while IFS= read -r d; do
                d=$(printf '%s' "$d" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//; s/ is needed by .*//')
                [ -n "$d" ] || continue
                n=$((n + 1))
                [ $n -le 5 ] && deps+=("$d")
            done < "$RESULT_DIR/rpm_unmet_deps.txt"
            if [ $n -gt 0 ]; then
                unmet=$(join_by ', ' "${deps[@]}")
                [ $n -gt 5 ] && unmet="$unmet 외 $((n - 5))건"
                log "미충족 의존성 ${n}건 (rpm_unmet_deps.txt)"
                sed 's/^/    /' "$RESULT_DIR/rpm_unmet_deps.txt"
                INSTALL_NOTE="${INSTALL_NOTE:+$INSTALL_NOTE; }미충족 의존성(--nodeps로 설치): $unmet"
            else
                log "미충족 의존성 없음 (rpm_unmet_deps.txt)"
            fi
        fi
    else
        log "rpm DB에서 cubrid 패키지를 찾지 못해 rpm 진단 생략"
    fi
    if [ "$OS_FAMILY" = deb ]; then
        dpkg -l 2>/dev/null | grep -i cubrid > "$RESULT_DIR/dpkg_registered.txt"
        log "dpkg -l | grep -i cubrid: $(wc -l < "$RESULT_DIR/dpkg_registered.txt")건 (0이면 dpkg DB에 등록되지 않음)"
        sed 's/^/    /' "$RESULT_DIR/dpkg_registered.txt"
    fi
}
pkg_diag_after_remove() {
    local name=$1 f
    {
        echo "# 제거 후 $INSTALL_DIR 아래에 남은 항목 (find -mindepth 1)"
        find "$INSTALL_DIR" -mindepth 1 2>/dev/null
        echo "# rpm_files.txt 목록 중 제거 후에도 존재하는 파일 (디렉터리 제외)"
        if [ -f "$RESULT_DIR/rpm_files.txt" ]; then
            while IFS= read -r f; do
                case "$f" in /*) ;; *) continue ;; esac
                if { [ -e "$f" ] || [ -L "$f" ]; } && [ ! -d "$f" ]; then echo "$f"; fi
            done < "$RESULT_DIR/rpm_files.txt"
        else
            echo "(rpm_files.txt 없음)"
        fi
    } > "$RESULT_DIR/rpm_after_remove.txt"
    log "제거 후 남은 항목: $(grep -vc '^#' "$RESULT_DIR/rpm_after_remove.txt")개 (rpm_after_remove.txt)"
    grep -v '^#' "$RESULT_DIR/rpm_after_remove.txt" | head -n 20 | sed 's/^/    /'
}

# rpm install on the deb family (ubuntu, debian): install directly with the rpm command (rpm -ivh --nodeps). No fallback.
#   rpm on Debian may need --force-debian -> try with it first, and retry without it if the option is unknown.
#   If it fails because there is no rpm DB, run rpm --initdb and retry. If it still fails, Install fails (reason has the rpm exit code and first error line).
# Globals set: RPM_FORCE_DEBIAN (1 if --force-debian was used)
pkg_install_rpm_deb() {
    local file=$1 rc=1 attempt=0 initdb_done=0 out="$RESULT_DIR/rpm_install.log" opts=(--force-debian) first
    RPM_FORCE_DEBIAN=1
    if ! command -v rpm >/dev/null 2>&1; then
        INSTALL_NOTE="rpm 명령 없음"
        return 127
    fi
    while [ $attempt -lt 4 ]; do
        attempt=$((attempt + 1))
        log "\$ rpm -ivh --nodeps ${opts[*]:-} $file   (시도 $attempt)"
        # Capture output to a file, then show it (so it does not hang on processes the install scripts leave behind)
        rpm -ivh --nodeps ${opts[@]+"${opts[@]}"} "$file" > "$out" 2>&1
        rc=$?
        cat "$out"
        log "rpm -ivh 종료 코드: $rc"
        [ $rc -eq 0 ] && break
        if [ ${#opts[@]} -gt 0 ] && grep -Eqi 'unknown option|unrecognized option|invalid option|bad option|unknown --' "$out"; then
            log "rpm이 --force-debian 을 모른다고 함 -> 옵션 없이 재시도"
            opts=(); RPM_FORCE_DEBIAN=0
            continue
        fi
        if [ $initdb_done -eq 0 ] && grep -Eqi 'cannot open Packages|rpmdb|rpm database|database .*(open|init)|initdb|error: db' "$out"; then
            initdb_done=1
            log "rpm DB 문제로 보임 -> rpm --initdb 후 재시도"
            rpm --initdb 2>&1 | sed 's/^/    /'
            continue
        fi
        break
    done
    if [ $rc -eq 0 ]; then
        INSTALL_NOTE=""
        log "rpm 직접 설치 성공 (옵션: ${opts[*]:-없음}, 시도 $attempt회)"
        return 0
    fi
    first=$(grep -Ei 'error|fail|cannot|unable|denied|not ' "$out" | head -n 1)
    [ -n "$first" ] || first=$(grep -v '^[[:space:]]*$' "$out" | tail -n 1)
    first=$(printf '%s' "$first" | tr '\n|' '  ' | cut -c1-150)
    INSTALL_NOTE="rpm -ivh --nodeps 실패(rc=$rc)${first:+: $first}"
    return $rc
}

# .rpm: rpm family uses rpm -ivh --nodeps (yum install on failure); deb family installs with rpm directly (no fallback)
# Install output check: a scriptlet failure is an install failure reason (INSTALL_SCRIPTLET_FAIL); other warnings go to the Note (INSTALL_NOTE).
# Signing key warnings (NOKEY, Header ... Signature) are kept only in the log.
pkg_scan_install_log() {
    local f=$1 line
    [ -s "$f" ] || return 0
    line=$(grep -Ei 'scriptlet failed|Error in (PRE|POST)(IN|UN) scriptlet' "$f" | head -n 1)
    if [ -n "$line" ] && [ -z "${INSTALL_SCRIPTLET_FAIL:-}" ]; then
        INSTALL_SCRIPTLET_FAIL=$(short "$(printf '%s' "$line" | tr '|' ' ')" 150)
        log "설치 스크립트(scriptlet) 실패 감지: $INSTALL_SCRIPTLET_FAIL ($(basename "$f"))"
    fi
    line=$(grep -E '^warning:' "$f" | grep -Eiv 'scriptlet|NOKEY|Header V[0-9]+ .*[Ss]ignature|signature' | head -n 1)
    if [ -n "$line" ]; then
        line=$(short "$(printf '%s' "$line" | tr '|' ' ')" 150)
        log "설치 출력 warning: $line ($(basename "$f"))"
        INSTALL_NOTE="${INSTALL_NOTE:+$INSTALL_NOTE; }설치 출력 $line"
    fi
}

pkg_install_rpm() {
    local file=$1 rc
    INSTALL_SCRIPTLET_FAIL=""
    rm -f "$RESULT_DIR/rpm_install.log" "$RESULT_DIR/yum_install.log"
    if [ "$OS_FAMILY" = deb ]; then
        pkg_install_rpm_deb "$file"
        rc=$?
    else
        pkg_install_rpm_native "$file"
        rc=$?
    fi
    pkg_scan_install_log "$RESULT_DIR/rpm_install.log"
    pkg_scan_install_log "$RESULT_DIR/yum_install.log"
    return $rc
}

pkg_install_rpm_native() {
    local file=$1 rc pm

    # rpm family: first rpm -ivh --nodeps. If it fails, try installing with yum (dnf if no yum).
    # Dependencies are treated as an OS-side issue and are not judged (unmet ones are only noted).
    local rc1 rc2
    local rlog="$RESULT_DIR/rpm_install.log"
    log "\$ rpm -ivh --nodeps $file"
    # Capture output to a file, then show it (so it does not hang on processes the install scripts leave behind; no tee)
    rpm -ivh --nodeps "$file" > "$rlog" 2>&1
    rc1=$?
    cat "$rlog"
    log "rpm -ivh --nodeps 종료 코드: $rc1"
    [ $rc1 -eq 0 ] && return 0

    pm=$(command -v yum 2>/dev/null || command -v dnf 2>/dev/null)
    if [ -z "$pm" ]; then
        INSTALL_NOTE="rpm -ivh --nodeps 실패(rc=$rc1), yum/dnf 없음"
        return $rc1
    fi
    local ylog="$RESULT_DIR/yum_install.log"
    : > "$ylog"
    log "rpm -ivh --nodeps 실패(rc=$rc1) -> $pm install"
    log "\$ $pm install -y $file  (상한 ${YUM_TIMEOUT_SEC}초)"
    timeout -k 10 "$YUM_TIMEOUT_SEC" "$pm" install -y "$file" > "$ylog" 2>&1
    rc2=$?
    cat "$ylog"
    log "$pm install 종료 코드: $rc2"
    if [ $rc2 -eq 0 ]; then
        INSTALL_NOTE="rpm -ivh --nodeps 실패(rc=$rc1), yum install로 설치"
        return 0
    fi
    case $rc2 in
        124|137) INSTALL_NOTE="rpm -ivh --nodeps 실패(rc=$rc1), yum install 시간 초과(${YUM_TIMEOUT_SEC}초, rc=$rc2)" ;;
        *)       INSTALL_NOTE="rpm -ivh --nodeps 실패(rc=$rc1), yum install 실패(rc=$rc2)" ;;
    esac
    return $rc2
}

pkg_install() {
    local file=$1
    INSTALL_NOTE=""
    pkg_plan "$file"
    case "$(pkg_kind "$file")" in
        sh)  pkg_install_sh "$file" ;;
        tar) pkg_install_tar "$file" ;;
        rpm) pkg_install_rpm "$file" ;;
        *)   INSTALL_NOTE="알 수 없는 패키지 형식"; return 1 ;;
    esac
}

# Name of the cubrid package registered as installed in the rpm DB (empty if none)
pkg_registered_name() {
    rpm -qa --qf '%{NAME}\n' 2>/dev/null \
        | awk 'tolower($0) ~ /cubrid/' \
        | sort | awk 'NR == 1 || $0 == "cubrid" { n = $0 } END { print n }'
}

# Remove: rpm -ev (with --force-debian if it was used on install on the deb family)
pkg_uninstall() {
    local name=$1
    if [ "$OS_FAMILY" = deb ] && [ "${RPM_FORCE_DEBIAN:-0}" = 1 ]; then
        log "\$ rpm -ev --force-debian $name"
        rpm -ev --force-debian "$name"
    else
        log "\$ rpm -ev $name"
        rpm -ev "$name"
    fi
}

# Removal check: if cubrid is still in rpm -qa, print the list and return 1; otherwise 0.
pkg_check_removed() {
    local listing
    listing=$(rpm -qa 2>/dev/null | grep -i cubrid)
    [ -n "$listing" ] && printf '%s\n' "$listing"
    [ -z "$listing" ]
}
