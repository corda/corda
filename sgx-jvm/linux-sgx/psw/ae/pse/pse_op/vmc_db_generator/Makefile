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

TOP_DIR  = ../../../../..
include $(TOP_DIR)/buildenv.mk

INCLUDE += -I$(COMMON_DIR)/inc          \
           -I$(COMMON_DIR)/inc/internal \
           -I$(SGX_IPP_INC)

INCLUDE += -I$(LINUX_PSW_DIR)/ae/inc           \
           -I$(LINUX_PSW_DIR)/ae/inc/internal  \
           -I$(LINUX_PSW_DIR)/ae/pse/pse_op    \
           -I$(LINUX_EXTERNAL_DIR)/sqlite/src

CXXFLAGS += -fPIC

TCRYPTO_LIBDIR  := $(LINUX_SDK_DIR)/tlibcrypto
EXTERNAL_LIB += -L$(TCRYPTO_LIBDIR) -lsgx_tcrypto

vpath %.c  $(LINUX_EXTERNAL_DIR)/sqlite/src

SRC := main.cpp
OBJ := $(SRC:.cpp=.o)

SQLITESRC  := sqlite3.c
OBJ        += $(SQLITESRC:.c=.o)


APPNAME := vmc_db_generator

.PHONY: all
all: $(APPNAME)

sgx_tcrypto:
	$(MAKE) -C $(TCRYPTO_LIBDIR)

%.o :%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDE) -c $< -o $@

sqlite3.o :sqlite3.c
	$(CC) $(CFLAGS) $(INCLUDE) -c $< -o $@

$(APPNAME): $(OBJ) sgx_tcrypto
	$(CXX) $(CXXFLAGS) $(OBJ) $(LIBNAME) $(LDUFLAGS) $(EXTERNAL_LIB)   -o $@ -ldl -pthread

.PHONY: clean
clean:
	@$(RM) *.o
	@$(RM) $(APPNAME)

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all

