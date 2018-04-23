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

TOP_DIR  = ../..
include $(TOP_DIR)/buildenv.mk

AENAME = sgx_tae_service
EDLFILE = $(TOP_DIR)/common/inc/$(AENAME).edl

INCLUDE += -I$(COMMON_DIR)/inc/tlibc    \
           -I$(COMMON_DIR)/inc/internal \
           -I$(COMMON_DIR)/inc

INCLUDE += -I$(LINUX_PSW_DIR)/ae/data/constants/linux 

INCLUDE += -I$(LINUX_PSW_DIR)/ae/common \
           -I$(LINUX_PSW_DIR)/ae/inc    \
           -I$(LINUX_PSW_DIR)/ae/inc/internal
#           -I$(LINUX_PSW_DIR)/ae/pse/pse_op

INCLUDE += -I$(SGX_IPP_INC)

vpath %.cpp $(COMMON_DIR)/src:\
            $(LINUX_PSW_DIR)/ae/common:\
            $(TOP_DIR)/src

CXXFLAGS += $(ENCLAVE_CXXFLAGS)

EDGER8R_DIR    := $(LINUX_SDK_DIR)/edger8r/linux
EDGER8R        := $(EDGER8R_DIR)/_build/Edger8r.native

EC_DH_LIB_DIR := $(TOP_DIR)/sdk/ec_dh_lib
EC_DH_LIB     := $(EC_DH_LIB_DIR)/libsgx_dh.a

SRC := $(wildcard *.cpp)
SRC += dh.cpp

OBJ := $(sort $(SRC:.cpp=.o))

LIBNAME := libtae_service.a

.PHONY: all
all: $(LIBNAME)

$(AENAME)_t.h: $(EDLFILE) $(EDGER8R)
	$(EDGER8R) --trusted --header-only --search-path $(TOP_DIR)/common/inc $<

$(EDGER8R):
	$(MAKE) -C $(EDGER8R_DIR)

$(LIBNAME): $(OBJ)
	$(MAKE) -C $(EC_DH_LIB_DIR)
	$(CP) $(EC_DH_LIB) $@
	$(AR) rs $@ $(OBJ)

$(OBJ): $(AENAME)_t.h
$(OBJ): %.o :%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDE)  -c $< -o $@

.PHONY: clean
clean:
	@$(RM) *.o
	@$(RM) $(COMMON_DIR)/src/*.o
	@$(RM) *_t.* *_u.*
	@$(RM) $(LIBNAME)
	$(MAKE) -C $(EC_DH_LIB_DIR) clean
	$(MAKE) -C $(EDGER8R_DIR) clean

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
