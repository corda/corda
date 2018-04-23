#!/usr/bin/make -f

#define variables
TOOLS_INSTALL_DIR = $(epidinstalldir)/tools

EXTRACTGRPS_INCLUDE_DIR = ./extractgrps
EXTRACTGRPS_SRC = $(wildcard ./extractgrps/src/*.c)
EXTRACTGRPS_OBJ = $(sort $(EXTRACTGRPS_SRC:.c=.o))
EXTRACTGRPS_EXE = ./extractgrps/src/extractgrps

EXTRACTKEYS_INCLUDE_DIR = ./extractkeys
EXTRACTKEYS_SRC = $(wildcard ./extractkeys/src/*.c)
EXTRACTKEYS_OBJ = $(sort $(EXTRACTKEYS_SRC:.c=.o))
EXTRACTKEYS_EXE = ./extractkeys/src/extractkeys

REVOKEGRP_INCLUDE_DIR = ./revokegrp
REVOKEGRP_SRC = $(wildcard ./revokegrp/src/*.c)
REVOKEGRP_OBJ = $(sort $(REVOKEGRP_SRC:.c=.o))
REVOKEGRP_EXE = ./revokegrp/src/revokegrp

REVOKEKEY_INCLUDE_DIR = ./revokekey
REVOKEKEY_SRC = $(wildcard ./revokekey/src/*.c)
REVOKEKEY_OBJ = $(sort $(REVOKEKEY_SRC:.c=.o))
REVOKEKEY_EXE = ./revokekey/src/revokekey

REVOKESIG_INCLUDE_DIR = ./revokesig
REVOKESIG_SRC = $(wildcard ./revokesig/src/*.c)
REVOKESIG_OBJ = $(sort $(REVOKESIG_SRC:.c=.o))
REVOKESIG_EXE = ./revokesig/src/revokesig

EPID_SDK_ROOT_DIR = ..
EXAMPLE_DIR = $(EPID_SDK_ROOT_DIR)/example
LIB_DROPT_DIR = $(EPID_SDK_ROOT_DIR)/ext/dropt/src
LIB_UTIL_DIR = $(EPID_SDK_ROOT_DIR)/example/util/src
LIB_IPPCP_DIR = $(EPID_SDK_ROOT_DIR)/ext/ipp/sources/ippcp/src
LIB_IPPCPEPID_DIR = $(EPID_SDK_ROOT_DIR)/ext/ipp/sources/ippcpepid/src
LIB_MEMBER_DIR = $(EPID_SDK_ROOT_DIR)/epid/member
LIB_COMMON_DIR = $(EPID_SDK_ROOT_DIR)/epid/common

#set linker flags
LDFLAGS += -L$(LIB_UTIL_DIR) \
	-L$(LIB_DROPT_DIR) \
	-L$(LIB_IPPCP_DIR) \
	-L$(LIB_COMMON_DIR) \
	-L$(LIB_IPPCPEPID_DIR) \
	-lcommon -lippcpepid \
	-lippcp -lutil -ldropt

$(EXTRACTGRPS_EXE): $(EXTRACTGRPS_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) $(LDFLAGS)

$(EXTRACTGRPS_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(EPID_SDK_ROOT_DIR) \
			-I${EXAMPLE_DIR} -I$(EXTRACTGRPS_INCLUDE_DIR) \
			-I${LIB_DROPT_DIR}/../include \
			-c $^

$(EXTRACTKEYS_EXE): $(EXTRACTKEYS_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) $(LDFLAGS)

$(EXTRACTKEYS_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(EPID_SDK_ROOT_DIR) \
			-I${EXAMPLE_DIR} -I$(EXTRACTKEYS_INCLUDE_DIR) \
			-I${LIB_DROPT_DIR}/../include \
			-c $^

$(REVOKEGRP_EXE): $(REVOKEGRP_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) $(LDFLAGS)

$(REVOKEGRP_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(EPID_SDK_ROOT_DIR) \
			-I${EXAMPLE_DIR} -I$(REVOKEGRP_INCLUDE_DIR) \
			-I${LIB_DROPT_DIR}/../include \
			-c $^

$(REVOKEKEY_EXE): $(REVOKEKEY_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) -L$(LIB_MEMBER_DIR) -lmember $(LDFLAGS)

$(REVOKEKEY_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(EPID_SDK_ROOT_DIR) \
			-I$(LIB_MEMBER_DIR)/../.. \
			-I${EXAMPLE_DIR} -I$(REVOKEKEY_INCLUDE_DIR) \
			-I${LIB_DROPT_DIR}/../include \
			-c $^

$(REVOKESIG_EXE): $(REVOKESIG_OBJ)
	$(CC) -o $@ $^ $(CFLAGS) $(LDFLAGS)

$(REVOKESIG_OBJ): %.o: %.c
	$(CC) -o $@ $(CFLAGS) -I$(EPID_SDK_ROOT_DIR) \
			-I${EXAMPLE_DIR} -I$(REVOKESIG_INCLUDE_DIR) \
			-I${LIB_DROPT_DIR}/../include \
			-c $^

#target part
build: all install

all: $(EXTRACTGRPS_EXE) $(EXTRACTKEYS_EXE) $(REVOKEGRP_EXE) $(REVOKEKEY_EXE) $(REVOKESIG_EXE)

install:
	mkdir -p '$(TOOLS_INSTALL_DIR)'
	cp $(EXTRACTGRPS_EXE) $(EXTRACTKEYS_EXE) $(REVOKEGRP_EXE) $(REVOKEKEY_EXE) $(REVOKESIG_EXE) \
		'$(TOOLS_INSTALL_DIR)'

clean:
	rm -f $(EXTRACTGRPS_EXE) $(EXTRACTKEYS_EXE) $(REVOKEGRP_EXE) $(REVOKEKEY_EXE) $(REVOKESIG_EXE) \
	$(EXTRACTGRPS_OBJ) $(EXTRACTKEYS_OBJ) $(REVOKEGRP_OBJ) $(REVOKEKEY_OBJ) $(REVOKESIG_OBJ)
