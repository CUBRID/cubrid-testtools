#!/bin/bash
#
# CUBRID install test 러너 (호스트에서 실행)
#
#   ./run_install_test.sh <빌드 URL | 패키지 디렉터리> [옵션]
#
# 패키지 준비 -> OS별 이미지 빌드(있으면 건너뜀) -> 조합(OS x 패키지)마다 새 컨테이너에서
# test_script/install_test.sh 실행 -> 결과/로그 수집 -> 컨테이너 삭제 -> report.csv / report.md 생성.
# 모든 조합이 pass일 때만 종료 코드 0.
#
set -u
set -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RUNNER_VERSION=v3
RUNNER_SHA=""        # 이 스크립트의 sha256 앞 12자 (main에서 계산)
TESTSCRIPT_SHA=""    # test_script/ 전체 파일의 해시 앞 12자 (main에서 계산)
HOST_INFO="(dry-run)"  # report.md 의 "호스트" 줄 (실행 시 main에서 채움)

# ---------------- 기본값 (한 곳에 모음) ----------------
DEFAULT_JOBS=4
MAX_JOBS=4
# 조합당 제한 시간(초). 기본 1200. --timeout 으로 변경 가능.
DEFAULT_TIMEOUT_SEC=1200
# 이미지 준비 단계 제한 시간(초). 넘으면 그 OS만 fail 처리한다.
PULL_TIMEOUT_SEC=900     # 베이스 이미지 docker pull
DETECT_TIMEOUT_SEC=120   # 계열 판별 컨테이너(/etc/os-release 읽기)
BUILD_TIMEOUT_SEC=1800   # docker build
# 동시 실행 방지용 호스트 공용 잠금 파일(환경변수로 바꿀 수 있음: 시험용)
LOCK_FILE=${INSTALL_TEST_LOCK_FILE:-/tmp/cubrid-install-test.lock}
IMAGE_REPO=cubrid-install-test
CONTAINER_PREFIX=cit
LABEL_KEY=cubrid-install-test.run
CONTAINER_RESULT_DIR=/var/tmp/install_test
# 이미지 빌드(yum/apt) 시 네트워크. 기본 bridge에서 외부 접속이 안 되는 호스트(ip_forward=0)가 있어 host 사용.
BUILD_NETWORK=host
# 이미지 빌드는 legacy builder로 한다. BuildKit은 이미지와 별도로 빌드 캐시를 남겨
# 이미지를 지워도 레이어가 남고, 그 캐시는 다른 프로젝트 것과 섞여 있어 골라 지우기 어렵다.
export DOCKER_BUILDKIT=0
# 테스트 컨테이너 전용 네트워크. host나 기본 bridge(docker0)에 붙이지 않고, 다른 컨테이너와 분리한다.
# 없으면 러너가 만들고(이번 실행이 만든 경우에만 끝나면 지움), 있으면 서브넷이 같은지 확인해 재사용한다.
TEST_NETWORK=install_test
TEST_SUBNET=172.200.255.0/24
TEST_GATEWAY=172.200.255.1
NETWORK_LABEL=cubrid-install-test.network
VERSION_RE='[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+-[0-9a-f]+'

ITEMS=(install bash_profile createdb service_start server_start broker manager sql stop deletedb uninstall)
declare -A ITEM_LABEL=(
    [install]="Install" [bash_profile]="in .bash_profile" [createdb]="Createdb"
    [service_start]="Service start" [server_start]="Server start" [broker]="Broker"
    [manager]="Manager" [sql]="SQL Statement" [stop]="Service/Server stop"
    [deletedb]="Deletedb" [uninstall]="UnInstall"
)
REPORT_HEADER=("Test Build" "Test OS" "Install" "in .bash_profile" "UnInstall" "Service" "Server"
    "Broker" "Manager" "Createdb" "Deletedb" "SQL Statement" "Test build" "Note")

usage() {
    cat <<EOF
사용법: $(basename "$0") <빌드 URL | 패키지 디렉터리> [옵션]

  빌드 URL      패키지 파일 URL 또는 그 파일이 있는 디렉터리 URL.
                필요한 패키지를 test_build/ 로 내려받는다. 같은 이름의 비어 있지 않은 파일이
                이미 있으면 원격 크기와 같거나 원격 크기를 확인할 수 없을 때 건너뛰고, 다르면 다시 받는다.
                예) http://<서버>/REPO_ROOT/store_01/11.4.0.1768-87b1b31/drop/CUBRID-11.4.0.1768-87b1b31-Linux.x86_64.sh
  패키지 디렉터리 CUBRID-<버전>-Linux.x86_64*.{sh,tar.gz,rpm} 파일이 있는 디렉터리 (그대로 사용)
                패키지 종류별로 파일을 찾는다. 한 종류에 여러 버전이 있으면 --version 으로 지정.

옵션:
  --jobs N, -j N     동시에 실행할 조합 수 (1~${MAX_JOBS}, 기본 ${DEFAULT_JOBS})
  --os LIST          일부 OS만 (콤마 구분. OS 키 또는 이미지 이름의 앞부분, 예: ubuntu,rocky9)
  --pkg LIST         일부 패키지만 (콤마 구분. matrix.conf의 패키지 키, 예: rpm,debug.sh)
  --version VER      디렉터리에 여러 빌드 버전이 있을 때 사용할 버전 (예: 11.4.0.1768-87b1b31)
  --timeout SEC      조합당 제한 시간(초, 기본 ${DEFAULT_TIMEOUT_SEC})
  --keep-failed      실패한 조합의 컨테이너(와 그 이미지)를 삭제하지 않고 남김
  --keep-images      끝난 뒤 이미지를 지우지 않음 (기본: 이번 실행이 빌드/pull한 이미지 삭제)
  --rebuild-images   이미지가 있어도 다시 빌드
  --images-only      이미지 빌드(확인)만 하고 종료 (빌드 URL/디렉터리 불필요, --keep-images 포함)
  --check-network    테스트 네트워크(${TEST_NETWORK}, ${TEST_SUBNET})만 준비/확인하고 종료
                     (이번 실행이 만든 네트워크는 종료 시 지움)
  --dry-run          docker 없이 가짜 결과로 대기열/리포트 동작만 확인 (results/dry-run/ 에 기록)
  --matrix FILE      matrix.conf 대신 사용할 파일
  --results-dir DIR  결과 상위 디렉터리 (기본 ./results)
  -h, --help         도움말

결과: <results-dir>/<빌드 버전>/report.csv, report.md, report.xml(JUnit), <OS>__<패키지>/console.log ...
EOF
}

die() { echo "오류: $*" >&2; exit 2; }

# ---------------- 인자 처리 ----------------
SRC=""
JOBS=$DEFAULT_JOBS
TIMEOUT_SEC=$DEFAULT_TIMEOUT_SEC
OS_FILTER=()
PKG_FILTER=()
KEEP_FAILED=0
DRY_RUN=0
REBUILD=0
IMAGES_ONLY=0
KEEP_IMAGES=0
VERSION_FILTER=""
CHECK_NETWORK_ONLY=0
MATRIX_FILE=$SCRIPT_DIR/matrix.conf
RESULTS_BASE=$SCRIPT_DIR/results

# URL 의 authority("://" 뒤 첫 "/" 앞) 안의 마지막 "@" 까지(사용자[:암호], 그 안에 "@" 가 있어도)를 *** 로 가린다.
# 경로의 "@" 는 그대로 둔다. 화면, run.log, report.md, 오류 메시지에 URL을 적을 때 사용(실제 curl 호출에는 원래 URL을 그대로 쓴다).
# "://" 가 없고 첫 "/" 앞에 "사용자:암호@" 꼴이 있으면(스킴을 빠뜨린 입력) 첫 "/" 앞의 마지막 "@" 까지 가린다.
# 디렉터리 경로에는 ":" 를 쓸 수 없으므로(README) ":" 를 요구하면 유효한 디렉터리는 가려지지 않는다.
# 인자 처리 중 die 에서도 쓰므로 가능한 앞쪽에 둔다.
mask_url() { printf '%s' "$1" | sed -E '/:\/\//!s#^[^/:]*:[^/]*@#***@#; s#(://)[^/]*@#\1***@#'; }

# docker 의 name 필터는 정규식이라 컨테이너 이름의 "." 이 아무 글자와도 맞는다. 필터에 넣을 때만 "." 을 "\." 로 바꾼다
# (실제 rm/inspect 에는 정확한 이름 그대로). 이름에 올 수 있는 문자는 영문/숫자/._- 라 "." 만 처리하면 된다.
name_re() { printf '%s' "${1//./\\.}"; }

need_arg() {   # need_arg <옵션> <남은 인자 수> <값>
    [ "$2" -ge 2 ] || die "$1 옵션에 값이 필요합니다"
    case "${3-}" in
        -*) die "$1 의 값이 '-' 로 시작합니다: ${3} (값이 빠졌는지 확인하세요)" ;;
    esac
}
split_csv() { local IFS=,; read -r -a SPLIT <<< "$1"; }
# --os/--pkg 값: 비었거나 앞·뒤·연속 콤마로 빈 항목이 생기면 오류(빈 항목은 모든 키의 접두사라 "전체"로 해석되기 때문)
split_csv_opt() {   # split_csv_opt <옵션> <값>
    local opt=$1 val=$2
    if [ -z "$val" ] || [[ $val == ,* ]] || [[ $val == *, ]] || [[ $val == *,,* ]]; then
        die "$opt 값에 빈 항목이 있습니다: '$val'"
    fi
    split_csv "$val"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --jobs|-j)     need_arg "$1" $# "${2-}"; JOBS=$2; shift 2 ;;
        --jobs=*)      JOBS=${1#*=}; shift ;;
        --timeout)     need_arg "$1" $# "${2-}"; TIMEOUT_SEC=$2; shift 2 ;;
        --timeout=*)   TIMEOUT_SEC=${1#*=}; shift ;;
        --os)          need_arg "$1" $# "${2-}"; split_csv_opt "$1" "$2"; OS_FILTER+=("${SPLIT[@]}"); shift 2 ;;
        --os=*)        split_csv_opt --os "${1#*=}"; OS_FILTER+=("${SPLIT[@]}"); shift ;;
        --pkg)         need_arg "$1" $# "${2-}"; split_csv_opt "$1" "$2"; PKG_FILTER+=("${SPLIT[@]}"); shift 2 ;;
        --pkg=*)       split_csv_opt --pkg "${1#*=}"; PKG_FILTER+=("${SPLIT[@]}"); shift ;;
        --matrix)      need_arg "$1" $# "${2-}"; MATRIX_FILE=$2; shift 2 ;;
        --results-dir) need_arg "$1" $# "${2-}"; RESULTS_BASE=$2; shift 2 ;;
        --keep-failed) KEEP_FAILED=1; shift ;;
        --rebuild-images) REBUILD=1; shift ;;
        --images-only) IMAGES_ONLY=1; shift ;;
        --keep-images) KEEP_IMAGES=1; shift ;;
        --owner|--owner=*)
                       die "--owner 는 지원하지 않는 옵션입니다 (report에서 owner 열을 뺐습니다)" ;;
        --check-network) CHECK_NETWORK_ONLY=1; shift ;;
        --run-network|--run-network=*)
                       die "--run-network 은 지원하지 않습니다. 테스트 컨테이너는 항상 ${TEST_NETWORK} 네트워크(${TEST_SUBNET})에 붙습니다 (README 네트워크 참고)" ;;
        --version)     need_arg "$1" $# "${2-}"; VERSION_FILTER=$2; shift 2 ;;
        --dry-run)     DRY_RUN=1; shift ;;
        -*)            die "알 수 없는 옵션: $1 (--help 참고)" ;;
        *)             [ -z "$SRC" ] || die "빌드 URL/디렉터리는 하나만 지정할 수 있습니다: $(mask_url "$1")"
                       SRC=$1; shift ;;
    esac
done

[ -n "$SRC" ] || [ "$IMAGES_ONLY" -eq 1 ] || [ "$CHECK_NETWORK_ONLY" -eq 1 ] || { usage >&2; exit 2; }
[ "$IMAGES_ONLY" -eq 1 ] && KEEP_IMAGES=1
if ! [[ $JOBS =~ ^[0-9]{1,5}$ ]] || [ $((10#$JOBS)) -lt 1 ] || [ $((10#$JOBS)) -gt "$MAX_JOBS" ]; then
    die "--jobs 는 1~${MAX_JOBS} 사이의 정수여야 합니다: $JOBS"
fi
JOBS=$((10#$JOBS))
if ! [[ $TIMEOUT_SEC =~ ^[0-9]{1,9}$ ]] || [ $((10#$TIMEOUT_SEC)) -lt 1 ]; then
    die "--timeout 은 1 이상의 정수(초)여야 합니다: $TIMEOUT_SEC"
fi
TIMEOUT_SEC=$((10#$TIMEOUT_SEC))
[ -z "$VERSION_FILTER" ] || [[ $VERSION_FILTER =~ ^$VERSION_RE$ ]] \
    || die "--version 형식이 아닙니다(예: 11.4.0.1768-87b1b31): $VERSION_FILTER"
mkdir -p "$RESULTS_BASE" || die "결과 디렉터리를 만들 수 없습니다: $RESULTS_BASE"
RESULTS_BASE=$(cd "$RESULTS_BASE" && pwd)

# ---------------- 실행 상태 ----------------
RUN_ID=$(date +%Y%m%d-%H%M%S)-$$
RUN_TMP=$(mktemp -d "${TMPDIR:-/tmp}/cubrid-install-test.XXXXXX") || die "임시 디렉터리 생성 실패"
RUN_LOG=$RUN_TMP/run.log
KEPT_FILE=$RUN_TMP/kept_containers   # 줄마다 "<OS 키> <컨테이너 이름>"
: > "$KEPT_FILE"
declare -A BASE_PREEXIST=() IMAGE_PREEXIST=() IMAGE_TAG=() BASE_ID=()
declare -A IMAGE_FAMILY=()   # OS 키 -> 계열(deb|rpm|custom|unknown)
declare -A IMAGE_DIR=()      # OS 키 -> 실제 쓰는 Dockerfile 디렉터리
declare -A BUILD_STARTED=()  # 이번 실행이 docker build 를 시작한 OS 키
declare -A BASE_ASSUMED=()   # 시작 때 베이스 이미지 확인이 실패해 "기존 이미지로 간주"한 OS 키
CLEANUP_ARMED=0      # 1이면 정리 단계가 docker 를 건드린다(잠금 획득 뒤, dry-run 은 잠금을 건너뛰는 자리에서 1)
CLEANUP_ERROR=0      # 정리 중 오류(기존 이미지 삭제 등) -> 러너 종료 코드를 0이 아니게
CREATED_FILE=$RUN_TMP/created_containers   # 이번 실행이 만든 컨테이너(한 줄에 하나)
REMOVED_FILE=$RUN_TMP/removed_containers   # 조합 종료 시 지운 컨테이너
: > "$CREATED_FILE"; : > "$REMOVED_FILE"
HOST_TZ=""           # 컨테이너에 넘길 POSIX TZ 값(예: KST-09:00). 호스트의 현재 UTC 오프셋 기준
NETWORK_READY=0      # 이번 실행에서 테스트 네트워크를 쓸 준비가 됨
NETWORK_CREATED=0    # 이번 실행이 테스트 네트워크를 만듦
CLEANUP_DONE=0
CLEANUP_SUMMARY=""
RESULT_ROOT=""
RUN_START=$(date +%s)

say() {
    local line
    line="[$(date '+%H:%M:%S')] $*"
    printf '%s\n' "$line"
    printf '%s\n' "$line" >> "$RUN_LOG"
}

# 호스트의 현재 시각대를 POSIX TZ 형식으로 만든다(tzdata가 없는 이미지에서도 동작). 예: +0900 -> KST-09:00
compute_host_tz() {
    local off sign hh mm abbr
    off=$(date +%z) || return 0
    abbr=$(date +%Z)
    sign=${off:0:1}; hh=${off:1:2}; mm=${off:3:2}
    [[ $abbr =~ ^[A-Za-z]{3,6}$ ]] || abbr="HOST"
    if [ "$sign" = "+" ]; then sign="-"; else sign="+"; fi   # POSIX TZ는 부호가 반대
    HOST_TZ="${abbr}${sign}${hh}:${mm}"
}

# ---------------- 정리 (Ctrl-C 포함) ----------------
collect_tree() {
    local p=$1 c
    echo "$p"
    for c in $(pgrep -P "$p" 2>/dev/null); do
        collect_tree "$c"
    done
}

kill_jobs() {
    local pids=() p
    for p in $(jobs -p); do
        mapfile -t -O "${#pids[@]}" pids < <(collect_tree "$p")
    done
    [ ${#pids[@]} -gt 0 ] && kill -TERM "${pids[@]}" 2>/dev/null
    sleep 1
}

# ---------------- 테스트 네트워크 ----------------
ip2int() {
    local IFS=.
    # shellcheck disable=SC2086
    set -- $1
    echo $(( ($1 << 24) + ($2 << 16) + ($3 << 8) + $4 ))
}

cidr_range() {   # "a.b.c.d/n" 또는 "a.b.c.d" -> "시작 끝"(정수)
    local ip=${1%/*} len=32 n mask
    [[ $1 == */* ]] && len=${1#*/}
    n=$(ip2int "$ip")
    mask=$(( len == 0 ? 0 : (0xFFFFFFFF << (32 - len)) & 0xFFFFFFFF ))
    echo $(( n & mask )) $(( (n & mask) | (~mask & 0xFFFFFFFF) ))
}

cidr_overlap() {
    local a1 a2 b1 b2
    read -r a1 a2 < <(cidr_range "$1")
    read -r b1 b2 < <(cidr_range "$2")
    [ "$a1" -le "$b2" ] && [ "$b1" -le "$a2" ]
}

is_ipv4_cidr() { [[ $1 =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}(/[0-9]{1,2})?$ ]]; }

network_subnets() {   # 네트워크(이름/ID)의 IPv4 서브넷 목록
    docker network inspect -f '{{range .IPAM.Config}}{{.Subnet}} {{end}}' "$1" 2>/dev/null \
        | tr ' ' '\n' | while read -r c; do is_ipv4_cidr "$c" && echo "$c"; done
}

# TEST_SUBNET이 기존 docker 네트워크, 호스트 주소/라우트와 겹치면 그 목록을 출력
subnet_conflicts() {
    local id name c dst
    for id in $(docker network ls -q 2>/dev/null); do
        name=$(docker network inspect -f '{{.Name}}' "$id" 2>/dev/null)
        [ "$name" = "$TEST_NETWORK" ] && continue
        for c in $(network_subnets "$id"); do
            cidr_overlap "$c" "$TEST_SUBNET" && echo "docker 네트워크 $name($c)"
        done
    done
    if command -v ip > /dev/null 2>&1; then
        while read -r dst _; do
            [ "$dst" = default ] && continue
            is_ipv4_cidr "$dst" || continue
            cidr_overlap "$dst" "$TEST_SUBNET" && echo "호스트 라우트 $dst"
        done < <(ip -4 route show 2>/dev/null)
        while read -r c; do
            is_ipv4_cidr "$c" || continue
            cidr_overlap "${c%/*}" "$TEST_SUBNET" && echo "호스트 주소 $c"
        done < <(ip -4 -o addr show 2>/dev/null | awk '{ print $4 }')
    fi
}

# 테스트 네트워크 준비: 있으면 서브넷 확인 후 재사용, 없으면 겹침 확인 후 생성. 문제가 있으면 실행 전에 중단.
prepare_network() {
    local subnets conflicts
    if [ "$DRY_RUN" -eq 1 ]; then
        say "  (dry-run) 네트워크 준비 생략: $TEST_NETWORK ($TEST_SUBNET, gateway $TEST_GATEWAY)"
        say "            (없으면 생성, 있으면 서브넷 확인 후 재사용, 다른 서브넷이면 중단)"
        return
    fi
    if ! docker network inspect "$TEST_NETWORK" > /dev/null 2>&1; then
        conflicts=$(subnet_conflicts)
        [ -z "$conflicts" ] || die "$TEST_SUBNET 이 기존 네트워크/라우트와 겹쳐 $TEST_NETWORK 네트워크를 만들 수 없습니다: $(tr '\n' ' ' <<< "$conflicts")"
        if docker network create --driver bridge --subnet "$TEST_SUBNET" --gateway "$TEST_GATEWAY" \
                --label "$NETWORK_LABEL=1" --label "$NETWORK_LABEL.created-by=$RUN_ID" \
                "$TEST_NETWORK" > /dev/null 2> "$RUN_TMP/network.err"; then
            NETWORK_CREATED=1
            NETWORK_READY=1
            say "  네트워크 생성: $TEST_NETWORK ($TEST_SUBNET, gateway $TEST_GATEWAY) - 이번 실행이 만든 것이라 끝나면 지움"
            return
        fi
        # 다른 실행이 동시에 만든 경우가 아니면 실패
        docker network inspect "$TEST_NETWORK" > /dev/null 2>&1 \
            || die "$TEST_NETWORK 네트워크 생성 실패: $(head -c 300 "$RUN_TMP/network.err")"
    fi
    subnets=$(network_subnets "$TEST_NETWORK" | tr '\n' ' ')
    [ "$(docker network inspect -f '{{.Driver}}' "$TEST_NETWORK" 2>/dev/null)" = bridge ] \
        || die "기존 $TEST_NETWORK 네트워크가 bridge 드라이버가 아닙니다. 기존 네트워크는 바꾸지 않으므로 확인 후 다시 실행하세요"
    [[ " $subnets " == *" $TEST_SUBNET "* ]] \
        || die "기존 $TEST_NETWORK 네트워크의 서브넷(${subnets:-없음})이 $TEST_SUBNET 이 아닙니다. 기존 네트워크는 바꾸지 않으므로 확인 후 다시 실행하세요"
    NETWORK_READY=1
    say "  네트워크 재사용: $TEST_NETWORK ($TEST_SUBNET) - 실행 전부터 있던 것이라 끝나도 남김"
}

# 이번 실행이 만든 네트워크만, 붙어 있는 컨테이너가 없을 때 지운다
cleanup_network() {
    local n owner
    # 네트워크 생성 도중 신호가 오면 docker network create 직후 NETWORK_CREATED 를 세우기 전에 트랩이 돌 수 있다.
    # 변수 대신 네트워크에 붙인 created-by label 로 이번 실행이 만든 것인지 확인한다.
    if [ "$NETWORK_CREATED" -eq 0 ]; then
        owner=$(docker network inspect -f '{{index .Labels "'"$NETWORK_LABEL"'.created-by"}}' "$TEST_NETWORK" 2>/dev/null)
        if [ -n "$owner" ] && [ "$owner" = "$RUN_ID" ]; then
            NETWORK_CREATED=1
            NETWORK_READY=1
            printf '[%s]   네트워크 생성 직후 중단: created-by label로 이번 실행이 만든 것을 확인\n' "$(date '+%H:%M:%S')" >> "$RUN_LOG"
        fi
    fi
    [ "$NETWORK_READY" -eq 1 ] || return 0
    if [ "$NETWORK_CREATED" -eq 0 ]; then
        CLEAN_NET="네트워크 $TEST_NETWORK 유지(실행 전부터 있음)"
        return 0
    fi
    if [ -s "$KEPT_FILE" ]; then
        CLEAN_NET="네트워크 $TEST_NETWORK 유지(--keep-failed 컨테이너가 붙어 있음)"
        return 0
    fi
    n=$(docker network inspect -f '{{len .Containers}}' "$TEST_NETWORK" 2>/dev/null || echo 0)
    if [ "${n:-0}" -gt 0 ]; then
        CLEAN_NET="네트워크 $TEST_NETWORK 유지(다른 실행의 컨테이너 ${n}개가 붙어 있음)"
        return 0
    fi
    if docker network rm "$TEST_NETWORK" > /dev/null 2>&1; then
        CLEAN_NET="네트워크 $TEST_NETWORK 삭제"
    else
        CLEAN_LEFT+=("네트워크 $TEST_NETWORK(삭제 실패)")
    fi
}

is_kept_container() {
    awk -v n="$1" '$2 == n { f = 1 } END { exit !f }' "$KEPT_FILE" 2>/dev/null
}

cleanup_containers() {
    local id name
    [ "$DRY_RUN" -eq 1 ] && return
    command -v docker > /dev/null 2>&1 || return
    for id in $(docker ps -aq --filter "label=$LABEL_KEY=$RUN_ID" 2>/dev/null); do
        name=$(docker inspect -f '{{.Name}}' "$id" 2>/dev/null)
        name=${name#/}
        is_kept_container "$name" && continue
        # 생성 수에 아직 안 센 컨테이너(중단 등)는 세어 두어 "생성 N개, 삭제 M개"가 어긋나지 않게 한다
        grep -qxF "$name" "$CREATED_FILE" 2>/dev/null || echo "$name" >> "$CREATED_FILE"
        docker rm -f "$id" > /dev/null 2>&1 && CLEAN_RM_CT=$((CLEAN_RM_CT + 1))
    done
}

remove_image() {   # remove_image <이미지>: 있으면 지우고 결과를 기록
    docker image inspect "$1" > /dev/null 2>&1 || return 0
    if log_cmd "docker rmi $1" docker rmi "$1"; then
        CLEAN_RM_IMG+=("$1")
    else
        CLEAN_LEFT+=("이미지 $1(삭제 실패)")
    fi
}

# docker 명령의 결과를 run.log에 남긴다(터미널에는 내지 않음)
log_cmd() {   # log_cmd <설명> <명령...>: 종료 코드를 그대로 돌려준다
    local desc=$1 out rc
    shift
    out=$("$@" 2>&1)
    rc=$?
    printf '[%s]   %s: rc=%s %s\n' "$(date '+%H:%M:%S')" "$desc" "$rc" "$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-300)" >> "$RUN_LOG"
    return $rc
}

# 이번 실행이 쓴 빌드 로그 경로만 돌려준다(이전 실행의 image_build/<os>.log 는 읽지 않는다):
# 이번 실행이 이 OS의 docker build 를 시작했고(BUILD_STARTED), 로그 수정 시각이 실행 시작 이후일 때.
this_run_build_log() {
    local os=$1 log mt
    [ -n "$RESULT_ROOT" ] || return 1
    [ -n "${BUILD_STARTED[$os]+x}" ] || return 1
    log="$RESULT_ROOT/image_build/$os.log"
    [ -f "$log" ] || return 1
    mt=$(stat -c %Y "$log" 2>/dev/null) || return 1
    [ "$mt" -ge "$RUN_START" ] || return 1
    printf '%s\n' "$log"
}

# 이번 빌드(로그 원본)의 " ---> Running in <id>" 임시 컨테이너 중 아직 남은 것을 지운다.
# 조합/판별 컨테이너 집계(CREATED/REMOVED_FILE)와는 섞지 않고 CLEAN_RM_BUILD 와 run.log 에만 기록한다.
cleanup_build_containers() {
    local os=$1 log cid full name created cts
    log=$(this_run_build_log "$os") || return 0
    for cid in $(sed -n 's/^ ---> Running in \([0-9a-f]\{12\}\)$/\1/p' "$log" 2>/dev/null | awk '!s[$0]++'); do
        full=$(docker ps -aq --no-trunc --filter "id=$cid" 2>/dev/null | head -n 1)
        [ -n "$full" ] || continue    # 이미 없음(정상 빌드는 임시 컨테이너를 스스로 지움)
        # 생성 시각이 이번 실행 시작 이후가 아니면 건너뛴다(접두사 일치 등으로 다른 컨테이너를 잡았을 때의 방어 한 겹)
        created=$(docker inspect -f '{{.Created}}' "$full" 2>/dev/null)
        cts=$(date -d "$created" +%s 2>/dev/null)
        if [ -z "$cts" ] || [ "$cts" -lt "$RUN_START" ]; then
            printf '[%s]   빌드용 임시 컨테이너 정리 건너뜀(생성 시각 %s 이 실행 시작 이전이거나 확인 불가): %s\n' "$(date '+%H:%M:%S')" "${created:-?}" "$cid" >> "$RUN_LOG"
            continue
        fi
        name=$(docker inspect -f '{{.Name}}' "$full" 2>/dev/null); name=${name#/}
        if [ -n "$name" ] && is_kept_container "$name"; then
            printf '[%s]   빌드용 임시 컨테이너 정리 건너뜀(--keep-failed 로 남긴 컨테이너): %s\n' "$(date '+%H:%M:%S')" "$name" >> "$RUN_LOG"
            continue
        fi
        if log_cmd "docker rm -f $cid(이번 빌드의 임시 컨테이너, $os)" docker rm -f "$full"; then
            CLEAN_RM_BUILD=$((CLEAN_RM_BUILD + 1))
        fi
    done
}

# 이번 실행의 빌드 로그 원본의 " ---> <id>" 줄에서 태그 없는 중간 이미지만 지운다(실패/중단된 legacy 빌드가 남긴 것).
# 로그의 " ---> <id>" 에는 FROM 단계의 베이스 이미지 id도 있으므로 반드시 제외한다(정리 시점에 구한 id와 시작 때 기록한 BASE_ID 둘 다).
# 이미 지워졌거나 태그가 있는 이미지는 건너뛰므로 여러 번 불려도 된다.
cleanup_intermediate_images() {
    local os=$1 base id base_id full ntags
    base=${OS_IMAGE[$os]}
    base_id=$(docker image inspect -f '{{.Id}}' "$base" 2>/dev/null)
    base_id=${base_id#sha256:}
    for id in $(sed -n 's/^ ---> \([0-9a-f]\{12\}\)$/\1/p' "$(this_run_build_log "$os" || echo /dev/null)" 2>/dev/null | awk '!s[$0]++'); do
        if { [ -n "$base_id" ] && [ "${base_id:0:12}" = "$id" ]; } \
            || { [ -n "${BASE_ID[$os]:-}" ] && [ "${BASE_ID[$os]:0:12}" = "$id" ]; }; then
            printf '[%s]   중간 이미지 정리 건너뜀(베이스 이미지 %s): %s\n' "$(date '+%H:%M:%S')" "$base" "$id" >> "$RUN_LOG"
            continue
        fi
        full=$(docker image inspect -f '{{.Id}}' "$id" 2>/dev/null) || continue   # 이미 없음
        ntags=$(docker image inspect -f '{{len .RepoTags}}' "$id" 2>/dev/null)
        if [ "${ntags:-1}" != 0 ]; then
            printf '[%s]   중간 이미지 정리 건너뜀(태그 있음 %s개): %s\n' "$(date '+%H:%M:%S')" "$ntags" "$id" >> "$RUN_LOG"
            continue
        fi
        log_cmd "docker rmi $id(중간 이미지)" docker rmi "$id"
    done
}

# 이번 실행이 빌드했거나 새로 pull한 이미지만 지운다. 실행 전부터 있던 이미지는 남긴다.
cleanup_images() {
    local os tag base
    declare -A kept_os=()
    for os in $(awk '{ print $1 }' "$KEPT_FILE" 2>/dev/null); do kept_os[$os]=1; done
    for os in "${!IMAGE_TAG[@]}"; do
        tag=${IMAGE_TAG[$os]}
        base=${OS_IMAGE[$os]}
        if [ -n "${kept_os[$os]+x}" ]; then
            CLEAN_KEPT+=("$tag, $base(--keep-failed 컨테이너가 사용)")
            continue
        fi
        if [ "${IMAGE_PREEXIST[$os]:-1}" = 0 ]; then
            remove_image "$tag"
            cleanup_intermediate_images "$os"
        fi
        [ "${BASE_PREEXIST[$os]:-1}" = 0 ] && remove_image "$base"
    done
}

# 정리 후 남은 것 확인 -> CLEANUP_SUMMARY 한 줄
verify_cleanup() {
    local os n left=() errs=() warns=() created removed_run out
    n=$(docker ps -aq --filter "label=$LABEL_KEY=$RUN_ID" 2>/dev/null | wc -l)
    n=$((n - $(wc -l < "$KEPT_FILE")))
    [ "$n" -gt 0 ] && left+=("컨테이너 ${n}개")
    if [ "$KEEP_IMAGES" -eq 0 ]; then
        for os in "${!IMAGE_TAG[@]}"; do
            awk -v o="$os" '$1 == o { f = 1 } END { exit !f }' "$KEPT_FILE" 2>/dev/null && continue
            [ "${IMAGE_PREEXIST[$os]:-1}" = 0 ] && docker image inspect "${IMAGE_TAG[$os]}" > /dev/null 2>&1 \
                && left+=("${IMAGE_TAG[$os]}")
            [ "${BASE_PREEXIST[$os]:-1}" = 0 ] && docker image inspect "${OS_IMAGE[$os]}" > /dev/null 2>&1 \
                && left+=("${OS_IMAGE[$os]}")
        done
    fi
    # 실행 전부터 있던 베이스 이미지가 여전히 있는지 확인 (--keep-images 여부와 관계없이)
    # "No such image" 일 때만 삭제된 것으로 보고 오류(종료 코드 1)로 한다. 그 밖의 확인 실패(docker 접근 오류 등)는 경고만 하고
    # 종료 코드는 바꾸지 않는다. 시작 때 확인에 실패해 "기존 이미지로 간주"한 OS는 끝에 No such image 여도 경고만 한다.
    for os in "${!IMAGE_TAG[@]}"; do
        [ "${BASE_PREEXIST[$os]:-0}" = 1 ] || continue
        if out=$(docker image inspect "${OS_IMAGE[$os]}" 2>&1); then
            continue
        fi
        if printf '%s' "$out" | grep -qi 'no such image'; then
            if [ "${BASE_ASSUMED[$os]:-0}" = 1 ]; then
                warns+=("기존 이미지 확인 불가: ${OS_IMAGE[$os]}(시작 때 확인 실패)")
            else
                errs+=("기존 이미지 삭제됨: ${OS_IMAGE[$os]}")
            fi
        else
            warns+=("기존 이미지 확인 불가: ${OS_IMAGE[$os]}($(printf '%s' "$out" | tr '\n' ' ' | cut -c1-100))")
        fi
    done
    created=$(wc -l < "$CREATED_FILE" 2>/dev/null || echo 0)
    removed_run=$(wc -l < "$REMOVED_FILE" 2>/dev/null || echo 0)
    CLEANUP_SUMMARY="정리: 컨테이너 ${created}개 생성, $((removed_run + CLEAN_RM_CT))개 삭제(조합/계열 판별 종료 시 ${removed_run}개, 정리 단계 ${CLEAN_RM_CT}개), 이미지 ${#CLEAN_RM_IMG[@]}개 삭제"
    [ ${#CLEAN_RM_IMG[@]} -gt 0 ] && CLEANUP_SUMMARY+="(${CLEAN_RM_IMG[*]})"
    [ "${CLEAN_RM_BUILD:-0}" -gt 0 ] && CLEANUP_SUMMARY+=", 중단된 빌드의 임시 컨테이너 ${CLEAN_RM_BUILD}개 삭제(위 컨테이너 수와 별도)"
    if [ "$KEEP_IMAGES" -eq 1 ] && [ ${#IMAGE_TAG[@]} -gt 0 ]; then
        CLEANUP_SUMMARY+=" / 이미지 유지(--keep-images)"
    fi
    if [ -s "$KEPT_FILE" ]; then
        CLEANUP_SUMMARY+=" / 남긴 컨테이너(--keep-failed): $(awk '{ print $2 }' "$KEPT_FILE" | tr '\n' ' ')"
    fi
    [ ${#CLEAN_KEPT[@]} -gt 0 ] && CLEANUP_SUMMARY+=" / 남긴 이미지: ${CLEAN_KEPT[*]}"
    [ -n "$CLEAN_NET" ] && CLEANUP_SUMMARY+=" / $CLEAN_NET"
    if [ "$NETWORK_CREATED" -eq 1 ] && [ "$CLEAN_NET" = "네트워크 $TEST_NETWORK 삭제" ] \
        && docker network inspect "$TEST_NETWORK" > /dev/null 2>&1; then
        left+=("네트워크 $TEST_NETWORK")
    fi
    left+=("${CLEAN_LEFT[@]}")
    if [ ${#errs[@]} -gt 0 ]; then
        CLEANUP_ERROR=1
        CLEANUP_SUMMARY+=" / 오류: ${errs[*]}"
    fi
    if [ ${#warns[@]} -gt 0 ]; then
        CLEANUP_SUMMARY+=" / 경고: ${warns[*]}"
    fi
    if [ ${#left[@]} -gt 0 ]; then
        CLEANUP_SUMMARY+=" / 정리 후 남은 것: ${left[*]}"
    else
        CLEANUP_SUMMARY+=" / 정리 후 남은 것: 없음"
    fi
}

# 정리 결과 한 줄을 이번 실행이 쓴 report.md 끝에 덧붙인다.
# 중단된 실행은 리포트(generate_reports)를 거치지 않아 최상위 report.md 가 이전 실행의 파일이므로 건드리지 않는다
# (이번 실행 ID 줄 "- 실행 ID: `<RUN_ID>`" 이 있는 파일에만 덧붙임. 정리 결과는 run.log 에는 항상 남는다).
# 항상 0을 돌려준다(on_exit 에서 호출됨).
append_cleanup_summary() {
    local f
    [ -n "${RESULT_ROOT:-}" ] || return 0
    for f in "$RESULT_ROOT/runs/$RUN_ID/report.md" "$RESULT_ROOT/report.md"; do
        if [ -f "$f" ] && grep -qF -- "- 실행 ID: \`$RUN_ID\`" "$f" 2>/dev/null; then
            printf '\n- %s\n' "$CLEANUP_SUMMARY" >> "$f"
        fi
    done
    return 0
}

# 컨테이너, 이미지 정리 (정상/실패 종료, Ctrl-C 모두). 결과 한 줄을 로그와 리포트에 남긴다.
final_cleanup() {
    local os
    # 정리가 시작되면 INT/TERM/HUP 을 무시해 끝까지 간다(정리 도중 신호로 끊기면 이미지/네트워크/임시 디렉터리가 남음).
    # 정리 전 실행 중의 신호는 on_signal 이 그대로 조합을 멈추고 이 함수를 부른다.
    trap '' INT TERM HUP
    [ "$CLEANUP_DONE" -eq 1 ] && return
    CLEANUP_DONE=1
    # 잠금을 잡기 전의 오류(옵션/matrix 오류, 잠금 거부)에서는 docker 를 조회하지 않고 "정리:" 줄도 내지 않는다
    [ "$CLEANUP_ARMED" -eq 1 ] || return
    if [ "$DRY_RUN" -eq 1 ]; then
        say "(dry-run) 정리 생략: 컨테이너, 이번 실행이 빌드/pull한 이미지, 이번 실행이 만든 $TEST_NETWORK 네트워크"
        return
    fi
    command -v docker > /dev/null 2>&1 || return
    CLEAN_RM_CT=0; CLEAN_RM_BUILD=0; CLEAN_RM_IMG=(); CLEAN_LEFT=(); CLEAN_KEPT=(); CLEAN_NET=""
    cleanup_containers
    sleep 1
    cleanup_containers   # 중단 직전에 생성 요청된 컨테이너까지 한 번 더
    # 중단/시간 초과로 클라이언트만 죽으면 이번 빌드의 임시 컨테이너가 남을 수 있다. --keep-images/--images-only 와 무관하게
    # 이미지 정리 전에 한 번 지운다(이번 실행이 빌드를 시작한 OS의 로그 원본에서 " ---> Running in <id>" 만 대상).
    for os in "${!BUILD_STARTED[@]}"; do cleanup_build_containers "$os"; done
    # 빌드가 실패/중단/시간 초과된 OS의 중간 이미지는 --keep-images/--images-only/--rebuild-images 와 무관하게 지운다
    # (성공한 빌드의 레이어는 준비 이미지의 부모라 지워지지 않으므로 대상에서 뺀다).
    for os in "${!BUILD_STARTED[@]}"; do image_ok "$os" || cleanup_intermediate_images "$os"; done
    [ "$KEEP_IMAGES" -eq 0 ] && cleanup_images
    cleanup_network
    verify_cleanup
    say "$CLEANUP_SUMMARY"
    append_cleanup_summary
    save_run_log
}

save_run_log() {
    [ -n "$RESULT_ROOT" ] && [ -d "$RESULT_ROOT/runs/$RUN_ID" ] && cp -f "$RUN_LOG" "$RESULT_ROOT/runs/$RUN_ID/run.log"
}

on_signal() {
    trap '' INT TERM HUP
    echo
    say "중단 신호($1) 수신 - 실행 중인 조합을 멈추고 이번 실행의 컨테이너와 이미지를 정리합니다."
    kill_jobs
    final_cleanup
    exit 130
}

on_exit() {
    local rc=$?
    # 신호가 아닌 비정상 종료(예: set -u 오류)에서 아직 돌고 있는 백그라운드 작업이 있으면 먼저 멈춘다:
    # 작업이 잠금을 물고 남거나 정리와 겹치지 않게 한다. 정상 종료는 작업을 모두 기다린 뒤라 영향이 없다.
    if [ -n "$(jobs -rp)" ]; then
        kill_jobs
    fi
    final_cleanup
    rm -rf "$RUN_TMP"
    # 정리 중 오류(기존 이미지 삭제 등)가 있으면 종료 코드를 0이 아니게 한다
    if [ "$CLEANUP_ERROR" -eq 1 ] && [ "$rc" -eq 0 ]; then
        exit 1
    fi
}

trap 'on_signal INT' INT
trap 'on_signal TERM' TERM
trap 'on_signal HUP' HUP
trap on_exit EXIT

# ---------------- matrix.conf ----------------
PKG_KEYS=()
OS_KEYS=()
declare -A PKG_SUFFIX=() OS_IMAGE=() OS_PKGS=()

pkg_kind_of() {
    case "${PKG_SUFFIX[$1]}" in
        *.rpm) echo rpm ;;
        *.tar.gz|*.tgz) echo tar ;;
        *.sh) echo sh ;;
        *) echo unknown ;;
    esac
}

load_matrix() {
    local line n=0 kind a b c extra p
    [ -r "$MATRIX_FILE" ] || die "matrix 파일을 읽을 수 없습니다: $MATRIX_FILE"
    while IFS= read -r line || [ -n "$line" ]; do
        n=$((n + 1))
        line=${line%$'\r'}     # CRLF matrix 대응
        line=${line%%#*}
        read -r kind a b c extra <<< "$line"
        [ -z "$kind" ] && continue
        case "$kind" in
            pkg)
                [ -n "$b" ] && [ -z "$c" ] || die "$MATRIX_FILE:$n: 형식 오류 (pkg <키> <접미사>)"
                [[ $a =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] && [[ $a != *__* ]] \
                    || die "$MATRIX_FILE:$n: 패키지 키에는 영문/숫자/._- 만 쓰고 첫 글자는 영문/숫자, '__' 는 쓸 수 없습니다: $a"
                [ -z "${PKG_SUFFIX[$a]+x}" ] || die "$MATRIX_FILE:$n: 패키지 키 중복: $a"
                PKG_KEYS+=("$a"); PKG_SUFFIX[$a]=$b
                [ "$(pkg_kind_of "$a")" != unknown ] || die "$MATRIX_FILE:$n: 접미사는 .sh/.tar.gz/.rpm 으로 끝나야 합니다: $b"
                ;;
            os)
                [ -n "$b" ] && [ -z "$extra" ] || die "$MATRIX_FILE:$n: 형식 오류 (os <키> <이미지:태그> [패키지 목록])"
                [ -z "${OS_IMAGE[$a]+x}" ] || die "$MATRIX_FILE:$n: OS 키 중복: $a"
                [[ $a =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] && [[ $a != *__* ]] \
                    || die "$MATRIX_FILE:$n: OS 키에는 영문/숫자/._- 만 쓰고 첫 글자는 영문/숫자, '__' 는 쓸 수 없습니다: $a"
                OS_KEYS+=("$a"); OS_IMAGE[$a]=$b; OS_PKGS[$a]=$c
                ;;
            *) die "$MATRIX_FILE:$n: 알 수 없는 항목: $kind (pkg 또는 os)" ;;
        esac
    done < "$MATRIX_FILE"
    [ ${#PKG_KEYS[@]} -gt 0 ] || die "$MATRIX_FILE: pkg 항목이 없습니다"
    [ ${#OS_KEYS[@]} -gt 0 ] || die "$MATRIX_FILE: os 항목이 없습니다"
    for a in "${OS_KEYS[@]}"; do
        split_csv "${OS_PKGS[$a]}"
        for p in "${SPLIT[@]}"; do
            [ -n "${PKG_SUFFIX[$p]+x}" ] || die "$MATRIX_FILE: os $a 의 패키지 키 '$p' 가 pkg 목록에 없습니다"
        done
    done
}

os_matches() {   # os_matches <os> <필터 토큰>
    [[ $1 == "$2"* || ${OS_IMAGE[$1]} == "$2"* ]]
}

os_selected() {
    local t
    [ ${#OS_FILTER[@]} -eq 0 ] && return 0
    for t in "${OS_FILTER[@]}"; do
        os_matches "$1" "$t" && return 0
    done
    return 1
}

pkg_selected() {
    local t
    [ ${#PKG_FILTER[@]} -eq 0 ] && return 0
    for t in "${PKG_FILTER[@]}"; do
        [ "$1" = "$t" ] && return 0
    done
    return 1
}

os_allows_pkg() {
    [ -z "${OS_PKGS[$1]}" ] && return 0
    [[ ,${OS_PKGS[$1]}, == *",$2,"* ]]
}

build_combos() {
    local t os pkg found
    for t in "${OS_FILTER[@]}"; do
        found=0
        for os in "${OS_KEYS[@]}"; do os_matches "$os" "$t" && found=1; done
        [ $found -eq 1 ] || die "--os '$t' 에 해당하는 OS가 없습니다 (가능: ${OS_KEYS[*]})"
    done
    for t in "${PKG_FILTER[@]}"; do
        [ -n "${PKG_SUFFIX[$t]+x}" ] || die "--pkg '$t' 는 없는 패키지 키입니다 (가능: ${PKG_KEYS[*]})"
    done
    COMBOS=()
    for pkg in "${PKG_KEYS[@]}"; do
        pkg_selected "$pkg" || continue
        for os in "${OS_KEYS[@]}"; do
            os_selected "$os" && os_allows_pkg "$os" "$pkg" && COMBOS+=("$os|$pkg")
        done
    done
    [ ${#COMBOS[@]} -gt 0 ] || die "선택된 조합이 없습니다"
}

# ---------------- 패키지 준비 ----------------
BUILD_VER=""
PKG_DIR=""
declare -A PKG_FILE=() PKG_MISSING=() PKG_VER=()

is_url() { [[ $1 =~ ^(https?|ftp):// ]]; }

selected_pkgs() {
    local c pkg
    declare -A seen=()
    for c in "${COMBOS[@]}"; do
        pkg=${c#*|}
        [ -n "${seen[$pkg]+x}" ] && continue
        seen[$pkg]=1
        echo "$pkg"
    done
}

# 패키지 파일의 sha256 을 run.log 에 남긴다(파일이 있을 때만).
log_pkg_sha() {
    local f=$1
    [ -f "$f" ] || return 0
    say "  sha256: $(basename "$f") $(sha256sum "$f" | cut -d' ' -f1)"
}

# 러너와 test_script 의 해시(앞 12자). test_script 는 파일 경로 순으로 정렬한 sha256 목록을 다시 해시해 재현 가능하게 한다.
compute_runner_hashes() {
    RUNNER_SHA=$(sha256sum "$SCRIPT_DIR/run_install_test.sh" 2>/dev/null | cut -c1-12)
    TESTSCRIPT_SHA=$(cd "$SCRIPT_DIR/test_script" 2>/dev/null \
        && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum 2>/dev/null | sha256sum | cut -c1-12)
    [ -n "$RUNNER_SHA" ] || RUNNER_SHA="확인불가"
    [ -n "$TESTSCRIPT_SHA" ] || TESTSCRIPT_SHA="확인불가"
}

download_pkg() {   # download_pkg <url> <dest>
    local url=$1 dest=$2 remote local_size
    if [ -s "$dest" ]; then
        remote=$(curl -sIL --max-time 30 "$url" 2>/dev/null | tr -d '\r' \
            | awk 'tolower($1) == "content-length:" { s = $2 } END { print s }')
        local_size=$(stat -c %s "$dest")
        if [ -z "$remote" ] || [ "$remote" = "$local_size" ]; then
            say "  이미 있음: $(basename "$dest")"
            return 0
        fi
        say "  크기가 달라 다시 받음: $(basename "$dest") (로컬 $local_size, 원격 $remote)"
    fi
    say "  다운로드: $(mask_url "$url")"
    if curl -fL --retry 2 --connect-timeout 30 -sS -o "$dest.part" "$url"; then
        mv -f "$dest.part" "$dest"
    else
        rm -f "$dest.part"
        return 1
    fi
}

prepare_packages() {
    local name base pkg fname versions count
    if is_url "$SRC"; then
        PKG_DIR=$SCRIPT_DIR/test_build
        mkdir -p "$PKG_DIR" || die "$PKG_DIR 를 만들 수 없습니다"
        name=${SRC##*/}
        if [[ $name =~ ^CUBRID-($VERSION_RE)-Linux ]]; then
            BUILD_VER=${BASH_REMATCH[1]}
            base=${SRC%/*}
        else
            base=${SRC%/}
            BUILD_VER=$(grep -oE "$VERSION_RE" <<< "$base" | tail -n 1)
            [ -n "$BUILD_VER" ] || BUILD_VER=$VERSION_FILTER
            if [ -z "$BUILD_VER" ]; then
                versions=$(curl -fsSL --max-time 60 "$base/" 2>/dev/null \
                    | grep -oE "CUBRID-$VERSION_RE-Linux" | sed 's/^CUBRID-//; s/-Linux$//' | sort -u)
                count=$(grep -c . <<< "$versions")
                [ "$count" -eq 1 ] || die "URL에서 빌드 버전을 알 수 없습니다: $(mask_url "$SRC") (찾은 버전: ${versions:-없음}) - --version 으로 지정하세요"
                BUILD_VER=$versions
            fi
        fi
        say "빌드 버전: $BUILD_VER (URL: $(mask_url "$base")/)"
        for pkg in $(selected_pkgs); do
            fname="CUBRID-${BUILD_VER}${PKG_SUFFIX[$pkg]}"
            PKG_FILE[$pkg]=$fname
            PKG_VER[$pkg]=$BUILD_VER
            if [ "$DRY_RUN" -eq 1 ]; then
                say "  (dry-run) 다운로드 생략: $(mask_url "$base")/$fname"
                log_pkg_sha "$PKG_DIR/$fname"
                continue
            fi
            download_pkg "$base/$fname" "$PKG_DIR/$fname" \
                || PKG_MISSING[$pkg]="다운로드 실패: $fname"
            log_pkg_sha "$PKG_DIR/$fname"
        done
    else
        [ -d "$SRC" ] || die "URL도 디렉터리도 아닙니다: $(mask_url "$SRC")"
        PKG_DIR=$(cd "$SRC" && pwd)
        local f v suffix vers=() all_vers=() missing=()
        for pkg in $(selected_pkgs); do
            suffix=${PKG_SUFFIX[$pkg]}
            vers=()
            # CUBRID-<버전><접미사> 형태만 (예: -Linux.x86_64.sh 는 -debug.sh 와 구분됨)
            while IFS= read -r f; do
                v=${f#CUBRID-}
                v=${v%"$suffix"}
                [[ $v =~ ^$VERSION_RE$ ]] || continue
                [ -z "$VERSION_FILTER" ] || [ "$v" = "$VERSION_FILTER" ] || continue
                vers+=("$v")
            done < <(find "$PKG_DIR" -maxdepth 1 -type f -name "CUBRID-*$suffix" -printf '%f\n' | sort)
            if [ ${#vers[@]} -eq 0 ]; then
                missing+=("$pkg")
                continue
            fi
            [ ${#vers[@]} -eq 1 ] || die "$PKG_DIR 에 $pkg 패키지가 여러 버전 있습니다: ${vers[*]} - --version 으로 지정하세요"
            PKG_FILE[$pkg]="CUBRID-${vers[0]}${suffix}"
            PKG_VER[$pkg]=${vers[0]}
            all_vers+=("${vers[0]}")
            say "  사용: ${PKG_FILE[$pkg]}"
            log_pkg_sha "$PKG_DIR/${PKG_FILE[$pkg]}"
        done
        [ ${#all_vers[@]} -gt 0 ] \
            || die "$PKG_DIR 에 선택한 패키지(CUBRID-<버전>-Linux*)가 하나도 없습니다${VERSION_FILTER:+ (--version $VERSION_FILTER)}"
        BUILD_VER=$(printf '%s\n' "${all_vers[@]}" "$VERSION_FILTER" | grep . | sort -u | paste -sd+ -)
        # 없는 패키지는 (버전이 하나면) 그 버전의 파일명으로 표시
        v=$BUILD_VER
        [[ $v == *+* ]] && v="<버전>"
        for pkg in "${missing[@]}"; do
            PKG_FILE[$pkg]="CUBRID-${v}${PKG_SUFFIX[$pkg]}"
            PKG_MISSING[$pkg]="패키지 파일 없음: ${PKG_FILE[$pkg]}"
            say "  없음: ${PKG_FILE[$pkg]} -> 이 패키지의 조합은 fail로 기록"
        done
        if [[ $BUILD_VER == *+* ]]; then
            say "주의: 빌드 버전이 섞여 있습니다 ($BUILD_VER). 행마다 Test build 열에 패키지 버전을 적습니다."
        fi
        say "빌드 버전: $BUILD_VER (디렉터리: $PKG_DIR)"
    fi
}

# ---------------- 이미지 ----------------
# 이미지 입력(실제 쓰는 Dockerfile 디렉터리, 계열, 베이스 이미지, add_openssl.sh)이 바뀌면 태그도 바뀌어 자동으로 다시 빌드됨
image_tag_for() {
    local os=$1 h
    h=$( { find "${IMAGE_DIR[$os]}" -type f -print0 | sort -z | xargs -0 cat
           echo "${IMAGE_FAMILY[$os]}"
           cat "$SCRIPT_DIR/test_script/add_openssl.sh"
           echo "${OS_IMAGE[$os]}"; } | sha256sum | cut -c1-12)
    echo "$IMAGE_REPO:$os-$h"
}

build_image() {   # 백그라운드 작업: 결과를 $RUN_TMP/image-<os>.status 에 기록
    local os=$1 tag=$2 log=$3 ctx
    ctx=$RUN_TMP/ctx-$os
    rm -rf "$ctx"
    mkdir -p "$ctx"
    cp -a "${IMAGE_DIR[$os]}/." "$ctx/"
    cp "$SCRIPT_DIR/test_script/add_openssl.sh" "$ctx/"
    local brc
    timeout -k 15 "$BUILD_TIMEOUT_SEC" docker build --network "$BUILD_NETWORK" --force-rm --label "$IMAGE_REPO=1" \
            --build-arg "BASE_IMAGE=${OS_IMAGE[$os]}" -t "$tag" "$ctx" > "$log" 2>&1
    brc=$?
    if [ $brc -eq 0 ]; then
        echo ok > "$RUN_TMP/image-$os.status"
        say "  이미지 빌드 완료: $tag"
    elif [ $brc -eq 124 ] || [ $brc -eq 137 ]; then
        echo fail > "$RUN_TMP/image-$os.status"
        echo "이미지 빌드 시간 초과(${BUILD_TIMEOUT_SEC}초, image_build/$os.log)" > "$RUN_TMP/image-$os.reason"
        say "  이미지 빌드 시간 초과: $tag (${BUILD_TIMEOUT_SEC}초, 로그: $log)"
    else
        echo fail > "$RUN_TMP/image-$os.status"
        say "  이미지 빌드 실패: $tag (로그: $log)"
    fi
}

# /etc/os-release 의 ID, ID_LIKE 로 계열 판별: deb | rpm | unknown
family_from_os_release() {   # $1: os-release 내용
    local id like
    id=$(printf '%s\n' "$1" | sed -n 's/^ID=//p' | head -n 1 | tr -d "\"'" | tr 'A-Z' 'a-z')
    like=$(printf '%s\n' "$1" | sed -n 's/^ID_LIKE=//p' | head -n 1 | tr -d "\"'" | tr 'A-Z' 'a-z')
    case " $id $like " in
        *" debian "*|*" ubuntu "*) echo deb ;;
        *" rhel "*|*" fedora "*|*" centos "*|*" rocky "*|*" ol "*|*" almalinux "*) echo rpm ;;
        *) echo unknown ;;
    esac
}

# 백그라운드 작업: 베이스 이미지를 확보하고(없으면 pull) 임시 컨테이너(--rm --network none, 실행 ID label)로
# /etc/os-release 를 읽어 계열을 $RUN_TMP/family-<os> 에 기록한다. 결과: deb | rpm | unknown | pullfail | pulltimeout | detecttimeout | runfail
detect_family_job() {
    local os=$1 base cname out rc prc log
    base=${OS_IMAGE[$os]}
    log="$RESULT_ROOT/image_build/$os.detect.log"
    cname="${CONTAINER_PREFIX}-${RUN_ID}-detect-${os}"
    {
        if ! docker image inspect "$base" > /dev/null 2>&1; then
            echo "\$ docker pull $base   (제한 ${PULL_TIMEOUT_SEC}초)"
            timeout -k 15 "$PULL_TIMEOUT_SEC" docker pull "$base"
            prc=$?
            if [ $prc -eq 124 ] || [ $prc -eq 137 ]; then echo pulltimeout > "$RUN_TMP/family-$os"; return; fi
            if [ $prc -ne 0 ]; then echo pullfail > "$RUN_TMP/family-$os"; return; fi
        fi
        echo "\$ docker run --rm --network none --label $LABEL_KEY=$RUN_ID $base (/etc/os-release 읽기, 제한 ${DETECT_TIMEOUT_SEC}초)"
        out=$(timeout -k 5 "$DETECT_TIMEOUT_SEC" docker run --rm --name "$cname" --network none --label "$LABEL_KEY=$RUN_ID" \
              --entrypoint sh "$base" -c 'cat /etc/os-release 2>/dev/null || cat /usr/lib/os-release' 2>&1)
        rc=$?
        # 생성/삭제 집계(컨테이너가 실제로 만들어졌을 때만 센다):
        #  - rc 125 는 docker 자체 오류(컨테이너 안 만들어짐) -> 세지 않음
        #  - 시간 초과(124/137)는 클라이언트만 죽어 만들어졌는지 알 수 없다 -> 이름 필터로 남아 있는 것이 확인될 때만
        #    rm -f 후 생성·삭제 각 1, 없으면 둘 다 0
        #  - 그 밖에는 만들어진 것으로 보고 생성 1, 지워졌는지는 docker inspect 실패가 아니라 이름 필터 결과가 비었을 때로 판단
        #    (데몬 일시 오류로 inspect가 실패해도 이중 집계 없음)
        if [ $rc -eq 124 ] || [ $rc -eq 137 ]; then
            if [ -n "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ]; then
                docker rm -f "$cname" > /dev/null 2>&1
                echo "$cname" >> "$CREATED_FILE"
                [ -z "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ] && echo "$cname" >> "$REMOVED_FILE"
            fi
        elif [ $rc -ne 125 ]; then
            echo "$cname" >> "$CREATED_FILE"
            if [ -n "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ]; then
                docker rm -f "$cname" > /dev/null 2>&1
            fi
            [ -z "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ] && echo "$cname" >> "$REMOVED_FILE"
        fi
        printf '%s\n' "$out"
        if [ $rc -eq 124 ] || [ $rc -eq 137 ]; then echo detecttimeout > "$RUN_TMP/family-$os"; return; fi
        if [ $rc -ne 0 ]; then echo runfail > "$RUN_TMP/family-$os"; return; fi
        family_from_os_release "$out" > "$RUN_TMP/family-$os"
    } > "$log" 2>&1
}

wait_for_slot() {
    while [ "$(jobs -rp | wc -l)" -ge "$JOBS" ]; do
        wait -n 2> /dev/null
    done
}

prepare_images() {
    local c os tag log inspect_out inspect_rc fam
    declare -A need=()
    for c in "${COMBOS[@]}"; do
        os=${c%%|*}
        [ -n "${PKG_MISSING[${c#*|}]:-}" ] && continue
        need[$os]=1
    done
    mkdir -p "$RESULT_ROOT/image_build"
    # 1) 베이스 이미지가 원래 있었는지 기록 (끝나면 이번 실행이 만든/받은 것만 지움)
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        [ "$DRY_RUN" -eq 1 ] && continue
        # "No such image" 일 때만 이번 실행이 받는 것(0). 그 밖의 inspect 실패(일시적 오류 등)는 기존 것으로 간주(1)해 지우지 않는다.
        inspect_out=$(docker image inspect "${OS_IMAGE[$os]}" 2>&1); inspect_rc=$?
        if [ $inspect_rc -eq 0 ]; then
            BASE_PREEXIST[$os]=1
        elif printf '%s' "$inspect_out" | grep -qi 'no such image'; then
            BASE_PREEXIST[$os]=0
        else
            BASE_PREEXIST[$os]=1
            BASE_ASSUMED[$os]=1
            say "  경고: ${OS_IMAGE[$os]} 이미지 확인 실패(No such image 아님) -> 기존 이미지로 간주: $(printf '%s' "$inspect_out" | tr '\n' ' ' | cut -c1-150)"
        fi
        BASE_ID[$os]=$(docker image inspect -f '{{.Id}}' "${OS_IMAGE[$os]}" 2>/dev/null); BASE_ID[$os]=${BASE_ID[$os]#sha256:}
    done
    # 2) 계열 결정: images/<OS 키>/Dockerfile 이 있으면 그것을 우선 쓴다(판별 생략). 없으면 베이스 이미지를 받아
    #    /etc/os-release 로 판별. dry-run 은 판별을 건너뛴다.
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        if [ -f "$SCRIPT_DIR/images/$os/Dockerfile" ]; then
            IMAGE_FAMILY[$os]=custom
            IMAGE_DIR[$os]="$SCRIPT_DIR/images/$os"
            [ "$DRY_RUN" -eq 1 ] || say "  $os: images/$os/Dockerfile 사용(OS 계열 판별 생략)"
        elif [ "$DRY_RUN" -eq 1 ]; then
            IMAGE_FAMILY[$os]=dry-run
            IMAGE_DIR[$os]="$SCRIPT_DIR/images/rpm"
        else
            wait_for_slot
            detect_family_job "$os" &
        fi
    done
    wait
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        [ -z "${IMAGE_FAMILY[$os]:-}" ] || continue
        fam=$(cat "$RUN_TMP/family-$os" 2>/dev/null)
        case "$fam" in
            deb|rpm)
                IMAGE_FAMILY[$os]=$fam
                IMAGE_DIR[$os]="$SCRIPT_DIR/images/$fam"
                say "  $os: OS 계열 $fam (${OS_IMAGE[$os]}, images/$fam/Dockerfile 사용)"
                [ -f "${IMAGE_DIR[$os]}/Dockerfile" ] || { IMAGE_FAMILY[$os]=unknown; echo "images/$fam/Dockerfile 이 없습니다" > "$RUN_TMP/image-$os.reason"; }
                ;;
            runfail)
                IMAGE_FAMILY[$os]=unknown
                echo "OS 계열 판별 컨테이너 실행 실패(image_build/$os.detect.log)" > "$RUN_TMP/image-$os.reason"
                say "  $os: OS 계열 판별 컨테이너 실행 실패: ${OS_IMAGE[$os]} (로그: $RESULT_ROOT/image_build/$os.detect.log). Dockerfile 을 만든다고 해결되는 문제가 아닐 수 있음"
                ;;
            pulltimeout)
                IMAGE_FAMILY[$os]=unknown
                echo "베이스 이미지 받기 시간 초과(${PULL_TIMEOUT_SEC}초, ${OS_IMAGE[$os]}, image_build/$os.detect.log)" > "$RUN_TMP/image-$os.reason"
                say "  $os: 베이스 이미지 받기 시간 초과(${PULL_TIMEOUT_SEC}초): ${OS_IMAGE[$os]} (로그: $RESULT_ROOT/image_build/$os.detect.log)"
                ;;
            detecttimeout)
                IMAGE_FAMILY[$os]=unknown
                echo "OS 계열 판별 시간 초과(${DETECT_TIMEOUT_SEC}초, image_build/$os.detect.log)" > "$RUN_TMP/image-$os.reason"
                say "  $os: OS 계열 판별 시간 초과(${DETECT_TIMEOUT_SEC}초): ${OS_IMAGE[$os]} (로그: $RESULT_ROOT/image_build/$os.detect.log)"
                ;;
            pullfail)
                IMAGE_FAMILY[$os]=unknown
                echo "베이스 이미지를 받지 못함(${OS_IMAGE[$os]}, image_build/$os.detect.log)" > "$RUN_TMP/image-$os.reason"
                say "  $os: 베이스 이미지를 받지 못함: ${OS_IMAGE[$os]} (로그: $RESULT_ROOT/image_build/$os.detect.log)"
                ;;
            *)
                IMAGE_FAMILY[$os]=unknown
                echo "OS 계열 판별 불가, images/$os/Dockerfile 필요" > "$RUN_TMP/image-$os.reason"
                say "  $os: OS 계열 판별 불가 (${OS_IMAGE[$os]}, ${fam:-결과 없음}) - 이 OS의 조합은 실패 처리, images/$os/Dockerfile 이 필요함"
                ;;
        esac
    done
    # 3) 이미지 태그(실제 쓰는 Dockerfile 디렉터리 + 계열 + 베이스 이미지)와 기존 이미지 여부, 빌드
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        if [ "${IMAGE_FAMILY[$os]}" = unknown ]; then
            IMAGE_DIR[$os]=${IMAGE_DIR[$os]:-$SCRIPT_DIR/images/rpm}
            IMAGE_TAG[$os]="$IMAGE_REPO:$os-unknown"
            IMAGE_PREEXIST[$os]=1     # 빌드하지 않으므로 지울 이미지 없음
            echo fail > "$RUN_TMP/image-$os.status"
            continue
        fi
        tag=$(image_tag_for "$os")
        IMAGE_TAG[$os]=$tag
        if [ "$DRY_RUN" -eq 1 ]; then
            say "  (dry-run) 계열 판별/이미지 빌드 생략: $tag (FROM ${OS_IMAGE[$os]})"
            echo ok > "$RUN_TMP/image-$os.status"
            continue
        fi
        if [ "$REBUILD" -eq 0 ] && docker image inspect "$tag" > /dev/null 2>&1; then
            IMAGE_PREEXIST[$os]=1
        else
            IMAGE_PREEXIST[$os]=0
        fi
        if [ "${IMAGE_PREEXIST[$os]}" = 1 ]; then
            say "  이미지 있음(건너뜀, 실행 전부터 있던 이미지라 끝나도 남김): $tag"
            echo ok > "$RUN_TMP/image-$os.status"
            continue
        fi
        log="$RESULT_ROOT/image_build/$os.log"
        wait_for_slot
        say "  이미지 빌드 시작: $tag (FROM ${OS_IMAGE[$os]})"
        BUILD_STARTED[$os]=1
        build_image "$os" "$tag" "$log" &
    done
    wait
}

image_ok() { [ "$(cat "$RUN_TMP/image-$1.status" 2>/dev/null)" = ok ]; }

# ---------------- 조합 실행 ----------------
item_applicable() {   # item_applicable <pkg> <item>
    [ "$2" != uninstall ] || [ "$(pkg_kind_of "$1")" = rpm ]
}

# --dry-run 용 가짜 install_test.sh 출력
#   INSTALL_TEST_DRY_FAIL="os__pkg[:항목],..." 해당 조합(항목)을 fail로
#   INSTALL_TEST_DRY_HANG="os__pkg,..."        해당 조합을 멈춰서 timeout 유도
fake_install_test() {
    local os=$1 pkg=$2 fname=$3 id="${1}__${2}" item
    echo "[dry-run] $id: $fname"
    echo "OSINFO|dry-run ($os)|-"
    case ",${INSTALL_TEST_DRY_HANG:-}," in
        *",$id,"*) echo "[dry-run] 멈춤 (timeout 유도)"; sleep 100000 ;;
    esac
    sleep $(( RANDOM % 3 + 1 ))
    for item in install bash_profile createdb service_start server_start broker manager sql stop deletedb uninstall; do
        if [ "$item" = uninstall ] && [[ $fname != *.rpm ]]; then
            echo "RESULT|$item|NA|"
            continue
        fi
        case ",${INSTALL_TEST_DRY_FAIL:-}," in
            *",$id,"*|*",$id:$item,"*) echo "RESULT|$item|fail|dry-run 가짜 실패"; continue ;;
        esac
        echo "RESULT|$item|pass|"
    done
    case ",${INSTALL_TEST_DRY_FAIL:-}," in *",$id"[,:]*) exit 1 ;; esac
    exit 0
}

# result.txt 작성: META 줄 + 항목별 ITEM 줄
finalize_combo() {   # <os> <pkg> <cdir> <rc> <start> <cname> <prefail>
    local os=$1 pkg=$2 cdir=$3 rc=$4 start=$5 cname=$6 prefail=$7
    local dur timed_out=0 tag item st reason status=pass osinfo="" note_rc=""
    declare -A RS=() RR=()
    dur=$(( $(date +%s) - start ))
    if [ -z "$prefail" ] && [ -f "$cdir/console.log" ]; then
        while IFS='|' read -r tag item st reason; do
            [ "$tag" = RESULT ] || continue
            RS[$item]=$st
            RR[$item]=$reason
        done < <(grep -a '^RESULT|' "$cdir/console.log")
        osinfo=$(grep -a -m 1 '^OSINFO|' "$cdir/console.log" | cut -d'|' -f2)
    fi
    if [ -z "$prefail" ] && { [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; } && [ "$dur" -ge "$TIMEOUT_SEC" ]; then
        timed_out=1
    fi
    {
        for item in "${ITEMS[@]}"; do
            if ! item_applicable "$pkg" "$item"; then
                st=NA; reason=""
            elif [ -n "$prefail" ]; then
                st=fail
                if [ "$item" = install ]; then reason=$prefail; else reason="선행 실패(실행 안 됨)"; fi
            elif [ -n "${RS[$item]:-}" ]; then
                st=${RS[$item]}; reason=${RR[$item]}
            elif [ $timed_out -eq 1 ]; then
                st=timeout; reason="제한 시간 ${TIMEOUT_SEC}초 초과"
            else
                st=fail; reason="결과 없음(install_test.sh 종료 코드 $rc)"
            fi
            [ "$st" = pass ] || [ "$st" = NA ] || status=fail
            printf 'ITEM|%s|%s|%s\n' "$item" "$st" "$reason"
        done
    } > "$cdir/result.tmp"
    [ $timed_out -eq 1 ] && status=timeout
    if [ "$status" = pass ] && [ -z "$prefail" ] && [ "$rc" -ne 0 ]; then
        status=fail
        note_rc="install_test.sh 종료 코드 $rc"
    fi
    {
        printf 'META|os=%s\n' "$os"
        printf 'META|pkg=%s\n' "$pkg"
        printf 'META|image=%s\n' "${OS_IMAGE[$os]}"
        printf 'META|file=%s\n' "${PKG_FILE[$pkg]:-}"
        printf 'META|version=%s\n' "${PKG_VER[$pkg]:-$BUILD_VER}"
        printf 'META|status=%s\n' "$status"
        printf 'META|start=%s\n' "$start"
        printf 'META|duration=%s\n' "$dur"
        printf 'META|container=%s\n' "$cname"
        printf 'META|rc=%s\n' "$rc"
        printf 'META|osinfo=%s\n' "$osinfo"
        printf 'META|note=%s\n' "$note_rc"
        cat "$cdir/result.tmp"
    } > "$cdir/result.txt"
    rm -f "$cdir/result.tmp"
    echo "$status"
}

# timeout 으로 docker exec 클라이언트만 죽으면 컨테이너 안의 install_test.sh 가 계속 돌아 남긴 컨테이너가 계속 변한다
# (종료, 삭제, 제거 단계까지 진행). 남기기 전에 테스트 스크립트 계열 프로세스만 멈추고 CUBRID 프로세스(cub_*)는 조사를 위해 그대로 둔다.
# 패턴의 [i] 는 pkill 자신(sh -c 명령줄)이 걸리지 않게 하려는 것이다.
stop_test_script() {   # stop_test_script <컨테이너> <조합 디렉터리>
    local cname=$1 cdir=$2 rc msg note
    # 컨테이너 안 종료 코드: 0 = 멈춘 프로세스 있음, 1 = pkill 매칭 0건, 3 = pkill 명령 없음, 그 밖 = 확인 불가
    timeout -k 5 30 docker exec -u root "$cname" sh -c 'command -v pkill > /dev/null 2>&1 || exit 3; pkill -f "[i]nstall_test.sh"; exit $?' >> "$cdir/runner.log" 2>&1
    rc=$?
    case $rc in
        0) msg="제한 시간 초과로 컨테이너를 남기기 전에 컨테이너 안 install_test.sh 계열 프로세스를 멈춤(CUBRID 프로세스는 그대로 둠)"
           note="timeout 후 남긴 컨테이너 안 테스트 스크립트를 멈춤(CUBRID 프로세스는 그대로)" ;;
        1) msg="제한 시간 초과 후 컨테이너 안에 멈출 install_test.sh 계열 프로세스가 없었음(pkill 매칭 0건, CUBRID 프로세스는 그대로 둠)"
           note="timeout 후 남긴 컨테이너 안에 멈출 테스트 스크립트 프로세스 없음" ;;
        3) msg="컨테이너 안에 pkill 이 없어 install_test.sh 를 멈추지 못함: 테스트 스크립트가 계속 돌 수 있음"
           note="pkill 없음, 테스트 스크립트가 계속 돌 수 있음" ;;
        *) msg="컨테이너 안 install_test.sh 중지 결과를 확인하지 못함(종료 코드 $rc): 테스트 스크립트가 계속 돌 수 있음"
           note="테스트 스크립트 중지 확인 불가(종료 코드 $rc), 계속 돌 수 있음" ;;
    esac
    echo "$msg" >> "$cdir/runner.log"
    say "  $os / $pkg: $msg"
    # 리포트 Note 에 남긴다(result.txt 의 META|note= 뒤에 덧붙임)
    awk -v add="$note" \
        'BEGIN{FS=OFS="|"} $1=="META" && $2 ~ /^note=/ { v=substr($2,6); $2="note=" (v=="" ? add : v "; " add) } { print }' \
        "$cdir/result.txt" > "$cdir/result.tmp" && mv "$cdir/result.tmp" "$cdir/result.txt"
}

progress() { printf '[%2d/%d]' "$1" "${#COMBOS[@]}"; }

run_combo() {   # 백그라운드 작업
    local os=$1 pkg=$2 idx=$3
    local fname=${PKG_FILE[$pkg]:-} tag=${IMAGE_TAG[$os]:-}
    local cdir="$RESULT_ROOT/${os}__${pkg}"
    local cname="${CONTAINER_PREFIX}-${RUN_ID}-${os}__${pkg}"
    local start rc=0 prefail="" status
    start=$(date +%s)
    rm -rf "$cdir"
    mkdir -p "$cdir"
    say "$(progress "$idx") 시작  $os / $pkg"

    if [ -n "${PKG_MISSING[$pkg]:-}" ]; then
        prefail=${PKG_MISSING[$pkg]}
    elif ! image_ok "$os"; then
        prefail=$(cat "$RUN_TMP/image-$os.reason" 2>/dev/null)
        [ -n "$prefail" ] || prefail="이미지 빌드 실패(image_build/$os.log)"
    fi

    if [ -n "$prefail" ]; then
        echo "실행하지 않음: $prefail" > "$cdir/console.log"
        cname=""
    elif [ "$DRY_RUN" -eq 1 ]; then
        cname="(dry-run)"
        timeout -k 5 "$TIMEOUT_SEC" bash -c "$(declare -f fake_install_test); fake_install_test \"\$@\"" \
            _ "$os" "$pkg" "$fname" > "$cdir/console.log" 2>&1
        rc=$?
    else
        {
            echo "image=$tag container=$cname"
            docker run -d --init --name "$cname" --hostname "${os}-${pkg//./-}" \
                --label "$LABEL_KEY=$RUN_ID" --network "$TEST_NETWORK" \
                -e "INSTALL_TEST_IN_CONTAINER=$RUN_ID" \
                -v "$PKG_DIR:/pkgs:ro" -v "$SCRIPT_DIR/test_script:/test_script:ro" \
                "$tag" sleep infinity
        } > "$cdir/runner.log" 2>&1
        rc=$?
        if [ $rc -ne 0 ]; then
            prefail="컨테이너 생성 실패(runner.log)"
            cp "$cdir/runner.log" "$cdir/console.log"
        else
            echo "$cname" >> "$CREATED_FILE"
            timeout -k 10 "$TIMEOUT_SEC" docker exec -u root \
                -e "INSTALL_TEST_IN_CONTAINER=$RUN_ID" \
                ${HOST_TZ:+-e "TZ=$HOST_TZ"} "$cname" \
                bash /test_script/install_test.sh "/pkgs/$fname" --pkg-key "$pkg" \
                > "$cdir/console.log" 2>&1
            rc=$?
            docker cp "$cname:$CONTAINER_RESULT_DIR/." "$cdir/" >> "$cdir/runner.log" 2>&1
        fi
    fi

    status=$(finalize_combo "$os" "$pkg" "$cdir" "$rc" "$start" "$cname" "$prefail")

    if [ "$DRY_RUN" -eq 0 ] && [ -n "$cname" ] && [ -z "$prefail" ]; then
        if [ "$status" != pass ] && [ "$KEEP_FAILED" -eq 1 ]; then
            if [ "$status" = timeout ]; then
                stop_test_script "$cname" "$cdir"
            fi
            echo "$os $cname" >> "$KEPT_FILE"
            say "$(progress "$idx") 컨테이너 유지: $cname"
        else
            docker rm -f "$cname" >> "$cdir/runner.log" 2>&1 && echo "$cname" >> "$REMOVED_FILE"
        fi
    elif [ "$DRY_RUN" -eq 0 ] && [ -n "$prefail" ] && [ -n "$cname" ]; then
        # docker run -d 가 실패했을 때: 컨테이너가 실제로 있을 때만 지우고 생성·삭제로 센다
        # (최근 docker 는 없는 컨테이너의 rm -f 도 성공을 돌려주므로 그 성공만으로는 세지 않는다)
        if [ -n "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ]; then
            docker rm -f "$cname" >> "$cdir/runner.log" 2>&1
            grep -qxF -- "$cname" "$CREATED_FILE" 2>/dev/null || echo "$cname" >> "$CREATED_FILE"
            [ -z "$(docker ps -aq --filter "name=^$(name_re "$cname")\$" 2>/dev/null)" ] && echo "$cname" >> "$REMOVED_FILE"
        fi
    fi
    say "$(progress "$idx") 완료  $os / $pkg : $status ($(( $(date +%s) - start ))s)"
}

run_queue() {
    local i=0 c
    for c in "${COMBOS[@]}"; do
        i=$((i + 1))
        wait_for_slot
        run_combo "${c%%|*}" "${c#*|}" "$i" &
    done
    wait
}

# ---------------- 리포트 ----------------
combine() {   # 여러 항목 상태를 하나로: fail > timeout > pass > NA
    local v out=NA
    for v in "$@"; do [ "$v" = fail ] && { echo fail; return; }; done
    for v in "$@"; do [ "$v" = timeout ] && { echo timeout; return; }; done
    for v in "$@"; do [ "$v" = pass ] && out=pass; done
    echo "$out"
}

csv_field() {
    local s=$1
    case "$s" in
        *[\",]*|*$'\n'*) s=${s//\"/\"\"}; s="\"$s\"" ;;
    esac
    printf '%s' "$s"
}

csv_row() {
    local first=1 f
    for f in "$@"; do
        [ $first -eq 1 ] || printf ','
        first=0
        csv_field "$f"
    done
    printf '\n'
}

md_row() {
    local f out="|"
    for f in "$@"; do
        f=${f//|/\\|}
        out+=" $f |"
    done
    printf '%s\n' "$out"
}

# result.txt 읽기 -> M[], S[], R[] (호출한 함수의 지역 연관 배열)
read_result() {
    local file=$1 kind a b c
    while IFS='|' read -r kind a b c; do
        case "$kind" in
            META) M[${a%%=*}]=${a#*=} ;;
            ITEM) S[$a]=$b; R[$a]=$c ;;
        esac
    done < "$file"
}

# Note: 근본 사유만 적고(같은 사유는 항목을 묶음) 선행 실패 항목은 개수로 요약
make_note() {
    local item reason i found pre=0
    local reasons=() labels=() notes=()
    [ "${M[status]}" = timeout ] && notes+=("timeout(${TIMEOUT_SEC}s)")
    for item in "${ITEMS[@]}"; do
        reason=${R[$item]}
        # install pass의 사유는 cubrid_rel 버전 (+ 참고 사항) -> 버전은 Note에서 뺌
        if [ "$item" = install ] && [ "${S[$item]}" = pass ]; then
            reason=${reason#"${M[version]:-}"}
            reason=${reason#; }
        fi
        case "${S[$item]}" in
            pass|NA) [ -n "$reason" ] || continue ;;
            timeout) continue ;;
            *) if [[ $reason == "선행 실패"* ]]; then pre=$((pre + 1)); continue; fi ;;
        esac
        found=-1
        for i in "${!reasons[@]}"; do
            [ "${reasons[$i]}" = "$reason" ] && found=$i
        done
        if [ "$found" -ge 0 ]; then
            labels[found]+=", ${ITEM_LABEL[$item]}"
        else
            reasons+=("$reason")
            labels+=("${ITEM_LABEL[$item]}")
        fi
    done
    for i in "${!reasons[@]}"; do
        notes+=("${labels[$i]}: ${reasons[$i]}")
    done
    [ $pre -gt 0 ] && notes+=("선행 실패로 ${pre}개 항목 fail")
    [ -n "${M[note]}" ] && notes+=("${M[note]}")
    local IFS=$'\x1f'
    local joined="${notes[*]}"
    printf '%s' "${joined//$'\x1f'/ / }"
}

# ---- JUnit XML (report.xml) ----
# XML 본문/속성용 이스케이프: UTF-8로 유효하지 않은 바이트와 XML 1.0 에서 허용되지 않는 제어 문자(탭, LF, CR 제외)를 지우고
# & < > " ' 를 이스케이프한다.
xml_escape() {
    local s=$1
    if command -v iconv > /dev/null 2>&1; then
        s=$(printf '%s' "$s" | iconv -c -f UTF-8 -t UTF-8 2> /dev/null | tr -d '\000-\010\013\014\016-\037')
    else
        s=$(printf '%s' "$s" | tr -d '\000-\010\013\014\016-\037')
    fi
    printf '%s' "$s" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g' -e "s/'/\&apos;/g"
}

# 속성값: 줄바꿈, 탭, CR 은 공백으로 바꾼 뒤 이스케이프
xml_attr() {
    xml_escape "$(printf '%s' "$1" | tr '\n\r\t' '   ')"
}

# report.csv 판정 열(Install ~ SQL Statement) 10개에 대응하는 result.txt 항목(여러 개면 첫 fail/timeout 항목의 사유를 쓴다)
JUNIT_COL_ITEMS=("install" "bash_profile" "uninstall" "service_start stop" "server_start stop"
    "broker" "manager" "createdb" "deletedb" "sql")
JU_TESTS=0; JU_FAILS=0; JU_SKIPS=0

# testsuite 하나를 표준 출력에 쓴다. 호출한 함수의 M[], S[], R[] 를 읽는다.
# 사용: junit_suite <OS 키> <패키지 키> <조합 디렉터리> <Note> <report.csv 판정 열 상태 10개>
junit_suite() {
    local os=$1 pkg=$2 cdir=$3 note=$4
    shift 4
    local sts=("$@") cls="${os}.${pkg//./_}" i st name it its reason body log host dur start nfail=0 nskip=0
    for st in "${sts[@]}"; do
        case "$st" in
            pass) ;;
            NA) nskip=$((nskip + 1)) ;;
            *) nfail=$((nfail + 1)) ;;
        esac
    done
    JU_TESTS=$((JU_TESTS + ${#sts[@]})); JU_FAILS=$((JU_FAILS + nfail)); JU_SKIPS=$((JU_SKIPS + nskip))
    dur=${M[duration]:-0}; [[ $dur =~ ^[0-9]+$ ]] || dur=0
    start=${M[start]:-$RUN_START}; [[ $start =~ ^[0-9]+$ ]] || start=$RUN_START
    # 컨테이너가 없는(실행하지 않은) 조합은 호스트 이름 대신 not-run (리포트를 밖으로 공유해도 호스트 이름이 나가지 않게)
    host=${M[container]:-}; [ -n "$host" ] || host=not-run
    log="${cdir#"$SCRIPT_DIR"/}/console.log"
    printf '  <testsuite name="%s" tests="%d" failures="%d" errors="0" skipped="%d" time="%s" timestamp="%s" hostname="%s">\n' \
        "$(xml_attr "$os / $pkg")" "${#sts[@]}" "$nfail" "$nskip" "$dur" \
        "$(date -d "@$start" '+%FT%T%:z')" "$(xml_attr "$host")"
    printf '    <properties>\n'
    local k v
    for k in run_id runner host test_build test_os actual_os build_version note; do
        case "$k" in
            run_id) v=$RUN_ID ;;
            runner) v="install_test_$RUNNER_VERSION (run_install_test.sh $RUNNER_SHA, test_script $TESTSCRIPT_SHA)" ;;
            host) v=$HOST_INFO ;;
            test_build) v=${M[file]:-} ;;
            test_os) v=${M[image]:-} ;;
            actual_os) v=${M[osinfo]:-} ;;
            build_version) v=${M[version]:-$BUILD_VER} ;;
            note) v=$note ;;
        esac
        printf '      <property name="%s" value="%s"/>\n' "$k" "$(xml_attr "$v")"
    done
    printf '    </properties>\n'
    for i in "${!sts[@]}"; do
        st=${sts[$i]}
        name=${REPORT_HEADER[$((i + 2))]}
        printf '    <testcase classname="%s" name="%s"' "$(xml_attr "$cls")" "$(xml_attr "$name")"
        case "$st" in
            pass) printf '/>\n' ;;
            NA) printf '>\n      <skipped message="NA"/>\n    </testcase>\n' ;;
            *)
                reason=""; its=""
                for it in ${JUNIT_COL_ITEMS[$i]}; do
                    if [ "${S[$it]:-}" = "$st" ]; then its=$it; reason=${R[$it]:-}; break; fi
                done
                [ -n "$reason" ] || reason=$note
                [ -n "$reason" ] || reason=$st
                body=""
                [ -z "$its" ] || body="항목: $its"$'\n'
                body+="사유: $reason"$'\n'"로그: $log"
                if [ "$st" = timeout ]; then
                    printf '>\n      <failure type="timeout" message="%s">%s</failure>\n    </testcase>\n' \
                        "$(xml_attr "timeout(${TIMEOUT_SEC}s)")" "$(xml_escape "$body")"
                else
                    printf '>\n      <failure type="fail" message="%s">%s</failure>\n    </testcase>\n' \
                        "$(xml_attr "$reason")" "$(xml_escape "$body")"
                fi
                ;;
        esac
    done
    printf '  </testsuite>\n'
}

# 전체 report.xml 작성: junit_write <출력 파일> <본문(testsuite들) 파일> <빌드 버전> <전체 소요 초>
junit_write() {
    local out=$1 body=$2 ver=$3 elapsed=$4
    {
        printf '<?xml version="1.0" encoding="UTF-8"?>\n'
        # testsuites 에는 skipped, timestamp 속성을 쓰지 않는다(Jenkins xUnit 의 junit-10.xsd 에 없음)
        printf '<testsuites name="%s" tests="%d" failures="%d" errors="0" time="%s">\n' \
            "$(xml_attr "CUBRID install test - $ver")" "$JU_TESTS" "$JU_FAILS" "$elapsed"
        cat "$body"
        printf '</testsuites>\n'
    } > "$out"
}

generate_reports() {
    local run_dir="$RESULT_ROOT/runs/$RUN_ID" csv md c os pkg cdir row note
    local total=0 npass=0 nfail=0 ntimeout=0 failed_combos=() xml xml_body="$RUN_TMP/xml_body"
    JU_TESTS=0; JU_FAILS=0; JU_SKIPS=0
    mkdir -p "$run_dir"
    xml="$run_dir/report.xml"
    : > "$xml_body"
    csv="$run_dir/report.csv"
    md="$run_dir/report.md"
    local md_table="$RUN_TMP/md_table" md_fail="$RUN_TMP/md_fail"
    : > "$md_table"; : > "$md_fail"

    printf '\xEF\xBB\xBF' > "$csv"   # Excel에서 한글이 깨지지 않도록 UTF-8 BOM
    csv_row "${REPORT_HEADER[@]}" >> "$csv"
    md_row "${REPORT_HEADER[@]}" "실제 OS" >> "$md_table"
    local sep=() i
    for ((i = 0; i <= ${#REPORT_HEADER[@]}; i++)); do sep+=("---"); done
    md_row "${sep[@]}" >> "$md_table"

    for c in "${COMBOS[@]}"; do
        os=${c%%|*}; pkg=${c#*|}
        cdir="$RESULT_ROOT/${os}__${pkg}"
        declare -A M=() S=() R=()
        if [ -f "$cdir/result.txt" ]; then
            read_result "$cdir/result.txt"
        else
            M=([status]=fail [image]="${OS_IMAGE[$os]}" [file]="${PKG_FILE[$pkg]:-}" [note]="결과 파일 없음"
               [osinfo]="" [container]="" [version]="${PKG_VER[$pkg]:-$BUILD_VER}")
            local item
            for item in "${ITEMS[@]}"; do
                if item_applicable "$pkg" "$item"; then S[$item]=fail; else S[$item]=NA; fi
                R[$item]=""
            done
        fi
        total=$((total + 1))
        case "${M[status]}" in
            pass) npass=$((npass + 1)) ;;
            timeout) ntimeout=$((ntimeout + 1)); nfail=$((nfail + 1)); failed_combos+=("$c") ;;
            *) nfail=$((nfail + 1)); failed_combos+=("$c") ;;
        esac
        note=$(make_note)
        row=("${M[file]}" "${M[image]}" "${S[install]}" "${S[bash_profile]}" "${S[uninstall]}"
            "$(combine "${S[service_start]}" "${S[stop]}")"
            "$(combine "${S[server_start]}" "${S[stop]}")"
            "${S[broker]}" "${S[manager]}" "${S[createdb]}" "${S[deletedb]}" "${S[sql]}"
            "${M[version]:-$BUILD_VER}" "$note")
        csv_row "${row[@]}" >> "$csv"
        md_row "${row[@]}" "${M[osinfo]}" >> "$md_table"
        junit_suite "$os" "$pkg" "$cdir" "$note" "${row[@]:2:10}" >> "$xml_body"

        if [ "${M[status]}" != pass ]; then
            {
                echo "### ${os} / ${pkg} : ${M[status]}"
                echo
                echo "- 이미지: \`${M[image]}\`${M[osinfo]:+ (${M[osinfo]})}, 패키지: \`${M[file]}\`"
                echo "- 로그: \`${cdir#"$SCRIPT_DIR"/}/console.log\` (결과 디렉터리: \`${cdir#"$SCRIPT_DIR"/}/\`)"
                if [ -n "${M[container]}" ] && is_kept_container "${M[container]}"; then
                    echo "- 남긴 컨테이너: \`${M[container]}\` (확인 후 \`docker rm -f ${M[container]}\`)"
                fi
                [ -n "${M[note]}" ] && echo "- 참고: ${M[note]}"
                for item in "${ITEMS[@]}"; do
                    case "${S[$item]}" in
                        pass|NA) ;;
                        *) echo "- ${ITEM_LABEL[$item]} (\`$item\`): ${S[$item]} - ${R[$item]}" ;;
                    esac
                done
                echo
            } >> "$md_fail"
        fi
        unset M S R
    done

    {
        echo "# CUBRID install test 결과 - $BUILD_VER"
        echo
        echo "- 실행 ID: \`$RUN_ID\`$([ "$DRY_RUN" -eq 1 ] && echo " (dry-run, 가짜 결과)")"
        echo "- 실행 시간: $(date -d "@$RUN_START" '+%F %T %z') ~ $(date '+%F %T %z')"
        echo "- 러너: install_test_$RUNNER_VERSION (run_install_test.sh $RUNNER_SHA, test_script $TESTSCRIPT_SHA)"
        echo "- 호스트: $HOST_INFO"
        echo "- 패키지: \`$(mask_url "$SRC")\`"
        echo "- 병렬 수: $JOBS, 조합당 제한 시간: ${TIMEOUT_SEC}초"
        echo "- 결과: 전체 $total, pass $npass, fail $nfail (그중 timeout $ntimeout)"
        echo
        echo "## 결과표"
        echo
        echo "Service, Server 열은 시작(4, 5번)과 종료(9번)가 모두 pass일 때 pass. UnInstall은 rpm만 해당(그 외 NA)."
        echo
        cat "$md_table"
        echo
        if [ ${#failed_combos[@]} -gt 0 ]; then
            echo "## 실패 조합 상세"
            echo
            cat "$md_fail"
        fi
    } > "$md"

    junit_write "$xml" "$xml_body" "$BUILD_VER" "$(( $(date +%s) - RUN_START ))"
    cp -f "$csv" "$RESULT_ROOT/report.csv"
    cp -f "$xml" "$RESULT_ROOT/report.xml"
    cp -f "$md" "$RESULT_ROOT/report.md"
    SUMMARY="전체 $total, pass $npass, fail $nfail (timeout $ntimeout)"
    [ "$nfail" -eq 0 ]
}

# ---------------- 동시 실행 방지 ----------------
# 호스트 공용 잠금 파일을 flock -n 으로 잡는다. 이미 잡혀 있으면 docker 에 아무것도 하지 않고 종료 코드 2.
# 잠금 fd(9)는 러너 종료까지 유지한다. 백그라운드 작업(계열 판별, 이미지 빌드, 조합 실행)은 모두 이 fd 를 물려받으므로,
# 러너 본체가 강제 종료(SIGKILL 등)돼도 남은 작업이 끝날 때까지 잠금이 유지된다(그동안 새 러너는 거부된다).
# 작업의 하위 프로세스(timeout, docker 클라이언트)도 fd 를 물려받으므로 이들이 끝날 때까지도 유지된다(pgrep 에는 안 보일 수 있어 fuser -v 로 확인).
acquire_lock() {
    command -v flock > /dev/null 2>&1 || die "flock 명령이 필요합니다 (util-linux)"
    if [ ! -e "$LOCK_FILE" ]; then
        ( umask 000; : >> "$LOCK_FILE" ) 2>/dev/null
        chmod 666 "$LOCK_FILE" 2>/dev/null
    fi
    # 읽기 전용(O_CREAT 없음)으로 열어 flock 한다: fs.protected_regular=1 호스트에서 다른 계정이 만든 /tmp 파일을
    # O_CREAT 로 열면 거부되는 문제를 피한다(파일은 위에서 없을 때만 만들고 666 권한을 준다).
    if ! exec 9< "$LOCK_FILE"; then
        die "잠금 파일을 열 수 없습니다: $LOCK_FILE (다른 계정이 만든 파일이면 권한 확인)"
    fi
    if ! flock -n 9; then
        die "다른 install test가 실행 중입니다 (잠금 파일 $LOCK_FILE). 끝난 뒤 다시 실행하세요. 러너가 강제 종료됐다면 남은 작업과 그 하위 프로세스가 끝날 때까지 잠금이 유지됩니다(확인: fuser -v $LOCK_FILE 또는 pgrep -af run_install_test.sh)"
    fi
    CLEANUP_ARMED=1     # 이 시점부터 정리 단계가 docker 를 조회한다
    touch -c "$LOCK_FILE" 2>/dev/null || true   # 수정 시각을 갱신해 /tmp 정리 도구가 오래된 파일로 지우지 않게 한다
}

# ---------------- main ----------------
main() {
    local rc os
    load_matrix
    build_combos
    compute_runner_hashes
    say "러너: install_test_$RUNNER_VERSION (run_install_test.sh $RUNNER_SHA, test_script $TESTSCRIPT_SHA)"
    say "CUBRID install test 시작 (실행 ID: $RUN_ID$([ "$DRY_RUN" -eq 1 ] && echo ", dry-run"))"
    say "조합 ${#COMBOS[@]}개, 병렬 $JOBS, 조합당 제한 시간 ${TIMEOUT_SEC}초"

    if [ "$DRY_RUN" -eq 1 ]; then
        CLEANUP_ARMED=1     # dry-run 은 잠금을 잡지 않지만 "(dry-run) 정리 생략" 줄은 그대로 낸다
    else
        acquire_lock
    fi
    if [ "$DRY_RUN" -eq 0 ]; then
        command -v docker > /dev/null 2>&1 || die "docker 명령이 없습니다"
        docker info > /dev/null 2>&1 || die "docker 데몬에 접근할 수 없습니다"
        local dsv
        dsv=$(docker version --format '{{.Server.Version}}' 2>/dev/null | head -n 1)
        HOST_INFO="$(uname -r), docker ${dsv:-확인 불가}"
    fi

    if [ "$CHECK_NETWORK_ONLY" -eq 1 ]; then
        say "네트워크 확인 (--check-network)"
        prepare_network
        [ "$DRY_RUN" -eq 1 ] || say "  $(docker network inspect -f '{{.Name}} driver={{.Driver}} subnet={{range .IPAM.Config}}{{.Subnet}}{{end}} gateway={{range .IPAM.Config}}{{.Gateway}}{{end}} containers={{len .Containers}}' "$TEST_NETWORK")"
        return 0
    fi

    if [ "$IMAGES_ONLY" -eq 1 ]; then
        RESULT_ROOT=$RESULTS_BASE
        say "이미지 준비 (--images-only)"
        prepare_images
        rc=0
        for os in "${!IMAGE_TAG[@]}"; do
            image_ok "$os" || rc=1
        done
        say "이미지 준비 $([ $rc -eq 0 ] && echo 완료 || echo "실패 (로그: $RESULT_ROOT/image_build/)")"
        return $rc
    fi

    say "1) 네트워크 준비"
    prepare_network

    say "2) 패키지 준비"
    prepare_packages
    if [ "$DRY_RUN" -eq 1 ]; then
        RESULT_ROOT=$RESULTS_BASE/dry-run/$BUILD_VER
    else
        RESULT_ROOT=$RESULTS_BASE/$BUILD_VER
    fi
    mkdir -p "$RESULT_ROOT/runs/$RUN_ID" || die "결과 디렉터리를 만들 수 없습니다: $RESULT_ROOT"

    say "3) 이미지 준비"
    prepare_images

    compute_host_tz
    [ -n "$HOST_TZ" ] && say "  컨테이너 시각대: $HOST_TZ (호스트와 같게 TZ로 전달)"
    say "4) 조합 실행"
    run_queue

    say "5) 리포트 생성"
    if generate_reports; then rc=0; else rc=1; fi
    say "결과: $SUMMARY"
    say "리포트: $RESULT_ROOT/report.md"
    say "        $RESULT_ROOT/report.csv"
    say "        $RESULT_ROOT/report.xml"
    say "6) 정리"
    save_run_log
    return $rc
}

main
exit $?
