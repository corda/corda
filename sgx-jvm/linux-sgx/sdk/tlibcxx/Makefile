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


include ../../buildenv.mk

CFLAGS   += $(ENCLAVE_CFLAGS)
CXXFLAGS += $(ENCLAVE_CXXFLAGS)

CPPFLAGS += -I./include \
            -I$(COMMON_DIR)/inc       \
            -I$(COMMON_DIR)/inc/stdc++ \
            -I$(COMMON_DIR)/inc/tlibc \
            -I$(COMMON_DIR)/inc/stdc++ \
            -I$(COMMON_DIR)/inc/internal \

CPPFLAGS += -I$(LINUX_SDK_DIR)/trts       \
            -I$(LINUX_SDK_DIR)/trts/linux

ifeq ($(BUILD),ANDROID)
CPPFLAGS += -I$(ROOT_DIR)/android/ndk-toolchain/sysroot/usr/include
endif   #ANDROID

CPP_SRCS := $(wildcard src/*.cpp)
CPP_OBJS := $(CPP_SRCS:.cpp=.o)

OBJS     := $(CPP_OBJS)

LIBCXX_NAME = libcxx.a

.PHONY:all
all: $(LIBCXX_NAME)

$(LIBCXX_NAME): $(OBJS)
	$(AR) rcs $(LIBCXX_NAME) $^

$(OBJS): %.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

.PHONY: clean
clean:
	@$(RM) $(LIBCXX_NAME) $(OBJS)
