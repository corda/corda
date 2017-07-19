MAKEFILE_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ROOT=$(MAKEFILE_DIR)/dependencies/root
SHELL=/bin/bash
# JAVA_HOME=$(ROOT)/usr/lib/jvm/java-8-openjdk-amd64
# JAVA_HOME=$(ROOT)/usr/lib/jvm/java-openjdk

JDK_IMAGE=$(PWD)/jdk8u/build/linux-x86_64-normal-server-release/images/j2re-image

.PHONY: all
all: jvm-enclave/standalone/build/standalone_sgx_verify

# The final binary
jvm-enclave/standalone/build/standalone_sgx_verify: avian linux-sgx/build/linux/aesm_service
	JAVA_HOME=$(JAVA_HOME) $(MAKE) -C jvm-enclave

# Avian with SGX support
AVIAN_EXTRA_CFLAGS="-I$(ROOT)/usr/include -I$(ROOT)/usr/include/x86_64-linux-gnu"
AVIAN_EXTRA_LDFLAGS="-L$(ROOT)/usr/lib -L$(ROOT)/usr/lib/x86_64-linux-gnu -ldl -lpthread -lz"
.PHONY: avian
avian: | $(JDK_IMAGE)
	PATH=$(ROOT)/usr/bin:$(PATH) $(MAKE) -C avian build-lflags=$(AVIAN_EXTRA_LDFLAGS) extra-lflags=$(AVIAN_EXTRA_LDFLAGS) extra-build-cflags+=$(AVIAN_EXTRA_CFLAGS) extra-cflags+=$(AVIAN_EXTRA_CFLAGS) mode=debug openjdk=$(JAVA_HOME) openjdk-src=$(PWD)/jdk8u/jdk/src openjdk-image=$(JDK_IMAGE) system=sgx

# The SGX SDK
LINUX_SGX_SOURCES=$(shell find linux-sgx \( -name '*.c' -o -name '*.cpp' -o -name '*.h' -o -name '*.edl' \) ! \( -name '*_u.?' -o -name '*_t.?'  -o -name '*.pb.h' -o -path 'linux-sgx/sdk/cpprt/linux/libunwind/*' -o -path 'linux-sgx/external/rdrand/src/config.h' \)) # Poor man's up-to-date check (they don't have one in the SDK??)
linux-sgx/build/linux/aesm_service: $(LINUX_SGX_SOURCES) linux-sgx/external/ippcp_internal/inc
	LD_LIBRARY_PATH=$(ROOT)/usr/lib/x86_64-linux-gnu:$(ROOT)/lib/x86_64-linux-gnu LIBRARY_PATH=$(ROOT)/usr/lib/x86_64-linux-gnu PATH=$(ROOT)/usr/bin:$(PATH) ACLOCAL_PATH=$(ROOT)/usr/share/aclocal $(MAKE) -C linux-sgx EXTRA_CXXFLAGS+="-isystem $(ROOT)/usr/include -isystem $(ROOT)/usr/include/x86_64-linux-gnu -L$(ROOT)/usr/lib/x86_64-linux-gnu" DEBUG=1

jdk8u:
	git clone -b deterministic-jvm8 --single-branch https://github.com/corda/openjdk $@

$(JDK_IMAGE): jdk8u
	cd jdk8u && $(SHELL) ./configure && $(MAKE) JOBS=2 images

linux-sgx/external/ippcp_internal/inc:
	cd linux-sgx && $(SHELL) ./download_prebuilt.sh

build:
	mkdir -p $@

.PHONY: clean
clean:
	$(MAKE) -C jvm-enclave clean
	$(MAKE) -C linux-sgx clean
	[ ! -d jdk8u ] || $(MAKE) -C jdk8u clean
	$(MAKE) -C avian clean

.PHONY: distclean
distclean: clean
	$(RM) -r jdk8u
	$(RM) -r linux-sgx/external/{ippcp_internal,libirc,libm}

