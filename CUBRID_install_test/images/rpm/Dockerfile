# Shared image for the rpm family (rhel, fedora, centos, rocky, ol, almalinux, etc.) - CUBRID install test
# BASE_IMAGE (image:tag) is passed by run_install_test.sh from matrix.conf.
# If an OS needs something different, put images/<OS key>/Dockerfile; it takes precedence.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

# diff, ps, su, en_US.UTF-8 locale. curl only when missing (RHEL 9 family has curl-minimal, which conflicts with installing curl)
# Keep the yum metadata cache so that yum during the rpm install in a test does not download repository metadata again
RUN yum install -y diffutils procps-ng glibc-langpack-en util-linux tar gzip findutils \
 && { command -v curl > /dev/null || yum install -y curl; } \
 && echo 'LANG=en_US.UTF-8' > /etc/locale.conf
ENV LANG=en_US.UTF-8

# TLS settings for connecting to CMS
COPY add_openssl.sh /usr/local/share/cubrid-install-test/add_openssl.sh
RUN bash /usr/local/share/cubrid-install-test/add_openssl.sh

RUN for c in bash curl diff ps su tar gzip sed awk; do \
      command -v "$c" > /dev/null || { echo "필수 명령 없음: $c" >&2; exit 1; }; \
    done

CMD ["sleep", "infinity"]
