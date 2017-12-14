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

IPC_COMMON_DIR        := $(TOP_DIR)/psw/ae/common
IPC_COMMON_SRC_DIR    := $(IPC_COMMON_DIR)/src
IPC_COMMON_INC_DIR    := $(IPC_COMMON_DIR)/inc
IPC_COMMON_PROTO_DIR  := $(IPC_COMMON_DIR)/proto
UAE_WRAPPER_DIR        := ../uae_wrapper
UAE_SRC_DIR    := $(UAE_WRAPPER_DIR)/src
UAE_INC_DIR    := $(UAE_WRAPPER_DIR)/inc
AE_COMMON_DIR    := $(LINUX_PSW_DIR)/ae/common 

INCLUDE += -I.
INCLUDE += -I$(COMMON_DIR)              \
           -I$(COMMON_DIR)/inc          \
           -I$(COMMON_DIR)/inc/internal \

INCLUDE += -I$(LINUX_PSW_DIR)/ae/common \
           -I$(LINUX_PSW_DIR)/ae/inc    \
           -I$(LINUX_PSW_DIR)/ae/inc/internal    \
           -I$(LINUX_SDK_DIR)/tseal

INCLUDE += -I$(LINUX_EXTERNAL_DIR)/epid-sdk-3.0.0    \
           -I$(IPC_COMMON_INC_DIR) \
           -I$(UAE_INC_DIR) \
           -I$(IPC_COMMON_PROTO_DIR) \
           -I$(LINUX_PSW_DIR)/ae/aesm_service/source

CXXFLAGS += -fPIC -Werror -g -DPROTOBUF_INLINE_NOT_IN_HEADERS=0

EXTERNAL_LIB += -lprotobuf 
 
vpath %.cpp .. $(COMMON_DIR)/src  $(IPC_COMMON_SRC_DIR) $(IPC_COMMON_PROTO_DIR) $(UAE_SRC_DIR) $(AE_COMMON_DIR)
vpath %.c   $(COMMON_DIR)/src

C_SRC   := se_trace.c

IPC_SRC  := AECloseSessionRequest.cpp    \
             AEGetQuoteResponse.cpp \
             AECloseSessionResponse.cpp  \
             AEInitQuoteRequest.cpp \
             AECreateSessionRequest.cpp  \
             AEInitQuoteResponse.cpp \
             AECreateSessionResponse.cpp  \
             AEInvokeServiceRequest.cpp \
             AEExchangeReportRequest.cpp   \
             AEInvokeServiceResponse.cpp \
             AEExchangeReportResponse.cpp \
             AEGetPsCapRequest.cpp \
             AEGetPsCapResponse.cpp \
             AEReportAttestationRequest.cpp \
             AEReportAttestationResponse.cpp \
             ProtobufSerializer.cpp \
             AEGetLaunchTokenRequest.cpp  \
             AEGetWhiteListSizeRequest.cpp \
             AEGetWhiteListSizeResponse.cpp \
             AEGetWhiteListRequest.cpp \
             AEGetWhiteListResponse.cpp \
             AESGXGetExtendedEpidGroupIdRequest.cpp \
             AESGXGetExtendedEpidGroupIdResponse.cpp \
             AESGXSwitchExtendedEpidGroupRequest.cpp \
             AESGXSwitchExtendedEpidGroupResponse.cpp \
             SocketTransporter.cpp \
             AEGetLaunchTokenResponse.cpp  \
             UnixCommunicationSocket.cpp \
             AEGetQuoteRequest.cpp \
             UnixSocketFactory.cpp \
             NonBlockingUnixCommunicationSocket.cpp \
             NonBlockingUnixSocketFactory.cpp

PROTOBUF_SRC  :=  messages.pb.cc


SRC :=       AEServicesImpl.cpp \
             AEServicesProvider.cpp \
             tae_ocall_api.cpp \
             uae_api.cpp \
             se_sig_rl.cpp \
             sgx_uae_service.cpp \
             uae_service_version.cpp \
             uae_service_assert.cpp

OBJ := $(C_SRC:.c=.o) $(SRC:.cpp=.o) $(IPC_SRC:.cpp=.o)  $(PROTOBUF_SRC:.cc=.o) 

LDUFLAGS:= -pthread $(COMMON_LDFLAGS)
LDUFLAGS += -Wl,--version-script=uae_service.lds -Wl,--gc-sections

LIBNAME  = libsgx_uae_service.so
LIBNAME_DEBUG  = libsgx_uae_service.so.debug


.PHONY: all
all:  install_lib
 

.PHONY: install_lib
install_lib: $(LIBNAME) $(LIBNAME_DEBUG) | $(BUILD_DIR)
	@$(CP) $(LIBNAME) $|
ifndef DEBUG
	@$(CP) $(LIBNAME_DEBUG) $|
endif

$(LIBNAME): $(OBJ)
	$(CXX) $(CXXFLAGS) $(OBJ) -shared $(LDUFLAGS) $(EXTERNAL_LIB) -o $@
$(LIBNAME_DEBUG): $(LIBNAME)
	((test -f $(LIBNAME_DEBUG)) || $(MAKE) separate_debug_info)

.PHONY: separate_debug_info
separate_debug_info:
ifndef DEBUG
	$(CP) $(LIBNAME) $(LIBNAME).orig
	$(OBJCOPY) --only-keep-debug $(LIBNAME) $(LIBNAME_DEBUG)
	$(STRIP) -g $(LIBNAME)
	$(OBJCOPY) --add-gnu-debuglink=$(LIBNAME_DEBUG) $(LIBNAME)
endif

$(IPC_SRC:.cpp=.o) : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc
AEServicesImpl.o : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc

messages.pb.o : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc
	$(CXX) $(filter-out -Wshadow, $(CXXFLAGS)) -c $< -o $@
%.o :%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDE) -c $< -o $@
%.o: %.c
	$(CC) $(CFLAGS) $(INCLUDE) -Werror -fPIC -c $< -o $@


$(BUILD_DIR):
	@$(MKDIR) $@

$(IPC_COMMON_PROTO_DIR)/messages.pb.cc: $(IPC_COMMON_PROTO_DIR)/messages.proto 
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR) 

.PHONY: clean
clean:
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR) clean
	@$(RM) $(OBJ) $(LIBNAME).orig
	@$(RM) $(LIBNAME) $(BUILD_DIR)/$(LIBNAME) 
	@$(RM) $(LIBNAME_DEBUG) $(BUILD_DIR)/$(LIBNAME_DEBUG)

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all
