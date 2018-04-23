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
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pse_op/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pse_pr/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pve/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/qe/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/pce/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/storage/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/upse/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/aesm_service/source/upse/BackendInterface/
INCLUDE += -I$(LINUX_PSW_DIR)/ae/data/constants/linux 

INCLUDE += -I$(LINUX_PSW_DIR)/ae/inc                        \
           -I$(LINUX_PSW_DIR)/ae/inc/internal               \
           -I$(LINUX_PSW_DIR)/ae/common

INCLUDE += -I$(LINUX_PSW_DIR)/ae/pve                        \
           -I$(LINUX_PSW_DIR)/ae/pse/pse_op

INCLUDE += -I$(LINUX_EXTERNAL_DIR)/epid-sdk-3.0.0                  \
           -I$(LINUX_EXTERNAL_DIR)/sqlite/src                      \
           -I$(LINUX_EXTERNAL_DIR)/DALSdk/JHI/linux

INCLUDE += -I$(SGX_IPP_INC) \
           -I$(LINUX_EXTERNAL_DIR)/       \
           -I$(LINUX_EXTERNAL_DIR)/rdrand \
           -I$(IPC_COMMON_INC_DIR) \
           -I$(AESM_INC_DIR) \
           -I$(IPC_COMMON_PROTO_DIR)

EDGER8R  := $(LINUX_SDK_DIR)/edger8r/linux/_build/Edger8r.native


SQLITECFLAGS := -fpie -Wall -Wno-unused-const-variable -Werror
ifeq ($(CC_BELOW_4_9), 1)
	SQLITECFLAGS += -fstack-protector
else
	SQLITECFLAGS += -fstack-protector-strong
endif

ifdef DEBUG
	SQLITECFLAGS += -ggdb -DDEBUG -UNDEBUG
else
	SQLITECFLAGS += -O2 -UDEBUG -DNDEBUG -D_FORTIFY_SOURCE=2
endif
ifeq ($(ARCH), x86)
	SQLITECFLAGS += -m32
else
	SQLITECFLAGS += -m64
endif

ifdef PROFILE
        CXXFLAGS += -D_PROFILE_
        CFLAGS += -D_PROFILE_
endif

CXXFLAGS += -fpie
CFLAGS += -fpie
CFLAGS := $(filter-out -Werror, $(CFLAGS))
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

vpath %.cpp $(COMMON_DIR)/src ./source/epid_provision  ./source/upse ./source/upse/BackendInterface $(LINUX_PSW_DIR)/ae/common  $(AESM_APPLICATION) $(AESM_EXTENSION) $(AESM_SRC_DIR) $(IPC_COMMON_PROTO_DIR) $(TOP_DIR)/sdk/tseal $(IPC_COMMON_SRC_DIR) 
vpath %.cc $(IPC_COMMON_PROTO_DIR)
vpath %.c  $(LINUX_EXTERNAL_DIR)/sqlite/src
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
              ./source/pse_op/PSEClass.cpp             \
              ./source/pse_pr/PSEPRClass.cpp           \
              ./source/pse_op/PSDAService.cpp          \
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
              ./source/pse_op/pse_op_logic.cpp \
              ./source/pse_op/aesm_pse_status.cpp \
              platform_info_logic.cpp \
              platform_info_facility.cpp \
              pve_pub_key.cpp \
              pek_pub_key.cpp \
              ./source/network/network_encoding_wrapper.cpp \
              endpoint_select_info.cpp \
              aesm_ecdsa.cpp \
              ./source/pse_op/pse_op_psda_ocall.cpp \
              ./source/pse_op/pse_op_vmc_sqlite_ocall.cpp \
              upse.cpp  \
              upse_iclsInit.cpp \
              helper.cpp \
              sigma_helper.cpp \
              interface_ocsp.cpp \
              u_certificate_provisioning.cpp \
              uecall_bridge.cpp \
              u_long_term_pairing.cpp \
              CertificateProvisioningProtocol.cpp \
              pse_crypto_helper.cpp \
              pse_provisioning_msg1.cpp \
              pse_provisioning_msg2.cpp \
              pse_provisioning_msg3.cpp \
              pse_provisioning_msg4.cpp \
              interface_psda.cpp \
              pse_pr_common.cpp  \
              se_sig_rl.cpp \
              aesm_long_lived_thread.cpp

COMMON_OBJ := $(COMMON_SRC:.cpp=.o) 

IPC_SRC  :=  AECloseSessionRequest.cpp    \
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
             ProtobufSerializer.cpp \
             AEGetLaunchTokenRequest.cpp  \
             AEGetPsCapRequest.cpp  \
             AEGetPsCapResponse.cpp  \
             AEGetWhiteListSizeRequest.cpp \
             AEGetWhiteListSizeResponse.cpp \
             AEGetWhiteListRequest.cpp \
             AEGetWhiteListResponse.cpp \
             AESGXGetExtendedEpidGroupIdRequest.cpp \
             AESGXGetExtendedEpidGroupIdResponse.cpp \
             AESGXSwitchExtendedEpidGroupRequest.cpp \
             AESGXSwitchExtendedEpidGroupResponse.cpp \
             AEReportAttestationRequest.cpp \
             AEReportAttestationResponse.cpp \
             SocketTransporter.cpp \
             AEGetLaunchTokenResponse.cpp  \
             UnixCommunicationSocket.cpp \
             NonBlockingUnixCommunicationSocket.cpp \
             AEGetQuoteRequest.cpp \
             UnixSocketFactory.cpp \
             NonBlockingUnixSocketFactory.cpp

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
AESM_SRC  += source/pse_op/jhi_proxy.cpp

CSRC       := $(sort $(wildcard *.c))

MEMCMPSRC  := consttime_memequal.c

SQLITESRC  := sqlite3.c

OBJ        := $(IPC_SRC:.cpp=.o) \
              $(CSRC:.c=.o) \
              $(MEMCMPSRC:.c=.o) \
              $(AESM_SRC:.cpp=.o) \
              $(PROTOBUF_SRC:.cc=.o)

OBJ        += $(SQLITESRC:.c=.o)

LDUFLAGS := -pthread -L$(BUILD_DIR) $(URTSLIB) 
LDUFLAGS += -pie $(COMMON_LDFLAGS)

APPNAME           := aesm_service
APPNAME_DEBUG     := aesm_service.debug
LIBNAME           := libaesm_service_common.a

.PHONY: all
all: $(APPNAME) $(APPNAME_DEBUG) copy_data_file | $(BUILD_DIR)
	@$(CP) $(APPNAME)          $|
ifndef DEBUG
	@$(CP) $(APPNAME_DEBUG)    $|
endif

copy_data_file:
	@$(MKDIR) -p data
	@$(CP) $(LINUX_PSW_DIR)/ae/pse/pse_op/vmc_db_generator/prebuild_pse_vmc.db data
	@$(CP) $(WHITE_LIST_FILE) data/white_list_cert_to_be_verify.bin

sgx_tcrypto:
	$(MAKE) -C $(TCRYPTO_LIBDIR)

$(LIBNAME): $(COMMON_OBJ)
	$(AR) rcs $@ $^

$(IPC_SRC:.cpp=.o) : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc
CAESMServer.o : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc
%.o :%.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDE) -c $< -o $@

messages.pb.o : $(IPC_COMMON_PROTO_DIR)/messages.pb.cc
	$(CXX) $(filter-out -Wshadow, $(CXXFLAGS)) $(INCLUDE) -c $< -o $@

consttime_memequal.o :consttime_memequal.c
	$(CC) $(filter-out -O2,$(CFLAGS)) -O1 $(INCLUDE) -c $< -o $@

sqlite3.o :sqlite3.c
	$(CC) $(SQLITECFLAGS) $(INCLUDE) -c $< -o $@

$(APPNAME): $(OBJ) $(LIBNAME) sgx_tcrypto -lrdrand urts
	$(CXX) $(CXXFLAGS) $(OBJ) $(LIBNAME) $(LDUFLAGS) $(EXTERNAL_LIB)   -o $@ -ldl

$(APPNAME_DEBUG): $(APPNAME)
ifndef DEBUG
	$(CP) $(APPNAME) $(APPNAME).orig
	$(OBJCOPY) --only-keep-debug $(APPNAME) $(APPNAME_DEBUG)
	$(STRIP) -g $(APPNAME)
	$(OBJCOPY) --add-gnu-debuglink=$(APPNAME_DEBUG) $(APPNAME)
endif


./source/le/LEClass.o: ${AESM_EXTENSION}/launch_enclave_u.c
./source/pve/PVEClass.o: ${AESM_EXTENSION}/provision_enclave_u.c
./source/qe/QEClass.o: ${AESM_EXTENSION}/quoting_enclave_u.c
./source/pse_op/PSEClass.o: ${AESM_EXTENSION}/pse_op_u.c
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

${AESM_EXTENSION}/pse_op_u.c: ${LINUX_PSW_DIR}/ae/pse/pse_op/pse_op.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/pse_pr_u.c:  ${LINUX_PSW_DIR}/ae/pse/pse_pr/pse_pr.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

${AESM_EXTENSION}/pce_u.c:  ${LINUX_PSW_DIR}/ae/pce/pce.edl
	@$(EDGER8R) --untrusted --untrusted-dir ${AESM_EXTENSION} $<

$(IPC_COMMON_PROTO_DIR)/messages.pb.cc:
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR)

.PHONY: urts
urts:
	$(MAKE) -C $(LINUX_PSW_DIR)/urts/linux

$(BUILD_DIR):
	@$(MKDIR) $@

.PHONY: clean
clean:
	@$(RM) *.o $(IPC_COMMON_SRC_DIR)/*.o
	@$(RM) -r data
	@$(RM) $(APPNAME) $(APPNAME).orig $(APPNAME_DEBUG)
	@$(RM) $(BUILD_DIR)/$(APPNAME)  $(BUILD_DIR)/$(APPNAME_DEBUG)
	@$(RM) $(LIBNAME)
	$(MAKE) -C $(IPC_COMMON_PROTO_DIR) clean
	@$(RM) source/le/*.o
	@$(RM) source/pse_op/*.o
	@$(RM) source/pve/*.o
	@$(RM) source/pce/*.o
	@$(RM) source/network/*.o
	@$(RM) source/oal/*.o
	@$(RM) source/qe/*.o
	@$(RM) source/pse_pr/*.o
	@$(RM) source/storage/*.o
	@$(RM) $(AESM_EXTENSION)/*_u.c $(AESM_EXTENSION)/*_u.h
ifeq ($(RDRAND_MAKEFILE), $(wildcard $(RDRAND_MAKEFILE)))
	@$(MAKE) distclean -C $(RDRAND_LIBDIR)
endif

.PHONY: rebuild
rebuild:
	$(MAKE) clean
	$(MAKE) all

