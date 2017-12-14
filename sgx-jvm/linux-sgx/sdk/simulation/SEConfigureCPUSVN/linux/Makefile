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

SIM_DIR := $(CUR_DIR)/../..

CXXFLAGS += -Werror
CPPFLAGS := -I$(COMMON_DIR)/inc           \
            -I$(COMMON_DIR)/inc/internal  \
            -I$(SIM_DIR)/urtssim          \
            -I$(SIM_DIR)/tinst

SRCS := $(wildcard ./*.cpp)
SRCS += $(SIM_DIR)/urtssim/cpusvn_util.cpp

OBJS := $(sort $(SRCS:.cpp=.o))

WRAPPER_LIB_DIR := $(COMMON_DIR)/se_wrapper
LDFLAGS := -L$(WRAPPER_LIB_DIR)
CXXFLAGS += -fpie
LDFLAGS += -pie $(COMMON_LDFLAGS)
LDLIBS  := -lwrapper

TARGET := sgx_config_cpusvn

.PHONY: all
all: $(TARGET) | $(BUILD_DIR)
	@$(CP) $< $|

$(TARGET): $(OBJS) $(LDLIBS)
	$(CXX) $(CXXFLAGS) -o $@ $^ $(LDFLAGS) $(LDLIBS)

$(OBJS): %.o: %.cpp
	$(CXX) $(CXXFLAGS) $(CPPFLAGS) -c $< -o $@

$(LDLIBS):
	$(MAKE) -C $(WRAPPER_LIB_DIR)

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	$(MAKE) -C $(WRAPPER_LIB_DIR) clean
	@$(RM) $(TARGET) $(BUILD_DIR)/$(TARGET) $(OBJS)
