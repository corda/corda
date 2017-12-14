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

CPPFLAGS := -I$(COMMON_DIR)/inc/internal \
            -I$(COMMON_DIR)/inc          \
            -I$(COMMON_DIR)/inc/tlibc

CXXFLAGS += $(ENCLAVE_CXXFLAGS) -Werror -fno-exceptions -fno-rtti

OBJ = init_tcrypto_lib.o sgx_aes_ctr.o sgx_aes_gcm.o sgx_cmac128.o sgx_ecc256.o sgx_ecc256_ecdsa.o sgx_sha256.o sgx_sha256_msg.o sgx_ecc256_internal.o sgx_rsa3072.o
SHARED_OBJ = tcrypto_version.o

ifneq ($(USE_OPT_LIBS), 1)

# Added to build with SgxSSL libraries
ifeq ($(ARCH), x86_64)
    OPENSSL_PACKAGE = $(LINUX_EXTERNAL_DIR)/sgxssl/package
else
    $(error SGXSSL doesn't support 32bit)
endif #($(ARCH), x86_64)

ifdef DEBUG
    OPENSSL_LIBRARY_PATH := $(OPENSSL_PACKAGE)/lib64/debug
else
    OPENSSL_LIBRARY_PATH := $(OPENSSL_PACKAGE)/lib64/release
endif

SGXSSL_Library_Name := sgx_tsgxssl
OpenSSL_Crypto_Library_Name := sgx_tsgxssl_crypto

PREPARE_SGXSSL := $(LINUX_EXTERNAL_DIR)/sgxssl/prepare_sgxssl.sh
PREPRARE_SGX_SSL:
	chmod 755 $(PREPARE_SGXSSL)
	test -f $(OPENSSL_LIBRARY_PATH)/lib$(SGXSSL_Library_Name).a || $(PREPARE_SGXSSL)

CPPFLAGS += -I$(OPENSSL_PACKAGE)/include
CXXFLAGS += -DUSE_SGXSSL
SRCDIR := sgxssl

LIB_NAME := libsgx_tcrypto_sgxssl.a

else

CPPFLAGS += -I$(SGX_IPP_INC)
OBJ += sgx_ecc256_common.o
SRCDIR := ipp

LIB_NAME := libsgx_tcrypto_ipp.a

endif #!($(USE_OPT_LIBS), 1)

OBJ := $(addprefix $(SRCDIR)/, $(OBJ))
LIB_NAME := $(addprefix $(SRCDIR)/, $(LIB_NAME))
TARGET := libsgx_tcrypto.a

ifneq ($(USE_OPT_LIBS), 1)

$(TARGET): PREPRARE_SGX_SSL 
	$(MAKE) $(OBJ) 
	$(MAKE) $(SHARED_OBJ)
	$(MKDIR) $(BUILD_DIR)/.libs
	$(RM) $(BUILD_DIR)/.libs/*
	cd $(BUILD_DIR)/.libs && \
	$(AR) x $(OPENSSL_LIBRARY_PATH)/lib$(OpenSSL_Crypto_Library_Name).a && \
	$(AR) x $(OPENSSL_LIBRARY_PATH)/lib$(SGXSSL_Library_Name).a 
	$(AR) rsD $(LIB_NAME) $(OBJ) $(SHARED_OBJ) $(BUILD_DIR)/.libs/*.o
	$(CP) $(LIB_NAME) $@
	$(RM) -r $(BUILD_DIR)/.libs 
else

$(TARGET): $(OBJ) $(SHARED_OBJ)
	$(MKDIR) $(BUILD_DIR)/.libippcore
	$(RM) $(BUILD_DIR)/.libippcore/*
	cd $(BUILD_DIR)/.libippcore && $(AR) x $(IPP_LIBS_DIR)/libippcore.a
	@$(CP) $(IPP_LIBS_DIR)/libippcp.a $(LIB_NAME)
	$(AR) rsD $(LIB_NAME) $(OBJ) $(SHARED_OBJ) $(BUILD_DIR)/.libippcore/*.o
	$(CP) $(LIB_NAME) $@
	$(RM) -r $(BUILD_DIR)/.libippcore

endif #!($(USE_OPT_LIBS), 1)

$(SRCDIR)/%.o: $(SRCDIR)/%.cpp
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) $< -o $@

$(SHARED_OBJ): %.o: %.cpp
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) $< -o $@

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: all
all: $(TARGET) | $(BUILD_DIR)
	@$(CP) $< $|

.PHONY: clean
clean:
	@$(RM) *.o ipp/*.o sgxssl/*.o $(TARGET) $(BUILD_DIR)/$(TARGET) $(LIB_NAME)

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
