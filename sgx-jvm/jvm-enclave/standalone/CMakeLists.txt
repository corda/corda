cmake_minimum_required(VERSION 3.5)
project(standalone_sgx_verify)

#
# Enclave library
#
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../enclave ${CMAKE_CURRENT_SOURCE_DIR}/../enclave/build)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -g")
set(CMAKE_CXX_FLAGS_DEBUG "-DNDEBUG -DEDEBUG -UDEBUG -g")   # For "pre-release" mode

#
# SGX SDK
#
include_directories("${SGX_SDK}/include")

add_library(urtslib SHARED IMPORTED)
set_target_properties(urtslib PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/lib${URTS_LIB}.so)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)


#
# Untrusted loader app
#

include_directories(${GENERATED_RPC_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${CMAKE_CURRENT_BINARY_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../common)
# TODO: Do the "-fPIC -wno-attributes" flags matter?

set(SOURCE_FILES main.cpp)
set_source_files_properties(${GENERATED_RPC_DIR}/java_u.c PROPERTIES GENERATED TRUE)
link_directories(${DEPENDENCIES_LIBRARY_PATH})
add_executable(standalone_sgx_verify ${SOURCE_FILES} $<TARGET_OBJECTS:common>)
target_include_directories(standalone_sgx_verify PUBLIC ${SGX_SDK_INCLUDE})
target_link_libraries(standalone_sgx_verify urtslib Threads::Threads)
add_dependencies(standalone_sgx_verify enclave)
