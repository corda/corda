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

######## SGX SDK Settings ########

SGX_SDK ?= /opt/intel/sgxsdk
SGX_MODE ?= HW
SGX_ARCH ?= x64
SGX_DEBUG ?= 1

ifeq ($(shell getconf LONG_BIT), 32)
	SGX_ARCH := x86
else ifeq ($(findstring -m32, $(CXXFLAGS)), -m32)
	SGX_ARCH := x86
endif

ifeq ($(SGX_ARCH), x86)
	SGX_COMMON_CFLAGS := -m32
	SGX_LIBRARY_PATH := $(SGX_SDK)/lib
	SGX_ENCLAVE_SIGNER := $(SGX_SDK)/bin/x86/sgx_sign
	SGX_EDGER8R := $(SGX_SDK)/bin/x86/sgx_edger8r
else
	SGX_COMMON_CFLAGS := -m64
	SGX_LIBRARY_PATH := $(SGX_SDK)/lib64
	SGX_ENCLAVE_SIGNER := $(SGX_SDK)/bin/x64/sgx_sign
	SGX_EDGER8R := $(SGX_SDK)/bin/x64/sgx_edger8r
endif

ifeq ($(SGX_DEBUG), 1)
ifeq ($(SGX_PRERELEASE), 1)
$(error Cannot set SGX_DEBUG and SGX_PRERELEASE at the same time!!)
endif
endif

ifeq ($(SGX_DEBUG), 1)
	SGX_COMMON_CFLAGS += -O0 -g
else
	SGX_COMMON_CFLAGS += -O2
endif

######## Library Settings ########

Trust_Lib_Name := libLocalAttestation_Trusted.a
TrustLib_Cpp_Files := $(wildcard LocalAttestationCode/*.cpp)
TrustLib_Cpp_Objects := $(TrustLib_Cpp_Files:.cpp=.o)
TrustLib_Include_Paths := -I$(SGX_SDK)/include -I$(SGX_SDK)/include/tlibc -I$(SGX_SDK)/include/libcxx -I$(SGX_SDK)/include/epid -I./Include
TrustLib_Compile_Flags := $(SGX_COMMON_CFLAGS) -nostdinc -fvisibility=hidden -fpie -fstack-protector  $(TrustLib_Include_Paths)
TrustLib_Compile_Cxx_Flags :=  -std=c++11 -nostdinc++

UnTrustLib_Name := libLocalAttestation_unTrusted.a
UnTrustLib_Cpp_Files := $(wildcard Untrusted_LocalAttestation/*.cpp)
UnTrustLib_Cpp_Objects := $(UnTrustLib_Cpp_Files:.cpp=.o)
UnTrustLib_Include_Paths := -I$(SGX_SDK)/include -I$(SGX_SDK)/include/ippcp -I./Include -I./LocalAttestationCode
UnTrustLib_Compile_Flags := $(SGX_COMMON_CFLAGS) -fPIC -Wno-attributes -std=c++11 $(UnTrustLib_Include_Paths)

######## App Settings ########

ifneq ($(SGX_MODE), HW)
	Urts_Library_Name := sgx_urts_sim
else
	Urts_Library_Name := sgx_urts
endif

App_Cpp_Files := $(wildcard App/*.cpp)
App_Include_Paths := -I$(SGX_SDK)/include -I$(SGX_SDK)/include/ippcp -I./Include -I./LocalAttestationCode

App_Compile_Flags := $(SGX_COMMON_CFLAGS) -fPIC -Wno-attributes $(App_Include_Paths)
# Three configuration modes - Debug, prerelease, release
#   Debug - Macro DEBUG enabled.
#   Prerelease - Macro NDEBUG and EDEBUG enabled.
#   Release - Macro NDEBUG enabled.
ifeq ($(SGX_DEBUG), 1)
	App_Compile_Flags += -DDEBUG -UNDEBUG -UEDEBUG
else ifeq ($(SGX_PRERELEASE), 1)
	App_Compile_Flags += -DNDEBUG -DEDEBUG -UDEBUG
else
	App_Compile_Flags += -DNDEBUG -UEDEBUG -UDEBUG
endif

App_Link_Flags := $(SGX_COMMON_CFLAGS) -L$(SGX_LIBRARY_PATH) -l$(Urts_Library_Name) -L. -lpthread -lLocalAttestation_unTrusted 

ifneq ($(SGX_MODE), HW)
	App_Link_Flags += -lsgx_uae_service_sim
else
	App_Link_Flags += -lsgx_uae_service
endif

App_Cpp_Objects := $(App_Cpp_Files:.cpp=.o)
App_Name := app

######## Enclave Settings ########

Enclave1_Version_Script := Enclave1/Enclave1.lds
Enclave2_Version_Script := Enclave2/Enclave2.lds
Enclave3_Version_Script := Enclave3/Enclave3.lds

ifneq ($(SGX_MODE), HW)
	Trts_Library_Name := sgx_trts_sim
	Service_Library_Name := sgx_tservice_sim
else
	Trts_Library_Name := sgx_trts
	Service_Library_Name := sgx_tservice
endif
Crypto_Library_Name := sgx_tcrypto

Enclave_Cpp_Files_1 := $(wildcard Enclave1/*.cpp)
Enclave_Cpp_Files_2 := $(wildcard Enclave2/*.cpp)
Enclave_Cpp_Files_3 := $(wildcard Enclave3/*.cpp)
Enclave_Include_Paths := -I$(SGX_SDK)/include -I$(SGX_SDK)/include/tlibc -I$(SGX_SDK)/include/libcxx -I./LocalAttestationCode -I./Include

CC_BELOW_4_9 := $(shell expr "`$(CC) -dumpversion`" \< "4.9")
ifeq ($(CC_BELOW_4_9), 1)
	Enclave_Compile_Flags := $(SGX_COMMON_CFLAGS) -nostdinc -fvisibility=hidden -fpie -ffunction-sections -fdata-sections -fstack-protector
else
	Enclave_Compile_Flags := $(SGX_COMMON_CFLAGS) -nostdinc -fvisibility=hidden -fpie -ffunction-sections -fdata-sections -fstack-protector-strong
endif

Enclave_Compile_Flags += $(Enclave_Include_Paths)

# To generate a proper enclave, it is recommended to follow below guideline to link the trusted libraries:
#    1. Link sgx_trts with the `--whole-archive' and `--no-whole-archive' options,
#       so that the whole content of trts is included in the enclave.
#    2. For other libraries, you just need to pull the required symbols.
#       Use `--start-group' and `--end-group' to link these libraries.
# Do NOT move the libraries linked with `--start-group' and `--end-group' within `--whole-archive' and `--no-whole-archive' options.
# Otherwise, you may get some undesirable errors.
Common_Enclave_Link_Flags := $(SGX_COMMON_CFLAGS) -Wl,--no-undefined -nostdlib -nodefaultlibs -nostartfiles -L$(SGX_LIBRARY_PATH) \
	-Wl,--whole-archive -l$(Trts_Library_Name) -Wl,--no-whole-archive \
	-Wl,--start-group -lsgx_tstdc -lsgx_tcxx -l$(Crypto_Library_Name) -L. -lLocalAttestation_Trusted -l$(Service_Library_Name) -Wl,--end-group \
	-Wl,-Bstatic -Wl,-Bsymbolic -Wl,--no-undefined \
	-Wl,-pie,-eenclave_entry -Wl,--export-dynamic  \
	-Wl,--defsym,__ImageBase=0 -Wl,--gc-sections
Enclave1_Link_Flags := $(Common_Enclave_Link_Flags) -Wl,--version-script=$(Enclave1_Version_Script)
Enclave2_Link_Flags := $(Common_Enclave_Link_Flags) -Wl,--version-script=$(Enclave2_Version_Script)
Enclave3_Link_Flags := $(Common_Enclave_Link_Flags) -Wl,--version-script=$(Enclave3_Version_Script)

Enclave_Cpp_Objects_1 := $(Enclave_Cpp_Files_1:.cpp=.o)
Enclave_Cpp_Objects_2 := $(Enclave_Cpp_Files_2:.cpp=.o)
Enclave_Cpp_Objects_3 := $(Enclave_Cpp_Files_3:.cpp=.o)

Enclave_Name_1 := libenclave1.so
Enclave_Name_2 := libenclave2.so
Enclave_Name_3 := libenclave3.so

ifeq ($(SGX_MODE), HW)
ifeq ($(SGX_DEBUG), 1)
	Build_Mode = HW_DEBUG
else ifeq ($(SGX_PRERELEASE), 1)
	Build_Mode = HW_PRERELEASE
else
	Build_Mode = HW_RELEASE
endif
else
ifeq ($(SGX_DEBUG), 1)
	Build_Mode = SIM_DEBUG
else ifeq ($(SGX_PRERELEASE), 1)
	Build_Mode = SIM_PRERELEASE
else
	Build_Mode = SIM_RELEASE
endif
endif

ifeq ($(Build_Mode), HW_RELEASE)
all: .config_$(Build_Mode)_$(SGX_ARCH) $(Trust_Lib_Name) $(UnTrustLib_Name) Enclave1.so Enclave2.so Enclave3.so $(App_Name)
	@echo "The project has been built in release hardware mode."
	@echo "Please sign the enclaves (Enclave1.so, Enclave2.so, Enclave3.so) first with your signing keys before you run the $(App_Name) to launch and access the enclave."
	@echo "To sign the enclaves use the following commands:"
	@echo "   $(SGX_ENCLAVE_SIGNER) sign -key <key1> -enclave Enclave1.so -out <$(Enclave_Name_1)> -config Enclave1/Enclave1.config.xml"
	@echo "   $(SGX_ENCLAVE_SIGNER) sign -key <key2> -enclave Enclave2.so -out <$(Enclave_Name_2)> -config Enclave2/Enclave2.config.xml"
	@echo "   $(SGX_ENCLAVE_SIGNER) sign -key <key3> -enclave Enclave3.so -out <$(Enclave_Name_3)> -config Enclave3/Enclave3.config.xml"
	@echo "You can also sign the enclaves using an external signing tool."
	@echo "To build the project in simulation mode set SGX_MODE=SIM. To build the project in prerelease mode set SGX_PRERELEASE=1 and SGX_MODE=HW."
else
all: .config_$(Build_Mode)_$(SGX_ARCH) $(Trust_Lib_Name) $(UnTrustLib_Name) $(Enclave_Name_1) $(Enclave_Name_2) $(Enclave_Name_3) $(App_Name)
ifeq ($(Build_Mode), HW_DEBUG)
	@echo "The project has been built in debug hardware mode."
else ifeq ($(Build_Mode), SIM_DEBUG)
	@echo "The project has been built in debug simulation mode."
else ifeq ($(Build_Mode), HW_PRERELEASE)
	@echo "The project has been built in pre-release hardware mode."
else ifeq ($(Build_Mode), SIM_PRERELEASE)
	@echo "The project has been built in pre-release simulation mode."
else
	@echo "The project has been built in release simulation mode."
endif
endif

.config_$(Build_Mode)_$(SGX_ARCH):
	@rm -rf .config_* $(App_Name) *.so *.a App/*.o Enclave1/*.o Enclave1/*_t.* Enclave1/*_u.* Enclave2/*.o Enclave2/*_t.* Enclave2/*_u.* Enclave3/*.o Enclave3/*_t.* Enclave3/*_u.*              LocalAttestationCode/*.o Untrusted_LocalAttestation/*.o LocalAttestationCode/*_t.* 
	@touch .config_$(Build_Mode)_$(SGX_ARCH)

######## Library Objects ########

LocalAttestationCode/LocalAttestationCode_t.c LocalAttestationCode/LocalAttestationCode_t.h : $(SGX_EDGER8R) LocalAttestationCode/LocalAttestationCode.edl
	@cd LocalAttestationCode && $(SGX_EDGER8R) --trusted ../LocalAttestationCode/LocalAttestationCode.edl --search-path $(SGX_SDK)/include 
	@echo "GEN  =>  $@"

LocalAttestationCode/LocalAttestationCode_t.o: LocalAttestationCode/LocalAttestationCode_t.c
	@$(CC) $(TrustLib_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

LocalAttestationCode/%.o: LocalAttestationCode/%.cpp LocalAttestationCode/LocalAttestationCode_t.h
	@$(CXX) $(TrustLib_Compile_Flags) $(TrustLib_Compile_Cxx_Flags) -c $< -o $@
	@echo "CC   <= $<"

$(Trust_Lib_Name): LocalAttestationCode/LocalAttestationCode_t.o $(TrustLib_Cpp_Objects)
	@$(AR) rcs $@ $^
	@echo "GEN  =>  $@"

Untrusted_LocalAttestation/%.o: Untrusted_LocalAttestation/%.cpp
	@$(CXX) $(UnTrustLib_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

$(UnTrustLib_Name): $(UnTrustLib_Cpp_Objects)
	@$(AR) rcs $@ $^
	@echo "GEN  =>  $@"

######## App Objects ########
Enclave1/Enclave1_u.c Enclave1/Enclave1_u.h: $(SGX_EDGER8R) Enclave1/Enclave1.edl
	@cd Enclave1 && $(SGX_EDGER8R) --use-prefix --untrusted ../Enclave1/Enclave1.edl --search-path $(SGX_SDK)/include 
	@echo "GEN  =>  $@"

App/Enclave1_u.o: Enclave1/Enclave1_u.c
	@$(CC) $(App_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

Enclave2/Enclave2_u.c Enclave2/Enclave2_u.h: $(SGX_EDGER8R) Enclave2/Enclave2.edl
	@cd Enclave2 && $(SGX_EDGER8R) --use-prefix --untrusted ../Enclave2/Enclave2.edl --search-path $(SGX_SDK)/include 
	@echo "GEN  =>  $@"

App/Enclave2_u.o: Enclave2/Enclave2_u.c
	@$(CC) $(App_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

Enclave3/Enclave3_u.c Enclave3/Enclave3_u.h: $(SGX_EDGER8R) Enclave3/Enclave3.edl
	@cd Enclave3 && $(SGX_EDGER8R) --use-prefix --untrusted ../Enclave3/Enclave3.edl --search-path $(SGX_SDK)/include 
	@echo "GEN  =>  $@"

App/Enclave3_u.o: Enclave3/Enclave3_u.c
	@$(CC) $(App_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

App/%.o: App/%.cpp Enclave1/Enclave1_u.h Enclave2/Enclave2_u.h Enclave3/Enclave3_u.h
	@$(CXX) $(App_Compile_Flags) -c $< -o $@
	@echo "CXX  <=  $<"

$(App_Name): App/Enclave1_u.o App/Enclave2_u.o App/Enclave3_u.o $(App_Cpp_Objects) $(UnTrustLib_Name)
	@$(CXX) $^ -o $@ $(App_Link_Flags)
	@echo "LINK =>  $@"


######## Enclave Objects ########

Enclave1/Enclave1_t.c Enclave1/Enclave1_t.h: $(SGX_EDGER8R) Enclave1/Enclave1.edl
	@cd Enclave1 && $(SGX_EDGER8R) --use-prefix --trusted ../Enclave1/Enclave1.edl --search-path $(SGX_SDK)/include 
	@echo "GEN  =>  $@"

Enclave1/Enclave1_t.o: Enclave1/Enclave1_t.c
	@$(CC) $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

Enclave1/%.o: Enclave1/%.cpp Enclave1/Enclave1_t.h
	@$(CXX) -std=c++11 -nostdinc++ $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CXX  <=  $<"

Enclave1.so: Enclave1/Enclave1_t.o $(Enclave_Cpp_Objects_1) $(Trust_Lib_Name)
	@$(CXX) Enclave1/Enclave1_t.o $(Enclave_Cpp_Objects_1) -o $@ $(Enclave1_Link_Flags)
	@echo "LINK =>  $@"

$(Enclave_Name_1): Enclave1.so
	@$(SGX_ENCLAVE_SIGNER) sign -key Enclave1/Enclave1_private.pem -enclave Enclave1.so -out $@ -config Enclave1/Enclave1.config.xml
	@echo "SIGN =>  $@"

Enclave2/Enclave2_t.c: $(SGX_EDGER8R) Enclave2/Enclave2.edl
	@cd Enclave2 && $(SGX_EDGER8R)  --use-prefix --trusted ../Enclave2/Enclave2.edl --search-path $(SGX_SDK)/include
	@echo "GEN  =>  $@"

Enclave2/Enclave2_t.o: Enclave2/Enclave2_t.c
	@$(CC) $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

Enclave2/%.o: Enclave2/%.cpp
	@$(CXX) -std=c++11 -nostdinc++ $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CXX  <=  $<"

Enclave2.so: Enclave2/Enclave2_t.o $(Enclave_Cpp_Objects_2) $(Trust_Lib_Name)
	@$(CXX) Enclave2/Enclave2_t.o $(Enclave_Cpp_Objects_2) -o $@ $(Enclave2_Link_Flags)
	@echo "LINK =>  $@"

$(Enclave_Name_2): Enclave2.so
	@$(SGX_ENCLAVE_SIGNER) sign -key Enclave2/Enclave2_private.pem -enclave Enclave2.so -out $@ -config Enclave2/Enclave2.config.xml
	@echo "SIGN =>  $@"

Enclave3/Enclave3_t.c: $(SGX_EDGER8R) Enclave3/Enclave3.edl
	@cd Enclave3 && $(SGX_EDGER8R)  --use-prefix --trusted ../Enclave3/Enclave3.edl --search-path $(SGX_SDK)/include
	@echo "GEN  =>  $@"

Enclave3/Enclave3_t.o: Enclave3/Enclave3_t.c
	@$(CC) $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CC   <=  $<"

Enclave3/%.o: Enclave3/%.cpp
	@$(CXX) -std=c++11 -nostdinc++ $(Enclave_Compile_Flags) -c $< -o $@
	@echo "CXX  <=  $<"

Enclave3.so: Enclave3/Enclave3_t.o $(Enclave_Cpp_Objects_3) $(Trust_Lib_Name)
	@$(CXX) Enclave3/Enclave3_t.o $(Enclave_Cpp_Objects_3) -o $@ $(Enclave3_Link_Flags)
	@echo "LINK =>  $@"

$(Enclave_Name_3): Enclave3.so
	@$(SGX_ENCLAVE_SIGNER) sign -key Enclave3/Enclave3_private.pem -enclave Enclave3.so -out $@ -config Enclave3/Enclave3.config.xml
	@echo "SIGN =>  $@"

######## Clean ########
.PHONY: clean

clean:
	@rm -rf .config_* $(App_Name) *.so *.a App/*.o Enclave1/*.o Enclave1/*_t.* Enclave1/*_u.* Enclave2/*.o Enclave2/*_t.* Enclave2/*_u.* Enclave3/*.o Enclave3/*_t.* Enclave3/*_u.* LocalAttestationCode/*.o Untrusted_LocalAttestation/*.o LocalAttestationCode/*_t.*
