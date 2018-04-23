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

TOP_DIR  = ../../..
include $(TOP_DIR)/buildenv.mk

INCLUDE += -I$(COMMON_DIR)/inc/internal \
           -I$(COMMON_DIR)/inc

CXXFLAGS += -fPIC -fno-rtti -Werror $(INCLUDE)

SRC := $(wildcard *.cpp)
OBJ := $(sort $(SRC:.cpp=.o))

EDGER8R_DIR = $(LINUX_SDK_DIR)/edger8r/linux
EDGER8R = $(EDGER8R_DIR)/_build/Edger8r.native

LIBNAME := libsgx_uprotected_fs.a

.PHONY: all
all: $(LIBNAME) | $(BUILD_DIR)
	$(CP) $< $|

$(LIBNAME): sgx_tprotected_fs_u.h $(OBJ)
	$(AR) rcsD $@ $(OBJ)
	
sgx_tprotected_fs_u.h: $(EDGER8R)
	$(EDGER8R) --header-only --untrusted $(COMMON_DIR)/inc/sgx_tprotected_fs.edl --search-path $(COMMON_DIR)/inc

$(EDGER8R):
	$(MAKE) -C $(EDGER8R_DIR)

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) $(OBJ)
	@$(RM) $(LIBNAME) $(BUILD_DIR)/$(LIBNAME)
	@$(RM) sgx_tprotected_fs_u.*
	$(MAKE) -C $(EDGER8R_DIR) clean

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
