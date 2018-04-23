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

#Don't CFLAGS +=, because it depend on gdb is m32 or m64
CFLAGS :=

CPPFLAGS += -I$(COMMON_DIR)/inc/           \
            -I$(COMMON_DIR)/inc/internal/

CFLAGS += -W -Wall -Werror -D_GNU_SOURCE -fpic
ifeq ($(CC_BELOW_4_9), 1)
	CFLAGS += -fstack-protector
else
	CFLAGS += -fstack-protector-strong
endif
LDLIBS += -ldl

ifdef DEBUG
    CFLAGS += -g -DSE_DEBUG=1 -ffunction-sections -fdata-sections
else
    CFLAGS += -O2 -D_FORTIFY_SOURCE=2 -ffunction-sections -fdata-sections
endif

OBJ1 := se_memory.o se_trace.o
OBJ2 := se_ptrace.o ptrace_version.o

OBJS := $(OBJ1) $(OBJ2)


GDB_PLUGINS_FILES := gdb-sgx-plugin/sgx_emmt.py         \
                     gdb-sgx-plugin/gdb_sgx_cmd         \
                     gdb-sgx-plugin/gdb_sgx_plugin.py   \
                     gdb-sgx-plugin/load_symbol_cmd.py  \
                     gdb-sgx-plugin/readelf.py

GDB_OUT_DIR := $(BUILD_DIR)/gdb-sgx-plugin
TARGET      := libsgx_ptrace.so


.PHONY: all
all: $(TARGET) install

.PHONY:install
install: $(TARGET) | $(GDB_OUT_DIR) $(GDB_PLUGINS_FILES)
	$(CP) $(GDB_PLUGINS_FILES) $(GDB_OUT_DIR)
	$(CP) $(TARGET) $(BUILD_DIR)

$(TARGET): $(OBJS)
	$(CC) -shared -o $@ $^ $(LDLIBS) $(CFLAGS) $(COMMON_LDFLAGS) -Wl,--gc-sections

$(OBJ1): %.o: $(COMMON_DIR)/src/%.c
	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< -o $@

$(OBJ2): %.o: %.c
	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< -o $@

$(GDB_OUT_DIR):
	@$(MKDIR) $@

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) $(TARGET) $(OBJS) $(BUILD_DIR)/$(TARGET)
	@$(RM) -r $(GDB_OUT_DIR)
