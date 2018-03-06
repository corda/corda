cmake_minimum_required(VERSION 3.5)
project(sgx_common)

set(SGX_USE_HARDWARE TRUE CACHE STRING "")
set(SGX_SDK ${CMAKE_CURRENT_SOURCE_DIR}/../../linux-sgx CACHE STRING "")
set(SGX_LIBRARY_PATH ${SGX_SDK}/build/linux CACHE STRING "")
set(SGX_SDK_INCLUDE ${SGX_SDK}/common/inc CACHE STRING "")
set(COMMON_INCLUDE ${CMAKE_CURRENT_SOURCE_DIR} CACHE STRING "")
set(GENERATED_RPC_DIR ${CMAKE_CURRENT_BINARY_DIR}/rpc CACHE STRING "")
set(PROGUARD_JAR_PATH /usr/share/proguard6.0beta1/lib/proguard.jar CACHE STRING "")
set(DEPENDENCIES_LIBRARY_PATH /usr/lib/x86_64-linux-gnu CACHE STRING "")

# C++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

add_executable(edger8r IMPORTED)
set_target_properties(edger8r PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/sgx_edger8r)

set(GENERATED_EDL_FILES ${GENERATED_RPC_DIR}/java_t.c ${GENERATED_RPC_DIR}/java_t.h ${GENERATED_RPC_DIR}/java_u.c ${GENERATED_RPC_DIR}/java_u.h)
set(GENERATED_EDL_FILES ${GENERATED_EDL_FILES} PARENT_SCOPE)
add_custom_command(
    OUTPUT ${GENERATED_EDL_FILES}
    COMMAND edger8r --search-path ${PROJECT_SOURCE_DIR}/rpc --search-path ${SGX_SDK_INCLUDE} --trusted-dir ${GENERATED_RPC_DIR} --untrusted-dir ${GENERATED_RPC_DIR} java.edl
    DEPENDS ${PROJECT_SOURCE_DIR}/rpc/java.edl ${SGX_LIBRARY_PATH}/sgx_edger8r ${SGX_SDK_INCLUDE}
)
set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)

add_custom_target(
    GENERATED_EDL
    DEPENDS ${GENERATED_EDL_FILES}
)

set(SOURCE_FILES sgx_utilities.cpp untrusted_start_thread.cpp untrusted_debug_print.cpp ${GENERATED_RPC_DIR}/java_u.c)
add_library(common OBJECT ${SOURCE_FILES})
set_property(TARGET common PROPERTY POSITION_INDEPENDENT_CODE ON)
target_include_directories(common PUBLIC ${SGX_SDK_INCLUDE} ${GENERATED_RPC_DIR})
