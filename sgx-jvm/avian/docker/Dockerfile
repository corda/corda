FROM debian:jessie
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

RUN echo 'deb http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
    echo 'deb-src http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
    dpkg --add-architecture i386 && \
    apt-get update && \
    mkdir /var/src/

# Install base dependencies and build tools, general debugging tools
RUN apt-get install -y \
        build-essential \
        g++-4.9 \
        zlib1g-dev \
        openjdk-8-jdk \
        locales \
        --no-install-recommends && \
    apt-get clean all

# Fix utf-8 default locale - we'd otherwise have trouble with the Strings and Misc tests
RUN dpkg-reconfigure locales && \
    locale-gen C.UTF-8 && \
    /usr/sbin/update-locale LANG=C.UTF-8

ENV LC_ALL C.UTF-8

# Set JAVA_HOME for avian's benefit
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

# Add i386 libraries
RUN apt-get install -y \
        libc6-dev-i386 && \
    apt-get download \
        zlib1g-dev:i386 && \
    dpkg -x *.deb / && \
    rm *.deb && \
    apt-get clean all

# Install cross-compile toolchain and emulator for testing
RUN apt-get install -y \
        mingw-w64 \
        wget \
        unzip \
        --no-install-recommends && \
    apt-get clean all

# Download win32 and win64 adjacent to avian
RUN cd /var/src/ && \
    wget https://github.com/ReadyTalk/win32/archive/master.zip -O win32.zip && \
    unzip win32.zip && \
    rm win32.zip && \
    mv win32-* win32 && \
    wget https://github.com/ReadyTalk/win64/archive/master.zip -O win64.zip && \
    unzip win64.zip && \
    rm win64.zip && \
    mv win64-* win64			

# Add openjdk-src stuff
RUN apt-get install -y \
    libcups2-dev \
    libgconf2-dev && \
    mkdir /var/src/openjdk/ && \
    cd /var/src/openjdk/ && \
    apt-get source openjdk-8 && \
    apt-get clean all && \
    find /var/src/openjdk && \
    rm /var/src/openjdk/*.gz /var/src/openjdk/*.dsc && \
    cd /var/src/openjdk/ && \
    tar -xf /var/src/openjdk/openjdk*/jdk.tar.xz && \
    mv /var/src/openjdk/jdk-*/src /var/src/openjdk-src && \
    rm -rf /var/src/openjdk && \
    apt-get clean all

# Download/extract lzma source
RUN mkdir /var/src/lzma && \
    cd /var/src/lzma && \
    apt-get install -y p7zip && \
    wget http://www.7-zip.org/a/lzma1507.7z -O lzma.7z && \
    p7zip -d lzma.7z

# Avian build location
VOLUME /var/src/avian
WORKDIR /var/src/avian
