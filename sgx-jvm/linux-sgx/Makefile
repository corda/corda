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

include buildenv.mk
.PHONY: all psw sdk clean rebuild sdk_install_pkg psw_install_pkg 

all: sdk psw

psw: sdk
	$(MAKE) -C psw/ USE_OPT_LIBS=$(USE_OPT_LIBS)

sdk:
	$(MAKE) -C sdk/ USE_OPT_LIBS=$(USE_OPT_LIBS)

# Generate SE SDK Install package
sdk_install_pkg: sdk
	./linux/installer/bin/build-installpkg.sh sdk

psw_install_pkg: psw
	./linux/installer/bin/build-installpkg.sh psw

clean:
	@$(MAKE) -C sdk/                                clean
	@$(MAKE) -C psw/                                clean
	@$(RM)   -r $(ROOT_DIR)/build
	@$(RM)   -r linux/installer/bin/sgx_linux*.bin
	@$(RM)   -rf linux/installer/common/psw/output
	@$(RM)   -rf linux/installer/common/psw/gen_source.py
	@$(RM)   -rf linux/installer/common/sdk/output
	@$(RM)   -rf linux/installer/common/sdk/pkgconfig/x64
	@$(RM)   -rf linux/installer/common/sdk/pkgconfig/x86
	@$(RM)   -rf linux/installer/common/sdk/gen_source.py

rebuild:
	$(MAKE) clean
	$(MAKE) all
