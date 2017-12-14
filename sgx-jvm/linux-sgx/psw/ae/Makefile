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

CHECK_CURL := $(shell which curl-config)

ifeq ($(CHECK_CURL), )
    CURL_RESULT := check_fail
else
    CURL_RESULT :=
endif

ifneq ($(USE_OPT_LIBS), 1)
.PHONY: all
all:
	$(info ***NOTE***: SDK is not built based on prebuilt optimized libs. SKIP the build for aesm_service.)

else

.PHONY: all
all: $(CURL_RESULT) AESM COPY_AES CSE| $(BUILD_DIR)

endif #!($(USE_OPT_LIBS), 1)

# COPY_AES: currently copy le, qe, pve, pce, pse_op, pse_pr
.PHONY: COPY_AES
COPY_AES: | $(BUILD_DIR)
	$(CP) data/prebuilt/* $(BUILD_DIR)

.PHONY: AESM
AESM: | $(BUILD_DIR)
	$(MAKE) -C aesm_service
	$(CP) aesm_service/config/aesmd_service/aesmd.conf $(BUILD_DIR)
	$(CP) aesm_service/config/aesmd_service/aesmd.service $(BUILD_DIR)

.PHONY: CSE
CSE: | $(BUILD_DIR)
	$(MAKE) -C cse_provision_tool
	@$(CP) cse_provision_tool/cse_provision_tool $|

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean::
	$(MAKE) -C aesm_service/                   clean
	$(MAKE) -C cse_provision_tool              clean

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all

.PHONY: check_fail
check_fail:
	@echo ERROR: Please install libcurl by command such as
	@echo    apt-get install libcurl4-openssl-dev
	@echo or
	@echo    yum install libcurl-devel
	@echo with root account
	@exit 2
