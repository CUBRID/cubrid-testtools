# rpm 계열 공용 이미지 (rhel, fedora, centos, rocky, ol, almalinux 등) - CUBRID install test
# BASE_IMAGE(이미지:태그)는 run_install_test.sh가 matrix.conf 값으로 전달한다.
# OS별로 다르게 해야 하면 images/<OS 키>/Dockerfile 을 따로 두면 그것이 우선 쓰인다.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

# diff, ps, su, en_US.UTF-8 로캘. curl은 없을 때만 설치(RHEL 9 계열은 curl-minimal이 있어 curl 설치가 충돌함)
# yum 메타데이터 캐시는 지우지 않는다: 테스트 때 rpm 설치의 yum이 저장소 메타데이터를 다시 받지 않도록
RUN yum install -y diffutils procps-ng glibc-langpack-en util-linux tar gzip findutils \
 && { command -v curl > /dev/null || yum install -y curl; } \
 && echo 'LANG=en_US.UTF-8' > /etc/locale.conf
ENV LANG=en_US.UTF-8

# CMS 접속용 TLS 설정
COPY add_openssl.sh /usr/local/share/cubrid-install-test/add_openssl.sh
RUN bash /usr/local/share/cubrid-install-test/add_openssl.sh

RUN for c in bash curl diff ps su tar gzip sed awk; do \
      command -v "$c" > /dev/null || { echo "필수 명령 없음: $c" >&2; exit 1; }; \
    done

CMD ["sleep", "infinity"]
