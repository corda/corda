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

TOP_DIR := ../../../..
include ../../buildenv.mk

CXXFLAGS  += -Werror
CFLAGS    += -Werror 

INCLUDE += -I.                                   \
           -I$(SGX_IPP_INC)/                     \
           -I$(LINUX_SDK_DIR)/tseal              \
           -I$(TOP_DIR)/common/inc               \
           -I$(LINUX_PSW_DIR)/ae/inc/internal

SRC := $(wildcard *.cpp)
SRC += dh.cpp              \
       t_pairing_blob.cpp  \
       sgx_sha256_128.cpp

OBJ := $(SRC:.cpp=.o)
OBJS := $(sort $(OBJ) version.o)

.PHONY: all
all: $(SONAME) 

$(SONAME): $(OBJS)
	$(CXX) $(CXXFLAGS) -o $@ $(OBJS) -nostdlib -nodefaultlibs -nostartfiles $(LDTFLAGS)
	$(STRIP)  --strip-unneeded $(SONAME)

$(OBJ): $(AENAME)_t.c
$(OBJ): %.o: %.cpp
	$(CXX) $(CXXFLAGS) -fno-exceptions -fno-rtti $(INCLUDE) $(DEFINES) -c $< -o $@

$(AENAME)_t.c: $(EDLFILE)
	@$(EDGER8R) --trusted $<

.PHONY: clean
clean:
	@$(RM) *.o
	@$(RM) *.so
	@$(RM) *.map
	@$(RM) *_t.*

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
