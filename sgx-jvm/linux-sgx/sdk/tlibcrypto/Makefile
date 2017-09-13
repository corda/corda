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

CXXFLAGS += $(ENCLAVE_CXXFLAGS) -Werror -fno-exceptions -fno-rtti

CPPFLAGS := -I$(COMMON_DIR)/inc/internal \
            -I$(COMMON_DIR)/inc          \
            -I$(COMMON_DIR)/inc/tlibc    \
            -I$(SGX_IPP_INC)

ifneq ($(USE_OPT_LIBS), 0)
	CXXFLAGS += -DSGX_USE_OPT_LIB	
endif

OBJ = init_tcrypto_lib.o sgx_aes_ctr.o sgx_aes_gcm.o sgx_cmac128.o sgx_ecc256.o sgx_ecc256_common.o sgx_ecc256_ecdsa.o sgx_sha256.o sgx_sha256_msg.o tcrypto_version.o sgx_ecc512.o sgx_rsa3072.o

TARGET := libsgx_tcrypto.a

ifneq ($(USE_OPT_LIBS), 0)
$(TARGET): $(OBJ)
	$(MKDIR) $(BUILD_DIR)/.libippcore
	$(RM) $(BUILD_DIR)/.libippcore/*
	cd $(BUILD_DIR)/.libippcore && $(AR) x $(IPP_LIBS_DIR)/libippcore.a
	@$(CP) $(IPP_LIBS_DIR)/libippcp.a $@
	$(AR) rsD $@ $(OBJ) $(BUILD_DIR)/.libippcore/*.o
	$(RM) -r $(BUILD_DIR)/.libippcore
else
libcrypto_px:
	$(MAKE) -C $(SGX_IPP_DIR)

$(TARGET): $(OBJ) libcrypto_px
	@$(CP) $(SGX_IPP_DIR)/libcrypto_px.a $@
	$(AR) rsD $@ $(OBJ)
endif

$(OBJ): %.o: %.cpp
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) $< -o $@

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: all
all: $(TARGET) | $(BUILD_DIR)
	@$(CP) $< $|

.PHONY: clean
clean:
ifeq ($(USE_OPT_LIBS), 0)
	$(MAKE) -C $(SGX_IPP_DIR)  clean
endif
	@$(RM) *.o $(TARGET) $(BUILD_DIR)/$(TARGET)

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
