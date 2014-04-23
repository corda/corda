FROM debian:jessie
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

# Install base dependencies and build tools, general debugging tools
RUN apt-get update && \
    apt-get install -y \
        build-essential \
        g++-4.8 \
        zlib1g-dev \
        openjdk-7-jdk \
        locales \
        --no-install-recommends && \
    apt-get clean all

# Fix utf-8 default locale - we'd otherwise have trouble with the Strings and Misc tests
RUN dpkg-reconfigure locales && \
    locale-gen C.UTF-8 && \
    /usr/sbin/update-locale LANG=C.UTF-8

ENV LC_ALL C.UTF-8

# Set JAVA_HOME for avian's benefit
ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64

# Avian build location
VOLUME /var/avian
WORKDIR /var/avian
