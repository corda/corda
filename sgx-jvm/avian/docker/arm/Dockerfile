FROM joshuawarner32/avian-build
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

RUN dpkg --add-architecture armel && \
    apt-get update && \
    mkdir -p /opt/arm && \
    apt-get download libc6-dev:armel \
            linux-headers-3.13-1-all-armel:armel \
            linux-libc-dev:armel \
            libc6:armel \
            zlib1g-dev:armel \
            zlib1g:armel && \
    for x in *.deb; do \
        dpkg -x $x /opt/arm; \
    done && \
    rm *.deb && \
    apt-get install -y \
        wget \
        libgmp-dev \
        libmpfr-dev \
        libmpc-dev \
        libisl-dev && \
    apt-get clean all && \
    for x in $(find /opt/arm -type l); do \
        r=$(readlink "$x" | sed 's,^/,/opt/arm/,g'); \
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
        --target=arm-linux-gnueabi \
        --prefix=/opt/arm \
        --disable-multilib \
        --program-prefix=arm-linux-gnueabi- \
        --with-sysroot=/opt/arm \
        --with-headers=/opt/arm/usr/include && \
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
        --target=arm-linux-gnueabi \
        --enable-languages=c,c++ \
        --prefix=/opt/arm \
        --disable-multilib \
        --program-prefix=arm-linux-gnueabi- \
        --with-sysroot=/opt/arm \
        --with-headers=/opt/arm/usr/include && \
    make && \
    make install && \
    cd /var/src && \
    rm -rf *

ENV PATH $PATH:/opt/arm/bin
