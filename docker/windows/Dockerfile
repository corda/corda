FROM joshuawarner32/avian-build
MAINTAINER Joshua Warner, joshuawarner32@gmail.com

# Install cross-compile toolchain and emulator for testing
RUN apt-get update && \
    apt-get install -y \
        mingw-w64 \
        wget \
        unzip \
        --no-install-recommends && \
    apt-get clean all

# Download win32 and win64 adjacent to avian
RUN cd .. && \
    wget https://github.com/ReadyTalk/win32/archive/master.zip -O win32.zip && \
    unzip win32.zip && \
    rm win32.zip && \
    mv win32-* win32 && \
    wget https://github.com/ReadyTalk/win64/archive/master.zip -O win64.zip && \
    unzip win64.zip && \
    rm win64.zip && \
    mv win64-* win64

