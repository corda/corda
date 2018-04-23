FROM joshuawarner32/avian-build
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

RUN dpkg --add-architecture arm64 && \
    apt-get update && \
    mkdir -p /opt/arm64 && \
    apt-get download libc6-dev:arm64 \
            linux-headers-3.16.0-4-all-arm64:arm64 \
            linux-libc-dev:arm64 \
            libc6:arm64 \
            zlib1g-dev:arm64 \
            zlib1g:arm64 && \
    for x in *.deb; do \
        dpkg -x $x /opt/arm64; \
    done && \
    rm *.deb && \
    apt-get install -y \
        wget \
        libgmp-dev \
        libmpfr-dev \
        libmpc-dev \
        libisl-dev && \
    apt-get clean all && \
    for x in $(find /opt/arm64 -type l); do \
        r=$(readlink "$x" | sed 's,^/,/opt/arm64/,g'); \
        rm "$x"; \
        ln -s "$r" "$x"; \
    done

RUN mkdir -p /var/src

# Build & install binutils
RUN wget ftp://sourceware.org/pub/binutils/snapshots/binutils-2.23.91.tar.bz2 -O /var/src/binutils.tar.bz2 && \
    cd /var/src/ && tar -xjf binutils.tar.bz2 && rm binutils.tar.bz2 && \
    cd /var/src/binutils* && \
    mkdir build && \
    cd build && \
    ../configure \
        --target=aarch64-linux-gnu \
        --prefix=/opt/arm64 \
        --disable-multilib \
        --program-prefix=aarch64-linux-gnu- \
        --with-sysroot=/opt/arm64 \
        --with-headers=/opt/arm64/usr/include \
        --disable-werror && \
    make && \
    make install && \
    cd /var/src && \
    rm -rf *

# build & install gcc
RUN wget http://www.netgull.com/gcc/releases/gcc-4.8.2/gcc-4.8.2.tar.bz2 -O /var/src/gcc.tar.bz2 && \
    cd /var/src/ && tar -xjf gcc.tar.bz2 && rm gcc.tar.bz2 && \
    cd /var/src/gcc* && \
    mkdir build && \
    cd build && \
    ../configure \
        --target=aarch64-linux-gnu \
        --enable-languages=c,c++ \
        --prefix=/opt/arm64 \
        --disable-multilib \
        --program-prefix=aarch64-linux-gnu- \
        --with-sysroot=/opt/arm64 \
        --with-headers=/opt/arm64/usr/include \
        --disable-werror && \
    make && \
    make install && \
    cd /var/src && \
    rm -rf *

ENV PATH $PATH:/opt/arm64/bin
