.PHONY: all clean

LIBDIR  = ../common
SOURCE  =  enclave-inspector.cpp ${LIBDIR}/enclave_metadata.cpp
HEADERS = ${LIBDIR}/enclave_metadata.h ${LIBDIR}/elf_types.h ${LIBDIR}/sgx_types.h
RESULT  = enclave-inspector

all: ${RESULT}

${RESULT}: ${SOURCE} ${HEADERS}
	g++ -std=c++11 -o $@ ${SOURCE}

clean:
	@rm -f ${RESULT}
