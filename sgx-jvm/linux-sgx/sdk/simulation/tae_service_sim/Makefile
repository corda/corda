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

TOP_DIR := ../../..
include $(TOP_DIR)/buildenv.mk

CFLAGS   += -Werror $(ENCLAVE_CFLAGS)
CXXFLAGS += -Werror $(ENCLAVE_CXXFLAGS)

INCLUDE += -I$(COMMON_DIR)            \
           -I$(COMMON_DIR)/inc/tlibc  \
           -I$(SGX_IPP_INC)           \
           -I$(LINUX_PSW_DIR)/ae/inc  \
           -I$(LINUX_PSW_DIR)/ae/inc/internal \
           -I$(TOP_DIR)/common/inc \
           -I$(TOP_DIR)/common/inc/internal 

TSERVICE_SIM_LIB := libsgx_tservice_sim.a
TAE_SERVICE_SIM_LIB := libtae_service_sim.a
EDLFILE := $(TOP_DIR)/common/inc/sgx_tae_service.edl

TSEAL_LIB_DIR := $(LINUX_SDK_DIR)/tseal/linux
TSEAL_LIB     := $(TSEAL_LIB_DIR)/libtSeal.a

SE_LIB_DIR    := $(LINUX_SDK_DIR)/selib/linux
SE_LIB        := $(SE_LIB_DIR)/libselib.a

EC_DH_LIB_DIR := $(LINUX_SDK_DIR)/ec_dh_lib
EC_DH_LIB     := $(EC_DH_LIB_DIR)/libsgx_dh.a

EDGER8R_DIR   := $(LINUX_SDK_DIR)/edger8r/linux
EDGER8R       := $(EDGER8R_DIR)/_build/Edger8r.native

SRC     := $(wildcard *.cpp)
SRC     += $(TOP_DIR)/common/src/dh.cpp
OBJS := $(sort $(SRC:.cpp=.o))

.PHONY: all
all: $(TSERVICE_SIM_LIB) | $(BUILD_DIR)
	$(CP) $^ $|

$(TAE_SERVICE_SIM_LIB): $(OBJS)
	$(AR) rcsD $@ $(OBJS)

$(TSERVICE_SIM_LIB): $(TAE_SERVICE_SIM_LIB) $(EC_DH_LIB) $(TSEAL_LIB) $(SE_LIB)
	@$(MKDIR) $(BUILD_DIR)/.tservice_sim
	@$(RM) -f $(BUILD_DIR)/.tservice_sim/*
	cd $(BUILD_DIR)/.tservice_sim && $(AR) x $(EC_DH_LIB)
	cd $(BUILD_DIR)/.tservice_sim && $(AR) x $(TSEAL_LIB)
	cd $(BUILD_DIR)/.tservice_sim && $(AR) x $(SE_LIB)
	cp $(TAE_SERVICE_SIM_LIB) $@
	$(AR) rsD $@ $(BUILD_DIR)/.tservice_sim/*.o
	@$(RM) -rf  $(BUILD_DIR)/.tservice_sim

sgx_tae_service_t.h: $(EDLFILE) $(EDGER8R)
	$(EDGER8R) --trusted --header-only --search-path $(TOP_DIR)/common/inc $<

$(OBJS): %.o: %.cpp sgx_tae_service_t.h
	$(CXX) $(CXXFLAGS) -fno-exceptions -fno-rtti $(INCLUDE) $(DEFINES) -c $< -o $@

$(EDGER8R):
	$(MAKE) -C $(EDGER8R_DIR)

$(EC_DH_LIB):
	$(MAKE) -C $(EC_DH_LIB_DIR)

$(TSEAL_LIB):
	$(MAKE) -C $(TSEAL_LIB_DIR)

$(SE_LIB):
	$(MAKE) -C $(SE_LIB_DIR)

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	$(MAKE) -C $(EDGER8R_DIR)   clean
	$(MAKE) -C $(EC_DH_LIB_DIR) clean
	$(MAKE) -C $(TSEAL_LIB_DIR) clean
	$(MAKE) -C $(SE_LIB_DIR)    clean
	@$(RM) *.o $(TSERVICE_SIM_LIB) $(TAE_SERVICE_SIM_LIB) $(BUILD_DIR)/$(TSERVICE_SIM_LIB)
	@$(RM) $(COMMON_DIR)/src/*.o
	@$(RM) sgx_tae_service_*.c
	@$(RM) sgx_tae_service_*.h

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
