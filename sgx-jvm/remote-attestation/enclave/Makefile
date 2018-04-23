.PHONY: info all clean \
        unsigned signed-openssl signed-hsm \
        obj-trusted obj-untrusted

# === GENERAL PARAMETERS ==========================================================================

SHELL                    = /bin/bash
MAKEFILE_DIR            := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

MODE                    ?= DEBUG

GRADLE_FILE              = $(MAKEFILE_DIR)/../host/build.gradle
NAME                     = corda_sgx_ra
VERSION                 := $(shell sed -n "s/^version = '\([^']*\)'.*/\\1/p" $(GRADLE_FILE))
PLATFORM                := $(shell uname -s | tr [:upper:] [:lower:])

OUT_DIR                  = $(MAKEFILE_DIR)/build
OBJ_DIR                  = $(MAKEFILE_DIR)/obj

ENCLAVE                  = $(OUT_DIR)/$(NAME)_enclave.a
ENCLAVE_SIGNED           = $(OUT_DIR)/$(NAME)_enclave.so
ENCLAVE_UNSIGNED         = $(OUT_DIR)/$(NAME)_enclave_unsigned.so
ENCLAVE_BLOB             = $(OUT_DIR)/$(NAME)_enclave_blob.bin

ENCLAVE_OPENSSL          = $(OUT_DIR)/$(NAME)_enclave_openssl.so
OPENSSL_PRIVATE_KEY      = $(MAKEFILE_DIR)/../../sign_helper/selfsigning.pem
OPENSSL_PUBLIC_KEY       = $(OUT_DIR)/selfsigning.public.pem
OPENSSL_SIGNATURE        = $(OUT_DIR)/$(NAME)_enclave.signature.openssl.sha256

ENCLAVE_HSM              = $(OUT_DIR)/$(NAME)_enclave_hsm.so
HSM_PROFILE             ?= dev_sim
HSM_PUBLIC_KEY           = $(OUT_DIR)/hsm.public.pem
HSM_SIGNATURE            = $(OUT_DIR)/$(NAME)_enclave.signature.hsm.sha256

HSM_BUILD_DIR            = $(MAKEFILE_DIR)/../../hsm-tool/build/libs/sgx-jvm
HSM_TOOL_VERSION         = 1.0-SNAPSHOT
HSM_TOOL                 = $(HSM_BUILD_DIR)/hsm-tool-$(HSM_TOOL_VERSION).jar

# === BUILD PARAMETERS ============================================================================

CPP                      = g++
GRADLE                   = $(MAKEFILE_DIR)/../../../gradlew

CPPFLAGS_BASE            = $(INC_DIRS) -Wall -fPIC
CPPFLAGS_DEBUG           = $(CPPFLAGS_BASE) -g
CPPFLAGS_RELEASE         = $(CPPFLAGS_BASE) -s -DNDEBUG

LDFLAGS_BASE             =
LDFLAGS_DEBUG            = $(LDFLAGS_BASE)
LDFLAGS_RELEASE          = $(LDFLAGS_BASE) -s

# === SGX-SPECIFIC BUILD PARAMETERS ===============================================================

SGX_SDK                 := $(MAKEFILE_DIR)/../../linux-sgx
include                    $(MAKEFILE_DIR)/sgx.mk

SGX_CONFIG               = $(MAKEFILE_DIR)/config/$(SGX_MODE_NAME).xml
RPC_DIR                  = $(MAKEFILE_DIR)/rpc

# === MODE-SPECIFIC BUILD PARAMETERS ==============================================================

ifeq ($(subst release,RELEASE,$(MODE)),RELEASE)
        CPPFLAGS         = $(CPPFLAGS_RELEASE) $(SGX_CPPFLAGS_RELEASE) -I$(RPC_DIR)
        LDFLAGS          = $(LDFLAGS_RELEASE) $(SGX_LDFLAGS_RELEASE)
else
        CPPFLAGS         = $(CPPFLAGS_DEBUG) $(SGX_CPPFLAGS_DEBUG) -I$(RPC_DIR)
        LDFLAGS          = $(LDFLAGS_DEBUG) $(SGX_LDFLAGS_DEBUG)
endif

# === ENCLAVE SOURCES AND OUTPUTS =================================================================

ENCLAVE_SOURCES          = $(wildcard *.cpp)
ENCLAVE_OBJECTS          = $(addprefix $(OBJ_DIR)/, $(ENCLAVE_SOURCES:.cpp=.o))

TGEN_SOURCES             = $(addprefix $(RPC_DIR)/, enclave_t.c)
TGEN_HEADERS             = $(TGEN_SOURCES:.c=.h)
TGEN_OBJECTS             = $(patsubst $(RPC_DIR)/%,$(OBJ_DIR)/%,$(TGEN_SOURCES:.c=.o))

UGEN_SOURCES             = $(addprefix $(RPC_DIR)/, enclave_u.c)
UGEN_HEADERS             = $(UGEN_SOURCES:.c=.h)
UGEN_OBJECTS             = $(patsubst $(RPC_DIR)/%,$(OBJ_DIR)/%,$(UGEN_SOURCES:.c=.o))

# === PSEUDO TARGETS ==============================================================================

info: # Show available targets
	@echo "Build Targets:"
	@sed -n 's/^\([a-z-]*\): .*# \(.*\)$$/    \1	- \2/p' Makefile | expand -t24 | sort
	@echo
	@echo "Configuration:"
	@echo "    HSM_PROFILE       = dev_sim (or dev_hsm, prod)"
	@echo "    MODE              = DEBUG (or RELEASE)"
	@echo "    SGX_DEBUG_MODE    = TRUE (or FALSE)"
	@echo "    SGX_IS_PRERELEASE = FALSE (or TRUE)"
	@echo "    SGX_USE_HARDWARE  = FALSE (or TRUE)"
	@echo

all: $(ENCLAVE) signed-openssl # Build enclave (self-signed using OpenSSL)

clean: # Clean build files
	@$(RM) -rf $(OUT_DIR)
	@$(RM) -rf $(OBJ_DIR)
	@$(RM) -rf $(RPC_DIR)

# === SIGNING =====================================================================================

mode: # Show state of SGX specific build variables
	@echo "SGX_MODE: $(SGX_MODE)"
	@echo "SGX_DEBUG: $(SGX_DEBUG)"
	@echo "SGX_PRERELEASE: $(SGX_PRERELEASE)"
	@echo "HSM_PROFILE: $(HSM_PROFILE)"

unsigned: $(ENCLAVE_BLOB) # Construct an unsigned enclave

signed-openssl: $(ENCLAVE_OPENSSL) # Construct a self-signed enclave using OpenSSL

signed-hsm: $(ENCLAVE_HSM) # Construct an enclave signed by an HSM
	mev_sim|dev_hsm|prod)

$(ENCLAVE_UNSIGNED): $(ENCLAVE)
	@echo "Using mode=$(MODE), config=$(SGX_CONFIG)"
	$(CPP) -o $(ENCLAVE_UNSIGNED) $(LDFLAGS)

$(ENCLAVE_BLOB): $(ENCLAVE_UNSIGNED)
	$(SGX_SIGNER) gendata \
		-enclave $(ENCLAVE_UNSIGNED) \
		-out $(ENCLAVE_BLOB) \
		-config $(SGX_CONFIG)

$(ENCLAVE_OPENSSL): $(ENCLAVE_BLOB) $(OPENSSL_PUBLIC_KEY) $(OPENSSL_SIGNATURE)
	$(SGX_SIGNER) catsig \
		-enclave $(ENCLAVE_UNSIGNED) \
		-key $(OPENSSL_PUBLIC_KEY) \
		-sig $(OPENSSL_SIGNATURE) \
		-unsigned $(ENCLAVE_BLOB) \
		-config $(SGX_CONFIG) \
		-out $(ENCLAVE_OPENSSL)
	@cp $(ENCLAVE_OPENSSL) $(ENCLAVE_SIGNED)

$(OPENSSL_PUBLIC_KEY): $(OPENSSL_PRIVATE_KEY)
	openssl rsa \
		-in $(OPENSSL_PRIVATE_KEY) \
		-pubout -out $(OPENSSL_PUBLIC_KEY)

$(OPENSSL_SIGNATURE): $(OPENSSL_PRIVATE_KEY) $(ENCLAVE_BLOB)
	openssl dgst \
		-sha256 \
		-sign $(OPENSSL_PRIVATE_KEY) \
		-out $(OPENSSL_SIGNATURE) \
		$(ENCLAVE_BLOB)

$(ENCLAVE_HSM): $(ENCLAVE_BLOB) $(HSM_PUBLIC_KEY) $(HSM_SIGNATURE)
	$(SGX_SIGNER) catsig \
		-enclave $(ENCLAVE_UNSIGNED) \
		-key $(HSM_PUBLIC_KEY) \
		-sig $(HSM_SIGNATURE) \
		-unsigned $(ENCLAVE_BLOB) \
		-config $(SGX_CONFIG) \
		-out $(ENCLAVE_HSM)
	@cp $(ENCLAVE_HSM) $(ENCLAVE_SIGNED)

$(HSM_PUBLIC_KEY) $(HSM_SIGNATURE): $(HSM_TOOL) $(ENCLAVE_BLOB)
	@echo "Using HSM profile=$(HSM_PROFILE)"
	java -jar $(HSM_TOOL) \
		--mode=Sign \
		--source=$(ENCLAVE_BLOB) \
		--pubkey=$(HSM_PUBLIC_KEY) \
		--signature=$(HSM_SIGNATURE) \
		--profile=$(HSM_PROFILE)

$(HSM_TOOL):
	$(GRADLE) sgx-hsm-tool:jar

# === ENCLAVE =====================================================================================

obj-trusted: $(TGEN_OBJECTS) # Object files for trusted zone

obj-untrusted: $(UGEN_OBJECTS) # Object files for untrusted zone

$(ENCLAVE): $(TGEN_OBJECTS) $(ENCLAVE_OBJECTS) | $(OUT_DIR)
	ar qc $@ $^
	ranlib $@

$(TGEN_SOURCES) $(TGEN_HEADERS) $(UGEN_SOURCES) $(UGEN_HEADERS): enclave.edl | $(RPC_DIR)
	$(SGX_EDGER8R) \
		--search-path $(SGX_INC_DIR) \
		--trusted-dir $(RPC_DIR) \
		--untrusted-dir $(RPC_DIR) \
		enclave.edl

$(ENCLAVE_OBJECTS): $(ENCLAVE_SOURCES) | $(OBJ_DIR)

$(TGEN_OBJECTS): $(TGEN_SOURCES)

$(UGEN_OBJECTS): $(UGEN_SOURCES)

$(OBJ_DIR)/%.o: $(RPC_DIR)/%.c
	@mkdir -p $(@D)
	$(CPP) $(CPPFLAGS) $(SGX_DEBUG_FLAGS) $(SGX_DEFS) -o $@ -c $<

$(OBJ_DIR)/%.o: %.cpp
	@mkdir -p $(@D)
	$(CPP) $(CPPFLAGS) $(SGX_DEBUG_FLAGS) $(SGX_DEFS) -o $@ -c $<

# === BUILD DIRECTORIES ===========================================================================

$(OUT_DIR):
	@mkdir -p $(OUT_DIR)

$(OBJ_DIR):
	@mkdir -p $(OBJ_DIR)

$(RPC_DIR):
	@mkdir -p $(RPC_DIR)
