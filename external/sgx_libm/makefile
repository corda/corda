ARCH ?= intel64
.PHONE: lib clean
all: lib
	cp $(ARCH)/libm.a libm.a
ifeq ($(ARCH),ia32)
CFLAGS := -m32
endif

SRC_FILES := $(wildcard $(ARCH)/*.S)
OBJ_FILES := $(SRC_FILES:.S=.o)
STATIC_LIB:= $(ARCH)/libm.a
SHARED_LIB:= $(ARCH)/libm.so
lib : $(STATIC_LIB) $(SHARED_LIB)

%.o : %.S
	gcc $(CFLAGS) -c $< -o $@

$(STATIC_LIB) : $(OBJ_FILES)
	ar crv $@ $^

$(SHARED_LIB) : $(OBJ_FILES)
	gcc $(CFLAGS) -shared -o $@ $^

clean:
	-rm -rf $(OBJ_FILES) $(STATIC_LIB) $(SHARED_LIB) libm.a
