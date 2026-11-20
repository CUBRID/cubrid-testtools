# CUBRID install test 자동화 (CUBRIDQA-1719)

`run_install_test.sh` 하나로 OS x 패키지 조합마다 새 컨테이너에서 설치, 검증, 제거를 하고
결과표(`report.csv`, `report.md`, CI용 `report.xml`)를 만든다. 중간에 입력할 것은 없다.

목차: 빠른 시작 · 준비물 · 옵션 · 진행 순서 · 대상 (matrix.conf) · 체크리스트와 pass 조건 · 패키지별 설치/제거 ·
결과물 · 문제 해결 · 정리 · 네트워크 · 동시 실행 방지 · 제한 시간 · 설정값 · 동작 확인용 · 파일 · 구현 참고

## 빠른 시작

```bash
cd cubrid-testtools/CUBRID_install_test

# 빌드 URL (패키지 파일 URL 또는 drop 디렉터리 URL). 필요한 패키지를 test_build/ 로 내려받는다
./run_install_test.sh http://<서버>/REPO_ROOT/store_01/11.4.0.1768-87b1b31/drop/CUBRID-11.4.0.1768-87b1b31-Linux.x86_64.sh

# debug 패키지가 없는 빌드: release 패키지 3종만
./run_install_test.sh <빌드 URL> --pkg sh,tar.gz,rpm

# 패키지가 이미 있는 디렉터리
./run_install_test.sh test_build

# 병렬 수(1~4, 기본 4), 일부 조합만
./run_install_test.sh <빌드 URL> --jobs 2
./run_install_test.sh <빌드 URL> --os ubuntu --pkg rpm

# 실패한 조합의 컨테이너를 남겨서 들어가 보기
./run_install_test.sh <빌드 URL> --keep-failed
docker exec -it <남긴 컨테이너 이름> bash
```

- `--pkg` 를 주지 않으면 debug 2종을 포함한 5종을 모두 실행한다. debug 패키지가 없으면 debug 14개 조합이
  `패키지 파일 없음` 으로 fail이 되므로 release만 볼 때는 `--pkg sh,tar.gz,rpm` 을 준다.
- `--keep-failed` 로 남긴 컨테이너(이름은 report.md "실패 조합 상세")와 그 이미지, 네트워크는 조사 후 "정리"의 수동 정리 명령으로 지운다.
- 끝나면(정상, 실패, Ctrl-C 모두) 이번 실행이 만든 컨테이너, 이미지, 네트워크를 지운다.

종료 코드

| 코드 | 경우 |
|---|---|
| 0 | 모든 조합 pass (`--images-only`: 모든 이미지 준비 성공, `--check-network`: 네트워크 준비 성공) |
| 1 | fail/timeout 조합이 있음, 리포트를 저장하지 못함(`오류: 리포트 저장 실패`), 정리 후 실행 전부터 있던 이미지가 없어짐(`오류: 기존 이미지 삭제됨`), `--images-only` 에서 이미지 준비 실패 |
| 2 | 실행 전 오류(`오류: ...`): 옵션/matrix.conf 오류, 선택된 조합 없음, docker 접근 불가, 다른 install test 실행 중, 네트워크 충돌, 패키지 준비 오류(패키지 없음, 한 종류에 버전 여러 개, 빌드 버전 불명 등) |
| 130 | Ctrl-C(INT), TERM, HUP 으로 중단. 정리는 하고 끝난다 |

## 준비물

- x86_64 호스트, docker(현재 사용자가 docker 그룹)
- bash 4.4 이상, curl, coreutils, GNU findutils, procps, util-linux `flock`, iproute2 `ip`(없으면 네트워크 겹침을 docker 네트워크끼리만 확인)
- 디스크: 패키지 5종 약 1.5GB, 이미지 7개 약 2.3GB, 실행 중 컨테이너당 1~2GB
- 외부 네트워크(이미지 빌드 중 yum/apt). 빌드 URL을 쓰면 그 서버에 접속할 수 있어야 한다.
- 러너 디렉터리와 패키지 디렉터리 경로에 `:` 이 없어야 한다(docker `-v` 구분자). 공백은 괜찮다.

소요 시간(2026-11-16, 21개 조합, `--jobs 4`): 약 19분(이미지 준비 8분, 조합 실행 10.5분, 정리 20초).
`--keep-images` 로 이미지를 남겨 두면 다음 실행은 이미지 받기와 빌드를 건너뛴다.

## 옵션

| 옵션 | 설명 |
|---|---|
| `--jobs N`, `-j N` | 동시에 실행할 조합 수. 1~4, 기본 4 |
| `--os LIST` | 일부 OS만. 콤마 구분. OS 키(`rocky9`) 또는 키/이미지 이름의 앞부분(`ubuntu`, `rocky`, `redhat/ubi9`) |
| `--pkg LIST` | 일부 패키지만. 콤마 구분. `sh`, `tar.gz`, `rpm`, `debug.sh`, `debug.tar.gz` |
| `--version VER` | 디렉터리에 한 종류의 패키지가 여러 버전일 때 쓸 버전 (예: `11.4.0.1768-87b1b31`) |
| `--timeout SEC` | 조합당 제한 시간(초). 기본 1200 |
| `--keep-failed` | 실패(timeout 포함)한 조합의 컨테이너와 그 이미지를 남김 |
| `--keep-images` | 끝난 뒤 이미지를 지우지 않음(실패한 빌드의 중간 이미지는 지움) |
| `--rebuild-images` | 이미지가 있어도 다시 빌드. `--keep-images` 없이 쓰면 끝날 때 삭제 |
| `--images-only` | 이미지 빌드만 하고 종료. 빌드 URL 불필요. `--keep-images` 포함 |
| `--check-network` | 테스트 네트워크만 준비/확인하고 종료 |
| `--dry-run` | docker 없이 가짜 결과로 대기열과 리포트 동작 확인 ("동작 확인용" 참고) |
| `--matrix FILE` | `matrix.conf` 대신 쓸 파일 |
| `--results-dir DIR` | 결과 상위 디렉터리(기본 `./results`) |

값은 띄어서 준다(`--jobs`, `--timeout`, `--os`, `--pkg` 는 `--옵션=값` 도 됨). 값이 `-` 로 시작하거나 `--os`/`--pkg` 에 빈 항목이 있으면 오류(종료 코드 2).

## 진행 순서

1. **네트워크 준비**: 전용 docker 네트워크 `install_test`(172.200.255.0/24)를 준비한다("네트워크" 참고).
2. **패키지 준비**: URL이면 이번에 실행할 조합에 필요한 패키지만 `test_build/` 로 받는다(같은 크기의 파일이 있으면 건너뜀). 디렉터리면 그대로 쓴다.
   - 패키지 파일명은 `CUBRID-<빌드 버전><접미사>` 다. 빌드 버전은 파일명이나 URL 경로에서 정하고, 디렉터리에 한 종류의 버전이 여러 개면 `--version` 으로 고른다.
   - 종류마다 버전이 달라도 된다(예: 11.5 .sh + 11.4.7 .rpm). 행마다 Test build 열에 그 버전을 적고 결과 디렉터리 이름은 `<버전1>+<버전2>` 다.
   - 받은 패키지는 지우지 않는다. 쌓이면 디렉터리 실행 때 "여러 버전" 오류가 나므로 `--version` 을 주거나 직접 지운다.
   - URL의 `사용자:암호@` 는 화면, run.log, report.md에 `***@` 로 가려 적는다.
   - 파일이 없거나 받지 못한 패키지의 조합은 실행하지 않고 fail로 기록한다.
3. **이미지 준비**: OS마다 베이스 이미지를 받고, 임시 컨테이너(`--network none`)로 `/etc/os-release` 를 읽어 계열을 정한 뒤
   계열별 공용 Dockerfile(`images/rpm`, `images/deb`)로 `cubrid-install-test:<OS 키>-<해시>` 이미지를 만든다.
   `images/<OS 키>/Dockerfile` 이 있으면 판별 없이 그것을 쓴다. 같은 태그의 이미지가 있으면 빌드를 건너뛴다.
   - 계열: debian, ubuntu → deb / rhel, fedora, centos, rocky, ol, almalinux → rpm / 그 밖 → 그 OS의 조합만 fail, 다른 OS는 계속.
4. **조합 실행**: 대기열에서 최대 N개씩, 조합마다 새 컨테이너(`cit-<실행 ID>-<OS 키>__<패키지 키>`)를 `install_test` 네트워크에 만든다.
   `test_build`, `test_script` 는 읽기 전용으로 마운트하고 호스트 포트는 열지 않는다. `docker exec` 로 `test_script/install_test.sh` 를 실행하고 결과를 복사한 뒤 컨테이너를 지운다.
5. **리포트 생성**: `results/<빌드 버전>/report.csv`, `report.md`, `report.xml`
6. **정리**: 이번 실행이 만든 것을 지우고 결과 한 줄을 run.log와 report.md 끝에 남긴다("정리" 참고).

Ctrl-C, TERM, HUP 으로 중단하면 실행 중인 조합을 멈추고 같은 정리를 한다. 정리가 시작되면 신호를 무시해 끝까지 간다.
리포트 생성 전에 중단하면 리포트를 만들지 않고, 최상위 리포트는 이전 실행 것이 남는다(어느 실행인지는 report.md 첫머리의 실행 ID로 확인).
`| tee` 로 출력을 받다가 Ctrl-C 하면 출력이 끊기고 종료 코드가 141이 되므로 tee 없이 실행한다(진행 기록은 run.log에 남는다).

## 대상 (matrix.conf)

| OS 키 | 베이스 이미지 | 계열 |
|---|---|---|
| rocky8 | `rockylinux:8.9` | rpm |
| rocky9 | `rockylinux:9.3` | rpm |
| ubuntu24 | `ubuntu:24.04` | deb |
| debian12 | `debian:12.10` | deb |
| fedora42 | `fedora:42` | rpm |
| oracle9 | `oraclelinux:9` (9.x 마이너 태그 없음) | rpm |
| ubi9 | `redhat/ubi9:9.5` | rpm |

- 패키지 5종(`.sh`, `.tar.gz`, `.rpm`, `-debug.sh`, `-debug.tar.gz`, debug rpm 없음) → 35개 조합.
- 제외: 32bit(미지원), 컨테이너 이미지가 없는 배포판(ProLinux 등), Windows(별도 이슈), CentOS(EOL).
- OS 마이너 버전은 고정하지 않는다. 이미지 빌드 중 패키지가 최신 마이너로 올라가며, 실제 OS와 glibc/libc6 버전은 report.md "실제 OS" 열에 나온다.

### matrix.conf 형식

```
pkg  <패키지 키>  <파일명 접미사>               # 예: pkg  rpm  -Linux.x86_64.rpm
os   <OS 키>      <베이스 이미지:태그>  [패키지 키 목록]
```

- `#` 뒤는 주석, 빈 줄 무시, 칸은 공백 구분(CRLF 파일도 읽음).
- 설치 방법은 접미사의 확장자(`.sh`, `.tar.gz`, `.rpm`)로 정한다. 파일명에 `-debug.` 가 있으면 debug 빌드인지 확인한다.
- `os` 줄 셋째 칸(콤마 구분)을 쓰면 그 OS에서는 그 패키지만 실행한다.
- 키는 영문/숫자/`._-` 만 쓰고 첫 글자는 영문/숫자, `__` 는 넣을 수 없다. 형식 오류는 `matrix.conf:<줄 번호>: ...` 로 멈춘다(종료 코드 2).

### OS 추가/변경/제거

`os` 줄 하나만 고친다. 러너, 테스트 스크립트, Dockerfile은 고치지 않는다.

```
os   rocky10   rockylinux/rockylinux:10      # 추가
os   ubuntu24  ubuntu:24.04  sh,tar.gz        # 이 OS는 sh, tar.gz만
os   ubuntu26  ubuntu:26.04                   # 버전 변경: 태그와 키를 같이 바꾼다(키는 결과 디렉터리 이름에 쓰임)
```

rpm/deb 계열이 아닌 OS(alpine, suse 등)는 `images/<OS 키>/Dockerfile` 을 따로 만든다. 규칙:
`ARG BASE_IMAGE` + `FROM ${BASE_IMAGE}` 로 시작(러너가 matrix.conf의 이미지를 넘기고 그 이미지만 추적/정리),
`add_openssl.sh` 가 컨텍스트 루트에 복사되므로 실행, 필수 명령(bash, curl, diff, ps, su, tar, gzip, sed, awk, deb 계열이면 rpm) 확인,
마지막은 `CMD ["sleep", "infinity"]`. `--keep-failed` timeout 조합의 테스트 프로세스는 `ps`(procps)로 찾아 멈춘다.

```dockerfile
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
RUN zypper --non-interactive install diffutils procps util-linux tar gzip curl rpm   # OS에 맞게
COPY add_openssl.sh /usr/local/share/cubrid-install-test/add_openssl.sh
RUN bash /usr/local/share/cubrid-install-test/add_openssl.sh
RUN for c in bash curl diff ps su tar gzip sed awk; do command -v "$c" > /dev/null || { echo "필수 명령 없음: $c" >&2; exit 1; }; done
CMD ["sleep", "infinity"]
```

## 체크리스트와 pass 조건

조합마다 순서대로 수행하고, 한 항목이 실패해도 다음 항목을 계속한다.

| 순서 | 항목(키) | 수행 | pass 조건 | sheet 열 |
|---|---|---|---|---|
| 1 | 패키지 설치 (`install`) | 설치 명령 | 종료 코드 0, `cubrid_rel` 버전 == 파일명 버전, debug/release 일치, 설치 출력에 scriptlet 실패(`scriptlet failed`, `Error in PRE/POST(IN/UN) scriptlet`) 없음 | Install |
| 2 | 환경변수 (`bash_profile`) | 새 로그인 셸 `env -i ... bash -lc` | (1) `$CUBRID` 가 설치 경로, `cubrid` 가 PATH에 있음 (2) `~/.bash_profile` 에 `.cubrid.sh` 를 읽는 줄이 있음 (3) `.cubrid.sh` 만 읽은 환경(`bash --noprofile --norc`)에서 `$CUBRID` 가 설치 경로, `CUBRID_DATABASES` 가 비어 있지 않음, `LD_LIBRARY_PATH` 에 `$CUBRID/lib` 있음 | in .bash_profile |
| 3 | demodb 생성 (`createdb`) | databases.txt에 demodb가 없을 때만 `make_cubrid_demo.sh` | databases.txt에 demodb (생성했으면 종료 코드 0) | Createdb |
| 4 | 서비스 시작 (`service_start`) | `cubrid service start` | 종료 코드 0, `cubrid service status` 에 master 실행 중 | Service |
| 5 | DB 서버 시작 (`server_start`) | `cubrid server start demodb` | 종료 코드 0, `cubrid server status` 에 demodb | Server |
| 6 | 브로커 (`broker`) | `cubrid broker status -b`, CMS `getbrokerstatus` | broker1 존재, status == success | Broker |
| 7 | CMS 접속 (`manager`) | `https://localhost:8001/cm_api` login | 비어 있지 않은 token | Manager |
| 8 | query test (`sql`) | `csql -u dba demodb -i sql_test.sql` | 종료 코드 0, 시간 값을 지운 출력이 `sql_test.answer` 와 같음 | SQL Statement |
| 9 | 서비스 종료 (`stop`) | `cubrid server stop demodb`, `cubrid service stop` | 종료 코드 0, `cub_` 프로세스 0개(최대 30초 대기) | Service, Server |
| 10 | demodb 삭제 (`deletedb`) | `cubrid deletedb demodb` | 종료 코드 0, databases.txt에 demodb 없음 | Deletedb |
| 11 | 패키지 제거 (`uninstall`) | `rpm -ev <패키지>` | 종료 코드 0, `rpm -qa` 에 cubrid 없음, `$CUBRID/bin/cubrid` 와 `/etc/profile.d/cubrid.sh` 없음. .sh/.tar.gz는 NA | UnInstall |

판정 규칙
- 조합은 적용 항목이 모두 pass일 때 pass. 제한 시간을 넘기면 끝나지 않은 항목을 `timeout` 으로 적고 조합은 fail.
- Service 열 = 4번 and 9번, Server 열 = 5번 and 9번. 9번은 정리를 위해 항상 수행하고 자기 명령 결과로만 판정한다.
- 앞 항목 실패로 정말 수행할 수 없는 항목만 fail(`선행 실패(<항목>)`)로 둔다.
  - 1번이 fail이어도 `bin/cubrid`, `bin/csql` 이 실행 가능하면 2~10번을 수행한다. 아니면 2~10번 모두 `선행 실패(install)`.
  - 5 ← 3, 8 ← 5, 10 ← 3. 6·7번은 master(cub_master)가 떠 있는지만 본다(없으면 `선행 실패(master 미실행)`).
  - 11번은 패키지 DB에 cubrid 패키지가 없으면 `선행 실패(install)`.

세부 동작
- 2번 실패 사유: `.bash_profile` 등록이 없으면 `~/.bash_profile 에 .cubrid.sh 등록 없음`(컨테이너에 `/etc/profile.d/cubrid.sh` 가 있으면 `(/etc/profile.d 로만 설정됨)` 을 붙임), (3)이 틀리면 빠진 변수 이름.
- 3번: `.sh` 와 `setup.sh` 는 설치 중 demodb를 만든다. 있으면 그대로 쓰고 없을 때만 만든다. 어느 쪽인지 Note에 남는다.
- 4번 실패 사유에는 `cubrid service start` 출력의 `++ ...: fail` 구성 요소와 첫 오류 줄을 넣는다.
- 6·7번: CMS 로그인을 최대 20회(3초 간격) 재시도한다. curl은 `--tlsv1.2 -k`, debian/ubuntu는 `--tls-max 1.2` 추가.
- 8번: 원본 `test.log` 는 두고 사본(`test.log.cmp`)에서 `sed 's/[0-9]\.[0-9]*[0-9]//g'` 로 시간 값을 지운 뒤 비교한다.
- 9번: 남은 `cub_` 프로세스는 fail로 기록하고 강제 종료한다.
- 11번: 제거 출력에 오류성 줄이 있으면 판정은 그대로 두고 Note에 `제거 스크립트 출력에 오류: <첫 줄>` 을 남긴다.

## 패키지별 설치/제거

| 패키지 | 설치 | 실행 계정 | `$CUBRID` | 제거 |
|---|---|---|---|---|
| `.sh`, `-debug.sh` | `/root/CUBRID` 에서 `printf 'y\nn\ny\n' \| sh CUBRID*.sh` | root | `/root/CUBRID` | 없음(서비스 정리만) |
| `.tar.gz`, `-debug.tar.gz` | `/root` 에서 `tar -xzf` 후 `printf 'y\n' \| sh $CUBRID/share/scripts/setup.sh $CUBRID` (CBRD-24402) | root | `/root/CUBRID` | 없음(서비스 정리만) |
| `.rpm` (rpm 계열) | `rpm -ivh --nodeps`. 실패하면 `yum install -y`(없으면 dnf, 상한 300초) | cubrid | `/opt/cubrid` | `rpm -ev <패키지>` |
| `.rpm` (debian, ubuntu) | `rpm -ivh --nodeps --force-debian`. 대체 경로 없음, 실패하면 Install fail | cubrid | `/opt/cubrid` | `rpm -ev [--force-debian] <패키지>` |

- rpm은 설치 때 cubrid 계정이 생긴다. `/tmp` 를 1777로 연 뒤 테스트 스크립트를 `/opt/cubrid/test_script` 로 복사해 `su - cubrid` 로 2~10번을 수행한다. 1번과 11번은 root.
- rpm의 환경 파일은 `/opt/cubrid/.cubrid.sh` 다(root의 `~/.cubrid.sh` 아님).
- Install의 Note(판정은 그대로): yum으로 넘어간 경우, setup.sh 출력의 오류성 줄, `--nodeps` 로 넘어간 미충족 의존성(`미충족 의존성(--nodeps로 설치): a, b`, 패키지 이름만), 그 밖의 `warning:` 첫 줄(서명 키 경고는 로그에만).
- rpm 진단 파일(rpm/deb 계열 공통): `rpm_install.log`, `rpm_files.txt`, `rpm_conffiles.txt`, `rpm_verify.txt`, `rpm_after_remove.txt`, `conf_listing.txt`.
  rpm 계열은 `rpm_unmet_deps.txt`, deb 계열은 `dpkg_registered.txt` 도 남긴다. 판정에는 쓰지 않는다.

## 결과물

```
results/<빌드 버전>/
  report.csv, report.md, report.xml   # 마지막 실행 결과 (--dry-run, --images-only, --check-network 는 덮어쓰지 않음)
  runs/<실행 ID>/                     # 실행별 report.csv, report.md, report.xml, run.log (보존)
  image_build/<OS 키>.log             # 이미지 빌드 로그 (<OS 키>.detect.log: 베이스 이미지 받기/계열 판별)
  <OS 키>__<패키지 키>/
    console.log                       # install_test.sh 전체 출력
    result.txt, container_results.txt, runner.log
    test.log, test.log.cmp, sql_test.diff, csql.err
    env_check.txt, cms_login.txt, cms_getbrokerstatus.txt, service_start.log
    cubrid_log/, databases.txt        # 실패 항목이 있을 때
    yum_install.log, setup_sh.log, uninstall.log, rpm_*.txt ...   # 해당 단계가 있을 때
```

- 최상위 리포트는 마지막 실행 결과로 덮어쓰고, 조합 디렉터리는 이번에 실행한 조합만 교체한다.
  일부 조합만 다시 실행하면 최상위 report.csv에는 그 조합 행만 있으므로, 필요하면 `runs/<실행 ID>/` 의 리포트에서 행을 합친다.
- `--results-dir DIR` 이면 `DIR/` 아래, `--dry-run` 은 `results/dry-run/` 아래에 쓴다.

### report.csv

한 행이 조합 하나이고 열은 14개다. UTF-8 BOM을 붙인다(Excel 한글).

```
Test Build, Test OS, Install, in .bash_profile, UnInstall, Service, Server, Broker, Manager,
Createdb, Deletedb, SQL Statement, Test build, Note
```

- `Test Build` 는 패키지 파일 이름, `Test build` 는 빌드 버전, Test OS는 베이스 이미지 태그다.
- 값은 `pass` / `fail` / `NA` / `timeout`.
- Note: `<항목>: <사유>` 를 ` / ` 로 잇는다. fail 사유와 함께 pass 항목의 참고 사항(예: `Createdb: 설치 시 생성된 demodb 사용`)도 들어간다. 판정은 각 열의 값으로 본다.
- sheet에 붙여 넣을 때 owner 열(M)은 건너뛰고, Note에 report.md의 "실제 OS" 값을 같이 적는다.

### report.md

같은 표에 "실제 OS" 열(배포판, glibc/libc6 버전)을 더하고, 실패 조합의 항목 사유, 로그 경로, 남긴 컨테이너 이름, 정리 결과 줄을 붙인다.
첫머리에는 실행 ID, 실행 시간(`%z` 포함), 러너(`install_test_v3 (run_install_test.sh <sha256 12자>, test_script <해시 12자>)`), 호스트(커널, docker 서버 버전)가 있다.
test_script 해시는 파일 경로 순으로 정렬한 각 파일 sha256 목록의 sha256이다. run.log에는 패키지별 sha256이 남는다.

### report.xml

JUnit XML이다. Jenkins(JUnit 플러그인)와 GitLab(`artifacts:reports:junit`)에서 읽는다. 예: Jenkins `junit 'results/*/report.xml'`.

```
<testsuites name="CUBRID install test - <빌드 버전>" tests failures errors="0" time>
  <testsuite name="rocky8 / sh" tests="10" failures skipped time timestamp hostname>
    <properties> run_id, runner, host, test_build, test_os, actual_os, build_version, note </properties>
    <testcase classname="rocky8.sh" name="Install"/> ...   (판정 열 10개: Install ~ SQL Statement)
```

- testsuite는 조합, testcase는 판정 열 하나다. classname은 `<OS 키>.<패키지 키>`(점은 `_`, 예 `rocky8.tar_gz`).
- `pass` 는 빈 testcase, `fail` 은 `<failure type="fail" message="사유">`, `timeout` 은 `<failure type="timeout">`, `NA` 는 `<skipped message="NA"/>`.
- 판정 열이 모두 pass/NA인데 조합이 fail/timeout이면(예: 항목 결과를 다 낸 뒤 `install_test.sh` 종료 코드가 0이 아님) testcase `Run` 을 하나 더 넣어 failure로 적는다.
- `time` 은 초(testsuite, testsuites에만), `timestamp` 는 조합 시작 시각(ISO 8601), `hostname` 은 컨테이너 이름(실행하지 않은 조합은 `not-run`).
- `testsuites` 에는 `skipped`, `timestamp` 를 쓰지 않는다(junit-10.xsd). UTF-8, BOM 없음, XML에 쓸 수 없는 제어 문자는 지운다.

## 문제 해결

report.md의 Note와 "실패 조합 상세"에서 사유를 보고, `<OS 키>__<패키지 키>/console.log` 와 항목별 파일을 본다.
컨테이너 안을 봐야 하면 그 조합만 `--os <OS 키> --pkg <패키지 키> --keep-failed` 로 다시 실행한다.

| 메시지 | 뜻 | 조치 |
|---|---|---|
| `다른 install test가 실행 중입니다` (2) | 다른 러너, 또는 강제 종료된 러너의 남은 작업이 잠금을 잡고 있음 | `fuser -v /tmp/cubrid-install-test.lock` 으로 확인하고 끝난 뒤 실행 |
| `172.200.255.0/24 이 ... 겹쳐` / `기존 install_test 네트워크의 서브넷 ...` (2) | 서브넷이 다른 네트워크/라우트와 겹치거나 같은 이름의 다른 네트워크가 있음 | 대상 확인. 바꿔야 하면 러너 맨 위 `TEST_SUBNET`, `TEST_GATEWAY` |
| `패키지 파일 없음` / `다운로드 실패` | 그 패키지의 조합은 실행 안 함 | 파일명/URL 확인. 없는 패키지는 `--pkg` 로 뺀다 |
| `베이스 이미지를 받지 못함(...)` | 태그 오타, Docker Hub 익명 한도, 외부 접속 불가 | `image_build/<OS 키>.detect.log`. 재실행이 잦으면 `--keep-images` |
| `OS 계열 판별 불가` / `OS 계열 판별 컨테이너 실행 실패` | rpm/deb가 아닌 OS, 또는 이미지에 `sh` 없음 등 | `images/<OS 키>/Dockerfile` 작성, detect.log |
| `이미지 빌드 실패` / `... 시간 초과(N초, ...)` | yum/apt 실패, 필수 명령 없음, 이미지 준비 제한 시간 초과 | `image_build/<OS 키>.log` |
| `컨테이너 생성 실패(runner.log)` | `docker run` 실패 | 그 조합의 `runner.log` |
| `설치 실패(... yum install 실패/시간 초과 ...)` | `rpm -ivh --nodeps` 실패 후 yum도 실패(테스트 컨테이너는 외부 접속이 안 될 수 있음) | console.log의 rpm 출력, `yum_install.log` |
| `timeout(1200s)` | 조합 제한 시간 초과 | console.log 마지막 항목. `--timeout` 또는 `--keep-failed` |
| `선행 실패(<항목>)` | 앞 항목 실패로 수행 불가 | 앞 항목의 사유 |
| `오류: 기존 이미지 삭제됨` (1) / `경고: 기존 이미지 확인 불가` | 실행 전부터 있던 이미지가 없어짐 / 확인 실패 | run.log의 정리 기록, `docker images` |
| 다운로드나 컨테이너 단계에서 멈춤 | curl/docker 응답 없음(제한 시간 없음) | Ctrl-C(정리로 넘어감) |
| 정리 단계에서 멈춤 | docker 데몬 응답 없음(정리 중에는 Ctrl-C 무시) | 데몬 복구. 급하면 멈춘 docker 명령 PID에 `kill -TERM` |

## 정리

| 대상 | 기본 동작 | 예외 |
|---|---|---|
| 테스트 컨테이너 (`cubrid-install-test.run=<실행 ID>` label) | 삭제 | `--keep-failed`: 실패 조합의 것은 남김 |
| 러너가 빌드한 준비 이미지, 이번 실행이 받은 베이스 이미지 | 삭제 | `--keep-images`, `--keep-failed` 로 남긴 컨테이너가 쓰는 이미지는 남김 |
| 이번 실행이 만든 `install_test` 네트워크 | 붙은 컨테이너가 없으면 삭제 | 남긴 컨테이너가 있으면 남김 |
| 실행 전부터 있던 이미지, 네트워크, 다른 용도의 컨테이너 | 건드리지 않음 | |

- 실패하거나 끊긴 빌드의 임시 컨테이너와 중간 이미지도 빌드 로그에서 찾아 지운다(베이스 이미지 id와 태그 있는 이미지는 제외).
- 베이스 이미지를 받는 도중(계열 판별 단계)에 중단해도 그때까지 받은 베이스 이미지는 지운다. 다만 받기가 끊긴 이미지는
  docker가 태그 없이(`<none>`) 남길 수 있다. 같은 베이스 이미지를 받는 다음 실행이 태그를 붙이고 끝날 때 지운다.
- 끝에 `정리: 컨테이너 N개 생성, M개 삭제(...), 이미지 K개 삭제(...) / 네트워크 ... / 정리 후 남은 것: 없음` 한 줄을 남긴다.
  실행 전부터 있던 이미지가 없어졌으면 `오류: 기존 이미지 삭제됨` 을 적고 종료 코드 1.
- 기본 동작은 실행마다 베이스 이미지를 다시 받는다(Docker Hub 익명 한도 주의). 재실행이 잦거나 다른 사람이 같은 이미지를 쓰는 장비에서는 `--keep-images`.
- 남아 있는 베이스 이미지는 이동 태그(`oraclelinux:9`, `fedora:42` 등)가 갱신돼도 예전 내용 그대로 쓰인다. 최신으로 하려면 직접 `docker pull` 후 `--rebuild-images --keep-images`.
- 빌드는 legacy builder(`DOCKER_BUILDKIT=0`)로 한다. BuildKit 캐시는 이미지를 지워도 남고 골라 지우기 어렵기 때문이다(폐기 경고는 무시해도 됨).

수동 정리: 러너가 강제 종료(SIGKILL)되면 정리를 거치지 못한다. 진행 중이던 백그라운드 작업은 끝까지 돌고 그동안 잠금도 유지된다.
기다리는 것이 좋고, 급하면 실행 중인 다른 러너가 없는 것을 확인한 뒤 `fuser -k -TERM` 으로 멈춘다. 남은 것은 아래 명령으로 지운다.

```bash
fuser -v /tmp/cubrid-install-test.lock                            # 잠금을 가진 프로세스
fuser -k -TERM /tmp/cubrid-install-test.lock                      # 남은 작업을 멈출 때(다른 러너가 없을 때만)
docker ps -a --filter label=cubrid-install-test.run               # 남은 테스트 컨테이너
docker rm -f $(docker ps -aq --filter label=cubrid-install-test.run=<실행 ID>)
docker images cubrid-install-test                                 # 남은 준비 이미지
docker rmi $(docker images -q cubrid-install-test)                # 실행 중인 러너가 없을 때만
docker images -f dangling=true -f label=cubrid-install-test=1     # 다시 빌드로 태그를 잃은 이전 준비 이미지
docker network rm install_test                                    # 러너가 만든 것만(label cubrid-install-test.network)
docker rmi <이번 실행이 받은 베이스 이미지>                         # detect.log 첫머리에 '$ docker pull' 줄이 있는 것만
rm -rf /tmp/cubrid-install-test.XXXXXX                            # 강제 종료된 실행의 임시 디렉터리(run.log 있음)
```

## 네트워크

- 테스트 컨테이너는 전용 bridge 네트워크 `install_test`(172.200.255.0/24, 게이트웨이 .1, label `cubrid-install-test.network=1`)에만 붙는다.
  host 네트워크는 CUBRID 포트가 호스트·병렬 컨테이너와 겹치고, 기본 bridge는 다른 용도의 컨테이너와 섞이므로 쓰지 않는다.
- 없으면 만든다. 서브넷이 기존 docker 네트워크, 호스트 라우트/주소와 겹치면 멈춘다. 있으면 bridge이고 서브넷이 같을 때만 쓴다(기존 네트워크는 바꾸지 않음).
- 이미지 빌드는 `--network host` 로 한다(`ip_forward=0` 호스트에서 yum/apt가 저장소에 접속하기 위해).
- rpm 계열에서 `rpm -ivh --nodeps` 가 실패해 yum으로 넘어가면 테스트 컨테이너가 저장소에 접속해야 한다. 외부 접속이 안 되는 호스트에서는 이 경로가 실패해 Install fail이 된다. 러너는 호스트 설정을 바꾸지 않는다.

## 동시 실행 방지

- 한 장비에서는 러너가 한 번에 하나만 돈다. 러너는 docker를 쓰기 전에 `/tmp/cubrid-install-test.lock` 을 `flock -n` 으로 잡고, 다른 실행이 잡고 있으면 종료 코드 2로 끝난다. `--dry-run` 은 잠금을 쓰지 않는다.
- 백그라운드 작업이 잠금을 물려받으므로 러너가 강제 종료돼도 작업이 끝날 때까지 잠금이 유지된다.
- 잠금 파일은 지우지 않는다(남아 있는 것이 정상). 권한은 666이고, 러너는 잡을 때마다 파일 시각을 갱신한다(/tmp 자동 정리 대비).
- `INSTALL_TEST_LOCK_FILE` 로 경로를 바꿀 수 있지만 시험용이다(경로가 다르면 서로 막지 못함).

## 제한 시간

- 조합당 1200초(`--timeout`). 넘으면 `docker exec` 를 끊고 그때까지의 로그를 복사한 뒤 컨테이너를 지운다.
- 이미지 준비: 베이스 이미지 받기 900초, 계열 판별 120초, 빌드 1800초. 넘으면 그 OS의 조합만 fail이고 Note에 단계가 적힌다.
- `--keep-failed` 로 남기는 timeout 조합은 컨테이너 안의 `install_test.sh` 와 그 하위 프로세스(rpm, setup, `cub_admin` 등)를 SIGKILL로 멈춘다.
  CUBRID 데몬(cub_master, cub_server, cub_broker, cub_cas, cub_manager 등)은 그대로 둔다. 멈춘 프로세스 목록은 runner.log, 결과는 Note에 남는다.

## 설정값

| 위치 | 값 | 기본 |
|---|---|---|
| `matrix.conf` | 대상 OS, 패키지 | OS 7개, 패키지 5종 |
| `run_install_test.sh` 맨 위 | `DEFAULT_JOBS`, `MAX_JOBS` | 4, 4 |
| 〃 | `DEFAULT_TIMEOUT_SEC` | 1200 |
| 〃 | `PULL_TIMEOUT_SEC`, `DETECT_TIMEOUT_SEC`, `BUILD_TIMEOUT_SEC` | 900, 120, 1800 |
| 〃 | `TEST_NETWORK`, `TEST_SUBNET`, `TEST_GATEWAY` | `install_test`, 172.200.255.0/24, 172.200.255.1 |
| 〃 | `LOCK_FILE` (환경변수 `INSTALL_TEST_LOCK_FILE`) | `/tmp/cubrid-install-test.lock` |
| `test_script/lib/check.sh` 맨 위 | `CMS_WAIT_TRIES`(3초 간격), `STOP_WAIT_SEC` | 20, 30 |
| `test_script/lib/install.sh` 맨 위 | `YUM_TIMEOUT_SEC` | 300 |
| `test_script/sql_test.sql`, `sql_test.answer` | query test 내용과 정답 | |

## 동작 확인용

```bash
./run_install_test.sh <빌드 URL> --dry-run                     # docker 없이 대기열/리포트만 (URL이면 내려받지 않음)
INSTALL_TEST_DRY_FAIL="rocky9__sh:sql,ubuntu24__rpm" \
INSTALL_TEST_DRY_HANG="debian12__sh" \
./run_install_test.sh <빌드 URL> --dry-run --jobs 2 --timeout 5   # 가짜 실패/timeout
./run_install_test.sh --images-only                             # 이미지만 빌드
```

- `INSTALL_TEST_DRY_FAIL`: `<OS 키>__<패키지 키>` 는 그 조합 전체, `...:<항목 키>` 는 그 항목만 가짜 fail. `INSTALL_TEST_DRY_HANG`: 그 조합을 멈춰 timeout을 낸다. 둘 다 `--dry-run` 에서만 쓰인다.
- `--dry-run` 도 빌드 URL 또는 패키지가 있는 디렉터리가 필요하다(빈 `test_build` 는 종료 코드 2).

## 파일

```
README.md                    이 문서
run_install_test.sh          호스트 러너
matrix.conf                  OS x 패키지 목록
images/rpm/Dockerfile        rpm 계열 공용 준비 이미지
images/deb/Dockerfile        deb 계열 공용 준비 이미지
images/<OS 키>/Dockerfile    (선택) 특수한 OS용. 있으면 공용보다 우선
test_build/                  패키지 파일 (URL로 실행하면 생긴다. git에서 제외)
test_script/
  install_test.sh            컨테이너 안 진입점 (bash install_test.sh <패키지 파일>)
  lib/install.sh             패키지 종류별 설치/제거
  lib/check.sh               체크 항목별 함수
  add_openssl.sh             CMS 접속용 TLS 설정
  sql_test.sql, sql_test.answer
results/<빌드 버전>/         로그와 리포트
```

## 구현 참고

- TLS 설정(`add_openssl.sh`): 실제 쓰이는 openssl.cnf에 표시 줄로 감싼 블록을 넣어 `CipherString = DEFAULT@SECLEVEL=1` 로 낮춘다. 이미지 빌드 때와 `install_test.sh` 시작 때 실행하며, 다시 실행해도 블록이 중복되지 않는다.
- 데몬 출력 캡처 금지: `cubrid service start` 가 띄운 `cub_master` 가 stdout/stderr를 물려받으므로 이 출력을 `$(...)` 나 파이프로 받으면 멈춘다. 파일로 리다이렉트한 뒤 읽는다.
- 컨테이너 밖 실행 차단: `install_test.sh` 는 `INSTALL_TEST_IN_CONTAINER=<실행 ID>` 와 컨테이너 표시 파일(`/.dockerenv` 또는 `/run/.containerenv`)이 모두 있을 때만 진행한다(아니면 종료 코드 2).
- 시각대: 러너가 호스트의 UTC 오프셋으로 POSIX `TZ`(예: `KST-09:00`)를 넘겨 컨테이너 로그와 러너 로그의 시각을 맞춘다.
