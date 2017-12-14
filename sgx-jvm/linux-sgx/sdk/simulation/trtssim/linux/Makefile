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

include ../../../../buildenv.mk

CPPFLAGS += -I$(COMMON_DIR)/inc/         \
            -I$(COMMON_DIR)/inc/tlibc/   \
            -I$(COMMON_DIR)/inc/internal \
            -I$(LINUX_SDK_DIR)/trts/

CFLAGS   += $(ENCLAVE_CFLAGS)
ASFLAGS  := -DSE_SIM -Werror $(CFLAGS)
CFLAGS   += -DSE_SIM -Werror -fasynchronous-unwind-tables
CXXFLAGS += -DSE_SIM -Werror $(ENCLAVE_CXXFLAGS)\
            -fno-exceptions -fno-rtti

SIM_DIR    := $(CUR_DIR)/../..
# The sources from tRTS - shared by both Sim/HW mode
TRTS_DIR   := $(LINUX_SDK_DIR)/trts/
# The sources for SE0 instruction simulation
TINST_DIR  := $(SIM_DIR)/tinst/
# The sources for low-level API used by instruction simulation
LOWLIB_DIR := $(SIM_DIR)/assembly/linux/
# The sources for TLS support
TLS_DIR    := $(SIM_DIR)/uinst/linux/
# The source for trusted loader
TLDR_DIR   := $(LINUX_SDK_DIR)/trts/linux/
# Support for platform dependent 'setcontext'
SC_DIR     := $(LINUX_SDK_DIR)/trts/linux/$(ARCH)/
# The source for xsave/xrstor
XSAVE_DIR     := $(COMMON_DIR)/src/linux/

TRTS1_OBJS  := init_enclave.o \
               trts.o         \
               trts_ecall.o   \
               trts_ocall.o   \
               trts_util.o    \
               trts_veh.o     \
               trts_xsave.o   \
               init_optimized_lib.o \
               trts_emodpr.o  \
               trts_add_trim.o
TRTS2_OBJS  := trts_nsp.o
TRTS_OBJS := $(TRTS1_OBJS) $(TRTS2_OBJS)

TINST_OBJS   := t_instructions.o \
                deriv.o

LOWLIB_OBJS  := lowlib.o

TLS_OBJS     := get_tcs.o \
                restore_tls.o

TLDR_ASM_OBJS := trts_pic.o \
                metadata_sec.o \
                xsave_gnu.o

TLDR_C_OBJS  := elf_parser.o  \
                global_init.o \
                tls_support.o

TLDR_OBJS := $(TLDR_ASM_OBJS) $(TLDR_C_OBJS)

SC_OBJ := setcontext.o

LIBTRTS := libsgx_trts_sim.a

vpath %.cpp $(TRTS_DIR):$(TINST_DIR)
vpath %.S   $(LOWLIB_DIR):$(TLDR_DIR):$(SC_DIR):$(XSAVE_DIR)
vpath %.c   $(TLS_DIR):$(TLDR_DIR)

# ------------------------------------------------------------
.PHONY: all
all: $(LIBTRTS) | $(BUILD_DIR)
	$(CP) $< $|

$(LIBTRTS): $(TRTS_OBJS) $(TINST_OBJS) $(LOWLIB_OBJS) $(TLS_OBJS) $(TLDR_OBJS) $(SC_OBJ) 
	$(AR) rcsD $@ $(TRTS_OBJS) $(TINST_OBJS) $(LOWLIB_OBJS) $(TLS_OBJS) $(TLDR_OBJS) $(SC_OBJ)

# ------------------------------------------------------------
$(TRTS1_OBJS):    CPPFLAGS += -I$(COMMON_DIR)/inc/tlibc \
                             -I$(SIM_DIR)/tinst/
$(TRTS2_OBJS): %.o:%.cpp
	$(CXX) -c $(filter-out -fstack-protector-strong, $(CXXFLAGS)) -I$(SIM_DIR)/tinst/ $(CPPFLAGS) $< -o $@


# Explicitly disable optimization for tRTS simulation library,
# since the '_SE3' function has assumptions on stack layout.
# c.f. ../../tinst/Makefile
$(TINST_OBJS):   CPPFLAGS += -I$(SIM_DIR)/assembly/    \
                             -I$(SIM_DIR)/assembly/linux \
                             -I$(SIM_DIR)/uinst/       \
                             -I$(LINUX_SDK_DIR)/selib/ \
                             -O0

$(TLS_OBJS):     CPPFLAGS += -I$(SIM_DIR)/assembly/ \
                             -I$(SIM_DIR)/assembly/linux \
                             -I$(SIM_DIR)/uinst/

$(TLDR_C_OBJS):  CPPFLAGS += -I$(COMMON_DIR)/inc/internal/linux/ \
                             -I$(SIM_DIR)/assembly/

$(TLDR_ASM_OBJS): ASFLAGS += -I$(SIM_DIR)/assembly/

$(SC_OBJ):       ASFLAGS += -I$(LINUX_SDK_DIR)/cpprt/linux/libunwind/src/$(ARCH) -DISE=1

$(BUILD_DIR):
	$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) *.o $(LIBTRTS) $(BUILD_DIR)/$(LIBTRTS)

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
