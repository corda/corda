#
# Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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
            -I$(LINUX_EXTERNAL_DIR)/crypto_px/include                       \
            -I$(LINUX_EXTERNAL_DIR)/epid/inc                \
            -I$(LINUX_EXTERNAL_DIR)/rdrand/                 \
            -I$(LINUX_PSW_DIR)/ae/inc     \
            -I$(LINUX_PSW_DIR)/ae/inc/internal \
            -I$(LINUX_PSW_DIR)/ae/common  

CXXFLAGS += -Wall -fPIC $(INCLUDES) -Werror
CFLAGS := $(filter-out -fPIC -Werror, $(CFLAGS)) -Wall $(INCLUDES)

VERSION_SCRIPT = $(shell pwd)/uae_service_sim.linux.version
LD_OPT_WITHVER = -Wl,--version-script=$(VERSION_SCRIPT)

IPP_LIBDIR := $(LINUX_EXTERNAL_DIR)/crypto_px/
RDRAND_LIBDIR := $(LINUX_EXTERNAL_DIR)/rdrand/src
RDRAND_MAKEFILE := $(RDRAND_LIBDIR)/Makefile

EXTERNAL_LIB += -L$(IPP_LIBDIR) -lcrypto_px
EXTERNAL_LIB += -L$(RDRAND_LIBDIR) -lrdrand
EXTERNAL_LIB += -L$(RDRAND_LIBDIR) -lrt
EXTERNAL_LIB += -ldl

vpath %.cpp $(LINUX_PSW_DIR)/ae/common \
            $(LINUX_SDK_DIR)/simulation/urtssim \
            $(COMMON_DIR)/src

vpath %.c   $(COMMON_DIR)/src

CPP_SRC := $(wildcard *.cpp ../*.cpp) se_sig_rl.cpp cpusvn_util.cpp sgx_read_rand.cpp
C_SRC   := se_trace.c se_thread.c

OBJ := $(CPP_SRC:.cpp=.o) \
       $(C_SRC:.c=.o)

DLLNAME = libsgx_uae_service_sim.so
LIBUAE_SERVICE_DEPLOY := libsgx_uae_service_deploy.so

.PHONY: all
all: $(DLLNAME) $(LIBUAE_SERVICE_DEPLOY) | $(BUILD_DIR)
	$(CP) $(DLLNAME) $|
	$(CP) $(LIBUAE_SERVICE_DEPLOY) $|

$(DLLNAME): $(OBJ) -lrdrand -lcrypto_px
	$(CXX) $(CXXFLAGS) $(OBJ) -shared $(EXTERNAL_LIB) -o $@ $(LD_OPT_WITHVER)

%.o: %.c
	$(CC) $(CFLAGS) -Werror -fPIC -c $< -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

-lcrypto_px:
	$(MAKE) -C $(IPP_LIBDIR)

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
	@$(RM) ../*.o
	@$(RM) $(DLLNAME) $(BUILD_DIR)/$(DLLNAME)
	@$(RM) $(LIBUAE_SERVICE_DEPLOY) $(BUILD_DIR)/$(LIBUAE_SERVICE_DEPLOY)
	@$(MAKE) -C $(IPP_LIBDIR) clean
ifeq ($(RDRAND_MAKEFILE), $(wildcard $(RDRAND_MAKEFILE)))
	@$(MAKE) distclean -C $(RDRAND_LIBDIR)
endif

.PHONY: rebuild
rebuild: clean all
