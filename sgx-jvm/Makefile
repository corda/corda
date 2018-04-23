MAKEFILE_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
SHELL=/bin/bash

JDK_IMAGE=$(MAKEFILE_DIR)/jdk8u/build/linux-x86_64-normal-server-release/images/j2re-image

.PHONY: all

all: \
	jvm-enclave/standalone/build/standalone_sgx_verify \
	linux-sgx-driver/isgx.ko

# === JVM Enclave ===

jvm-enclave/standalone/build/standalone_sgx_verify: \
		avian linux-sgx/build/linux/aesm_service
	JAVA_HOME=$(JAVA_HOME) JDK_IMAGE=$(JDK_IMAGE) $(MAKE) -C jvm-enclave

# === Avian with SGX Support ===

AVIAN_EXTRA_CFLAGS="-I/usr/include -I/usr/include/x86_64-linux-gnu"
AVIAN_EXTRA_LDFLAGS="-L/usr/lib -L/usr/lib/x86_64-linux-gnu -ldl -lpthread -lz"
.PHONY: avian
avian: | $(JDK_IMAGE)
	PATH=/usr/bin:$(PATH) $(MAKE) -C avian \
	     build-lflags=$(AVIAN_EXTRA_LDFLAGS) \
	     extra-lflags=$(AVIAN_EXTRA_LDFLAGS) \
	     extra-build-cflags+=$(AVIAN_EXTRA_CFLAGS) \
	     extra-cflags+=$(AVIAN_EXTRA_CFLAGS) \
	     mode=debug \
	     openjdk=$(JAVA_HOME) \
	     openjdk-src=$(MAKEFILE_DIR)/jdk8u/jdk/src \
	     openjdk-image=$(JDK_IMAGE) \
	     system=sgx

# === Deterministic JDK ===

jdk8u:
	git clone -b deterministic-jvm8 --single-branch https://github.com/corda/openjdk $@

$(JDK_IMAGE): jdk8u
	cd jdk8u && \
		ALSA_NOT_NEEDED=yes CUPS_NOT_NEEDED=yes FREETYPE_NOT_NEEDED=yes \
		PULSE_NOT_NEEDED=yes X11_NOT_NEEDED=yes $(SHELL) ./configure && \
		$(MAKE) JOBS=2 images docs

# === SGX SDK ===

# Poor man's up-to-date check (they don't have one in the SDK)
LINUX_SGX_SOURCES=$(shell find linux-sgx \
    \( \
        -name '*.c' -o \
        -name '*.cpp' -o \
        -name '*.h' -o \
        -name '*.edl' \
    \) ! \( \
        -name '*_u.?' -o \
        -name '*_t.?' -o \
        -name '*.pb.h' -o \
        -path 'linux-sgx/sdk/cpprt/linux/libunwind/*' -o \
        -path 'linux-sgx/external/rdrand/src/config.h' \
    \))
linux-sgx/build/linux/aesm_service: \
		$(LINUX_SGX_SOURCES) \
		linux-sgx/external/ippcp_internal/inc
	$(MAKE) -C linux-sgx DEBUG=1

linux-sgx/external/ippcp_internal/inc:
	cd linux-sgx && $(SHELL) ./download_prebuilt.sh

# === SGX Driver ===

linux-sgx-driver/isgx.ko:
	$(MAKE) -C linux-sgx-driver

# === Directories and Clean ===

build:
	mkdir -p $@

.PHONY: clean
clean:
	$(MAKE) -C jvm-enclave clean
	$(MAKE) -C linux-sgx clean
	$(MAKE) -C linux-sgx-driver clean
	[ ! -d jdk8u ] || $(MAKE) -C jdk8u clean
	../gradlew -p .. verify-enclave:clean
	$(MAKE) -C avian clean

.PHONY: distclean
distclean: clean
	$(RM) -r jdk8u
	$(RM) -r linux-sgx/external/{ippcp_internal,libirc,libm}

