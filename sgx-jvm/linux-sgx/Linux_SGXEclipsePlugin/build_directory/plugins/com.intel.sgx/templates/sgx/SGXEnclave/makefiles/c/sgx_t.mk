######## Intel(R) SGX SDK Settings ########
SGX_SDK ?= $(SdkPathFromPlugin)
SGX_MODE ?= SIM
SGX_ARCH ?= x64

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

ifneq ($(SGX_MODE), HW)
	Trts_Library_Name := sgx_trts_sim
	Service_Library_Name := sgx_tservice_sim
else
	Trts_Library_Name := sgx_trts
	Service_Library_Name := sgx_tservice
endif

Crypto_Library_Name := sgx_tcrypto

$(EnclaveName)_C_Files := trusted/$(enclaveName).c 
$(EnclaveName)_Include_Paths := -IInclude -Itrusted -I$(SGX_SDK)/include -I$(SGX_SDK)/include/tlibc -I$(SGX_SDK)/include/libcxx

Flags_Just_For_C := -Wno-implicit-function-declaration -std=c11
Common_C_Cpp_Flags := $(SGX_COMMON_CFLAGS) -nostdinc -fvisibility=hidden -fpie -fstack-protector $($(EnclaveName)_Include_Paths) -fno-builtin-printf -I.
$(EnclaveName)_C_Flags := $(Flags_Just_For_C) $(Common_C_Cpp_Flags)

$(EnclaveName)_Link_Flags := $(SGX_COMMON_CFLAGS) -Wl,--no-undefined -nostdlib -nodefaultlibs -nostartfiles -L$(SGX_LIBRARY_PATH) \
	-Wl,--whole-archive -l$(Trts_Library_Name) -Wl,--no-whole-archive \
	-Wl,--start-group -lsgx_tstdc -lsgx_tcxx -l$(Crypto_Library_Name) -l$(Service_Library_Name) -Wl,--end-group \
	-Wl,-Bstatic -Wl,-Bsymbolic -Wl,--no-undefined \
	-Wl,-pie,-eenclave_entry -Wl,--export-dynamic  \
	-Wl,--defsym,__ImageBase=0 \
	-Wl,--version-script=trusted/$(enclaveName).lds

$(EnclaveName)_C_Objects := $($(EnclaveName)_C_Files:.c=.o)

ifeq ($(SGX_MODE), HW)
ifneq ($(SGX_DEBUG), 1)
ifneq ($(SGX_PRERELEASE), 1)
Build_Mode = HW_RELEASE
endif
endif
endif


.PHONY: all run

ifeq ($(Build_Mode), HW_RELEASE)
all: $(enclaveName).so
	@echo "Build enclave $(enclaveName).so [$(Build_Mode)|$(SGX_ARCH)] success!"
	@echo
	@echo "*********************************************************************************************************************************************************"
	@echo "PLEASE NOTE: In this mode, please sign the $(enclaveName).so first using Two Step Sign mechanism before you run the app to launch and access the enclave."
	@echo "*********************************************************************************************************************************************************"
	@echo
else
all: $(enclaveName).signed.so
endif

run: all
ifneq ($(Build_Mode), HW_RELEASE)
	@$(CURDIR)/app
	@echo "RUN  =>  app [$(SGX_MODE)|$(SGX_ARCH), OK]"
endif


######## $(enclaveName) Objects ########

trusted/$(enclaveName)_t.c: $(SGX_EDGER8R) ./trusted/$(enclaveName).edl
	@cd ./trusted && $(SGX_EDGER8R) --trusted ../trusted/$(enclaveName).edl --search-path ../trusted --search-path $(SGX_SDK)/include
	@echo "GEN  =>  $@"

trusted/$(enclaveName)_t.o: ./trusted/$(enclaveName)_t.c
	@$(CC) $($(EnclaveName)_C_Flags) -c $< -o $@
	@echo "CC   <=  $<"

trusted/%.o: trusted/%.c
	@$(CC) $($(EnclaveName)_C_Flags) -c $< -o $@
	@echo "CC  <=  $<"

$(enclaveName).so: trusted/$(enclaveName)_t.o $($(EnclaveName)_C_Objects)
	@$(CXX) $^ -o $@ $($(EnclaveName)_Link_Flags)
	@echo "LINK =>  $@"

$(enclaveName).signed.so: $(enclaveName).so
	@$(SGX_ENCLAVE_SIGNER) sign -key trusted/$(enclaveName)_private.pem -enclave $(enclaveName).so -out $@ -config trusted/$(enclaveName).config.xml
	@echo "SIGN =>  $@"
clean:
	@rm -f $(enclaveName).* trusted/$(enclaveName)_t.*  $($(EnclaveName)_C_Objects)
