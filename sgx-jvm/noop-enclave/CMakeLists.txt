cmake_minimum_required(VERSION 3.5)

set(SGX_SDK ${CMAKE_CURRENT_SOURCE_DIR}/../linux-sgx)
set(SGX_LIBRARY_PATH ${SGX_SDK}/build/linux)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fpie -fstack-protector")
set(SGX_SIGN_TOOL ${SGX_SDK}/build/linux/sgx_sign)
set(ENCLAVE_UNSIGNED_OUTPUT_LIB noop_enclave.so)
set(ENCLAVE_BLOB_TO_SIGN noop_enclave_blob_to_sign.bin)
set(ENCLAVE_SIGNED_OUTPUT_LIB noop_enclave.signed.so)
set(PRIVATE_KEY_NAME selfsigning.pem)
set(PUBLIC_KEY_NAME selfsigning.public.pem)
set(NOOP_ENCLAVE noop_enclave_objects)
set(SGX_SDK_INCLUDE ${SGX_SDK}/common/inc)
set(GENERATED_RPC_DIR ${CMAKE_CURRENT_BINARY_DIR}/rpc)
set(ENCLAVE_SIGNATURE noop_enclave.signature.sha256)

set(GENERATED_EDL_FILES ${GENERATED_RPC_DIR}/empty_t.c ${GENERATED_RPC_DIR}/empty_t.h ${GENERATED_RPC_DIR}/empty_u.c ${GENERATED_RPC_DIR}/empty_u.h)
add_custom_command(
    OUTPUT ${GENERATED_EDL_FILES}
    COMMAND edger8r --search-path ${CMAKE_CURRENT_SOURCE_DIR}/src --search-path ${SGX_SDK_INCLUDE} --trusted-dir ${GENERATED_RPC_DIR} --untrusted-dir ${GENERATED_RPC_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/src/empty.edl
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/empty.edl ${SGX_LIBRARY_PATH}/sgx_edger8r ${SGX_SDK_INCLUDE}
)
set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)
add_custom_target(
    GENERATED_EDL
    DEPENDS ${GENERATED_EDL_FILES}
)

add_library(${NOOP_ENCLAVE} ${CMAKE_CURRENT_SOURCE_DIR}/src/noop_enclave.cpp ${GENERATED_RPC_DIR}/empty_t.c)
add_dependencies(${NOOP_ENCLAVE} GENERATED_EDL)
set_property(TARGET ${NOOP_ENCLAVE} PROPERTY POSITION_INDEPENDENT_CODE ON)
target_include_directories(${NOOP_ENCLAVE} PUBLIC ${SGX_SDK_INCLUDE} ${SGX_SDK_INCLUDE}/tlibc ${GENERATED_RPC_DIR})
target_compile_options(${NOOP_ENCLAVE} PUBLIC -nostdinc)

add_executable(edger8r IMPORTED)
set_target_properties(edger8r PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/sgx_edger8r)

if(SGX_USE_HARDWARE)
    set(TRTS_LIB "sgx_trts")
    set(SGX_SERVICE_LIB "sgx_tservice")
else()
    set(TRTS_LIB "sgx_trts_sim")
    set(SGX_SERVICE_LIB "sgx_tservice_sim")
endif()

set(ENCLAVE_LINKER_FLAGS
        "-Wl,--no-undefined"
        "-nostdlib"
        "-nodefaultlibs"
        "-nostartfiles"
        "-L${SGX_LIBRARY_PATH}"
        "-Wl,--whole-archive"
            "-l${TRTS_LIB}"
        "-Wl,--no-whole-archive"
        "-Wl,--start-group"
            "lib${NOOP_ENCLAVE}.a"
            "-lsgx_tstdc"
            "-lsgx_tstdcxx"
            "-lsgx_tcrypto"
            "-l${SGX_SERVICE_LIB}"
        "-Wl,--end-group"
        "-Wl,-Bstatic"
        "-Wl,-Bsymbolic"
        "-Wl,--no-undefined"
        "-Wl,-pie,-eenclave_entry"
        "-Wl,--export-dynamic"
        "-Wl,--defsym,__ImageBase=0"
        "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/linkerscript.lds"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB}
    COMMAND ${CMAKE_CXX_COMPILER} -o ${ENCLAVE_UNSIGNED_OUTPUT_LIB} ${ENCLAVE_LINKER_FLAGS}
    DEPENDS ${NOOP_ENCLAVE} ${SGX_LIBRARY_PATH}
)

add_executable(sgx_sign IMPORTED)
set_target_properties(sgx_sign PROPERTIES IMPORTED_LOCATION ${SGX_SIGN_TOOL})

# add_custom_command(
#     OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB}
#     COMMAND sgx_sign sign -key ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME} -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB} -config ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml
#     DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME} ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml
# )

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
    COMMAND sgx_sign gendata -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} -config ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB}
)

# TODO: replace with getting the pubkey from HSM
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME}
    COMMAND openssl rsa -in ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME} -pubout -out ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME}
)

# TODO: replace with signing on HSM
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE}
    COMMAND openssl dgst -sha256 -sign ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB}
    COMMAND sgx_sign catsig -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} -key ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME} -sig ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE} -unsigned ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} -config ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE} ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME}
)

add_custom_target(noop-enclave ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB})
