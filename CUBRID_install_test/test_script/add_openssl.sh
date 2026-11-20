#!/bin/bash
#
# CMS(cub_manager, https://localhost:8001) 접속용 OpenSSL 클라이언트 설정.
# 시스템 openssl.cnf 끝에 표시(marker)로 감싼 블록을 추가해 SSL 기본 설정을 SECLEVEL=1로 낮춘다.
# 기존 블록을 지우고 다시 쓰므로 여러 번 실행해도 결과가 같다.
#
# 대상: debian, ubuntu, rocky 8/9, oraclelinux, rhel(ubi), fedora (그 외는 경로 탐색으로 시도)
#
set -u

MARK_BEGIN='# >>> cubrid install_test add_openssl.sh >>>'
MARK_END='# <<< cubrid install_test add_openssl.sh <<<'
INIT_DEFAULT=install_test_openssl_init
SSL_SECT=install_test_ssl_sect
SSL_DEFAULT_SECT=install_test_ssl_default_sect
CIPHER_STRING='DEFAULT@SECLEVEL=1'

die() { echo "add_openssl.sh: $*" >&2; exit 1; }

os_id=$(. /etc/os-release 2>/dev/null; echo "${ID:-unknown}")
os_like=$(. /etc/os-release 2>/dev/null; echo "${ID_LIKE:-}")

case " $os_id $os_like " in
    *" debian "*|*" ubuntu "*)
        family=deb
        candidates="/etc/ssl/openssl.cnf /usr/lib/ssl/openssl.cnf" ;;
    *" rhel "*|*" fedora "*|*" centos "*|*" rocky "*|*" ol "*)
        family=rpm
        candidates="/etc/pki/tls/openssl.cnf /etc/ssl/openssl.cnf" ;;
    *)
        family=unknown
        candidates="/etc/ssl/openssl.cnf /etc/pki/tls/openssl.cnf /usr/lib/ssl/openssl.cnf /usr/local/ssl/openssl.cnf" ;;
esac
echo "OS: $os_id ($family 계열)"

cnf=""
for f in $candidates; do
    if [ -e "$f" ]; then
        cnf=$(readlink -f "$f")
        break
    fi
done
[ -n "$cnf" ] || die "openssl.cnf를 찾을 수 없음 (후보: $candidates). openssl 패키지 설치 필요"
echo "openssl.cnf: $cnf"

tmp=$(mktemp) || die "mktemp 실패"
trap 'rm -f "$tmp" "$tmp.new"' EXIT

# 1) 이전에 추가한 블록 제거
awk -v b="$MARK_BEGIN" -v e="$MARK_END" '
    $0 == b { skip = 1; next }
    $0 == e { skip = 0; next }
    !skip
' "$cnf" > "$tmp" || die "기존 블록 제거 실패"

# 2) 전역 openssl_conf 가 가리키는 초기화 섹션 이름 (첫 섹션 이전의 마지막 값)
init_sect=$(sed -n '/^[[:space:]]*\[/q; s/^[[:space:]]*openssl_conf[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p' "$tmp" | tail -n 1)

{
    if [ -z "$init_sect" ]; then
        init_sect=$INIT_DEFAULT
        printf '%s\nopenssl_conf = %s\n%s\n' "$MARK_BEGIN" "$init_sect" "$MARK_END"
    fi
    cat "$tmp"
    # 파일 끝에 개행이 없으면 보충
    [ -n "$(tail -c 1 "$tmp")" ] && echo
    # 같은 섹션을 다시 열어 ssl_conf 를 재정의하면 마지막 값이 적용된다
    cat <<EOF
$MARK_BEGIN
[$init_sect]
ssl_conf = $SSL_SECT

[$SSL_SECT]
system_default = $SSL_DEFAULT_SECT

[$SSL_DEFAULT_SECT]
CipherString = $CIPHER_STRING
$MARK_END
EOF
} > "$tmp.new" || die "새 설정 작성 실패"

# 원본 파일의 권한/소유자를 유지하도록 내용만 덮어씀
cat "$tmp.new" > "$cnf" || die "$cnf 쓰기 실패"

echo "적용 완료: [$init_sect] ssl_conf = $SSL_SECT (CipherString = $CIPHER_STRING)"
