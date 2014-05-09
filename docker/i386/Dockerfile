FROM joshuawarner32/avian-build
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y \
        libc6-dev-i386 && \
    apt-get download \
        zlib1g-dev:i386 && \
    dpkg -x *.deb / && \
    rm *.deb && \
    apt-get clean all
