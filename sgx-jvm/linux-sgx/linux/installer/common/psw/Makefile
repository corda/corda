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

include scripts/installConfig

SOURCE_FOLDER=package
SCRIPTS_FOLDER=scripts

INSTALL_PATH=$(shell readlink -m $(DESTDIR)/$(SGX_PACKAGES_PATH)/$(PSW_PKG_NAME))
SOURCE_INSTALL_PATH=$(INSTALL_PATH)
SCRIPTS_INSTALL_PATH=$(INSTALL_PATH)/$(SCRIPTS_FOLDER)
PSW_LIB_PATH=$(SOURCE_INSTALL_PATH)/$(LIB_DIR)
USR_LIB_PATH=$(addprefix $(DESTDIR)/usr/, $(notdir $(shell gcc -print-multi-os-directory)))

default:

install:
	install -d $(SOURCE_INSTALL_PATH)
	install -d $(SCRIPTS_INSTALL_PATH)
	install -d $(USR_LIB_PATH)
	cp -r $(SOURCE_FOLDER)/* $(SOURCE_INSTALL_PATH)
	install $(SCRIPTS_FOLDER)/* $(SCRIPTS_INSTALL_PATH)
	mv $(PSW_LIB_PATH)/libsgx_uae_service.so $(USR_LIB_PATH)
	mv $(PSW_LIB_PATH)/libsgx_urts.so $(USR_LIB_PATH)
	rmdir $(PSW_LIB_PATH)
