#!/usr/bin/make -f
API_INCLUDE_DIR = ../../include
SRC_INCLUDE_DIR = ../../sources/include
LIB_INSTALL_DIR = $(epidinstalldir)/lib/posix-$(ARCH)/

PCP_INCLUDE_DIR = ../ippcp/src
PCPEPID_INCLUDE_DIR = ./src
PCPEPID_SRC = $(wildcard ./src/*.c)
PCPEPID_OBJ = $(sort $(PCPEPID_SRC:.c=.o))
PCPEPID_LIB = ./src/libippcpepid.a


ifeq ($(ARCH), x86_64)
    CFLAGS += -DUSE_Y8_HEADER
else
    CFLAGS += -DUSE_P8_HEADER
endif

$(PCPEPID_OBJ): %.o: %.c
	$(CC) $(CFLAGS) -I$(API_INCLUDE_DIR) -I$(SRC_INCLUDE_DIR) -I$(PCP_INCLUDE_DIR) -I$(PCPEPID_INCLUDE_DIR) -c -o $@ $<

$(PCPEPID_LIB): $(PCPEPID_OBJ)
	$(AR) rc $@ $^
	ranlib $@

build: all

all: $(PCPEPID_LIB)

install:
	mkdir -p '$(LIB_INSTALL_DIR)'
	cp $(PCPEPID_LIB) '$(LIB_INSTALL_DIR)'

clean:
	rm -f $(PCPEPID_OBJ) $(PCPEPID_LIB)


