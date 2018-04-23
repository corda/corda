FROM ubuntu:xenial-20171114

# General information

LABEL version="1.0"
LABEL description="SGX build and test container"
LABEL maintainer="tommy.lillehagen@r3.com"

# Configure package management software

ENV LANG C.UTF-8
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update -qqy
RUN apt-get install -qqy software-properties-common apt-utils
RUN apt-get update -qqy

# Install dependencies (lock versions)

RUN apt-get install -qqy \
    autoconf=2.69-9 \
    ccache=3.2.4-1 \
    cmake=3.5.1-1ubuntu3 \
    cpio=2.11+dfsg-5ubuntu1 \
    exuberant-ctags=1:5.9~svn20110310-11 \
    g++=4:5.3.1-1ubuntu1 \
    gcc=4:5.3.1-1ubuntu1 \
    gdb=7.11.1-0ubuntu1~16.5 \
    gdbserver=7.11.1-0ubuntu1~16.5 \
    git=1:2.7.4-0ubuntu1.3 \
    libcurl3=7.47.0-1ubuntu2.5 \
    libcurl4-openssl-dev=7.47.0-1ubuntu2.5 \
    libprotobuf-dev=2.6.1-1.3 \
    libssl-dev=1.0.2g-1ubuntu4.10 \
    libtool=2.4.6-0.1 \
    libunwind8=1.1-4.1 \
    make=4.1-6 \
    ocaml=4.02.3-5ubuntu2 \
    openjdk-8-jdk=8u151-b12-0ubuntu0.16.04.2 \
    openssl=1.0.2g-1ubuntu4.10 \
    patch=2.7.5-1 \
    protobuf-compiler=2.6.1-1.3 \
    python2.7=2.7.12-1ubuntu0~16.04.2 \
    unzip=6.0-20ubuntu1 \
    wget=1.17.1-1ubuntu1.3 \
    zip=3.0-11 \
    zlib1g-dev=1:1.2.8.dfsg-2ubuntu4.1

# Set Python 2.7 as the default version

RUN ln -fs \
    /usr/bin/python2.7 \
    /usr/bin/python

# Link libcrypto properly

RUN ln -fs \
    /lib/x86_64-linux-gnu/libcrypto.so.1.0.0 \
    /usr/lib/x86_64-linux-gnu/libcrypto.so

# Location for the UNIX socket belonging to the Intel AESM service

RUN mkdir -p /var/run/aesmd/

# Update ProGuard to version 6 beta

ADD dependencies/proguard6.0beta1.tar.gz /usr/share/

# Expose ports for remote GDB and Java debugging, and test servers

EXPOSE 2000 5005 8080 8084 9080

# Environment

ENV SHELL               /bin/bash
ENV CODE                /code
ENV HOME                /home
ENV USER_HOME           ${HOME}
ENV GRADLE_USER_HOME    ${HOME}/.gradle
ENV SGX_SDK             /sgx

# Volumes and work directory

VOLUME ${CODE}
VOLUME ${HOME}
VOLUME ${SGX_SDK}
RUN mkdir -p ${CODE}
RUN mkdir -p ${HOME}
RUN mkdir -p ${SGX_SDK}
WORKDIR ${CODE}

#  Set up Java and SGX environment

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV _JAVA_OPTIONS=-Duser.home=${HOME}

ENV SGX_BIN ${SGX_SDK}/sgxsdk/bin:${SGX_SDK}/sgxsdk/bin/x64
ENV PATH ${PATH}:${JAVA_HOME}/jre/bin:${JAVA_HOME}/bin:${SGX_BIN}

ENV LD_LIBRARY_PATH ${LD_LIBRARY_PATH}:${CODE}/sgx-jvm/linux-sgx/build/linux
