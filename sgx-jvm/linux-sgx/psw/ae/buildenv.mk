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

ENV := $(strip $(wildcard $(TOP_DIR)/buildenv.mk))

ifeq ($(ENV),)
    $(error "Can't find $(TOP_DIR)/buildenv.mk")
endif

include $(TOP_DIR)/buildenv.mk

WORK_DIR := $(shell pwd)
AENAME   := $(notdir $(WORK_DIR))
SONAME  := $(AENAME).so
ifdef DEBUG
CONFIG   := config_debug.xml
else
CONFIG   := config.xml
endif
EDLFILE  := $(wildcard *.edl)

LINUX_EPID := $(LINUX_EXTERNAL_DIR)/epid/lib/linux

EXTERNAL_LIB_NO_CRYPTO = -lsgx_tstdc

URTSLIB := -lsgx_urts
TRTSLIB := -lsgx_trts
EXTERNAL_LIB_NO_CRYPTO += -lsgx_tservice

TCRYPTO_LIBDIR := $(LINUX_SDK_DIR)/tlibcrypto
EXTERNAL_LIB   = $(EXTERNAL_LIB_NO_CRYPTO) -L$(TCRYPTO_LIBDIR) -lsgx_tcrypto

INCLUDE := -I$(LINUX_PSW_DIR)/ae/inc \
           -I$(COMMON_DIR)/inc                         \
           -I$(COMMON_DIR)/inc/tlibc                   \
           -I$(COMMON_DIR)/inc/internal                \
           -I$(LINUX_SDK_DIR)/selib                    \
           -I$(LINUX_SDK_DIR)/trts

SIGNTOOL  := $(ROOT_DIR)/build/linux/sgx_sign
SGXSIGN   := $(ROOT_DIR)/build/linux/sgx_sign

KEYFILE   := $(LINUX_SDK_DIR)/sign_tool/sample_sec.pem
EDGER8R   := $(LINUX_SDK_DIR)/edger8r/linux/_build/Edger8r.native

CXXFLAGS  += $(ENCLAVE_CXXFLAGS)
CFLAGS    += $(ENCLAVE_CFLAGS)

LDTFLAGS  = -L$(BUILD_DIR) -Wl,--whole-archive $(TRTSLIB) -Wl,--no-whole-archive \
            -Wl,--start-group $(EXTERNAL_LIB) -Wl,--end-group                    \
            -Wl,--version-script=$(ROOT_DIR)/build-scripts/enclave.lds $(ENCLAVE_LDFLAGS)

LDTFLAGS_NO_CRYPTO = -L$(BUILD_DIR) -Wl,--whole-archive $(TRTSLIB) -Wl,--no-whole-archive \
            -Wl,--start-group $(EXTERNAL_LIB_NO_CRYPTO) -Wl,--end-group                    \
            -Wl,--version-script=$(ROOT_DIR)/build-scripts/enclave.lds $(ENCLAVE_LDFLAGS)

LDTFLAGS += -fuse-ld=gold -Wl,--rosegment -Wl,-Map=out.map -Wl,--undefined=version -Wl,--gc-sections
LDTFLAGS_NO_CRYPTO += -fuse-ld=gold -Wl,--rosegment -Wl,-Map=out.map -Wl,--undefined=version -Wl,--gc-sections

DEFINES := -D__linux__

vpath %.cpp $(COMMON_DIR)/src:$(LINUX_PSW_DIR)/ae/common

.PHONY : version

version.o: $(LINUX_PSW_DIR)/ae/common/version.cpp
	$(CXX) $(CXXFLAGS) -fno-exceptions -fno-rtti $(INCLUDE) $(DEFINES) -c $(LINUX_PSW_DIR)/ae/common/version.cpp -o $@
