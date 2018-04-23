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

$(EnclaveName)_Cpp_Files := static_trusted/$(enclaveName).cpp 
$(EnclaveName)_C_Files := 
$(EnclaveName)_Include_Paths := -IInclude -I$(EnclaveName) -I$(SGX_SDK)/include -I$(SGX_SDK)/include/tlibc -I$(SGX_SDK)/include/libcxx

Flags_Just_For_C := -Wno-implicit-function-declaration -std=c11
Common_C_Cpp_Flags := $(SGX_COMMON_CFLAGS) -nostdinc -fvisibility=hidden -fpie -fstack-protector $($(EnclaveName)_Include_Paths) -fno-builtin-printf -I.
$(EnclaveName)_C_Flags := $(Flags_Just_For_C) $(Common_C_Cpp_Flags)
$(EnclaveName)_Cpp_Flags := $(Common_C_Cpp_Flags) -std=c++11 -nostdinc++ -fno-builtin-printf  -I.

$(EnclaveName)_Cpp_Flags := $($(EnclaveName)_Cpp_Flags)  -fno-builtin-printf

$(EnclaveName)_Link_Flags := $(SGX_COMMON_CFLAGS) -Wl,--no-undefined -nostdlib -nodefaultlibs -nostartfiles -L$(SGX_LIBRARY_PATH) \
	-Wl,--whole-archive -l$(Trts_Library_Name) -Wl,--no-whole-archive \
	-Wl,--start-group -lsgx_tstdc -lsgx_tcxx -l$(Crypto_Library_Name) -l$(Service_Library_Name) -Wl,--end-group \
	-Wl,-Bstatic -Wl,-Bsymbolic -Wl,--no-undefined \
	-Wl,-pie,-eenclave_entry -Wl,--export-dynamic  \
	-Wl,--defsym,__ImageBase=0 \
	-Wl,--version-script=trusted/$(enclaveName).lds


$(EnclaveName)_Cpp_Objects := $($(EnclaveName)_Cpp_Files:.cpp=.o)
$(EnclaveName)_C_Objects := $($(EnclaveName)_C_Files:.c=.o)

ifeq ($(SGX_MODE), HW)
ifneq ($(SGX_DEBUG), 1)
ifneq ($(SGX_PRERELEASE), 1)
Build_Mode = HW_RELEASE
endif
endif
endif


.PHONY: all run

all: lib$(enclaveName).sgx.static.lib.a

######## $(enclaveName) Objects ########

static_trusted/$(enclaveName)_t.h: $(SGX_EDGER8R) ./static_trusted/$(enclaveName).edl
	@cd ./static_trusted && $(SGX_EDGER8R) --header-only  --trusted ../static_trusted/$(enclaveName).edl --search-path ../static_trusted --search-path $(SGX_SDK)/include
	@echo "GEN  =>  $@"

static_trusted/$(enclaveName)_t.o: ./trusted/$(enclaveName)_t.c
	@$(CC) $($(EnclaveName)_C_Flags) -c $< -o $@
	@echo "CC   <=  $<"

static_trusted/%.o: static_trusted/%.cpp
	@$(CXX) $($(EnclaveName)_Include_Paths) $($(EnclaveName)_Cpp_Flags) -c $< -o $@
	@echo "CXX  <=  $<"

static_trusted/%.o: static_trusted/%.c
	@$(CC) $($(EnclaveName)_C_Flags) -c $< -o $@
	@echo "CC  <=  $<"

lib$(enclaveName).sgx.static.lib.a: static_trusted/$(enclaveName)_t.h $($(EnclaveName)_Cpp_Objects) $($(EnclaveName)_C_Objects)
	ar rcs lib$(enclaveName).sgx.static.lib.a $($(EnclaveName)_Cpp_Objects) $($(EnclaveName)_C_Objects)  
	@echo "LINK =>  $@"

clean:
	@rm -f $(enclaveName).* static_trusted/$(enclaveName)_t.* $($(EnclaveName)_Cpp_Objects) $($(EnclaveName)_C_Objects)
