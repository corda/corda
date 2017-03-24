FROM ubuntu:xenial

RUN apt-get -y update
RUN apt-get install -y software-properties-common
RUN add-apt-repository "deb http://archive.ubuntu.com/ubuntu/ trusty main restricted"
RUN add-apt-repository "deb http://archive.ubuntu.com/ubuntu/ trusty universe"
RUN apt-get -y update

RUN apt-get install -y gcc libprotobuf8 ocaml autoconf libcurl3 openssl cmake libunwind8 proguard g++ zlib1g-dev openjdk-8-jdk patch libtool libunwind8 python2.7 libcurl4-openssl-dev libssl-dev
RUN apt-get install -y -t trusty protobuf-compiler libprotobuf-dev
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
# We re-link libcrypto as it's a global path by default
RUN ln -fs ../../../lib/x86_64-linux-gnu/libcrypto.so.1.0.0 /usr/lib/x86_64-linux-gnu/libcrypto.so
