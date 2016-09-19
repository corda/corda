#
# Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

IPC_COMMON_DIR        := ../common/
IPC_COMMON_SRC_DIR    := $(IPC_COMMON_DIR)/src
IPC_COMMON_INC_DIR    := $(IPC_COMMON_DIR)/inc
IPC_COMMON_PROTO_DIR  := source/protobuf
AESM_WRAPPER_DIR      := $(LINUX_PSW_DIR)/ae/aesm_service/source/aesm_wrapper
AESM_SRC_DIR    := $(AESM_WRAPPER_DIR)/src
AESM_INC_DIR    := $(AESM_WRAPPER_DIR)/inc
AESM_APPLICATION := $(LINUX_PSW_DIR)/ae/aesm_service/source/aesm/application
AESM_EXTENSION := $(LINUX_PSW_DIR)/ae/aesm_service/source/aesm/extension

INCLUDE += -I$(COMMON_DIR)/inc \
           -I$(COMMON_DIR)/inc/internal \
           -I./include         \
           -I./include/oal     \
           -I./source/         \
           -I./source/aesm_wrapper/inc 

INCLUDE += -I$(LINUX_SDK_DIR)/tseal

INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/aesm/application/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/aesm/extension/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/epid_provision/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/le/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/network/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/oal/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/protobuf/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pve/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/qe/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pce/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/storage/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/data/constants/linux 

INCLUDE += -I$(LINUX_PSW_DIR)/ae/inc                        \
           -I$(LINUX_PSW_DIR)/ae/inc/internal               \
           -I$(LINUX_PSW_DIR)/ae/common

INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/epid_provision 

INCLUDE += -I$(LINUX_EXTERNAL_DIR)/epid/inc                        

INCLUDE += -I$(LINUX_EXTERNAL_DIR)/crypto_px/include/              \
           -I$(LINUX_EXTERNAL_DIR)/                                                 \
           -I$(LINUX_EXTERNAL_DIR)/rdrand \
           -I$(IPC_COMMON_INC_DIR) \
           -I$(AESM_INC_DIR) \
           -I$(IPC_COMMON_PROTO_DIR)

EDGER8R  := $(LINUX_SDK_DIR)/edger8r/linux/_build/Edger8r.native

PROTPBUF_CXXFLAGS := -fstack-protector -fPIC -Wall -Werror

ifdef DEBUG
	PROTPBUF_CXXFLAGS += -ggdb -DDEBUG -UNDEBUG
else
	PROTPBUF_CXXFLAGS += -O2 -UDEBUG -DNDEBUG
endif
ifeq ($(ARCH), x86)
	PROTPBUF_CXXFLAGS += -m32
else
	PROTPBUF_CXXFLAGS += -m64
endif

ifdef PROFILE
        CXXFLAGS += -D_PROFILE_
        CFLAGS += -D_PROFILE_
endif

CXXFLAGS += -fPIC
CFLAGS := $(filter-out -fPIC -Werror, $(CFLAGS))
LINUX_SE_WRAPPER = $(COMMON_DIR)/se_wrapper

ifdef DEBUG 
CXXFLAGS += -DDBG_LOG
CFLAGS += -DDBG_LOG
endif

URTSLIB := -lurts_internal
TRTSLIB := -lsgx_trts
WHITE_LIST_FILE := $(LINUX_PSW_DIR)/ae/data/prebuilt/white_list_cert_to_be_verify.bin

TCRYPTO_LIBDIR  := $(LINUX_SDK_DIR)/tlibcrypto
VTUNE_LIBDIR := $(LINUX_EXTERNAL_DIR)/vtune/linux/sdk/src/ittnotify
RDRAND_LIBDIR :=$(LINUX_EXTERNAL_DIR)/rdrand/src
RDRAND_MAKEFILE := $(RDRAND_LIBDIR)/Makefile

ifeq ($(ARCH), x86)
    VTUNE_LIBDIR = $(LINUX_EXTERNAL_DIR)/vtune/linux/lib32
endif


EXTERNAL_LIB += -L$(TCRYPTO_LIBDIR) -lsgx_tcrypto
EXTERNAL_LIB += -lprotobuf -lrt
EXTERNAL_LIB += -lcrypto 
EXTERNAL_LIB += $(shell curl-config --libs)
EXTERNAL_LIB += -L$(LINUX_SE_WRAPPER) -lwrapper -lrt
EXTERNAL_LIB += -L$(RDRAND_LIBDIR) -lrdrand
EXTERNAL_LIB += -L$(VTUNE_LIBDIR) -littnotify

vpath %.cpp $(COMMON_DIR)/src ./source/epid_provision $(LINUX_PSW_DIR)/ae/common  $(AESM_APPLICATION) $(AESM_EXTENSION) $(AESM_SRC_DIR) $(IPC_COMMON_PROTO_DIR) $(COMMON_DIR)/src $(TOP_DIR)/sdk/tseal
vpath %.cc $(IPC_COMMON_PROTO_DIR)
vpath %.c $(TOP_DIR)/sdk/tlibc/string

COMMON_SRC := ./source/le/LEClass.cpp                  \
              ./source/oal/aesm_util.cpp                \
              ./source/storage/persistent_storage_table.cpp \
              aesm_logic.cpp  \
              ./source/oal/oal_power.cpp \
              aesm_rand.cpp \
              ipp_bn.cpp       \
              ipp_rsa_pub_key.cpp \
              ./source/oal/error_report.cpp \
              ./source/oal/internal_log.cpp \
              sgx_memset_s.cpp \
              sgx_read_rand.cpp \
              sgx_profile.cpp \
              tSeal_util.cpp \
              ./source/oal/aesm_thread.cpp \
              event_strings.cpp 

COMMON_SRC += ./source/qe/QEClass.cpp                  \
              ./source/pve/PVEClass.cpp                \
              ./source/pce/PCEClass.cpp                \
              aesm_encode.cpp              \
              aesm_epid_blob.cpp           \
              aesm_xegd_blob.cpp            \
              epid_provision_msg1.cpp \
              epid_provision_msg2.cpp \
              epid_provision_msg4.cpp \
              epid_endpoint_selection.cpp \
              epid_utility.cpp \
              type_length_value.cpp \
              ./source/qe/qe_logic.cpp \
              ./source/pve/pve_logic.cpp \
              platform_info_logic.cpp \
              platform_info_facility.cpp \
              pve_pub_key.cpp \
              pek_pub_key.cpp \
              ./source/network/network_encoding_wrapper.cpp \
              endpoint_select_info.cpp \
              aesm_ecdsa.cpp \
              se_sig_rl.cpp \
              aesm_long_lived_thread.cpp

COMMON_OBJ := $(COMMON_SRC:.cpp=.o) 

IPC_SRC  :=  $(IPC_COMMON_SRC_DIR)/AECloseSessionRequest.cpp    \
             $(IPC_COMMON_SRC_DIR)/AEGetQuoteResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AECloseSessionResponse.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEInitQuoteRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AECreateSessionRequest.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEInitQuoteResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AECreateSessionResponse.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEInvokeServiceRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AEExchangeReportRequest.cpp   \
             $(IPC_COMMON_SRC_DIR)/AEInvokeServiceResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AEExchangeReportResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/ProtobufSerializer.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetLaunchTokenRequest.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEGetPsCapRequest.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEGetPsCapResponse.cpp  \
             $(IPC_COMMON_SRC_DIR)/AEGetWhiteListSizeRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetWhiteListSizeResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetWhiteListRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetWhiteListResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AESGXGetExtendedEpidGroupIdRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AESGXGetExtendedEpidGroupIdResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AESGXSwitchExtendedEpidGroupRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AESGXSwitchExtendedEpidGroupResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/AEReportAttestationRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/AEReportAttestationResponse.cpp \
             $(IPC_COMMON_SRC_DIR)/SocketTransporter.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetLaunchTokenResponse.cpp  \
             $(IPC_COMMON_SRC_DIR)/UnixCommunicationSocket.cpp \
             $(IPC_COMMON_SRC_DIR)/NonBlockingUnixCommunicationSocket.cpp \
             $(IPC_COMMON_SRC_DIR)/AEGetQuoteRequest.cpp \
             $(IPC_COMMON_SRC_DIR)/UnixSocketFactory.cpp \
             $(IPC_COMMON_SRC_DIR)/NonBlockingUnixSocketFactory.cpp

PROTOBUF_SRC  := messages.pb.cc

AESM_SRC  := AESMLogicWrapper.cpp  \
             CAESMServer.cpp   \
             main.cpp \
             Thread.cpp   \
             AESMWorkerThread.cpp   \
             AESMQueueManager.cpp  \
             CSelector.cpp    \
             UnixServerSocket.cpp \
             ./source/oal/aesm_util.cpp \
             aesm_config.cpp 

AESM_SRC  += aesm_http_msg.cpp 

CSRC       := $(wildcard *.c)

MEMCMPSRC  := consttime_memequal.c

OBJ        := $(IPC_SRC:.cpp=.o) \
              $(CSRC:.c=.o) \
              $(MEMCMPSRC:.c=.o) \
              $(AESM_SRC:.cpp=.o) \
              $(PROTOBUF_SRC:.cc=.o)

LDUFLAGS := -pthread -L$(BUILD_DIR) $(URTSLIB)

APPNAME           := aesm_service
LIBNAME           := libaesm_service_common.a

.PHONY: all
all: $(APPNAME) copy_data_file | $(BUILD_DIR)
	@$(CP) $< $|

copy_data_file:
	@$(MKDIR) -p data
	@$(CP) $(WHITE_LIST_FILE) data/white_list_cert_to_be_verify.bin


sgx_tcrypto:
	$(MAKE) -C $(TCRYPTO_LIBDIR)

$(LIBNAME): $(COMMON_OBJ)
	$(AR) rcs $@ $^
	
%.o :%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDE) -c $< -o $@
%.o :%.cc
	$(CXX) $(CXXFLAGS) $(INCLUDE) -c $< -o $@

consttime_memequal.o :consttime_memequal.c
	$(CC) $(filter-out -O2,$(CFLAGS)) -fPIC -O1 $(INCLUDE) -c $< -o $@

$(APPNAME): $(OBJ) $(LIBNAME) sgx_tcrypto -lrdrand urts
	$(CXX) $(CXXFLAGS) $(OBJ) $(LIBNAME) $(LDUFLAGS) $(EXTERNAL_LIB)   -o $@ -ldl
$(IPC_SRC): PROTPBUF

./source/le/LEClass.o: ${AESM_EXTENSION}/launch_enclave_u.c
./source/pve/PVEClass.o: ${AESM_EXTENSION}/provision_enclave_u.c
./source/qe/QEClass.o: ${AESM_EXTENSION}/quoting_enclave_u.c
./source/pce/PCEClass.o: ${AESM_EXTENSION}/pce_u.c
uecall_bridge.o: ${AESM_EXTENSION}/pse_pr_u.c

-lrdrand: $(RDRAND_MAKEFILE)
	$(MAKE) -C $(RDRAND_LIBDIR)

$(RDRAND_MAKEFILE):
ifeq ($(ARCH), x86)
	@cd $(RDRAND_LIBDIR);./configure
else
	@cd $(RDRAND_LIBDIR);./configure CFLAGS=-fPIC
endif

${AESM_EXTENSION}/launch_enclave_u.c: ${LINUX_PSW_DIR}/ae/le/launch_enclave.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/provision_enclave_u.c: ${LINUX_PSW_DIR}/ae/pve/provision_enclave.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/quoting_enclave_u.c: ${LINUX_PSW_DIR}/ae/qe/quoting_enclave.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/pse_pr_u.c:  ${LINUX_PSW_DIR}/ae/pse/pse_pr/pse_pr.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/pce_u.c:  ${LINUX_PSW_DIR}/ae/pce/pce.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<
.PHONY: PROTPBUF
PROTPBUF:
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR)
	(test -f messages.pb.o) || ($(CXX) $(PROTPBUF_CXXFLAGS) -c $(IPC_COMMON_PROTO_DIR)/messages.pb.cc -o messages.pb.o)

.PHONY: urts
urts:
	$(MAKE) -C $(LINUX_PSW_DIR)/urts/linux

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) *.o $(IPC_COMMON_SRC_DIR)/*.o
	@$(RM) -r data
	@$(RM) $(APPNAME)
	@$(RM) $(LIBNAME)
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR) clean
	$(MAKE) -C $(LINUX_PSW_DIR)/urts/linux clean
	@$(RM) source/le/*.o
	@$(RM) source/pve/*.o
	@$(RM) source/pce/*.o
	@$(RM) source/network/*.o
	@$(RM) source/oal/*.o
	@$(RM) source/qe/*.o
	@$(RM) source/storage/*.o
	@$(RM) $(AESM_EXTENSION)/*_u.c $(AESM_EXTENSION)/*_u.h
ifeq ($(RDRAND_MAKEFILE), $(wildcard $(RDRAND_MAKEFILE)))
	@$(MAKE) distclean -C $(RDRAND_LIBDIR)
endif

.PHONY: rebuild
rebuild: clean all

