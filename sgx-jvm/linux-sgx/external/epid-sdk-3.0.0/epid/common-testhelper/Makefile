#!/usr/bin/make -f

#define variables
GTEST_INCLUDE_DIR = ../../ext
TEST_INSTALL_DIR = $(epidinstalldir)/test/

COMMON_TESTHELPER_INCLUDE_DIR = ../..
COMMON_TESTHELPER_11_INCLUDE_DIR = ../../1.1
COMMON_TESTHELPER_SRC = $(wildcard ./*.cc)
COMMON_TESTHELPER_SRC += $(wildcard ./1.1/*.cc)
COMMON_TESTHELPER_UTEST_SRC = $(wildcard ./unittests/*.cc)
COMMON_TESTHELPER_OBJ = $(sort $(COMMON_TESTHELPER_SRC:.cc=.o))
COMMON_TESTHELPER_UTEST_OBJ = $(sort $(COMMON_TESTHELPER_UTEST_SRC:.cc=.o))
COMMON_TESTHELPER_LIB = ./libcommon-testhelper.a
COMMON_TESTHELPER_UTEST_EXE = ./unittests/common-testhelper-utest

LIB_COMMON_TESTHELPER_DIR = .
LIB_COMMON_DIR = ../common
LIB_IPPCP_DIR = ../../ext/ipp/sources/ippcp/src
LIB_IPPCPEPID_DIR = ../../ext/ipp/sources/ippcpepid/src

#set flags for linker
LDFLAGS += -L$(GTEST_INCLUDE_DIR)/gtest -L$(LIB_COMMON_TESTHELPER_DIR) \
	-L$(LIB_COMMON_DIR) -L$(LIB_IPPCPEPID_DIR) -L$(LIB_IPPCP_DIR) \
	-lgtest -lcommon-testhelper -lcommon -lippcpepid -lippcp

#target part
$(COMMON_TESTHELPER_LIB): $(COMMON_TESTHELPER_OBJ)
	$(AR) rc $(COMMON_TESTHELPER_LIB) $(COMMON_TESTHELPER_OBJ)
	ranlib $(COMMON_TESTHELPER_LIB)

$(COMMON_TESTHELPER_OBJ): %.o: %.cc
	$(CXX) -o $@ $(CXXFLAGS) -I$(COMMON_TESTHELPER_INCLUDE_DIR) -c $^

$(COMMON_TESTHELPER_UTEST_EXE): $(COMMON_TESTHELPER_UTEST_OBJ)
	$(CXX) -o $@ $^ $(CXXFLAGS) $(LDFLAGS)

$(COMMON_TESTHELPER_UTEST_OBJ): %.o: %.cc
	$(CXX) -o $@ $(CXXFLAGS) -I$(COMMON_TESTHELPER_INCLUDE_DIR) \
	-I$(COMMON_TESTHELPER_11_INCLUDE_DIR) -I$(GTEST_INCLUDE_DIR) -c $^

build: all

all: $(COMMON_TESTHELPER_LIB)

install:
#install tests if they exist
ifneq (,$(wildcard $(COMMON_TESTHELPER_UTEST_EXE)))
	mkdir -p '$(TEST_INSTALL_DIR)'
	cp $(COMMON_TESTHELPER_UTEST_EXE) '$(TEST_INSTALL_DIR)'
endif

check:	$(COMMON_TESTHELPER_UTEST_EXE) run

run:
	$(COMMON_TESTHELPER_UTEST_EXE) $(GTEST_FLAGS)

clean:
	rm -f  $(COMMON_TESTHELPER_OBJ) \
		$(COMMON_TESTHELPER_LIB) \
		$(COMMON_TESTHELPER_UTEST_OBJ) \
		$(COMMON_TESTHELPER_UTEST_EXE) \
		*.xml
