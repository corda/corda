cmake_minimum_required(VERSION 3.5)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Debug ... FORCE)
endif()

if(CMAKE_BUILD_TYPE MATCHES Debug)
    set(ENCLAVE_CONFIG enclave-debug.xml)
elseif(CMAKE_BUILD_TYPE MATCHES Release)
    set(ENCLAVE_CONFIG enclave-release.xml)
else()
    message(FATAL_ERRORO "No build type")
endif()

set(SGX_SDK ${CMAKE_CURRENT_SOURCE_DIR}/../linux-sgx)
set(SGX_LIBRARY_PATH ${SGX_SDK}/build/linux)
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -fvisibility=hidden -fpie -fstack-protector")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -fpie -fstack-protector -s -DNDEBUG")
set(SGX_SIGN_TOOL ${SGX_SDK}/build/linux/sgx_sign)
set(ENCLAVE_UNSIGNED simple_enclave.unsigned.so)
set(ENCLAVE_BLOB_TO_SIGN simple_enclave_blob_to_sign.bin)
set(ENCLAVE_SIGNED_OPENSSL simple_enclave.signed.openssl.so)
set(ENCLAVE_SIGNED_HSM simple_enclave.signed.hsm.so)
set(ENCLAVE_SIGNATURE_OPENSSL simple_enclave.signature.openssl.sha256)
set(ENCLAVE_SIGNATURE_HSM simple_enclave.signature.hsm.sha256)
set(ENCLAVE_SIGSTRUCT_OPENSSL simple_enclave.sigstruct.openssl.bin)
set(ENCLAVE_SIGSTRUCT_HSM simple_enclave.sigstruct.hsm.bin)
set(ENCLAVE_SIGSTRUCT_PRETTY_OPENSSL simple_enclave.sigstruct-pretty.openssl.txt)
set(ENCLAVE_SIGSTRUCT_PRETTY_HSM simple_enclave.sigstruct-pretty.hsm.txt)
set(PRIVATE_KEY_NAME_OPENSSL ../sign_helper/selfsigning.pem)
set(PUBLIC_KEY_NAME_OPENSSL selfsigning.public.pem)
set(PUBLIC_KEY_NAME_HSM hsm.public.pem)
set(HSM_SGX_TOOL ${PROJECT_SOURCE_DIR}/../hsm-tool/build/libs/sgx-jvm/hsm-tool-1.0-SNAPSHOT.jar)
set(DEPENDENCIES_LIBRARY_PATH /usr/lib/x86_64-linux-gnu CACHE STRING "")
set(SIGN_HELPER env LD_LIBRARY_PATH=/lib/x86_64-linux-gnu ${PROJECT_SOURCE_DIR}/../sign_helper/sign_helper)

set(SIMPLE_ENCLAVE simple_enclave_objects)
set(SGX_SDK_INCLUDE ${SGX_SDK}/common/inc)
set(GENERATED_RPC_DIR ${CMAKE_CURRENT_BINARY_DIR}/rpc)

set(GENERATED_EDL_FILES ${GENERATED_RPC_DIR}/simple_t.c ${GENERATED_RPC_DIR}/simple_t.h ${GENERATED_RPC_DIR}/simple_u.c ${GENERATED_RPC_DIR}/simple_u.h)
add_custom_command(
    OUTPUT ${GENERATED_EDL_FILES}
    COMMAND edger8r --search-path ${CMAKE_CURRENT_SOURCE_DIR}/src --search-path ${SGX_SDK_INCLUDE} --trusted-dir ${GENERATED_RPC_DIR} --untrusted-dir ${GENERATED_RPC_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/src/simple.edl
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/simple.edl ${SGX_LIBRARY_PATH}/sgx_edger8r ${SGX_SDK_INCLUDE}
)
set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)
add_custom_target(
    GENERATED_EDL
    DEPENDS ${GENERATED_EDL_FILES}
)

add_library(${SIMPLE_ENCLAVE} ${CMAKE_CURRENT_SOURCE_DIR}/src/simple_enclave.cpp ${GENERATED_RPC_DIR}/simple_t.c)
add_dependencies(${SIMPLE_ENCLAVE} GENERATED_EDL)
set_property(TARGET ${SIMPLE_ENCLAVE} PROPERTY POSITION_INDEPENDENT_CODE ON)
target_include_directories(${SIMPLE_ENCLAVE} PUBLIC ${SGX_SDK_INCLUDE} ${SGX_SDK_INCLUDE}/tlibc ${GENERATED_RPC_DIR})
target_compile_options(${SIMPLE_ENCLAVE} PUBLIC -nostdinc)

add_executable(edger8r IMPORTED)
set_target_properties(edger8r PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/sgx_edger8r)

set(SGX_USE_HARDWARE TRUE)

if(SGX_USE_HARDWARE)
    set(URTS_LIB "sgx_urts")
    set(TRTS_LIB "sgx_trts")
    set(SGX_SERVICE_LIB "sgx_tservice")
else()
    set(URTS_LIB "sgx_urts_sim")
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
            "lib${SIMPLE_ENCLAVE}.a"
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
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED}
    COMMAND ${CMAKE_CXX_COMPILER} -o ${ENCLAVE_UNSIGNED} ${ENCLAVE_LINKER_FLAGS}
    DEPENDS ${SIMPLE_ENCLAVE} ${SGX_LIBRARY_PATH}
)


add_executable(sgx_sign IMPORTED)
set_target_properties(sgx_sign PROPERTIES IMPORTED_LOCATION ${SGX_SIGN_TOOL})

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
    COMMAND sgx_sign gendata -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} -config ${CMAKE_CURRENT_SOURCE_DIR}/${ENCLAVE_CONFIG}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED}
)

# outputs the unsigned enclave and the blob to sign
add_custom_target(unsigned DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN})

# OPENSSL ENCLAVE
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_OPENSSL}
    COMMAND openssl rsa -in ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME_OPENSSL} -pubout -out ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_OPENSSL}
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_OPENSSL}
    COMMAND openssl dgst -sha256 -sign ${CMAKE_CURRENT_SOURCE_DIR}/${PRIVATE_KEY_NAME_OPENSSL} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_OPENSSL} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN}
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OPENSSL}
    COMMAND sgx_sign catsig -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED} -key ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_OPENSSL} -sig ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_OPENSSL} -unsigned ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} -config ${CMAKE_CURRENT_SOURCE_DIR}/${ENCLAVE_CONFIG} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OPENSSL}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_OPENSSL} ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_OPENSSL}
)

add_custom_target(signed-openssl DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OPENSSL})
# /OPENSSL ENCLAVE


# HSM ENCLAVE
add_custom_command(
    OUTPUT ${HSM_SGX_TOOL}
    COMMAND ./gradlew sgx-jvm/hsm-tool:jar
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../..
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/../hsm-tool/src
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_HSM} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_HSM}
    COMMAND java -jar ${HSM_SGX_TOOL} --mode=Sign --source=${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} --pubkey=${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_HSM} --signature=${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_HSM} --profile=\${PROFILE}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} ${HSM_SGX_TOOL}
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_HSM}
    COMMAND sgx_sign catsig -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED} -key ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_HSM} -sig ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_HSM} -unsigned ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_BLOB_TO_SIGN} -config ${CMAKE_CURRENT_SOURCE_DIR}/${ENCLAVE_CONFIG} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_HSM}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNATURE_HSM} ${CMAKE_CURRENT_BINARY_DIR}/${PUBLIC_KEY_NAME_HSM}
)

add_custom_target(signed-hsm DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_HSM})
# /HSM ENCLAVE

# HSM KEY
add_custom_command(
    OUTPUT __generate-key-hsm-dummy__
    COMMAND java -jar ${HSM_SGX_TOOL} --mode=GenerateSgxKey --profile=\${PROFILE}
)
add_custom_target(generate-key-hsm DEPENDS __generate-key-hsm-dummy__)
# /HSM KEY

# HSM KEY OVERWRITE
add_custom_command(
    OUTPUT __generate-key-hsm-overwrite-dummy__
    COMMAND java -jar ${HSM_SGX_TOOL} --mode=GenerateSgxKey --profile=\${PROFILE} --overwriteKey
)
add_custom_target(generate-key-hsm-overwrite DEPENDS __generate-key-hsm-overwrite-dummy__)
# /HSM KEY OVERWRITE

# OPENSSL SIGSTRUCT
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_OPENSSL}
    COMMAND ${SIGN_HELPER} get-css -in ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OPENSSL} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_OPENSSL}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OPENSSL}
)
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_OPENSSL}
    COMMAND ${SIGN_HELPER} print-css -in ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_OPENSSL} > ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_OPENSSL}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_OPENSSL}
)
add_custom_target(sigstruct-openssl DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_OPENSSL} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_OPENSSL})
# /OPENSSL SIGSTRUCT

# HSM SIGSTRUCT
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_HSM}
    COMMAND ${SIGN_HELPER} get-css -in ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_HSM} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_HSM}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_HSM}
)
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_HSM}
    COMMAND ${SIGN_HELPER} print-css -in ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_HSM} > ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_HSM}
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_HSM}
)
add_custom_target(sigstruct-hsm DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_HSM} ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGSTRUCT_PRETTY_HSM})
# /HSM SIGSTRUCT

# test
add_library(urtslib SHARED IMPORTED)
set_target_properties(urtslib PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/lib${URTS_LIB}.so)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -g")
link_directories(${DEPENDENCIES_LIBRARY_PATH})
add_executable(simple_test src/test.cpp ${GENERATED_RPC_DIR}/simple_u.c)
target_include_directories(simple_test PUBLIC ${SGX_SDK_INCLUDE} ${GENERATED_RPC_DIR})
target_link_libraries(simple_test urtslib Threads::Threads)
