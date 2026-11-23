#!/bin/bash
#
# CUBRID install test runner (runs on the host)
#
#   ./run_install_test.sh <build URL | package directory> [options]
#
# Prepare packages -> build an image per OS (skipped if present) -> for each combination (OS x package), in a new container
# run test_script/install_test.sh -> collect results/logs -> delete the container -> write report.csv / report.md.
# Exit code 0 only when all combinations pass.
#
set -u
set -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RUNNER_VERSION=v3
RUNNER_SHA=""        # first 12 chars of this script's sha256 (computed in main)
TESTSCRIPT_SHA=""    # first 12 chars of the hash of all files in test_script/ (computed in main)
HOST_INFO="(dry-run)"  # "host" line in report.md (filled in main when running)

# ---------------- defaults (kept in one place) ----------------
DEFAULT_JOBS=4
MAX_JOBS=4
# Time limit per combination (seconds). Default 1200. Can be changed with --timeout.
DEFAULT_TIMEOUT_SEC=1200
# Time limits for the image preparation steps (seconds). Exceeding one fails only that OS.
PULL_TIMEOUT_SEC=900     # docker pull of the base image
DETECT_TIMEOUT_SEC=120   # family detection container (reads /etc/os-release)
BUILD_TIMEOUT_SEC=1800   # docker build
# Host-wide lock file to prevent concurrent runs (can be changed with an environment variable: for tests)
LOCK_FILE=${INSTALL_TEST_LOCK_FILE:-/tmp/cubrid-install-test.lock}
IMAGE_REPO=cubrid-install-test
CONTAINER_PREFIX=cit
LABEL_KEY=cubrid-install-test.run
CONTAINER_RESULT_DIR=/var/tmp/install_test
# Network for image builds (yum/apt). Uses host because some hosts (ip_forward=0) have no outside access from the default bridge.
BUILD_NETWORK=host
# Images are built with the legacy builder. BuildKit keeps a build cache separate from images, so
# layers stay after the images are removed, and that cache is mixed with other projects and hard to remove selectively.
export DOCKER_BUILDKIT=0
# Dedicated network for test containers. Not attached to host or the default bridge (docker0), kept apart from other containers.
# If missing, the runner creates it (and removes it at the end only if this run created it); if present, it is reused after checking the subnet.
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

# ---------------- argument handling ----------------
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

# Mask with *** everything up to the last "@" in the URL authority (after "://", before the first "/"), i.e. user[:password], even if it contains "@".
# "@" in the path is kept. Used when writing a URL to the screen, run.log, report.md and error messages (curl itself gets the original URL).
# Without "://", if there is a "user:password@" form before the first "/" (scheme left out), mask up to the last "@" before the first "/".
# Directory paths cannot contain ":" (README), so requiring ":" keeps valid directories from being masked.
# Also used by die during argument handling, so it is defined as early as possible.
mask_url() { printf '%s' "$1" | sed -E '/:\/\//!s#^[^/:]*:[^/]*@#***@#; s#(://)[^/]*@#\1***@#'; }

# docker's name filter is a regex, so "." in a container name matches any character. Replace "." with "\." only when putting it in a filter
# (rm/inspect get the exact name). Names can only contain letters/digits/._-, so only "." needs handling.
name_re() { printf '%s' "${1//./\\.}"; }

need_arg() {   # need_arg <option> <remaining arg count> <value>
    [ "$2" -ge 2 ] || die "$1 옵션에 값이 필요합니다"
    case "${3-}" in
        -*) die "$1 의 값이 '-' 로 시작합니다: ${3} (값이 빠졌는지 확인하세요)" ;;
    esac
}
split_csv() { local IFS=,; read -r -a SPLIT <<< "$1"; }
# --os/--pkg value: error if empty or if leading/trailing/consecutive commas make an empty entry (an empty entry is a prefix of every key, so it would mean "all")
split_csv_opt() {   # split_csv_opt <option> <value>
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

# ---------------- run state ----------------
RUN_ID=$(date +%Y%m%d-%H%M%S)-$$
RUN_TMP=$(mktemp -d "${TMPDIR:-/tmp}/cubrid-install-test.XXXXXX") || die "임시 디렉터리 생성 실패"
RUN_LOG=$RUN_TMP/run.log
KEPT_FILE=$RUN_TMP/kept_containers   # one "<OS key> <container name>" per line
: > "$KEPT_FILE"
declare -A BASE_PREEXIST=() IMAGE_PREEXIST=() IMAGE_TAG=() BASE_ID=()
REPORT_SAVE_ERR=()
declare -A IMAGE_FAMILY=()   # OS key -> family (deb|rpm|custom|unknown)
declare -A IMAGE_DIR=()      # OS key -> Dockerfile directory actually used
declare -A BUILD_STARTED=()  # OS keys whose docker build this run started
declare -A BASE_ASSUMED=()   # OS keys "assumed pre-existing" because the base image check failed at start
CLEANUP_ARMED=0      # 1 means the cleanup step touches docker (after taking the lock; for dry-run, set where the lock is skipped)
CLEANUP_ERROR=0      # error during cleanup (pre-existing image removed, etc.) -> make the runner exit code non-zero
CREATED_FILE=$RUN_TMP/created_containers   # containers this run created (one per line)
REMOVED_FILE=$RUN_TMP/removed_containers   # containers removed when a combination finished
: > "$CREATED_FILE"; : > "$REMOVED_FILE"
HOST_TZ=""           # POSIX TZ value passed to containers (e.g. KST-09:00), from the host's current UTC offset
NETWORK_READY=0      # the test network is ready for this run
NETWORK_CREATED=0    # this run created the test network
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

# Turn the host's current time zone into POSIX TZ form (works in images without tzdata). e.g. +0900 -> KST-09:00
compute_host_tz() {
    local off sign hh mm abbr
    off=$(date +%z) || return 0
    abbr=$(date +%Z)
    sign=${off:0:1}; hh=${off:1:2}; mm=${off:3:2}
    [[ $abbr =~ ^[A-Za-z]{3,6}$ ]] || abbr="HOST"
    if [ "$sign" = "+" ]; then sign="-"; else sign="+"; fi   # POSIX TZ has the opposite sign
    HOST_TZ="${abbr}${sign}${hh}:${mm}"
}

# ---------------- cleanup (including Ctrl-C) ----------------
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

# ---------------- test network ----------------
ip2int() {
    local IFS=.
    # shellcheck disable=SC2086
    set -- $1
    echo $(( ($1 << 24) + ($2 << 16) + ($3 << 8) + $4 ))
}

cidr_range() {   # "a.b.c.d/n" or "a.b.c.d" -> "start end" (integers)
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

network_subnets() {   # IPv4 subnets of a network (name/ID)
    docker network inspect -f '{{range .IPAM.Config}}{{.Subnet}} {{end}}' "$1" 2>/dev/null \
        | tr ' ' '\n' | while read -r c; do is_ipv4_cidr "$c" && echo "$c"; done
}

# Print the list if TEST_SUBNET overlaps existing docker networks or host addresses/routes
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

# Prepare the test network: if present, check the subnet and reuse it; if not, check for overlaps and create it. Stop before running if there is a problem.
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
        # Fail unless another run created it at the same time
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

# Remove only the network this run created, when no containers are attached
cleanup_network() {
    local n owner
    # If a signal arrives while creating the network, the trap can run right after docker network create, before NETWORK_CREATED is set.
    # So use the created-by label on the network instead of the variable to tell whether this run created it.
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
        # Count containers not yet counted as created (interrupted, etc.) so that "created N, removed M" stays consistent
        grep -qxF "$name" "$CREATED_FILE" 2>/dev/null || echo "$name" >> "$CREATED_FILE"
        docker rm -f "$id" > /dev/null 2>&1 && CLEAN_RM_CT=$((CLEAN_RM_CT + 1))
    done
}

remove_image() {   # remove_image <image>: remove it if present and record the result
    docker image inspect "$1" > /dev/null 2>&1 || return 0
    if log_cmd "docker rmi $1" docker rmi "$1"; then
        CLEAN_RM_IMG+=("$1")
    else
        CLEAN_LEFT+=("이미지 $1(삭제 실패)")
    fi
}

# Record docker command results in run.log (not shown on the terminal)
log_cmd() {   # log_cmd <description> <command...>: returns the exit code as is
    local desc=$1 out rc
    shift
    out=$("$@" 2>&1)
    rc=$?
    printf '[%s]   %s: rc=%s %s\n' "$(date '+%H:%M:%S')" "$desc" "$rc" "$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-300)" >> "$RUN_LOG"
    return $rc
}

# Return only the build log path written by this run (do not read image_build/<os>.log from an earlier run):
# when this run started the docker build for this OS (BUILD_STARTED) and the log was modified after the run started.
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

# Remove leftover " ---> Running in <id>" temporary containers of this build (from the original log).
# Not mixed with the combination/detection container counts (CREATED/REMOVED_FILE); recorded only in CLEAN_RM_BUILD and run.log.
cleanup_build_containers() {
    local os=$1 log cid full name created cts
    log=$(this_run_build_log "$os") || return 0
    for cid in $(sed -n 's/^ ---> Running in \([0-9a-f]\{12\}\)$/\1/p' "$log" 2>/dev/null | awk '!s[$0]++'); do
        full=$(docker ps -aq --no-trunc --filter "id=$cid" 2>/dev/null | head -n 1)
        [ -n "$full" ] || continue    # already gone (a normal build removes its temporary containers itself)
        # Skip if it was not created after this run started (one more guard against matching another container by prefix, etc.)
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

# Remove only untagged intermediate images from the " ---> <id>" lines of this run's original build log (left by failed/interrupted legacy builds).
# " ---> <id>" in the log also has the base image id from the FROM step, so it must be excluded (both the id found at cleanup and BASE_ID recorded at start).
# Images already removed or tagged are skipped, so it is safe to call more than once.
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
        full=$(docker image inspect -f '{{.Id}}' "$id" 2>/dev/null) || continue   # already gone
        ntags=$(docker image inspect -f '{{len .RepoTags}}' "$id" 2>/dev/null)
        if [ "${ntags:-1}" != 0 ]; then
            printf '[%s]   중간 이미지 정리 건너뜀(태그 있음 %s개): %s\n' "$(date '+%H:%M:%S')" "$ntags" "$id" >> "$RUN_LOG"
            continue
        fi
        log_cmd "docker rmi $id(중간 이미지)" docker rmi "$id"
    done
}

# OSes to clean up: union of the OSes with a prepared image tag (IMAGE_TAG) and those with a recorded base image state (BASE_PREEXIST).
# IMAGE_TAG is filled only after family detection (pulling base images) finishes, so an interruption before that leaves the OS only in BASE_PREEXIST.
image_cleanup_oses() {
    local os
    declare -A seen=()
    for os in "${!IMAGE_TAG[@]}" "${!BASE_PREEXIST[@]}"; do
        [ -n "${seen[$os]+x}" ] && continue
        seen[$os]=1
        echo "$os"
    done
}

# Remove only images this run built or newly pulled. Images that existed before the run are kept.
cleanup_images() {
    local os tag base
    declare -A kept_os=()
    for os in $(awk '{ print $1 }' "$KEPT_FILE" 2>/dev/null); do kept_os[$os]=1; done
    for os in $(image_cleanup_oses); do
        tag=${IMAGE_TAG[$os]:-}
        base=${OS_IMAGE[$os]}
        if [ -n "${kept_os[$os]+x}" ]; then
            CLEAN_KEPT+=("${tag:+$tag, }$base(--keep-failed 컨테이너가 사용)")
            continue
        fi
        if [ -n "$tag" ] && [ "${IMAGE_PREEXIST[$os]:-1}" = 0 ]; then
            remove_image "$tag"
            cleanup_intermediate_images "$os"
        fi
        [ "${BASE_PREEXIST[$os]:-1}" = 0 ] && remove_image "$base"
    done
}

# Check what is left after cleanup -> one CLEANUP_SUMMARY line
verify_cleanup() {
    local os n left=() errs=() warns=() created removed_run out
    n=$(docker ps -aq --filter "label=$LABEL_KEY=$RUN_ID" 2>/dev/null | wc -l)
    n=$((n - $(wc -l < "$KEPT_FILE")))
    [ "$n" -gt 0 ] && left+=("컨테이너 ${n}개")
    if [ "$KEEP_IMAGES" -eq 0 ]; then
        for os in $(image_cleanup_oses); do
            awk -v o="$os" '$1 == o { f = 1 } END { exit !f }' "$KEPT_FILE" 2>/dev/null && continue
            [ -n "${IMAGE_TAG[$os]:-}" ] && [ "${IMAGE_PREEXIST[$os]:-1}" = 0 ] \
                && docker image inspect "${IMAGE_TAG[$os]}" > /dev/null 2>&1 && left+=("${IMAGE_TAG[$os]}")
            [ "${BASE_PREEXIST[$os]:-1}" = 0 ] && docker image inspect "${OS_IMAGE[$os]}" > /dev/null 2>&1 \
                && left+=("${OS_IMAGE[$os]}")
        done
    fi
    # Check that base images present before the run are still there (regardless of --keep-images)
    # Treat it as removed (error, exit code 1) only on "No such image". Other check failures (docker access errors, etc.) only warn
    # and do not change the exit code. An OS "assumed pre-existing" because the check failed at start only warns even on No such image at the end.
    for os in $(image_cleanup_oses); do
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

# Append the cleanup result line to the report.md written by this run.
# An interrupted run skips generate_reports, so the top-level report.md is from an earlier run and is left alone
# (append only to the file with this run's ID line "- 실행 ID: `<RUN_ID>`". The cleanup result always goes to run.log).
# Always returns 0 (called from on_exit).
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

# Clean up containers and images (normal/failed exit and Ctrl-C). Write one result line to the log and the report.
final_cleanup() {
    local os
    # Once cleanup starts, ignore INT/TERM/HUP and finish (a signal during cleanup would leave images/networks/temp directories).
    # A signal during the run before cleanup goes to on_signal, which stops the combinations and calls this function.
    trap '' INT TERM HUP
    [ "$CLEANUP_DONE" -eq 1 ] && return
    CLEANUP_DONE=1
    # On errors before taking the lock (option/matrix errors, lock refused), do not query docker and do not print the "정리:" line
    [ "$CLEANUP_ARMED" -eq 1 ] || return
    if [ "$DRY_RUN" -eq 1 ]; then
        say "(dry-run) 정리 생략: 컨테이너, 이번 실행이 빌드/pull한 이미지, 이번 실행이 만든 $TEST_NETWORK 네트워크"
        return
    fi
    command -v docker > /dev/null 2>&1 || return
    CLEAN_RM_CT=0; CLEAN_RM_BUILD=0; CLEAN_RM_IMG=(); CLEAN_LEFT=(); CLEAN_KEPT=(); CLEAN_NET=""
    cleanup_containers
    sleep 1
    cleanup_containers   # once more, for containers requested just before the interruption
    # If an interruption/timeout kills only the client, temporary containers of this build can be left. Regardless of --keep-images/--images-only,
    # remove them once before image cleanup (only " ---> Running in <id>" in the original logs of OSes whose build this run started).
    for os in "${!BUILD_STARTED[@]}"; do cleanup_build_containers "$os"; done
    # Intermediate images of OSes whose build failed/was interrupted/timed out are removed regardless of --keep-images/--images-only/--rebuild-images
    # (layers of a successful build are parents of the prepared image and cannot be removed, so they are excluded).
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
    # On an abnormal exit that is not a signal (e.g. a set -u error), stop background jobs still running first:
    # so they do not keep holding the lock or overlap with cleanup. A normal exit has already waited for all jobs, so it is unaffected.
    if [ -n "$(jobs -rp)" ]; then
        kill_jobs
    fi
    final_cleanup
    rm -rf "$RUN_TMP"
    # If there was an error during cleanup (pre-existing image removed, etc.), make the exit code non-zero
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
        line=${line%$'\r'}     # handle CRLF in matrix
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

os_matches() {   # os_matches <os> <filter token>
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

# ---------------- package preparation ----------------
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

# Record the sha256 of a package file in run.log (only if the file exists).
log_pkg_sha() {
    local f=$1
    [ -f "$f" ] || return 0
    say "  sha256: $(basename "$f") $(sha256sum "$f" | cut -d' ' -f1)"
}

# Hashes (first 12 chars) of the runner and test_script. test_script hashes the sha256 list sorted by file path, so it is reproducible.
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
            # only the form CUBRID-<version><suffix> (e.g. -Linux.x86_64.sh is distinct from -debug.sh)
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
        # show a missing package with the file name of that version (if there is only one version)
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

# ---------------- images ----------------
# If the image inputs (Dockerfile directory actually used, family, base image, add_openssl.sh) change, the tag changes and it is rebuilt automatically
image_tag_for() {
    local os=$1 h
    h=$( { find "${IMAGE_DIR[$os]}" -type f -print0 | sort -z | xargs -0 cat
           echo "${IMAGE_FAMILY[$os]}"
           cat "$SCRIPT_DIR/test_script/add_openssl.sh"
           echo "${OS_IMAGE[$os]}"; } | sha256sum | cut -c1-12)
    echo "$IMAGE_REPO:$os-$h"
}

build_image() {   # background job: writes the result to $RUN_TMP/image-<os>.status
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

# Detect the family from ID, ID_LIKE in /etc/os-release: deb | rpm | unknown
family_from_os_release() {   # $1: os-release contents
    local id like
    id=$(printf '%s\n' "$1" | sed -n 's/^ID=//p' | head -n 1 | tr -d "\"'" | tr 'A-Z' 'a-z')
    like=$(printf '%s\n' "$1" | sed -n 's/^ID_LIKE=//p' | head -n 1 | tr -d "\"'" | tr 'A-Z' 'a-z')
    case " $id $like " in
        *" debian "*|*" ubuntu "*) echo deb ;;
        *" rhel "*|*" fedora "*|*" centos "*|*" rocky "*|*" ol "*|*" almalinux "*) echo rpm ;;
        *) echo unknown ;;
    esac
}

# Background job: get the base image (pull if missing) and, in a temporary container (--rm --network none, run ID label),
# read /etc/os-release and write the family to $RUN_TMP/family-<os>. Result: deb | rpm | unknown | pullfail | pulltimeout | detecttimeout | runfail
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
        # Created/removed counts (counted only when the container was actually created):
        #  - rc 125 is a docker error (no container created) -> not counted
        #  - on timeout (124/137) only the client died, so we cannot tell if it was created -> only when the name filter shows it is there,
        #    rm -f and count 1 created and 1 removed; otherwise 0 for both
        #  - otherwise treat it as created (1); whether it was removed is decided by an empty name filter result, not by a docker inspect failure
        #    (no double counting even if inspect fails on a transient daemon error)
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
    # 1) Record whether the base image was there already (at the end, remove only what this run created/pulled)
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        [ "$DRY_RUN" -eq 1 ] && continue
        # Only "No such image" means this run pulls it (0). Other inspect failures (transient errors, etc.) count as pre-existing (1) and are not removed.
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
    # 2) Decide the family: images/<OS key>/Dockerfile takes precedence if present (no detection). Otherwise pull the base image
    #    and detect from /etc/os-release. dry-run skips detection.
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
    # 3) Image tag (Dockerfile directory actually used + family + base image), whether the image exists, and the build
    for os in "${OS_KEYS[@]}"; do
        [ -n "${need[$os]+x}" ] || continue
        if [ "${IMAGE_FAMILY[$os]}" = unknown ]; then
            IMAGE_DIR[$os]=${IMAGE_DIR[$os]:-$SCRIPT_DIR/images/rpm}
            IMAGE_TAG[$os]="$IMAGE_REPO:$os-unknown"
            IMAGE_PREEXIST[$os]=1     # not built, so no image to remove
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

# ---------------- running combinations ----------------
item_applicable() {   # item_applicable <pkg> <item>
    [ "$2" != uninstall ] || [ "$(pkg_kind_of "$1")" = rpm ]
}

# Fake install_test.sh output for --dry-run
#   INSTALL_TEST_DRY_FAIL="os__pkg[:item],..." makes that combination (item) fail
#   INSTALL_TEST_DRY_HANG="os__pkg,..."        makes that combination hang to cause a timeout
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

# Write result.txt: META lines + one ITEM line per item
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

# When a timeout kills only the docker exec client, install_test.sh and its child processes (rpm, setup, cubrid, csql, etc.) keep running
# in the container, so the kept container keeps changing (going on to the stop, delete and remove steps). Before keeping it, stop install_test.sh
# and its children with SIGKILL. CUBRID daemons (cub_master, cub_server, cub_broker, cub_cas*, cub_manager, etc.) and their children are kept for investigation.
# Utilities such as cub_admin and cub_commdb are work the test started, so they are stopped.
# The script below runs inside the container with sh -s (passed on stdin, so install_test.sh does not appear in its command line).
# Exit code: 0 = stopped (nothing left), 1 = nothing to stop, 3 = no ps, 4 = some left after stopping
container_stop_script() {
    cat <<'EOF'
command -v ps > /dev/null 2>&1 || exit 3
# Targets: processes with /test_script/install_test.sh in the command line and their children. Excludes CUBRID daemons and their children, and zombies.
targets() {
    { ps -e -o pid= -o args= | awk '$0 ~ /\/test_script\/install_test\.sh/ { print "R", $1 }'
      ps -e -o pid= -o ppid= -o stat= -o comm= | awk '{ print "P", $1, $2, $3, $4 }'; } 2>/dev/null |
    awk -v keep='^cub_(master|server|broker|cas|gateway|proxy|manager|auto|js|javasp|pl|cmhttpd|cmserver)' '
         $1 == "R" { root[$2] = 1; next }
         { par[$2] = $3; st[$2] = $4; cm[$2] = $5 }
         END {
             for (p in root) if ((p in cm) && cm[p] !~ keep) sel[p] = 1
             do {
                 added = 0
                 for (p in par) if (!(p in sel) && (par[p] in sel) && cm[p] !~ keep) { sel[p] = 1; added = 1 }
             } while (added)
             for (p in sel) if (st[p] !~ /^Z/) print p
         }'
}
pids=$(targets)
[ -n "$pids" ] || exit 1
for round in 1 2 3; do
    pids=$(targets)
    [ -n "$pids" ] || break
    # Freeze with SIGSTOP first so no new children appear, collect again, then SIGKILL
    kill -STOP $pids 2> /dev/null
    pids=$(targets)
    [ -n "$pids" ] || break
    kill -STOP $pids 2> /dev/null
    ps -o pid= -o args= -p "$(echo $pids | tr ' ' ',')" 2> /dev/null | sed 's/^ */stopped /'
    kill -KILL $pids 2> /dev/null
    sleep 1
done
left=$(targets)
if [ -n "$left" ]; then
    ps -o pid= -o stat= -o args= -p "$(echo $left | tr ' ' ',')" 2> /dev/null | sed 's/^ */left /'
    exit 4
fi
exit 0
EOF
}

stop_test_script() {   # stop_test_script <container> <combination directory>
    local cname=$1 cdir=$2 rc out n msg note
    out=$(timeout -k 5 30 docker exec -i -u root "$cname" sh -s 2>&1 <<< "$(container_stop_script)")
    rc=$?
    [ -n "$out" ] && printf '%s\n' "$out" >> "$cdir/runner.log"
    n=$(printf '%s\n' "$out" | grep -c '^stopped ')
    case $rc in
        0) msg="제한 시간 초과로 컨테이너를 남기기 전에 컨테이너 안 install_test.sh 와 하위 프로세스 ${n}개를 멈춤(SIGKILL, 목록은 runner.log). 남은 것 없음, CUBRID 데몬은 그대로 둠"
           note="timeout 후 남긴 컨테이너 안 테스트 프로세스 ${n}개를 멈춤(CUBRID 데몬은 그대로)" ;;
        1) msg="제한 시간 초과 후 컨테이너 안에 멈출 install_test.sh 계열 프로세스가 없었음(CUBRID 데몬은 그대로 둠)"
           note="timeout 후 남긴 컨테이너 안에 멈출 테스트 프로세스 없음" ;;
        3) msg="컨테이너 안에 ps 가 없어 install_test.sh 와 하위 프로세스를 멈추지 못함: 계속 돌 수 있음"
           note="ps 없음, 테스트 프로세스가 계속 돌 수 있음" ;;
        4) msg="컨테이너 안 install_test.sh 와 하위 프로세스를 멈췄지만 일부가 남음(runner.log 의 left 줄): 계속 돌 수 있음"
           note="테스트 프로세스 일부가 멈추지 않음(runner.log), 계속 돌 수 있음" ;;
        *) msg="컨테이너 안 install_test.sh 중지 결과를 확인하지 못함(종료 코드 $rc): 테스트 프로세스가 계속 돌 수 있음"
           note="테스트 프로세스 중지 확인 불가(종료 코드 $rc), 계속 돌 수 있음" ;;
    esac
    echo "$msg" >> "$cdir/runner.log"
    say "  $os / $pkg: $msg"
    # Add it to the report Note (appended after META|note= in result.txt)
    awk -v add="$note" \
        'BEGIN{FS=OFS="|"} $1=="META" && $2 ~ /^note=/ { v=substr($2,6); $2="note=" (v=="" ? add : v "; " add) } { print }' \
        "$cdir/result.txt" > "$cdir/result.tmp" && mv "$cdir/result.tmp" "$cdir/result.txt"
}

progress() { printf '[%2d/%d]' "$1" "${#COMBOS[@]}"; }

run_combo() {   # background job
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
        # When docker run -d failed: remove it and count it as created/removed only if the container actually exists
        # (recent docker returns success for rm -f of a missing container, so that success alone is not counted)
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

# ---------------- report ----------------
combine() {   # combine several item states into one: fail > timeout > pass > NA
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

# Read result.txt -> M[], S[], R[] (local associative arrays of the caller)
read_result() {
    local file=$1 kind a b c
    while IFS='|' read -r kind a b c; do
        case "$kind" in
            META) M[${a%%=*}]=${a#*=} ;;
            ITEM) S[$a]=$b; R[$a]=$c ;;
        esac
    done < "$file"
}

# Note: only the root reasons (items with the same reason are grouped); prerequisite failures are summarized as a count
make_note() {
    local item reason i found pre=0
    local reasons=() labels=() notes=()
    [ "${M[status]}" = timeout ] && notes+=("timeout(${TIMEOUT_SEC}s)")
    for item in "${ITEMS[@]}"; do
        reason=${R[$item]}
        # The install pass reason is the cubrid_rel version (+ notes) -> drop the version from the Note
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
# Escape for XML text/attributes: remove bytes that are invalid UTF-8 and control characters not allowed in XML 1.0 (except tab, LF, CR),
# and escape & < > " '.
xml_escape() {
    local s=$1
    if command -v iconv > /dev/null 2>&1; then
        s=$(printf '%s' "$s" | iconv -c -f UTF-8 -t UTF-8 2> /dev/null | tr -d '\000-\010\013\014\016-\037')
    else
        s=$(printf '%s' "$s" | tr -d '\000-\010\013\014\016-\037')
    fi
    printf '%s' "$s" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g' -e "s/'/\&apos;/g"
}

# Attribute value: replace newline, tab and CR with spaces, then escape
xml_attr() {
    xml_escape "$(printf '%s' "$1" | tr '\n\r\t' '   ')"
}

# result.txt items for the 10 result columns (Install ~ SQL Statement) of report.csv (if several, the reason of the first fail/timeout item is used)
JUNIT_COL_ITEMS=("install" "bash_profile" "uninstall" "service_start stop" "server_start stop"
    "broker" "manager" "createdb" "deletedb" "sql")
JU_TESTS=0; JU_FAILS=0; JU_SKIPS=0

# Write one testsuite to stdout. Reads M[], S[], R[] of the caller.
# Usage: junit_suite <OS key> <package key> <combination directory> <Note> <10 result column states of report.csv>
junit_suite() {
    local os=$1 pkg=$2 cdir=$3 note=$4
    shift 4
    local sts=("$@") cls="${os}.${pkg//./_}" i st name it its reason body log host dur start nfail=0 nskip=0 ntests run_st=""
    for st in "${sts[@]}"; do
        case "$st" in
            pass) ;;
            NA) nskip=$((nskip + 1)) ;;
            *) nfail=$((nfail + 1)) ;;
        esac
    done
    ntests=${#sts[@]}
    # If every result column is pass/NA but the combination is fail/timeout (e.g. install_test.sh exited non-zero after reporting all items,
    # or the time limit hit after the last item), add one more testcase Run so the failure shows up in report.xml too.
    if [ "$nfail" -eq 0 ] && [ "${M[status]:-pass}" != pass ]; then
        run_st=${M[status]}
        ntests=$((ntests + 1)); nfail=1
    fi
    JU_TESTS=$((JU_TESTS + ntests)); JU_FAILS=$((JU_FAILS + nfail)); JU_SKIPS=$((JU_SKIPS + nskip))
    dur=${M[duration]:-0}; [[ $dur =~ ^[0-9]+$ ]] || dur=0
    start=${M[start]:-$RUN_START}; [[ $start =~ ^[0-9]+$ ]] || start=$RUN_START
    # A combination without a container (not run) gets not-run instead of a host name (so the host name does not leak when the report is shared)
    host=${M[container]:-}; [ -n "$host" ] || host=not-run
    log="${cdir#"$SCRIPT_DIR"/}/console.log"
    printf '  <testsuite name="%s" tests="%d" failures="%d" errors="0" skipped="%d" time="%s" timestamp="%s" hostname="%s">\n' \
        "$(xml_attr "$os / $pkg")" "$ntests" "$nfail" "$nskip" "$dur" \
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
    if [ -n "$run_st" ]; then
        reason=${M[note]:-}
        [ -n "$reason" ] || reason=$note
        [ -n "$reason" ] || reason="조합 결과 $run_st"
        body="조합 결과: $run_st"$'\n'"사유: $reason"$'\n'"로그: $log"
        if [ "$run_st" = timeout ]; then
            printf '    <testcase classname="%s" name="Run">\n      <failure type="timeout" message="%s">%s</failure>\n    </testcase>\n' \
                "$(xml_attr "$cls")" "$(xml_attr "timeout(${TIMEOUT_SEC}s)")" "$(xml_escape "$body")"
        else
            printf '    <testcase classname="%s" name="Run">\n      <failure type="fail" message="%s">%s</failure>\n    </testcase>\n' \
                "$(xml_attr "$cls")" "$(xml_attr "$reason")" "$(xml_escape "$body")"
        fi
    fi
    printf '  </testsuite>\n'
}

# Write the whole report.xml: junit_write <output file> <body (testsuites) file> <build version> <total seconds>
junit_write() {
    local out=$1 body=$2 ver=$3 elapsed=$4
    {
        printf '<?xml version="1.0" encoding="UTF-8"?>\n'
        # testsuites gets no skipped or timestamp attributes (not in junit-10.xsd used by Jenkins xUnit)
        printf '<testsuites name="%s" tests="%d" failures="%d" errors="0" time="%s">\n' \
            "$(xml_attr "CUBRID install test - $ver")" "$JU_TESTS" "$JU_FAILS" "$elapsed"
        cat "$body"
        printf '</testsuites>\n'
    } > "$out"
}

# Record a report file write failure (each path once)
report_save_fail() {
    local f
    for f in "${REPORT_SAVE_ERR[@]}"; do [ "$f" = "$1" ] && return 0; done
    REPORT_SAVE_ERR+=("$1")
}

generate_reports() {
    local run_dir="$RESULT_ROOT/runs/$RUN_ID" csv md c os pkg cdir row note f run_ok
    local total=0 npass=0 nfail=0 ntimeout=0 failed_combos=() xml xml_body="$RUN_TMP/xml_body"
    JU_TESTS=0; JU_FAILS=0; JU_SKIPS=0
    REPORT_SAVE_ERR=()
    mkdir -p "$run_dir" || report_save_fail "$run_dir"
    xml="$run_dir/report.xml"
    : > "$xml_body"
    csv="$run_dir/report.csv"
    md="$run_dir/report.md"
    local md_table="$RUN_TMP/md_table" md_fail="$RUN_TMP/md_fail"
    : > "$md_table"; : > "$md_fail"

    printf '\xEF\xBB\xBF' > "$csv" || report_save_fail "$csv"   # UTF-8 BOM so Korean text is not garbled in Excel
    csv_row "${REPORT_HEADER[@]}" >> "$csv" || report_save_fail "$csv"
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
        csv_row "${row[@]}" >> "$csv" || report_save_fail "$csv"
        md_row "${row[@]}" "${M[osinfo]}" >> "$md_table"
        junit_suite "$os" "$pkg" "$cdir" "$note" "${row[@]:2:10}" >> "$xml_body" || report_save_fail "$xml"

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
    } > "$md" || report_save_fail "$md"

    junit_write "$xml" "$xml_body" "$BUILD_VER" "$(( $(date +%s) - RUN_START ))" || report_save_fail "$xml"
    # Replace the top-level reports. If this run's reports were not fully written or a copy failed, remove the earlier run's reports so CI does not read them.
    run_ok=0; [ ${#REPORT_SAVE_ERR[@]} -eq 0 ] && run_ok=1
    for f in report.csv report.xml report.md; do
        [ "$run_ok" -eq 1 ] && cp -f "$run_dir/$f" "$RESULT_ROOT/$f" && continue
        [ "$run_ok" -eq 1 ] && report_save_fail "$RESULT_ROOT/$f"
        rm -f "$RESULT_ROOT/$f" 2>/dev/null
    done
    SUMMARY="전체 $total, pass $npass, fail $nfail (timeout $ntimeout)"
    if [ ${#REPORT_SAVE_ERR[@]} -gt 0 ]; then
        say "오류: 리포트 저장 실패: ${REPORT_SAVE_ERR[*]}"
        SUMMARY+=", 리포트 저장 실패"
        return 1
    fi
    [ "$nfail" -eq 0 ]
}

# ---------------- preventing concurrent runs ----------------
# Take the host-wide lock file with flock -n. If it is already held, do nothing to docker and exit with code 2.
# The lock fd (9) is held until the runner exits. Background jobs (family detection, image builds, combinations) all inherit this fd,
# so even if the runner itself is killed (SIGKILL, etc.), the lock stays until the remaining jobs finish (new runners are refused meanwhile).
# Child processes of the jobs (timeout, docker client) also inherit the fd, so it stays until they finish too (they may not show in pgrep; check with fuser -v).
acquire_lock() {
    command -v flock > /dev/null 2>&1 || die "flock 명령이 필요합니다 (util-linux)"
    if [ ! -e "$LOCK_FILE" ]; then
        ( umask 000; : >> "$LOCK_FILE" ) 2>/dev/null
        chmod 666 "$LOCK_FILE" 2>/dev/null
    fi
    # Open read-only (no O_CREAT) and flock: avoids being refused when opening with O_CREAT a /tmp file created by another account
    # on hosts with fs.protected_regular=1 (the file is created above only when missing, with mode 666).
    if ! exec 9< "$LOCK_FILE"; then
        die "잠금 파일을 열 수 없습니다: $LOCK_FILE (다른 계정이 만든 파일이면 권한 확인)"
    fi
    if ! flock -n 9; then
        die "다른 install test가 실행 중입니다 (잠금 파일 $LOCK_FILE). 끝난 뒤 다시 실행하세요. 러너가 강제 종료됐다면 남은 작업과 그 하위 프로세스가 끝날 때까지 잠금이 유지됩니다(확인: fuser -v $LOCK_FILE 또는 pgrep -af run_install_test.sh)"
    fi
    CLEANUP_ARMED=1     # from here on, the cleanup step queries docker
    touch -c "$LOCK_FILE" 2>/dev/null || true   # update the modification time so /tmp cleaners do not remove it as an old file
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
        CLEANUP_ARMED=1     # dry-run takes no lock but still prints the "(dry-run) 정리 생략" line
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
