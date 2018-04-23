#!/usr/bin/make -f

#define variables
IPP_API_INCLUDE_DIR = ../ext/ipp/include
EXAMPLE_INSTALL_DIR = $(epidinstalldir)/example
COMPRESSED_DEFAULT_FILES_INSTALL_DIR = $(epidinstalldir)/compressed_example

SIGNMSG_INCLUDE_DIR = ./signmsg
SIGNMSG_SRC = $(wildcard ./signmsg/src/*.c)
SIGNMSG_OBJ = $(sort $(SIGNMSG_SRC:.c=.o))
SIGNMSG_EXE = ./signmsg/src/signmsg

VERIFYSIG_INCLUDE_DIR = ./verifysig
VERIFYSIG_SRC = $(wildcard ./verifysig/src/*.c)
VERIFYSIG_OBJ = $(sort $(VERIFYSIG_SRC:.c=.o))
VERIFYSIG_EXE = ./verifysig/src/verifysig


LIB_UTIL_DIR = ./util/src
LIB_DROPT_DIR = ../ext/dropt/src
LIB_IPPCP_DIR = ../ext/ipp/sources/ippcp/src
LIB_IPPCPEPID_DIR = ../ext/ipp/sources/ippcpepid/src
LIB_MEMBER_DIR = ../epid/member
LIB_VERIFIER_DIR = ../epid/verifier
LIB_COMMON_DIR = ../epid/common

#set linker flags
LDFLAGS += -L$(LIB_UTIL_DIR) \
	-L$(LIB_DROPT_DIR) \
	-L$(LIB_IPPCP_DIR) \
	-L$(LIB_COMMON_DIR) \
	-L$(LIB_IPPCPEPID_DIR) \
	-lcommon -lippcpepid \
	-lippcp -lutil -ldropt

$(VERIFYSIG_EXE): $(VERIFYSIG_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) -L$(LIB_VERIFIER_DIR) -lverifier $(LDFLAGS)

$(VERIFYSIG_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(LIB_UTIL_DIR)/../.. \
			-I$(LIB_DROPT_DIR)/../include \
			-I$(LIB_VERIFIER_DIR)/../.. \
			-I$(VERIFYSIG_INCLUDE_DIR) \
			-I$(IPP_API_INCLUDE_DIR) -c $^


$(SIGNMSG_EXE): $(SIGNMSG_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) -L$(LIB_MEMBER_DIR) -lmember $(LDFLAGS)

$(SIGNMSG_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(LIB_UTIL_DIR)/../.. \
			-I$(LIB_DROPT_DIR)/../include \
			-I$(LIB_MEMBER_DIR)/../.. \
			-I$(SIGNMSG_INCLUDE_DIR) \
			-I$(IPP_API_INCLUDE_DIR) -c $^

#target part
build: all install

all: $(VERIFYSIG_EXE) $(SIGNMSG_EXE)

install:
	mkdir -p '$(EXAMPLE_INSTALL_DIR)/data'
	cp -r ./data/group* ./data/grprl_empty.bin \
		./data/*.md '$(EXAMPLE_INSTALL_DIR)/data'
	cp ./data/cacert.bin ./data/grprl.bin \
		./data/privrl.bin ./data/sigrl.bin \
		./data/mprivkey.dat ./data/pubkey.bin \
		'$(EXAMPLE_INSTALL_DIR)'
	mkdir -p '$(EXAMPLE_INSTALL_DIR)/compressed_data'
	cp -r ./compressed_data/group* ./compressed_data/grprl_empty.bin \
		./compressed_data/*.md '$(EXAMPLE_INSTALL_DIR)/compressed_data'
	mkdir -p '$(COMPRESSED_DEFAULT_FILES_INSTALL_DIR)'
	cp ./compressed_data/cacert.bin ./compressed_data/grprl.bin \
		./compressed_data/privrl.bin ./compressed_data/sigrl.bin \
		./compressed_data/mprivkey.dat ./compressed_data/pubkey.bin \
		'$(COMPRESSED_DEFAULT_FILES_INSTALL_DIR)'
	cp $(VERIFYSIG_EXE) $(SIGNMSG_EXE) '$(EXAMPLE_INSTALL_DIR)'

clean:
	rm -f $(VERIFYSIG_OBJ) \
		$(VERIFYSIG_EXE) \
		$(SIGNMSG_OBJ) \
		$(SIGNMSG_EXE)
