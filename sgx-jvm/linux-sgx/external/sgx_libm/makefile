ARCH ?= intel64
.PHONY: all lib clean
ifeq ($(ARCH),ia32)
CFLAGS := -m32
endif
CFLAGS += -fPIC

SRC_FILES := $(wildcard $(ARCH)/*.S)
OBJ_FILES := $(SRC_FILES:.S=.o)
STATIC_LIB:= $(ARCH)/libm.a
SHARED_LIB:= $(ARCH)/libm.so

all: lib
	cp $(ARCH)/libm.a libm.a

lib: $(STATIC_LIB) $(SHARED_LIB)

%.o: %.S
	$(CC) $(CFLAGS) -c $< -o $@

$(STATIC_LIB): $(OBJ_FILES)
	$(AR) crv $@ $^

$(SHARED_LIB): $(OBJ_FILES)
	$(CC) $(CFLAGS) -shared -o $@ $^

clean:
	$(RM) $(OBJ_FILES) $(STATIC_LIB) $(SHARED_LIB) libm.a
