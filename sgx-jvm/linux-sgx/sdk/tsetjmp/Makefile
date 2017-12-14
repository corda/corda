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
CXXFLAGS += $(ENCLAVE_CXXFLAGS) -fno-exceptions -fno-rtti -Werror

CPPFLAGS += -I.                                \
            -I$(COMMON_DIR)/inc                \
            -I$(COMMON_DIR)/inc/internal       \
            -I$(COMMON_DIR)/inc/internal/linux \
            -I$(COMMON_DIR)/inc/tlibc          \
            -I ../trts/linux                   \
            -I ../trts
			

LIBLONGJMP_CPP_SRCS := tlongjmp_version.cpp
LIBLONGJMP_ASM_SRCS := $(wildcard *.S)

LIBLONGJMP_OBJS := $(sort $(LIBLONGJMP_ASM_SRCS:.S=.o))
LIBLONGJMP_OBJS += $(LIBLONGJMP_CPP_SRCS:.cpp=.o)

LIBLONGJMP_NAME    := libsgx_tsetjmp.a

.PHONY: all
all: $(LIBLONGJMP_NAME)

$(LIBLONGJMP_NAME): $(LIBLONGJMP_OBJS)
	$(AR) rcs $@ $^

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

%.o: %.S
	$(CC)  $(ASFLAGS)  $(CPPFLAGS) -c $< -o $@


.PHONY: clean
clean:
	@$(RM) $(LIBLONGJMP_NAME) $(LIBLONGJMP_OBJS)


.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
