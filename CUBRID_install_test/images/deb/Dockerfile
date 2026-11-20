# deb 계열 공용 이미지 (debian, ubuntu 등) - CUBRID install test
# BASE_IMAGE(이미지:태그)는 run_install_test.sh가 matrix.conf 값으로 전달한다.
# OS별로 다르게 해야 하면 images/<OS 키>/Dockerfile 을 따로 두면 그것이 우선 쓰인다.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive
# rpm: rpm 패키지 직접 설치용(ubuntu는 universe 저장소), openssl: /etc/ssl/openssl.cnf 제공, procps: ps
# libncurses5-dev: 기존 수동 절차의 debian 사전 준비. 실제 설치 후보가 있을 때만 설치한다
# (apt-cache show 는 ubuntu noble 같은 가상 패키지에도 성공해 libncurses-dev 등이 딸려 설치되므로 쓰지 않는다).
# 후보가 없거나 (none)이면 건너뛰고 빌드 로그에 남긴다. debian 12 는 설치, ubuntu 24.04 는 미설치.
RUN apt-get update \
 && apt-get install -y rpm curl openssl ca-certificates procps \
 && cand=$(LC_ALL=C apt-cache policy libncurses5-dev 2>/dev/null | awk '/Candidate:/ { print $2 }') \
 && if [ -n "$cand" ] && [ "$cand" != "(none)" ]; then \
        echo "libncurses5-dev 설치 후보: $cand"; \
        apt-get install -y libncurses5-dev; \
    else \
        echo "libncurses5-dev 설치 후보 없음(가상 패키지 등), 건너뜀"; \
    fi \
 && rm -rf /var/lib/apt/lists/*

# CMS 접속용 TLS 설정
COPY add_openssl.sh /usr/local/share/cubrid-install-test/add_openssl.sh
RUN bash /usr/local/share/cubrid-install-test/add_openssl.sh

RUN for c in bash curl diff ps su tar gzip sed awk rpm; do \
      command -v "$c" > /dev/null || { echo "필수 명령 없음: $c" >&2; exit 1; }; \
    done

CMD ["sleep", "infinity"]
