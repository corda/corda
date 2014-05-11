FROM joshuawarner32/avian-build-i386
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

RUN echo 'deb-src http://http.debian.net/debian jessie main' >> /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y \
        libcups2-dev \
        libgconf2-dev && \
    mkdir /var/openjdk/ && \
    cd /var/openjdk/ && \
    apt-get source openjdk-7 && \
    apt-get clean all && \
    rm /var/openjdk/*.gz /var/openjdk/*.dsc && \
    cd /var/openjdk/ && \
    tar -xzf /var/openjdk/openjdk*/jdk.tar.gz && \
    mv /var/openjdk/jdk-*/src /var/openjdk-src && \
    rm -rf /var/openjdk