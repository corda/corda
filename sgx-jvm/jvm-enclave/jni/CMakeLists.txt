cmake_minimum_required(VERSION 3.5)
project(jni_sgx)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../enclave ${CMAKE_CURRENT_SOURCE_DIR}/../enclave/build)

set(VERIFY_ENCLAVE_JNI_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../verify-enclave/build/native/include)
include_directories("${JAVA_HOME}/include")
include_directories("${JAVA_HOME}/include/linux")
include_directories(${VERIFY_ENCLAVE_JNI_INCLUDE_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../common)
include_directories(${GENERATED_RPC_DIR})
include_directories(${SGX_SDK_INCLUDE})

add_library(urtslib SHARED IMPORTED)
set_target_properties(urtslib PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/lib${URTS_LIB}.so)

add_library(untrusted_corda_sgx SHARED jni_sgx_api.cpp ${VERIFY_ENCLAVE_JNI_INCLUDE_DIR}/jni_sgx_api.h $<TARGET_OBJECTS:common>)
target_link_libraries(untrusted_corda_sgx urtslib)
add_dependencies(untrusted_corda_sgx enclave common)
