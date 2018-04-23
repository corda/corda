#!/usr/bin/make -f

#define variables
IPP_SRC_INCLUDE_DIR = ../../ext/ipp/sources/ippcp/src/
IPP_INCLUDE_DIR = ../../ext/ipp/sources/include/
GTEST_INCLUDE_DIR = ../../ext/

LIB_INSTALL_DIR = $(epidinstalldir)/lib/posix-$(ARCH)/
TEST_INSTALL_DIR = $(epidinstalldir)/test/
HEADERS_INSTALL_DIR = $(epidinstalldir)/include/epid/verifier/
HEADERS_11_INSTALL_DIR = $(epidinstalldir)/include/epid/verifier/1.1/

API_HEADERS_INSTALL = ./api.h

API_11_HEADERS_INSTALL = ./1.1/api.h

VERIFIER_INCLUDE_DIR = ../../
VERIFIER_11_INCLUDE_DIR = ../../1.1

VERIFIER_SRC =  $(wildcard ./src/*.c)
VERIFIER_SRC += $(wildcard ./1.1/src/*.c)
VERIFIER_OBJ = $(sort $(VERIFIER_SRC:.c=.o))
VERIFIER_LIB = ./libverifier.a

VERIFIER_UTEST_EXE = ./verifier-utest
VERIFIER_UTEST_SRC = $(wildcard ./unittests/*.cc)
VERIFIER_UTEST_SRC += $(wildcard ./1.1/unittests/*.cc)
VERIFIER_UTEST_OBJ = $(sort $(VERIFIER_UTEST_SRC:.cc=.o))
LIB_VERIFIER_DIR = .
LIB_COMMON_TESTHELPER_DIR = ../common-testhelper
LIB_COMMON_DIR = ../common
LIB_IPPCP_DIR = ../../ext/ipp/sources/ippcp/src
LIB_IPPCPEPID_DIR = ../../ext/ipp/sources/ippcpepid/src

#set flags for linker
LDFLAGS += -L$(GTEST_INCLUDE_DIR)/gtest  -L$(LIB_COMMON_TESTHELPER_DIR) \
	-L$(LIB_VERIFIER_DIR) -L$(LIB_COMMON_DIR) -L$(LIB_IPPCPEPID_DIR) \
	-L$(LIB_IPPCP_DIR) -lgtest -lcommon-testhelper -lverifier -lcommon \
	-lippcpepid -lippcp

#target part
$(VERIFIER_OBJ): %.o: %.c
	$(CC) $(CFLAGS) -I$(IPP_INCLUDE_DIR) -I$(IPP_SRC_INCLUDE_DIR) \
	-I$(VERIFIER_INCLUDE_DIR) -I$(VERIFIER_11_INCLUDE_DIR) -c $^ -o $@

$(VERIFIER_LIB): $(VERIFIER_OBJ)
	$(AR) rc $(VERIFIER_LIB) $(VERIFIER_OBJ)
	ranlib $(VERIFIER_LIB)

$(VERIFIER_UTEST_EXE): $(VERIFIER_UTEST_OBJ)
	$(CXX) -o $@ $^ $(CXXFLAGS) $(LDFLAGS)

$(VERIFIER_UTEST_OBJ): %.o: %.cc
	$(CXX) -o $@ $(CXXFLAGS) -I$(VERIFIER_INCLUDE_DIR) \
	-I$(VERIFIER_11_INCLUDE_DIR) -I$(GTEST_INCLUDE_DIR) -c $^


build: all

all: $(VERIFIER_LIB)

install:
	mkdir -p '$(LIB_INSTALL_DIR)'
	mkdir -p '$(HEADERS_INSTALL_DIR)'
	mkdir -p '$(HEADERS_11_INSTALL_DIR)'
	cp $(VERIFIER_LIB) '$(LIB_INSTALL_DIR)'
	cp $(API_HEADERS_INSTALL) '$(HEADERS_INSTALL_DIR)'
	cp $(API_11_HEADERS_INSTALL) '$(HEADERS_11_INSTALL_DIR)'
#install tests if they exist
ifneq (,$(wildcard $(VERIFIER_UTEST_EXE)))
	mkdir -p '$(TEST_INSTALL_DIR)'
	cp $(VERIFIER_UTEST_EXE) '$(TEST_INSTALL_DIR)'
endif

check: $(VERIFIER_UTEST_EXE) run

run:
	$(VERIFIER_UTEST_EXE) $(GTEST_FLAGS)

clean:
	rm -f  $(VERIFIER_OBJ) \
		$(VERIFIER_LIB) \
		$(VERIFIER_UTEST_OBJ) \
		$(VERIFIER_UTEST_EXE) \
		*.xml
