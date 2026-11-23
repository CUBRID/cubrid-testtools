# Shared image for the deb family (debian, ubuntu, etc.) - CUBRID install test
# BASE_IMAGE (image:tag) is passed by run_install_test.sh from matrix.conf.
# If an OS needs something different, put images/<OS key>/Dockerfile; it takes precedence.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive
# rpm: for installing the rpm package directly (universe repository on ubuntu), openssl: provides /etc/ssl/openssl.cnf, procps: ps
# libncurses5-dev: debian prerequisite from the earlier manual procedure. Installed only when there is a real install candidate
# (apt-cache show also succeeds for virtual packages such as on ubuntu noble and pulls in libncurses-dev etc., so it is not used).
# If there is no candidate or it is (none), skip it and note it in the build log. Installed on debian 12, not on ubuntu 24.04.
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

# TLS settings for connecting to CMS
COPY add_openssl.sh /usr/local/share/cubrid-install-test/add_openssl.sh
RUN bash /usr/local/share/cubrid-install-test/add_openssl.sh

RUN for c in bash curl diff ps su tar gzip sed awk rpm; do \
      command -v "$c" > /dev/null || { echo "필수 명령 없음: $c" >&2; exit 1; }; \
    done

CMD ["sleep", "infinity"]
