#!/usr/bin/make -f

API_INCLUDE_DIR = ../../include
SRC_INCLUDE_DIR = ../../sources/include
LIB_INSTALL_DIR = $(epidinstalldir)/lib/posix-$(ARCH)/

PCP_INCLUDE_DIR = ./src
PCP_SRC = $(wildcard ./src/*.c)
PCP_OBJ = $(sort $(PCP_SRC:.c=.o))
PCP_LIB = ./src/libippcp.a


$(PCP_OBJ): %.o: %.c
	$(CC) $(CFLAGS) -I$(API_INCLUDE_DIR) -I$(SRC_INCLUDE_DIR) -I$(PCP_INCLUDE_DIR) -c -o $@ $<

$(PCP_LIB): $(PCP_OBJ)
	$(AR) rc $@ $^
	ranlib $@

build: all

all: $(PCP_LIB)

install:
	mkdir -p '$(LIB_INSTALL_DIR)'
	cp $(PCP_LIB) '$(LIB_INSTALL_DIR)'

clean:
	rm -f $(PCP_OBJ) $(PCP_LIB)


