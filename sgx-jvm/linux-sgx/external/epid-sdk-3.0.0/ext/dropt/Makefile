#!/usr/bin/make -f

DROPT_INCLUDE_DIR = ./include
DROPT_SRC = $(wildcard ./src/*.c)
DROPT_OBJ = $(sort $(DROPT_SRC:.c=.o))

DROPT_LIB = ./src/libdropt.a

$(DROPT_OBJ): %.o: %.c
	$(CC) -o $@ -c $(CFLAGS) -I$(DROPT_INCLUDE_DIR) $<

$(DROPT_LIB): $(DROPT_OBJ)
	$(AR) rc $@ $^
	ranlib $@

build: all

all: $(DROPT_LIB)

install:

clean:
	rm -f $(DROPT_OBJ) $(DROPT_LIB)
