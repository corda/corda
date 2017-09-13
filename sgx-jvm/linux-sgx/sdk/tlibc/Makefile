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
ASFLAGS  := $(CFLAGS)
CFLAGS   += -std=c99
CXXFLAGS += $(ENCLAVE_CXXFLAGS) -fno-exceptions -fno-rtti

CPPFLAGS += -I.                          \
            -I$(COMMON_DIR)/inc          \
            -I$(COMMON_DIR)/inc/tlibc    \
            -I$(COMMON_DIR)/inc/internal \
            -I$(LINUX_SDK_DIR)/trts      \
            -I./gdtoa

ifneq ($(USE_OPT_LIBS), 0)
CFLAGS += -D_TLIBC_USE_INTEL_FAST_STRING_
CXXFLAGS += -D_TLIBC_USE_INTEL_FAST_STRING_
LIBC_C_SRCS := $(wildcard gen/*.c gdtoa/*.c locale/*.c math/*.c stdlib/*.c string/*.c stdio/*.c time/*.c) 
else
LIBC_C_SRCS := $(wildcard gen/*.c gdtoa/*.c locale/*.c stdlib/*.c string/*.c stdio/*.c time/*.c)
endif

LIBC_CPP_SRCS := $(wildcard gen/*.cpp) tstdc_version.cpp
LIBC_ASM_SRCS := $(wildcard gen/*.S)

LIBC_OBJS := $(LIBC_C_SRCS:.c=.o)
LIBC_OBJS += $(LIBC_CPP_SRCS:.cpp=.o)
LIBC_OBJS += $(LIBC_ASM_SRCS:.S=.o)
LIBC_OBJS := $(sort $(LIBC_OBJS))

LIBC_NAME    := libtlibc.a

.PHONY: all
all: $(LIBC_NAME)

$(LIBC_NAME): $(LIBC_OBJS)
	$(AR) rcs $@ $^

string/consttime_memequal.o: string/consttime_memequal.c
	$(CC)  $(filter-out -O2,$(CFLAGS)) -O1 $(CPPFLAGS) -c $< -o $@
	
%.o: %.c
	$(CC)  $(CFLAGS)   $(CPPFLAGS) -c $< -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

%.o: %.S
	$(CC)  $(ASFLAGS)  $(CPPFLAGS) -c $< -o $@

.PHONY: clean
clean:
	@$(RM) $(LIBC_NAME) $(LIBC_OBJS)
