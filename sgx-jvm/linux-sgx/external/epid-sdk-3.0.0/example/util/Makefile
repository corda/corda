#!/usr/bin/make -f

#define variables
COMMON_INCLUDE_DIR = ../../
UTIL_INCLUDE_DIR = ../

UTIL_SRC =  $(wildcard ./src/*.c)
UTIL_OBJ = $(sort $(UTIL_SRC:.c=.o))
UTIL_LIB = ./src/libutil.a

#set additional compiler flag
CFLAGS += -D_CRT_SECURE_NO_WARNINGS

#target part
$(UTIL_OBJ): %.o: %.c
	$(CC) $(CFLAGS) -I$(COMMON_INCLUDE_DIR) \
			-I$(UTIL_INCLUDE_DIR) \
			-c $^ -o $@

$(UTIL_LIB): $(UTIL_OBJ)
	$(AR) rc $(UTIL_LIB) $(UTIL_OBJ)
	ranlib $(UTIL_LIB)

build: all

all: $(UTIL_LIB)

install:

clean:
	rm -f  $(UTIL_OBJ) $(UTIL_LIB)

