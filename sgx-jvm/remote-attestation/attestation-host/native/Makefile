.PHONY: all clean

# === GENERAL PARAMETERS ==========================================================================

SHELL                    = /bin/bash
MAKEFILE_DIR            := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

MODE                    ?= DEBUG # or RELEASE

GRADLE_FILE              = $(MAKEFILE_DIR)/../build.gradle
NAME                     = corda_sgx_ra
VERSION                 := $(shell sed -n "s/^version = '\([^']*\)'.*/\\1/p" $(GRADLE_FILE))
PLATFORM                := $(shell uname -s | tr [:upper:] [:lower:])

OUT_DIR                  = $(MAKEFILE_DIR)/build
OBJ_DIR                  = $(MAKEFILE_DIR)/obj

HOST_LIBRARY             = $(OUT_DIR)/lib$(NAME).so

# === BUILD PARAMETERS ============================================================================

ifeq ($(PLATFORM),linux)
        JDK_HOME        ?= $(dir $(word 1,$(wildcard /usr/lib/jvm/*/)))
        JDK_INC_DIRS     = -I$(JDK_HOME)include -I$(JDK_HOME)include/linux
endif

LOGGING_DEFS             =
ifeq ($(LOGGING),TRUE)
        LOGGING_DEFS     = -DLOGGING
endif

CPP                      = g++

CPPFLAGS_BASE            = $(JDK_INC_DIRS) -Wall -fPIC \
                           $(SGX_DEFS) $(LOGGING_DEFS)
CPPFLAGS_DEBUG           = $(CPPFLAGS_BASE) -g -O0 -DDEBUG
CPPFLAGS_RELEASE         = $(CPPFLAGS_BASE) -s -DNDEBUG

LDFLAGS_BASE             = \
                           -shared \
                           -Wl,-soname,lib$(NAME).so \
                           -Wl,-rpath,$(SGX_LIB_DIR):../../linux-sgx/build/linux \
                           -Wl,-z,defs \
                           -Wl,--no-as-needed \
                           -L$(SGX_LIB_DIR) \
                           -l$(URTS_LIB) \
                           -l$(CAPABLE_LIB) \
                           -l$(UAE_SERVICE_LIB) \
                           -lpthread
LDFLAGS_DEBUG            = $(LDFLAGS_BASE)
LDFLAGS_RELEASE          = $(LDFLAGS_BASE) -s

# === SGX-SPECIFIC BUILD PARAMETERS ===============================================================

SGX_SDK                 := $(MAKEFILE_DIR)/../../../linux-sgx
include                    $(MAKEFILE_DIR)/../../enclave/sgx.mk

# === MODE-SPECIFIC BUILD PARAMETERS ==============================================================

ifeq ($(subst release,RELEASE,$(MODE)),RELEASE)
        CPPFLAGS         = $(CPPFLAGS_RELEASE) $(SGX_CPPFLAGS_RELEASE)
        LDFLAGS          = $(LDFLAGS_RELEASE)
else
        CPPFLAGS         = $(CPPFLAGS_DEBUG) $(SGX_CPPFLAGS_DEBUG)
        LDFLAGS          = $(LDFLAGS_DEBUG)
endif

# === PSEUDO TARGETS ==============================================================================

all: $(HOST_LIBRARY)

clean:
	@rm -f $(JNI_HEADERS)
	@$(RM) -rf $(OUT_DIR)
	@$(RM) -rf $(OBJ_DIR)

# === HOST ========================================================================================

HOST_SOURCES             = $(wildcard *.cpp)
HOST_OBJECTS             = $(addprefix $(OBJ_DIR)/, $(HOST_SOURCES:.cpp=.o))

ENCLAVE_PROJECT          = $(MAKEFILE_DIR)/../../enclave
ENCLAVE_OBJECTS          = $(ENCLAVE_PROJECT)/obj/enclave_u.o
ENCLAVE_INC_DIR          = $(MAKEFILE_DIR)/../../enclave/rpc

JNI_HEADERS              = wrapper.hpp
JNI_SOURCES              = $(JNI_HEADERS:.hpp=.cpp)
JNI_OBJECTS              = $(addprefix $(OBJ_DIR)/, $(JNI_SOURCES:.cpp=.o))
CLASSPATH                = $(MAKEFILE_DIR)/../build/classes/kotlin/main

$(HOST_LIBRARY): $(HOST_OBJECTS) $(ENCLAVE_OBJECTS) $(JNI_OBJECTS) | $(OUT_DIR)
	$(CPP) $(LDFLAGS) -o $@ $^ \
		$(SGX_LIB_DIR)/lib$(UKEY_EXCHNG).a

$(HOST_OBJECTS): $(HOST_SOURCES) | $(OBJ_DIR)

$(ENCLAVE_OBJECTS):
	make -C $(ENCLAVE_PROJECT) obj-untrusted

$(JNI_OBJECTS): $(JNI_SOURCES) | $(JNI_HEADERS)

$(JNI_HEADERS): | $(JNI_SOURCES)
	javah -o $@ -cp $(CLASSPATH) net.corda.attestation.host.sgx.bridge.wrapper.NativeWrapper

$(OBJ_DIR)/%.o: %.cpp
	@mkdir -p $(@D)
	$(CPP) $(CPPFLAGS) -I$(ENCLAVE_INC_DIR) -o $@ -c $<

# === BUILD DIRECTORIES ===========================================================================

$(OUT_DIR):
	@mkdir -p $(OUT_DIR)

$(OBJ_DIR):
	@mkdir -p $(OBJ_DIR)
