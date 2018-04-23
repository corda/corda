#!/usr/bin/make -f

#define path variables
IPP_SRC_INCLUDE_DIR = ../../ext/ipp/sources/ippcp/src/
IPP_INCLUDE_DIR = ../../ext/ipp/sources/include/
GTEST_INCLUDE_DIR = ../../ext/

LIB_INSTALL_DIR = $(epidinstalldir)/lib/posix-$(ARCH)/
TEST_INSTALL_DIR = $(epidinstalldir)/test/
HEADERS_INSTALL_DIR :=  $(epidinstalldir)/include/epid/member/

API_HEADERS_INSTALL = ./api.h

MEMBER_INCLUDE_DIR = ../../

MEMBER_SRC =  $(wildcard ./src/*.c)
MEMBER_OBJ = $(sort $(MEMBER_SRC:.c=.o))
MEMBER_LIB = ./libmember.a

MEMBER_UTEST_EXE = ./member-utest
MEMBER_UTEST_SRC = $(wildcard ./unittests/*.cc)
MEMBER_UTEST_OBJ = $(sort $(MEMBER_UTEST_SRC:.cc=.o))
LIB_MEMBER_DIR = .
LIB_VERIFIER_DIR = ../verifier
LIB_COMMON_TESTHELPER_DIR = ../common-testhelper
LIB_COMMON_DIR = ../common
LIB_IPPCP_DIR = ../../ext/ipp/sources/ippcp/src
LIB_IPPCPEPID_DIR = ../../ext/ipp/sources/ippcpepid/src

#set flags for linker
LDFLAGS += -L$(GTEST_INCLUDE_DIR)/gtest  -L$(LIB_COMMON_TESTHELPER_DIR)  \
	-L$(LIB_MEMBER_DIR) -L$(LIB_VERIFIER_DIR) -L$(LIB_COMMON_DIR) \
	-L$(LIB_IPPCPEPID_DIR) -L$(LIB_IPPCP_DIR) -lgtest -lcommon-testhelper \
	-lmember -lverifier -lcommon -lippcpepid -lippcp

$(MEMBER_OBJ): %.o: %.c
	$(CC) $(CFLAGS) -I$(IPP_INCLUDE_DIR) -I$(IPP_SRC_INCLUDE_DIR) \
	-I$(MEMBER_INCLUDE_DIR) -c $^ -o $@

$(MEMBER_LIB): $(MEMBER_OBJ)
	$(AR) rc $(MEMBER_LIB) $(MEMBER_OBJ)
	ranlib $(MEMBER_LIB)

$(MEMBER_UTEST_EXE): $(MEMBER_UTEST_OBJ)
	$(CXX) -o $@ $^ $(CXXFLAGS) $(LDFLAGS)

$(MEMBER_UTEST_OBJ): %.o: %.cc
	$(CXX) -o $@ $(CXXFLAGS) -I$(MEMBER_INCLUDE_DIR) \
	-I$(GTEST_INCLUDE_DIR) -c $^

build: all

all: $(MEMBER_LIB)

install:
	mkdir -p '$(LIB_INSTALL_DIR)'
	mkdir -p '$(HEADERS_INSTALL_DIR)'
	cp $(MEMBER_LIB) '$(LIB_INSTALL_DIR)'
	cp $(API_HEADERS_INSTALL) '$(HEADERS_INSTALL_DIR)'
#install tests if they exist
ifneq (,$(wildcard $(MEMBER_UTEST_EXE)))
	mkdir -p '$(TEST_INSTALL_DIR)'
	cp $(MEMBER_UTEST_EXE) '$(TEST_INSTALL_DIR)'
endif

check: $(MEMBER_UTEST_EXE) run

run:
	$(MEMBER_UTEST_EXE) $(GTEST_FLAGS)

clean:
	rm -f $(MEMBER_OBJ) \
		$(MEMBER_LIB) \
		$(MEMBER_UTEST_OBJ) \
		$(MEMBER_UTEST_EXE) \
		*.xml
