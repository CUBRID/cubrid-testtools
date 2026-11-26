#!/bin/bash
#
# OpenSSL client settings for connecting to CMS (cub_manager, https://localhost:8001).
# Appends a block wrapped in markers to the end of the system openssl.cnf to lower the SSL default to SECLEVEL=1.
# It removes the old block and writes it again, so running it more than once gives the same result.
#
# Targets: debian, ubuntu, rocky 8/9, oraclelinux, rhel (ubi), fedora (others are tried by searching paths)
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

# 1) Remove the block added before
awk -v b="$MARK_BEGIN" -v e="$MARK_END" '
    $0 == b { skip = 1; next }
    $0 == e { skip = 0; next }
    !skip
' "$cnf" > "$tmp" || die "기존 블록 제거 실패"

# 2) Name of the init section the global openssl_conf points to (the last value before the first section)
init_sect=$(sed -n '/^[[:space:]]*\[/q; s/^[[:space:]]*openssl_conf[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p' "$tmp" | tail -n 1)

{
    if [ -z "$init_sect" ]; then
        init_sect=$INIT_DEFAULT
        printf '%s\nopenssl_conf = %s\n%s\n' "$MARK_BEGIN" "$init_sect" "$MARK_END"
    fi
    cat "$tmp"
    # Add a newline if the file does not end with one
    [ -n "$(tail -c 1 "$tmp")" ] && echo
    # Reopening the same section and redefining ssl_conf makes the last value apply
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

# Overwrite only the contents to keep the original file's permissions and owner
cat "$tmp.new" > "$cnf" || die "$cnf 쓰기 실패"

echo "적용 완료: [$init_sect] ssl_conf = $SSL_SECT (CipherString = $CIPHER_STRING)"
