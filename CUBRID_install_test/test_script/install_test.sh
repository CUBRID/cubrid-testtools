#!/bin/bash
#
# CUBRID install test - 컨테이너 내부 진입점 (root로 실행)
#
#   bash install_test.sh <패키지 파일> [--pkg-key <키>] [--result-dir <디렉터리>]
#
# 항목마다 "RESULT|<항목>|pass/fail/NA|<사유>" 한 줄을 출력하고, 모두 pass/NA면 종료 코드 0.
# rpm 패키지는 설치/제거만 root로 하고 2~10번 항목은 cubrid 계정으로 실행한다
# (스크립트를 cubrid 홈으로 복사 후 su - cubrid -c "... --phase checks").
#
set -u

# 안전장치: 이 스크립트는 cub_ 프로세스를 kill -9 하고 패키지를 설치/제거하므로 테스트 컨테이너 안에서만 실행한다.
# 러너가 docker run/exec 때 INSTALL_TEST_IN_CONTAINER=<실행 ID> 를 넘기고, 컨테이너 안(/.dockerenv 등)이어야 진행한다.
if [ -z "${INSTALL_TEST_IN_CONTAINER:-}" ] || { [ ! -f /.dockerenv ] && [ ! -f /run/.containerenv ]; }; then
    echo "오류: 테스트 컨테이너 밖에서는 실행할 수 없습니다 (run_install_test.sh 가 만든 컨테이너 안에서만 실행: INSTALL_TEST_IN_CONTAINER 표식과 컨테이너 환경 필요)" >&2
    exit 2
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

PHASE=all
PKG_FILE=""
PKG_KEY=""
RESULT_DIR=/var/tmp/install_test
INSTALL_DIR=""
RUN_USER=root
OS_FAMILY=""
OS_PRETTY=""
OS_LIBC=""
INSTALL_NOTE=""
INSTALL_SCRIPTLET_FAIL=""   # 설치 출력에서 scriptlet 실패가 감지되면 그 첫 줄
ENV_SH=""        # pkg_plan이 정함: 패키지가 만드는 .cubrid.sh 경로(비어 있으면 $HOME/.cubrid.sh)

ITEMS=(install bash_profile createdb service_start server_start broker manager sql stop deletedb uninstall)
CHECK_ITEMS=(bash_profile createdb service_start server_start broker manager sql stop deletedb)
declare -A ST=()

usage() {
    echo "사용법: bash $0 <패키지 파일> [--pkg-key <키>] [--result-dir <디렉터리>]" >&2
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --pkg-file|--pkg-key|--result-dir|--phase|--install-dir|--os-family)
            [ $# -ge 2 ] || usage
            case "$1" in
                --pkg-file)    PKG_FILE=$2 ;;
                --pkg-key)     PKG_KEY=$2 ;;
                --result-dir)  RESULT_DIR=$2 ;;
                --phase)       PHASE=$2 ;;
                --install-dir) INSTALL_DIR=$2 ;;
                --os-family)   OS_FAMILY=$2 ;;
            esac
            shift 2 ;;
        -h|--help) usage ;;
        -*) echo "알 수 없는 인자: $1" >&2; usage ;;
        *)  [ -z "$PKG_FILE" ] || usage
            PKG_FILE=$1
            shift ;;
    esac
done
[ -n "$PKG_FILE" ] || usage

# ---------- 공통 도우미 ----------

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

section() { printf '\n==================== %s ====================\n' "$*"; }

# 명령 실행 + 로그. 종료 코드를 그대로 돌려준다.
run() {
    local rc
    log "\$ $*"
    "$@"
    rc=$?
    log "종료 코드: $rc"
    return $rc
}

result() {
    local item=$1 status=$2 reason=${3:-}
    reason=${reason//$'\n'/ }
    reason=${reason//|//}
    ST[$item]=$status
    printf 'RESULT|%s|%s|%s\n' "$item" "$status" "$reason" | tee -a "$RESULT_DIR/container_results.txt"
}

# require <항목> <선행 항목>... : 선행 항목이 pass가 아니면 fail로 기록하고 1 반환
require() {
    local item=$1 p failed=()
    shift
    for p in "$@"; do
        [ "${ST[$p]:-}" = pass ] || failed+=("$p")
    done
    if [ ${#failed[@]} -gt 0 ]; then
        result "$item" fail "선행 실패($(IFS=,; echo "${failed[*]}"))"
        return 1
    fi
}

cubrid_usable() {
    [ -n "$1" ] && [ -x "$1/bin/cubrid" ] && [ -x "$1/bin/csql" ]
}

# 패키지가 만든 .cubrid.sh 를 읽고, 없거나 다른 경로면 직접 설정 (이후 항목 수행용)
# 읽는 파일: ENV_SH(rpm은 cubrid 계정 홈의 /opt/cubrid/.cubrid.sh), 없으면 현재 계정의 $HOME/.cubrid.sh
load_cubrid_env() {
    local dir=$1 envf=${ENV_SH:-$HOME/.cubrid.sh} exists=없음
    if [ -r "$envf" ]; then
        exists=있음
        set +u
        # shellcheck disable=SC1090
        . "$envf"
        set -u
    fi
    # 설치기/.cubrid.sh가 채우지 못한 변수는 여기서 직접 채운다. 어떤 변수를 채웠는지 기록한다
    # (bash_profile 항목의 판정과는 별개이며, 이 기록으로 "환경 파일이 만든 값"과 "시험 스크립트가 채운 값"을 구분한다).
    if [ "${CUBRID:-}" != "$dir" ]; then
        log "$envf (파일 $exists, 실행 계정 $(id -un))에서 CUBRID=$dir 가 설정되지 않아 직접 설정"
        CUBRID=$dir
        CUBRID_DATABASES=$dir/databases
        log "직접 채움: CUBRID=$CUBRID, CUBRID_DATABASES=$CUBRID_DATABASES"
    fi
    if [ -z "${CUBRID_DATABASES:-}" ]; then
        CUBRID_DATABASES=$CUBRID/databases
        log "직접 채움: CUBRID_DATABASES=$CUBRID_DATABASES ($envf 에 없음)"
    fi
    case ":$PATH:" in
        *":$CUBRID/bin:"*) ;;
        *) PATH=$CUBRID/bin:$PATH; log "직접 채움: PATH 앞에 $CUBRID/bin 추가 ($envf 에 없음)" ;;
    esac
    case ":${LD_LIBRARY_PATH:-}:" in
        *":$CUBRID/lib:"*) ;;
        *)
            LD_LIBRARY_PATH=$CUBRID/lib:$CUBRID/cci/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
            log "직접 채움: LD_LIBRARY_PATH에 $CUBRID/lib:$CUBRID/cci/lib 추가 ($envf 에 없음)"
            ;;
    esac
    export CUBRID CUBRID_DATABASES PATH LD_LIBRARY_PATH
}

detect_os() {
    local id like
    id=$(. /etc/os-release 2>/dev/null; echo "${ID:-}")
    like=$(. /etc/os-release 2>/dev/null; echo "${ID_LIKE:-}")
    OS_PRETTY=$(. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-unknown}")
    if [ -z "$OS_FAMILY" ]; then
        case " $id $like " in
            *" debian "*|*" ubuntu "*) OS_FAMILY=deb ;;
            *) OS_FAMILY=rpm ;;
        esac
    fi
}

# 주요 패키지 버전(glibc/libc6): 이미지 빌드 중 패키지가 올라가므로 OS 이름만으로는 내용을 알 수 없다
detect_libc() {
    if [ "$OS_FAMILY" = deb ]; then
        OS_LIBC="libc6 $(dpkg-query -W -f='${Version}' libc6 2>/dev/null)"
    else
        OS_LIBC="glibc $(rpm -q --qf '%{VERSION}-%{RELEASE}' glibc 2>/dev/null)"
    fi
    case "$OS_LIBC" in *" ") OS_LIBC="${OS_LIBC% } 확인 불가" ;; esac
    OS_LIBC=${OS_LIBC//|//}
}

any_fail() {
    local i
    for i in "${!ST[@]}"; do
        [ "${ST[$i]}" = pass ] || [ "${ST[$i]}" = NA ] || return 0
    done
    return 1
}

# shellcheck source=lib/install.sh
. "$SCRIPT_DIR/lib/install.sh"
# shellcheck source=lib/check.sh
. "$SCRIPT_DIR/lib/check.sh"

# ---------- 2~10번 항목 ----------

run_checks() {
    section "2. 환경변수 설정"
    check_env
    load_cubrid_env "$INSTALL_DIR"
    log "CUBRID=$CUBRID CUBRID_DATABASES=$CUBRID_DATABASES"
    section "3. demodb 생성";      check_createdb
    section "4. 서비스 시작";      check_service_start
    section "5. DB 서버 시작";     check_server_start
    section "6. 브로커";           check_broker
    section "7. CMS 접속";         check_manager
    section "8. query test";       check_sql
    section "9. 서비스 종료";      check_stop
    section "10. demodb 삭제";     check_deletedb
}

# rpm: cubrid 계정으로 2~10번 실행. 빠진 항목은 fail로 채운다.
run_checks_as() {
    local user=$1 home dst cmd rc item st
    if ! id "$user" > /dev/null 2>&1; then
        for item in "${CHECK_ITEMS[@]}"; do
            result "$item" fail "선행 실패(install): $user 계정 없음"
        done
        return
    fi
    home=$(getent passwd "$user" | cut -d: -f6)
    log "\$ chmod 1777 /tmp"
    chmod 1777 /tmp
    dst="$home/test_script"
    rm -rf "$dst"
    cp -r "$SCRIPT_DIR" "$dst"
    chown -R "$user": "$dst" "$RESULT_DIR"
    # su - 는 환경을 초기화하므로 컨테이너 시각대(TZ)를 명시적으로 넘긴다
    cmd=$(printf '%q ' env "INSTALL_TEST_IN_CONTAINER=$INSTALL_TEST_IN_CONTAINER" ${TZ:+"TZ=$TZ"} bash "$dst/install_test.sh" --phase checks \
        --pkg-file "$PKG_FILE" --pkg-key "$PKG_KEY" --install-dir "$INSTALL_DIR" \
        --result-dir "$RESULT_DIR" --os-family "$OS_FAMILY")
    log "\$ su - $user -c \"$cmd\""
    su - "$user" -c "$cmd"
    rc=$?
    log "su 종료 코드: $rc"
    for item in "${CHECK_ITEMS[@]}"; do
        st=$(awk -F'|' -v i="$item" '$1 == "RESULT" && $2 == i { s = $3 } END { print s }' \
            "$RESULT_DIR/container_results.txt")
        if [ -n "$st" ]; then
            ST[$item]=$st
        else
            result "$item" fail "결과 없음(su - $user 실행 종료 코드 $rc)"
        fi
    done
}

save_cubrid_log() {
    if any_fail && [ -n "$INSTALL_DIR" ] && [ -d "$INSTALL_DIR/log" ]; then
        log "실패 항목이 있어 \$CUBRID/log 복사 -> $RESULT_DIR/cubrid_log"
        cp -a "$INSTALL_DIR/log" "$RESULT_DIR/cubrid_log" 2>&1
        [ -f "$INSTALL_DIR/databases/databases.txt" ] && cp "$INSTALL_DIR/databases/databases.txt" "$RESULT_DIR/"
    fi
}

main_all() {
    local item rc=0
    if [ "$(id -u)" -ne 0 ]; then
        echo "root로 실행해야 합니다." >&2
        exit 2
    fi
    case "$RESULT_DIR" in
        /|/root|/tmp|/var|/var/tmp|"") echo "결과 디렉터리로 쓸 수 없는 경로: '$RESULT_DIR'" >&2; exit 2 ;;
    esac
    rm -rf "$RESULT_DIR"
    mkdir -p "$RESULT_DIR"
    : > "$RESULT_DIR/container_results.txt"

    detect_os
    detect_libc
    echo "OSINFO|$OS_PRETTY; $OS_LIBC|$OS_FAMILY"
    section "환경"
    log "OS: $OS_PRETTY ($OS_FAMILY 계열), $OS_LIBC, 패키지: $PKG_FILE ($PKG_KEY)"

    section "TLS 설정 (add_openssl.sh)"
    bash "$SCRIPT_DIR/add_openssl.sh" || log "경고: add_openssl.sh 실패"

    section "1. 패키지 설치"
    pkg_plan "$PKG_FILE"
    check_install

    if ! cubrid_usable "$INSTALL_DIR"; then
        for item in "${CHECK_ITEMS[@]}"; do
            result "$item" fail "선행 실패(install)"
        done
    elif [ "$RUN_USER" = root ]; then
        run_checks
    else
        run_checks_as "$RUN_USER"
    fi

    save_cubrid_log

    section "11. 패키지 제거"
    check_uninstall

    section "요약"
    for item in "${ITEMS[@]}"; do
        printf '  %-14s %s\n' "$item" "${ST[$item]:-?}"
    done
    any_fail && rc=1
    exit $rc
}

main_checks() {
    detect_os
    log "$(id -un) 계정으로 2~10번 항목 수행 (HOME=$HOME)"
    run_checks
    any_fail && exit 1
    exit 0
}

case "$PHASE" in
    all)    main_all ;;
    checks) main_checks ;;
    *)      usage ;;
esac
