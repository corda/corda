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

include ../../../buildenv.mk

CXXFLAGS += -Werror
CFLAGS   += -Werror

CFLAGS += -fpie
CXXFLAGS += -fpie
LDFLAGS := -pie $(COMMON_LDFLAGS)

INC += $(ADDED_INC)
INC +=  -I$(COMMON_DIR)/inc                         \
        -I$(COMMON_DIR)/inc/internal                \
        -I$(COMMON_DIR)/inc/internal/linux/         \
        -I$(LINUX_PSW_DIR)/urts                     \
        -I$(LINUX_PSW_DIR)/urts/linux               \
        -I$(LINUX_EXTERNAL_DIR)/tinyxml2            \
        -I$(LINUX_PSW_DIR)/urts/parser

DIR1 := $(LINUX_EXTERNAL_DIR)/tinyxml2/
DIR2 := $(COMMON_DIR)/src/
DIR3 := $(LINUX_PSW_DIR)/urts/
DIR4 := $(LINUX_PSW_DIR)/urts/linux
DIR5 := $(LINUX_PSW_DIR)/urts/parser
DIR6 := $(LINUX_PSW_DIR)/psw/urts/parser/linux

OBJ1 := manage_metadata.o      \
        enclave_creator_sign.o \
        sign_tool.o            \
        parse_key_file.o       \
        util_st.o

OBJ2 := sgx_memset_s.o crypto_evp_digest.o

OBJ3 := tinyxml2.o

OBJ4 := loader.o     \
        se_detect.o  

CPP_OBJS := $(OBJ1) $(OBJ2) $(OBJ3) $(OBJ4)
C_OBJS   := se_trace.o se_map.o
OBJS     := $(CPP_OBJS) $(C_OBJS)

vpath %.cpp $(DIR1):$(DIR2):$(DIR3):$(DIR4):$(DIR5):$(DIR6)

.PHONY: all
all: sgx_sign | $(BUILD_DIR)
	@$(CP) $< $|

$(BUILD_DIR):
	@$(MKDIR) $@

sgx_sign: PRIVATE_LDLIBS  := -lpthread -lenclaveparser -lcrypto
sgx_sign: PRIVATE_LDFLAGS := -L$(LINUX_PSW_DIR)/urts/parser $(LDFLAGS)

sgx_sign: $(OBJS) enclaveparser
	$(CXX) $(CXXFLAGS) $(OBJS) $(PRIVATE_LDFLAGS) $(PRIVATE_LDLIBS) -o $@

$(CPP_OBJS): %.o: %.cpp
	$(CXX) $(CXXFLAGS) $(INC) -c $< -o $@

$(C_OBJS):   %.o: $(DIR2)%.c
	$(CC) $(CFLAGS) $(INC) -c $< -o $@

.PHONY: clean
clean:
	$(RM) *~ *.o sgx_sign $(BUILD_DIR)/sgx_sign
	$(MAKE) -C $(LINUX_PSW_DIR)/urts/parser/ clean

.PHONY: enclaveparser
enclaveparser:
	$(MAKE) -C $(LINUX_PSW_DIR)/urts/parser/
