# shellcheck shell=bash
#
# One function per checklist item. Sourced by install_test.sh.
# Each function calls result <item> <pass|fail|NA> <reason> exactly once.
# Globals used: PKG_FILE, INSTALL_DIR, RESULT_DIR, SCRIPT_DIR, OS_FAMILY, ST[]
# PROFILE_D_FILE: for unit tests only (not an operating option). Replaces the /etc/profile.d/cubrid.sh path that check_env
#   and check_uninstall look at with a temp file during tests. The runner does not pass it to containers, so real runs always use the default.
#

DB_NAME=demodb
CMS_URL=https://localhost:8001/cm_api
CMS_LOGIN_BODY='{"task":"login","id":"admin","password":"admin","clientver":"8.4"}'
CMS_WAIT_TRIES=20        # login retries (3 seconds apart) - waiting for cub_manager to start
STOP_WAIT_SEC=30         # wait after stop until cub_ processes are gone

CMS_TOKEN=""
CMS_LOGIN_TRIED=0
CMS_LOGIN_ERR=""

# ---------- common helpers ----------

db_registered() {
    local f="${CUBRID_DATABASES:-$CUBRID/databases}/databases.txt"
    [ -f "$f" ] && awk -v db="$1" '$1 == db { f = 1 } END { exit !f }' "$f"
}

# First value in the form "key" : "value"
json_str() {
    sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" | head -n 1
}

# Running cub_* processes: list of "pid name" lines.
# Matches (^|_)cub_ against both the process name (comm) and the basename of the first command line token
# (CAS args look like broker1_cub_cas_N). The check script itself (bash, ps, awk, etc.) is excluded.
cub_proc_list() {
    ps -eo pid=,comm=,args= 2>/dev/null | awk '
        {
            pid = $1; comm = $2; a = $3; sub(/.*\//, "", a)
            if (comm ~ /^(ps|awk|grep|sed|bash|sh|su|sleep|timeout|tee|xargs)$/) next
            if (comm ~ /(^|_)cub_/) name = comm
            else if (a ~ /(^|_)cub_/) name = a
            else next
            print pid, name
        }'
}

# "name(pid) ..."
cub_procs() {
    cub_proc_list | awk '{ printf "%s(%s) ", $2, $1 }'
}

cub_pids() {
    cub_proc_list | awk '{ print $1 }'
}

# Whether we are inside a test container (marker from the runner + container environment). Guards against kill when only sourcing the functions.
in_test_container() {
    [ -n "${INSTALL_TEST_IN_CONTAINER:-}" ] && { [ -f /.dockerenv ] || [ -f /run/.containerenv ]; }
}

# Whether the master process is up (by service status or by process)
master_running() {
    cubrid service status 2>&1 | grep -q 'master is running' && return 0
    cub_proc_list | awk '$2 ~ /(^|_)cub_master$/ { f = 1 } END { exit !f }'
}

short() {
    printf '%s' "$1" | tr '\n' ' ' | cut -c1-"${2:-150}"
}

join_by() {
    local sep=$1 out="" x
    shift
    for x in "$@"; do
        out="${out:+$out$sep}$x"
    done
    printf '%s' "$out"
}

curl_tls_opts() {
    # debian/ubuntu: CMS fails to negotiate TLS 1.3, so pin 1.2
    if [ "$OS_FAMILY" = deb ]; then
        printf '%s\n' --tlsv1.2 --tls-max 1.2
    else
        printf '%s\n' --tlsv1.2
    fi
}

cms_post() {
    local opts
    mapfile -t opts < <(curl_tls_opts)
    curl -sS -k --max-time 15 "${opts[@]}" -X POST "$CMS_URL" \
        -H 'Accept: text/plain;charset=utf-8' -d "$1"
}

# Log in once and reuse the token (shared by the broker and manager items)
cms_login() {
    local i resp=""
    if [ "$CMS_LOGIN_TRIED" -eq 0 ]; then
        CMS_LOGIN_TRIED=1
        for i in $(seq 1 "$CMS_WAIT_TRIES"); do
            resp=$(cms_post "$CMS_LOGIN_BODY" 2>&1)
            CMS_TOKEN=$(printf '%s\n' "$resp" | json_str token)
            [ -n "$CMS_TOKEN" ] && break
            log "CMS 로그인 재시도 $i/$CMS_WAIT_TRIES: $(short "$resp")"
            sleep 3
        done
        printf '%s\n' "$resp" > "$RESULT_DIR/cms_login.txt"
        log "CMS login 응답: $(short "$resp" 300)"
        [ -z "$CMS_TOKEN" ] && CMS_LOGIN_ERR=$(short "$resp" 120)
    fi
    [ -n "$CMS_TOKEN" ]
}

# ---------- 1. package install ----------
check_install() {
    local rc rel expected actual problems=()
    if [ "$(pkg_kind "$PKG_FILE")" = unknown ]; then
        result install fail "알 수 없는 패키지 형식: $(basename "$PKG_FILE")"
        return
    fi
    if [ ! -r "$PKG_FILE" ]; then
        result install fail "패키지 파일 없음: $PKG_FILE"
        return
    fi

    pkg_install "$PKG_FILE"
    rc=$?
    log "설치 종료 코드: $rc, 설치 경로: $INSTALL_DIR"

    if ! cubrid_usable "$INSTALL_DIR"; then
        result install fail "설치 실패(rc=$rc${INSTALL_NOTE:+, $INSTALL_NOTE}): $INSTALL_DIR/bin/cubrid 없음"
        return
    fi
    [ $rc -eq 0 ] || problems+=("설치 종료 코드 $rc${INSTALL_NOTE:+($INSTALL_NOTE)}")
    [ -z "${INSTALL_SCRIPTLET_FAIL:-}" ] || problems+=("설치 스크립트 실패: $INSTALL_SCRIPTLET_FAIL")

    load_cubrid_env "$INSTALL_DIR"
    pkg_diag_after_install
    rel=$(cubrid_rel 2>&1)
    printf '%s\n' "$rel"
    expected=$(pkg_version "$PKG_FILE")
    actual=$(printf '%s\n' "$rel" | sed -n 's/.*(\([0-9][0-9.]*-[0-9a-f][0-9a-f]*\)).*/\1/p' | head -n 1)
    if [ -z "$expected" ]; then
        problems+=("파일명에서 버전을 읽을 수 없음")
    elif [ "$expected" != "$actual" ]; then
        problems+=("cubrid_rel 버전 불일치(기대 $expected, 실제 ${actual:-없음})")
    fi
    if pkg_is_debug "$PKG_FILE"; then
        printf '%s\n' "$rel" | grep -q 'debug build' || problems+=("debug 패키지인데 cubrid_rel이 debug build 아님")
    else
        printf '%s\n' "$rel" | grep -q 'release build' || problems+=("release 패키지인데 cubrid_rel이 release build 아님")
    fi

    if [ ${#problems[@]} -gt 0 ]; then
        result install fail "$(join_by '; ' "${problems[@]}")"
    else
        result install pass "${actual}${INSTALL_NOTE:+; $INSTALL_NOTE}"
    fi
}

# ---------- 2. environment variables (new login shell) ----------
check_env() {
    local out cub bin expect f out2 cub2 dbs ldp missing=() reg=0 pd=${PROFILE_D_FILE:-/etc/profile.d/cubrid.sh} hint=""
    for f in "$HOME/.bash_profile" "$HOME/.cubrid.sh"; do
        log "$f (주석 제외):"
        grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$' "$f" 2>&1 | sed 's/^/    /'
    done
    # Start a new login shell with env -i so it does not inherit the current process environment
    out=$(env -i HOME="$HOME" USER="$(id -un)" LOGNAME="$(id -un)" SHELL=/bin/bash TERM=dumb \
        PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
        bash -lc 'echo "ENV_CUBRID=${CUBRID:-}"; echo "ENV_CUBRID_BIN=$(command -v cubrid 2>/dev/null)"' \
        2>&1 < /dev/null)
    printf '%s\n' "$out" | tee "$RESULT_DIR/env_check.txt"
    cub=$(printf '%s\n' "$out" | sed -n 's/^ENV_CUBRID=//p' | tail -n 1)
    bin=$(printf '%s\n' "$out" | sed -n 's/^ENV_CUBRID_BIN=//p' | tail -n 1)
    expect=$(readlink -f "$INSTALL_DIR")

    # (a) ~/.bash_profile must be set to read .cubrid.sh.
    #     A login shell check alone cannot tell it from values set by /etc/profile.d/cubrid.sh.
    if grep -Eq '^[[:space:]]*(\.|source)[[:space:]].*\.cubrid\.sh' "$HOME/.bash_profile" 2>/dev/null; then
        reg=1
    fi
    log "~/.bash_profile 의 .cubrid.sh 등록: $([ $reg -eq 1 ] && echo 있음 || echo 없음)"

    # (b) Whether the variables are set in a minimal environment that reads only .cubrid.sh (.bash_profile is not run directly; no set -u)
    out2=$(env -i HOME="$HOME" PATH=/usr/bin:/bin bash --noprofile --norc -c \
        '. "$HOME/.cubrid.sh" >/dev/null 2>&1
         echo "E2_CUBRID=${CUBRID:-}"; echo "E2_DBS=${CUBRID_DATABASES:-}"; echo "E2_LD=${LD_LIBRARY_PATH:-}"' \
        2>&1 < /dev/null)
    printf '%s\n' "$out2" | sed 's/^/    [.cubrid.sh 단독] /' | tee -a "$RESULT_DIR/env_check.txt"
    cub2=$(printf '%s\n' "$out2" | sed -n 's/^E2_CUBRID=//p' | tail -n 1)
    dbs=$(printf '%s\n' "$out2" | sed -n 's/^E2_DBS=//p' | tail -n 1)
    ldp=$(printf '%s\n' "$out2" | sed -n 's/^E2_LD=//p' | tail -n 1)

    if [ -z "$cub" ]; then
        result bash_profile fail "새 로그인 셸에서 \$CUBRID 미설정"
    elif [ "$(readlink -f "$cub")" != "$expect" ]; then
        result bash_profile fail "\$CUBRID=$cub (기대 $INSTALL_DIR)"
    elif [ -z "$bin" ]; then
        result bash_profile fail "PATH에 cubrid 없음"
    elif [ $reg -ne 1 ]; then
        # Say that /etc/profile.d/cubrid.sh set the environment only when it actually exists
        [ -e "$pd" ] && hint="(/etc/profile.d 로만 설정됨)"
        result bash_profile fail "~/.bash_profile 에 .cubrid.sh 등록 없음$hint"
    elif [ -z "$cub2" ] || [ "$(readlink -f "$cub2")" != "$expect" ]; then
        result bash_profile fail ".cubrid.sh 만 읽은 환경에서 \$CUBRID=${cub2:-미설정} (기대 $INSTALL_DIR)"
    else
        [ -n "$dbs" ] || missing+=(CUBRID_DATABASES)
        case ":$ldp:" in
            *":$cub2/lib:"*|*":$expect/lib:"*|*":$INSTALL_DIR/lib:"*) ;;
            *) missing+=("LD_LIBRARY_PATH(\$CUBRID/lib 없음)") ;;
        esac
        if [ ${#missing[@]} -gt 0 ]; then
            result bash_profile fail ".cubrid.sh 에 변수 누락: $(join_by ', ' "${missing[@]}")"
        else
            result bash_profile pass ""
        fi
    fi
}

# ---------- 3. create demodb ----------
check_createdb() {
    local rc dbdir="${CUBRID_DATABASES:-$CUBRID/databases}/$DB_NAME"
    # The .sh installer and setup.sh create demodb during install -> use it if present, create it only when missing
    # (same as the earlier manual procedure)
    if db_registered "$DB_NAME"; then
        log "설치 과정에서 생성된 $DB_NAME 이 databases.txt에 있어 그대로 사용"
        result createdb pass "설치 시 생성된 $DB_NAME 사용"
        return
    fi
    log "databases.txt에 $DB_NAME 이 없어 make_cubrid_demo.sh로 생성"
    if ! mkdir -p "$dbdir"; then
        result createdb fail "$dbdir 생성 실패"
        return
    fi
    ( cd "$dbdir" && run bash "$CUBRID/demo/make_cubrid_demo.sh" )
    rc=$?
    if [ $rc -ne 0 ]; then
        result createdb fail "make_cubrid_demo.sh 종료 코드 $rc"
    elif ! db_registered "$DB_NAME"; then
        result createdb fail "make_cubrid_demo.sh 후 databases.txt에 $DB_NAME 없음"
    else
        case "$(pkg_kind "$PKG_FILE")" in
            rpm) result createdb pass "make_cubrid_demo.sh로 $DB_NAME 생성" ;;
            *)   result createdb pass "설치기가 ${DB_NAME}를 만들지 않아 make_cubrid_demo.sh로 생성" ;;
        esac
    fi
}

# ---------- 4. service start ----------
check_service_start() {
    local rc sout out fails errline slog="$RESULT_DIR/service_start.log" problems=()
    # Note: the cub_master daemon started by cubrid service start inherits stdout/stderr without closing them.
    # Capturing with $(...) or a pipe (| tee, etc.) hangs because EOF never comes until master exits, so always redirect to a file.
    run cubrid service start > "$slog" 2>&1
    rc=$?
    cat "$slog"
    sout=$(cat "$slog")
    out=$(cubrid service status 2>&1)
    printf '%s\n' "$out"
    if [ $rc -ne 0 ]; then
        # Include the failed components ("++ ... start: fail" lines) and the first error line in the reason
        fails=$(printf '%s\n' "$sout" | sed -n 's/^++ \(.*\): fail.*/\1/p' | sed 's/^cubrid //' | awk '!s[$0]++' | paste -sd, -)
        errline=$(printf '%s\n' "$sout" | grep -v -e '^[[:space:]]*$' -e '^@ ' -e '^++ ' -e '^\[' -e '^This may take' | head -n 1)
        problems+=("cubrid service start 종료 코드 $rc${fails:+; $fails: fail}${errline:+; $(short "$errline" 160)}")
    fi
    printf '%s\n' "$out" | grep -q 'master is running' || problems+=("service status에서 master 미실행")
    if [ ${#problems[@]} -gt 0 ]; then
        result service_start fail "$(join_by '; ' "${problems[@]}")"
    else
        result service_start pass ""
    fi
}

# ---------- 5. DB server start ----------
check_server_start() {
    local rc out problems=()
    require server_start createdb || return
    run cubrid server start "$DB_NAME"
    rc=$?
    out=$(cubrid server status 2>&1)
    printf '%s\n' "$out"
    [ $rc -eq 0 ] || problems+=("cubrid server start 종료 코드 $rc")
    printf '%s\n' "$out" | grep -Eq "Server[[:space:]]+$DB_NAME([^[:alnum:]_]|\$)" \
        || problems+=("server status에 $DB_NAME 없음")
    if [ ${#problems[@]} -gt 0 ]; then
        result server_start fail "$(join_by '; ' "${problems[@]}")"
    else
        result server_start pass ""
    fi
}

# ---------- 6. broker (broker status + CMS getbrokerstatus) ----------
# broker/CMS cannot be checked when master is not up, so only then treat it as a prerequisite failure
require_master() {   # require_master <item>
    if master_running; then
        return 0
    fi
    result "$1" fail "선행 실패(master 미실행): cubrid service start 후에도 cub_master가 떠 있지 않음(service_start: ${ST[service_start]:-미수행})"
    return 1
}

# CMS login failure reason + evidence file (cms_status.txt)
cms_fail_reason() {
    cubrid manager status > "$RESULT_DIR/cms_status.txt" 2>&1
    printf 'CMS 로그인 실패(%s)' "${CMS_LOGIN_ERR:-응답 없음}"
}

check_broker() {
    local out resp note listed problems=()
    require_master broker || return
    out=$(cubrid broker status -b 2>&1)
    printf '%s\n' "$out"
    if printf '%s\n' "$out" | grep -Eiq '(^|[^[:alnum:]_])broker1([^[:alnum:]_]|$)'; then
        listed="broker1 있음"
    else
        listed="broker1 없음"
        problems+=("cubrid broker status에 broker1 없음")
    fi

    if cms_login; then
        resp=$(cms_post "{\"task\":\"getbrokerstatus\",\"token\":\"$CMS_TOKEN\",\"bname\":\"broker1\"}" 2>&1)
        printf '%s\n' "$resp" > "$RESULT_DIR/cms_getbrokerstatus.txt"
        log "CMS getbrokerstatus 응답: $(short "$resp" 300)"
        # Top-level status is success/failure (does not overlap with the status values in the as info)
        if ! printf '%s\n' "$resp" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"success"' \
            || printf '%s\n' "$resp" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"failure"'; then
            note=$(printf '%s\n' "$resp" | json_str note)
            problems+=("$listed, CMS getbrokerstatus 실패: ${note:-$(short "$resp" 80)}")
        fi
    else
        problems+=("$listed, getbrokerstatus 불가: $(cms_fail_reason)")
    fi

    if [ ${#problems[@]} -gt 0 ]; then
        result broker fail "$(join_by '; ' "${problems[@]}")"
    else
        result broker pass ""
    fi
}

# ---------- 7. CMS connection ----------
check_manager() {
    local why
    require_master manager || return
    if cms_login; then
        result manager pass ""
    else
        cubrid manager status > "$RESULT_DIR/cms_status.txt" 2>&1
        # Lines starting with "@ " carry no information, so drop them. Prefer error lines, otherwise "++ " lines
        why=$(grep -v -e '^[[:space:]]*$' -e '^@ ' -e '^++ ' "$RESULT_DIR/cms_status.txt" | head -n 1)
        [ -n "$why" ] || why=$(grep -e '^++ ' "$RESULT_DIR/cms_status.txt" | head -n 1)
        result manager fail "CMS 로그인 token 없음: ${CMS_LOGIN_ERR:-응답 없음}${why:+ ($(short "$why" 120))}"
    fi
}

# ---------- 8. query test ----------
check_sql() {
    local rc drc
    require sql server_start || return
    log "\$ csql -u dba $DB_NAME -i $SCRIPT_DIR/sql_test.sql > $RESULT_DIR/test.log"
    csql -u dba "$DB_NAME" -i "$SCRIPT_DIR/sql_test.sql" > "$RESULT_DIR/test.log" 2> "$RESULT_DIR/csql.err"
    rc=$?
    log "csql 종료 코드: $rc"
    cat "$RESULT_DIR/test.log"
    [ -s "$RESULT_DIR/csql.err" ] && { log "csql stderr:"; cat "$RESULT_DIR/csql.err"; }
    # Keep the original (test.log); remove only the time values in a copy and compare (same substitution as the earlier manual answer comparison)
    sed 's/[0-9]\.[0-9]*[0-9]//g' "$RESULT_DIR/test.log" > "$RESULT_DIR/test.log.cmp"
    diff "$SCRIPT_DIR/sql_test.answer" "$RESULT_DIR/test.log.cmp" > "$RESULT_DIR/sql_test.diff" 2>&1
    drc=$?
    if [ $drc -eq 0 ] && [ $rc -eq 0 ]; then
        result sql pass ""
    elif [ $drc -eq 0 ]; then
        result sql fail "csql 종료 코드 $rc(출력은 답지와 같음, csql.err 참고)"
    else
        log "답지와 차이(sql_test.diff):"
        cat "$RESULT_DIR/sql_test.diff"
        result sql fail "답지와 다름(sql_test.diff)$([ $rc -ne 0 ] && echo ", csql 종료 코드 $rc")"
    fi
}

# ---------- 9. service stop ----------
# The stop item is judged only by its own commands (exit codes, remaining processes).
# (Start failures are already recorded in service_start/server_start, and the Service/Server columns in the report combine start and stop)
check_stop() {
    local rc1 rc2 left i srv_up=0 svc_up=0 problems=()
    # State before stopping (to give a specific reason when a command fails)
    cubrid server status 2>&1 | grep -Eq "Server[[:space:]]+$DB_NAME([^[:alnum:]_]|\$)" && srv_up=1
    master_running && svc_up=1
    log "종료 전 상태: master 실행=$svc_up, $DB_NAME 서버 실행=$srv_up"
    # Always run the stop commands regardless of earlier results, for cleanup
    run cubrid server stop "$DB_NAME"
    rc1=$?
    run cubrid service stop
    rc2=$?
    for i in $(seq 1 "$STOP_WAIT_SEC"); do
        left=$(cub_procs)
        [ -z "$left" ] && break
        sleep 1
    done
    log "남은 cub_ 프로세스: ${left:-없음}"

    if [ $rc1 -ne 0 ]; then
        if [ $srv_up -eq 0 ]; then
            problems+=("server stop 종료 코드 $rc1 (종료 시점에 $DB_NAME 서버가 실행 중이 아니었음)")
        else
            problems+=("server stop 종료 코드 $rc1")
        fi
    fi
    if [ $rc2 -ne 0 ]; then
        if [ $svc_up -eq 0 ]; then
            problems+=("service stop 종료 코드 $rc2 (종료 시점에 master가 실행 중이 아니었음)")
        else
            problems+=("service stop 종료 코드 $rc2")
        fi
    fi
    if [ -n "$left" ]; then
        problems+=("남은 프로세스: $left")
        # Force stop for the next items (deletedb, remove)
        if in_test_container; then
            cub_pids | xargs -r kill -9 2>/dev/null
        else
            log "경고: 테스트 컨테이너 밖이라 강제 종료(kill)를 수행하지 않음"
        fi
    fi

    if [ ${#problems[@]} -gt 0 ]; then
        result stop fail "$(join_by '; ' "${problems[@]}")"
    else
        result stop pass ""
    fi
}

# ---------- 10. delete demodb ----------
check_deletedb() {
    local rc
    require deletedb createdb || return
    run cubrid deletedb "$DB_NAME"
    rc=$?
    if [ $rc -ne 0 ]; then
        result deletedb fail "cubrid deletedb 종료 코드 $rc"
    elif db_registered "$DB_NAME"; then
        result deletedb fail "databases.txt에 $DB_NAME 남아 있음"
    else
        result deletedb pass ""
    fi
}

# ---------- 11. package remove (rpm only) ----------
check_uninstall() {
    local name rc leftover removed notes=() errline residual=() rf
    if [ "$(pkg_kind "$PKG_FILE")" != rpm ]; then
        result uninstall NA ""
        return
    fi
    name=$(pkg_registered_name)
    if [ -z "$name" ]; then
        result uninstall fail "선행 실패(install): 등록된 cubrid 패키지 없음"
        return
    fi
    [ "$name" = cubrid ] || { notes+=("등록된 패키지 이름 $name"); log "등록된 패키지 이름 $name"; }
    pkg_uninstall "$name" 2>&1 | tee "$RESULT_DIR/uninstall.log"
    rc=${PIPESTATUS[0]}
    pkg_diag_after_remove "$name"
    leftover=$(pkg_check_removed)
    removed=$?
    [ -n "$leftover" ] && log "제거 후 남은 항목: $leftover"
    # Judged by the exit code. Error-like output from remove scripts (init scripts, etc.) goes only to the Note.
    # Fail if the executable or the env script is left after removal. Leftover databases/logs/accounts are not judged.
    for rf in "$INSTALL_DIR/bin/cubrid" "${PROFILE_D_FILE:-/etc/profile.d/cubrid.sh}"; do
        if [ -e "$rf" ] || [ -L "$rf" ]; then residual+=("$rf"); fi
    done
    [ ${#residual[@]} -gt 0 ] && log "제거 후에도 남은 파일: ${residual[*]}"
    errline=$(grep -Ei 'no such file|command not found|error|fail|cannot|can.t ' "$RESULT_DIR/uninstall.log" | head -n 1)
    [ -n "$errline" ] && notes+=("제거 스크립트 출력에 오류: $(short "$errline" 120)")

    if [ $rc -ne 0 ]; then
        result uninstall fail "제거 명령 종료 코드 $rc${notes[0]:+; $(join_by '; ' ${notes[@]+"${notes[@]}"})}"
    elif [ $removed -ne 0 ]; then
        result uninstall fail "제거 후에도 설치 상태로 남음: $(short "$leftover" 100)"
    elif [ ${#residual[@]} -gt 0 ]; then
        result uninstall fail "제거 후에도 파일이 남음: $(join_by ', ' "${residual[@]}")"
    else
        result uninstall pass "$(join_by '; ' ${notes[@]+"${notes[@]}"})"
    fi
}
