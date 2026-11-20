# shellcheck shell=bash
#
# 패키지 종류별 설치/제거. install_test.sh가 source 해서 사용한다.
# 사용하는 전역: OS_FAMILY(deb|rpm), RESULT_DIR, HOME
# 설정하는 전역: INSTALL_DIR($CUBRID가 될 경로), RUN_USER(테스트 실행 계정), INSTALL_NOTE(참고 사유)
#

RPM_FORCE_DEBIAN=0    # deb 계열에서 rpm 설치에 --force-debian 을 썼으면 1 (제거 때도 같이 사용)
# rpm -ivh --nodeps 가 실패했을 때 대신 시도하는 yum install 의 상한(저장소에 접속하지 못해 멈추는 경우 대비).
YUM_TIMEOUT_SEC=300

# 파일명으로 설치 방식 판별: sh | tar | rpm | unknown
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

# 설치 경로와 실행 계정을 정한다(설치 전에도 호출 가능).
pkg_plan() {
    case "$(pkg_kind "$1")" in
        sh)  INSTALL_DIR="$HOME/CUBRID"; RUN_USER=root ;;
        tar) INSTALL_DIR="$HOME/CUBRID"; RUN_USER=root ;;
        rpm) INSTALL_DIR=/opt/cubrid;    RUN_USER=cubrid ;;
        *)   INSTALL_DIR="";             RUN_USER=root ;;
    esac
    # 환경 설정 파일: 패키지가 만드는 ~/.cubrid.sh (rpm은 cubrid 계정 홈 = 설치 경로)
    case "$(pkg_kind "$1")" in
        rpm) ENV_SH="$INSTALL_DIR/.cubrid.sh" ;;
        *)   ENV_SH="$HOME/.cubrid.sh" ;;
    esac
}

# .sh: 비대화형 응답 - 라이선스 동의(y), 하위 디렉터리 생성 안 함(n), 계속(y)
#      -> 현재 디렉터리($INSTALL_DIR)에 설치. ~/.cubrid.sh, ~/.bash_profile은 설치 스크립트가 만든다.
pkg_install_sh() {
    local file=$1
    mkdir -p "$INSTALL_DIR" || return 1
    log "설치 경로: $INSTALL_DIR (응답: license=y, include-subdir=n, continue=y)"
    log "\$ cd $INSTALL_DIR && printf 'y\\nn\\ny\\n' | sh $file"
    ( cd "$INSTALL_DIR" && printf 'y\nn\ny\n' | sh "$file" )
}

# .tar.gz: 압축 해제 후 share/scripts/setup.sh(CBRD-24402)로 환경변수 설정
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
    # setup.sh는 실패해도 종료 코드가 0일 수 있으므로 출력의 오류성 줄을 Note에 남긴다(판정은 그대로)
    errline=$(grep -Ei 'cannot|can.t |fail|error|no such|not found|unable|permission|denied' "$RESULT_DIR/setup_sh.log" | head -n 1)
    if [ -n "$errline" ]; then
        errline=$(printf '%s' "$errline" | tr '\n|' '  ' | cut -c1-120)
        INSTALL_NOTE="${INSTALL_NOTE:+$INSTALL_NOTE; }setup.sh 출력에 오류: $errline"
        log "setup.sh 출력에서 오류성 줄 발견(setup_sh.log): $errline"
    fi
    return $rc
}

# 설치 직후 진단 자료 (rpm 패키지): conf 목록, 끊어진 심볼릭 링크, deb 계열은 rpm DB 기준 파일/설정 파일/검증 + dpkg 미등록 확인
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
    # rpm DB 기준 파일/설정 파일/검증 (rpm 계열, deb 계열 공통). rpm_files.txt는 제거 후 잔존 파일 판단에도 쓴다.
    name=$(pkg_registered_name)
    if [ -n "$name" ]; then
        rpm -ql "$name" > "$RESULT_DIR/rpm_files.txt" 2>&1
        rpm -qc "$name" > "$RESULT_DIR/rpm_conffiles.txt" 2>&1
        rpm -V "$name" > "$RESULT_DIR/rpm_verify.txt" 2>&1
        log "rpm -ql $name: $(wc -l < "$RESULT_DIR/rpm_files.txt")줄 (rpm_files.txt), rpm -qc: $(wc -l < "$RESULT_DIR/rpm_conffiles.txt")줄 (rpm_conffiles.txt), rpm -V: $(wc -l < "$RESULT_DIR/rpm_verify.txt")줄 (rpm_verify.txt)"
        head -n 20 "$RESULT_DIR/rpm_verify.txt" | sed 's/^/    /'
        if [ "$OS_FAMILY" != deb ]; then
            # --nodeps로 설치했으므로 충족되지 않은 의존성을 기록한다(판정에는 쓰지 않고 Note에만 남김)
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

# deb 계열(ubuntu, debian)의 rpm 설치: rpm 명령으로 직접 설치한다(rpm -ivh --nodeps). 대체 경로는 없다.
#   Debian 계열의 rpm은 --force-debian 이 필요할 수 있다 -> 먼저 붙여 보고, 모르는 옵션이면 빼고 재시도.
#   rpm DB가 없어 실패하면 rpm --initdb 후 재시도. 끝내 실패하면 Install fail(사유에 rpm 종료 코드와 첫 오류 줄).
# 설정하는 전역: RPM_FORCE_DEBIAN(--force-debian 을 썼으면 1)
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
        # 출력은 파일로 받은 뒤 보여 준다(설치 스크립트가 남기는 프로세스가 있어도 멈추지 않도록)
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

# .rpm: rpm 계열은 rpm -ivh --nodeps(실패 시 yum install), deb 계열은 rpm 직접 설치(대체 경로 없음)
# 설치 출력 로그 검사: scriptlet 실패는 설치 실패 사유(INSTALL_SCRIPTLET_FAIL), 그 밖의 warning은 Note(INSTALL_NOTE).
# 서명 키 경고(NOKEY, Header ... Signature)는 로그에만 남긴다.
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

    # rpm 계열: 먼저 rpm -ivh --nodeps. 실패하면 그때 yum(없으면 dnf) install 로 설치를 시도한다.
    # 의존 패키지는 OS 쪽 문제로 보고 판정/기록 대상에 넣지 않는다.
    local rc1 rc2
    local rlog="$RESULT_DIR/rpm_install.log"
    log "\$ rpm -ivh --nodeps $file"
    # 출력은 파일로 받은 뒤 보여 준다(설치 스크립트가 남기는 프로세스가 있어도 멈추지 않도록, tee 사용 안 함)
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

# rpm DB에 설치 상태로 등록된 cubrid 패키지 이름(없으면 빈 문자열)
pkg_registered_name() {
    rpm -qa --qf '%{NAME}\n' 2>/dev/null \
        | awk 'tolower($0) ~ /cubrid/' \
        | sort | awk 'NR == 1 || $0 == "cubrid" { n = $0 } END { print n }'
}

# 제거: rpm -ev (deb 계열에서 설치 때 --force-debian 을 썼으면 같이)
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

# 제거 확인: rpm -qa 에 cubrid 가 남아 있으면 그 목록을 출력하고 1, 없으면 0.
pkg_check_removed() {
    local listing
    listing=$(rpm -qa 2>/dev/null | grep -i cubrid)
    [ -n "$listing" ] && printf '%s\n' "$listing"
    [ -z "$listing" ]
}
