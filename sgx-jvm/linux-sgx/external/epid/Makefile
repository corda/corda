include ../../buildenv.mk

EPIDLIB := libepid.a
TOP_DIR := $(LINUX_EXTERNAL_DIR)/epid

CFLAGS  += -fPIC
DEFINES := -DIPPEPID_USE_MERGEDLIB -DASSUME_GETOCTSTRING_POINT_VALID

ifdef DEBUG
    CFLAGS  += -O0
    DEFINES += -DDEBUG
else
    DEFINES += -DNDEBUG
endif

ifeq ($(ARCH), x86_64)
    DEFINES += -DUSE_Y8_HEADER
else
    DEFINES += -DUSE_P8_HEADER
endif

vpath %.c epid/common epid/common/math epid/member epid/verifier ext/ipp/sources/ippcpepid/src
 
INCLUDE += -I$(TOP_DIR)/ext/ipp/include                               \
           -I$(TOP_DIR)/ext/ipp/sources/include                       \
           -I$(TOP_DIR)/ext/ipp/sources/ippcp/src                     \
           -I$(TOP_DIR)/ext/ipp/sources/ippcpepid/src                 \
           -I$(TOP_DIR)

SRC := commitment.c epid2params.c errors.c grouppubkey.c memory.c printutils.c sigrlvalid.c stack.c \
      bignum.c ecgroup.c finitefield.c pairing.c \
      context.c nr_prove.c privkey.c signbasic.c get_sigsize.c presig.c request_join.c sign.c \
      check_privrl_entry.c nrverify.c verifybasic.c context.c sigs_linked.c verify.c \
      pcpgfp.c pcpgfpec.c pcpgfpecpoint.c pcpgfpecpointstuff.c pcpgfpstuff.c pcpgfpx.c pcpgfpxstuff.c

OBJ := $(SRC:.c=.o)

.PHONY: all
all: $(EPIDLIB)

$(EPIDLIB): $(OBJ)
	$(AR) rcs $@ $^

%.o: %.c
	$(CC) $(INCLUDE) $(filter-out -Werror, $(CFLAGS)) $(DEFINES) -c $< -o $@

.PHONY: clean
clean:
	@$(RM) $(OBJ) $(EPIDLIB)

.PHONY: rebuild
rebuild: clean all
