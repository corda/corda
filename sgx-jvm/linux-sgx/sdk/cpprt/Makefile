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

LINUXDIR := linux
CPPRT    := libcpprt.a

CFLAGS   += $(ENCLAVE_CFLAGS)
CXXFLAGS += $(ENCLAVE_CXXFLAGS)

CPPFLAGS += -I$(COMMON_DIR)/inc           \
            -I$(COMMON_DIR)/inc/tlibc     \
            -I$(COMMON_DIR)/inc/stdc++    \
            -I$(COMMON_DIR)/inc/internal  \

CPPFLAGS += -I$(LINUX_SDK_DIR)/trts       \
            -I$(LINUX_SDK_DIR)/trts/linux \

CPP_SRCS := $(wildcard memory_manage/new*.cpp) \
            $(wildcard memory_manage/delete*.cpp)
CPP_SRCS += tstdcxx_version.cpp

CC_SRCS  := $(wildcard $(LINUXDIR)/*.cc)
C_SRCS   := $(filter-out $(LINUXDIR)/libelftc_dem_gnu3.c, $(wildcard $(LINUXDIR)/*.c))

CPP_OBJS := $(CPP_SRCS:.cpp=.o)
CC_OBJS  := $(CC_SRCS:.cc=.o)
C_OBJS   := $(C_SRCS:.c=.o)

OBJS := $(sort $(CPP_OBJS) $(C_OBJS) $(CC_OBJS))

$(C_OBJS): %.o: %.c
	$(CC) -c  $(CFLAGS)   $(CPPFLAGS) $< -o $@

$(CC_OBJS): %.o: %.cc
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) $< -o $@

$(CPP_OBJS): %.o: %.cpp
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) $< -o $@


.PHONY: all
all: $(CPPRT)

.PHONY: prepare-libunwind
prepare-libunwind:
	$(CP) linux/pthread_compat.h linux/libunwind/include

$(CPPRT): $(OBJS) prepare-libunwind libunwind
	$(CP) linux/libunwind/src/.libs/libunwind.a $(CPPRT)
	$(AR) rs $@ $(OBJS)

.PHONY: libunwind
libunwind:
	cd linux/libunwind/ && ( test -f Makefile || ./autogen-linux.sh ) && $(MAKE) -j5


.PHONY: clean
clean:
	@$(RM) $(OBJS) $(CPPRT)
ifeq ("$(shell test -f linux/libunwind/Makefile && echo Makefile exists)", "Makefile exists")	
	@-$(MAKE) -C linux/libunwind distclean
endif
	@$(RM) -r linux/libunwind/INSTALL             \
              linux/libunwind/Makefile.in         \
              linux/libunwind/aclocal.m4          \
              linux/libunwind/autom4te.cache/     \
              linux/libunwind/config/             \
              linux/libunwind/configure           \
              linux/libunwind/doc/Makefile.in     \
              linux/libunwind/include/pthread_compat.h \
              linux/libunwind/include/config.h.in \
              linux/libunwind/src/Makefile.in

