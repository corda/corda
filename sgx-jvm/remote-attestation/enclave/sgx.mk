# === SGX-SPECIFIC BUILD PARAMETERS ===============================================================

SGX_LIB_DIR             := $(SGX_SDK)/build/linux
SGX_INC_DIR             := $(SGX_SDK)/common/inc

SGX_EDGER8R              = $(SGX_SDK)/build/linux/sgx_edger8r
SGX_SIGNER              := $(SGX_SDK)/build/linux/sgx_sign

SGX_MODE_NAME           := $(shell echo $(MODE) | tr [:upper:] [:lower:])
SGX_USE_HARDWARE        ?= FALSE
SGX_IS_PRERELEASE       ?= FALSE
SGX_DEBUG_MODE          ?= TRUE

SGX_CPPFLAGS_RELEASE     = -fvisibility=hidden -fpie -fstack-protector \
                           -I$(SGX_INC_DIR) \
                           -s -DNDEBUG

SGX_CPPFLAGS_DEBUG       = -fvisibility=hidden -fpie -fstack-protector \
                           -I$(SGX_INC_DIR)

ifeq ($(SGX_USE_HARDWARE),TRUE)
    URTS_LIB             = sgx_urts
    TRTS_LIB             = sgx_trts
    SGX_SERVICE_LIB      = sgx_tservice
    CAPABLE_LIB          = sgx_capable
    UAE_SERVICE_LIB      = sgx_uae_service
    UKEY_EXCHNG          = sgx_ukey_exchange
    SGX_SIM              = 0
    SGX_MODE             = HW
else
    URTS_LIB             = sgx_urts_sim
    TRTS_LIB             = sgx_trts_sim
    SGX_SERVICE_LIB      = sgx_tservice_sim
    CAPABLE_LIB          = sgx_capable
    UAE_SERVICE_LIB      = sgx_uae_service_sim
    UKEY_EXCHNG          = sgx_ukey_exchange
    SGX_SIM              = 1
    SGX_MODE             = SIM
endif

ifeq ($(SGX_DEBUG_MODE),TRUE)
    SGX_DEBUG            = 1
    SGX_DEBUG_FLAGS      = -DDEBUG -UNDEBUG -UEDEBUG
else
    SGX_DEBUG            = 0
    SGX_DEBUG_FLAGS      = -DNDEBUG -UEDEBUG -UDEBUG
endif

ifeq ($(SGX_IS_PRERELEASE),TRUE)
    SGX_PRERELEASE       = 1
    SGX_DEBUG            = 0
    SGX_DEBUG_FLAGS      = -DNDEBUG -DEDEBUG -UDEBUG
else
    SGX_PRERELEASE       = 0
endif

SGX_LIBS                 = -lsgx_tstdc -lsgx_tstdcxx -lsgx_tcrypto \
                           -lsgx_tkey_exchange \
                           -l$(SGX_SERVICE_LIB)

SGX_DEFS                 = -DSGX_SIM=$(SGX_SIM) \
                           -DSGX_MODE=$(SGX_MODE) \
                           -DSGX_DEBUG=$(SGX_DEBUG) \
                           -DSGX_PRERELEASE=$(SGX_PRERELEASE)

LINK_SCRIPT              = $(MAKEFILE_DIR)/enclave.lds

SGX_LDFLAGS_BASE         = -Wl,--no-undefined \
                           -nostdlib -nodefaultlibs -nostartfiles \
                           -L$(SGX_LIB_DIR) \
                           -Wl,--whole-archive \
                           -l$(TRTS_LIB) \
                           -Wl,--no-whole-archive \
                           -Wl,--start-group \
                               $(ENCLAVE) $(SGX_LIBS) \
                           -Wl,--end-group \
                           -Wl,-Bstatic -Wl,-Bsymbolic \
                           -Wl,-pie,-eenclave_entry \
                           -Wl,--export-dynamic \
                           -Wl,--defsym,__ImageBase=0 \
                           -Wl,--version-script=$(LINK_SCRIPT)

SGX_LDFLAGS_RELEASE      = $(SGX_LDFLAGS_BASE)
SGX_LDFLAGS_DEBUG        = $(SGX_LDFLAGS_BASE)
