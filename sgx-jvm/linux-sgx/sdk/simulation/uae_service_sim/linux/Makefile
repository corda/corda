#
# Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   * Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#   * Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in
#     the documentation and/or other materials provided with the
#     distribution.
#   * Neither the name of Intel Corporation nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
#

TOP_DIR  = ../../../..
include $(TOP_DIR)/buildenv.mk

INCLUDES := -I..                                            \
            -I$(COMMON_DIR)/inc                             \
            -I$(COMMON_DIR)/inc/internal                    \
            -I$(LINUX_SDK_DIR)/tseal                        \
            -I$(LINUX_SDK_DIR)/simulation/tinst             \
            -I$(LINUX_SDK_DIR)/simulation/urtssim           \
            -I$(LINUX_EXTERNAL_DIR)/epid-sdk-3.0.0          \
            -I$(LINUX_EXTERNAL_DIR)/rdrand/                 \
            -I$(LINUX_PSW_DIR)/ae/inc     \
            -I$(LINUX_PSW_DIR)/ae/inc/internal \
            -I$(LINUX_PSW_DIR)/ae/common  

CXXFLAGS += -Wall -fPIC $(INCLUDES) -Werror -g
CFLAGS := $(filter-out -fPIC -Werror, $(CFLAGS)) -Wall $(INCLUDES)

VERSION_SCRIPT = $(shell pwd)/uae_service_sim.linux.version
LD_OPT = $(COMMON_LDFLAGS) -Wl,--version-script=$(VERSION_SCRIPT)

RDRAND_LIBDIR := $(LINUX_EXTERNAL_DIR)/rdrand/src
RDRAND_MAKEFILE := $(RDRAND_LIBDIR)/Makefile

EXTERNAL_LIB += -L$(RDRAND_LIBDIR) -lrdrand
EXTERNAL_LIB += -L$(RDRAND_LIBDIR) -lrt
EXTERNAL_LIB += -ldl -lcrypto

vpath %.cpp $(LINUX_PSW_DIR)/ae/common \
            $(LINUX_SDK_DIR)/simulation/urtssim \
            $(COMMON_DIR)/src

vpath %.c   $(COMMON_DIR)/src

CPP_SRC := $(wildcard *.cpp ../*.cpp) se_sig_rl.cpp cpusvn_util.cpp sgx_read_rand.cpp 
CPP_SRC += crypto_evp_digest.cpp crypto_cmac_128.cpp
C_SRC   := se_trace.c se_thread.c

OBJ := $(sort $(CPP_SRC:.cpp=.o)) \
       $(C_SRC:.c=.o)

DLLNAME = libsgx_uae_service_sim.so
DLLNAME_DEBUG = libsgx_uae_service_sim.so.debug
LIBUAE_SERVICE_DEPLOY := libsgx_uae_service_deploy.so

.PHONY: all
all: $(DLLNAME) $(DLLNAME_DEBUG) $(LIBUAE_SERVICE_DEPLOY) | $(BUILD_DIR)
	$(CP) $(DLLNAME) $|
	$(CP) $(LIBUAE_SERVICE_DEPLOY) $|
ifndef DEBUG
	$(CP) $(DLLNAME_DEBUG) $|
endif

$(DLLNAME): $(OBJ) -lrdrand
	$(CXX) $(CXXFLAGS) $(OBJ) -shared $(EXTERNAL_LIB) -o $@ $(LD_OPT)

$(DLLNAME_DEBUG): $(DLLNAME)
ifndef DEBUG
	$(CP) $(DLLNAME) $(DLLNAME).orig
	$(OBJCOPY) --only-keep-debug $(DLLNAME) $(DLLNAME_DEBUG)
	$(STRIP) -g $(DLLNAME)
	$(OBJCOPY) --add-gnu-debuglink=$(DLLNAME_DEBUG) $(DLLNAME)
endif

%.o: %.c
	$(CC) $(CFLAGS) -Werror -fPIC -c $< -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

-lrdrand: $(RDRAND_MAKEFILE)
	$(MAKE) -C $(RDRAND_LIBDIR)


$(RDRAND_MAKEFILE):
ifeq ($(ARCH), x86)
	@cd $(RDRAND_LIBDIR);./configure
else
	@cd $(RDRAND_LIBDIR);./configure CFLAGS=-fPIC
endif

$(LIBUAE_SERVICE_DEPLOY): ../uae_service_deploy.c
	$(CC) -fPIC -shared -Wl,-soname=libsgx_uae_service.so $< -o $@

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) *.o
	@$(RM) ../*.o $(DLLNAME).orig
	@$(RM) $(DLLNAME) $(BUILD_DIR)/$(DLLNAME)
	@$(RM) $(DLLNAME_DEBUG) $(BUILD_DIR)/$(DLLNAME_DEBUG)
	@$(RM) $(LIBUAE_SERVICE_DEPLOY) $(BUILD_DIR)/$(LIBUAE_SERVICE_DEPLOY)
ifeq ($(RDRAND_MAKEFILE), $(wildcard $(RDRAND_MAKEFILE)))
	@$(MAKE) distclean -C $(RDRAND_LIBDIR)
endif

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
