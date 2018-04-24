.PHONY: all test clean

LIBDIR  = ../common
SDKDIR  = ../../linux-sgx/common/inc
SOURCE  =  tests.cpp ${LIBDIR}/enclave_map.cpp
HEADERS = ${LIBDIR}/enclave_map.h ${LIBDIR}/elf_types.h ${LIBDIR}/sgx_types.h
RESULT  = tests

all: ${RESULT}

${RESULT}: ${SOURCE} ${HEADERS}
	g++ -std=c++11 -o $@ -I${LIBDIR} -I${SDKDIR} ${SOURCE}

test: ${RESULT}
	./${RESULT}

clean:
	@rm -f ${RESULT}
