cmake_minimum_required(VERSION 3.12)
project(CordaCPP)

set(CMAKE_CXX_STANDARD 14)

# Load Qpid Proton for AMQP handling.
find_package(ProtonCpp REQUIRED)
include_directories(${ProtonCpp_INCLUDE_DIRS})
link_libraries(${ProtonCpp_LIBRARIES})
add_definitions(${ProtonCpp_DEFINITIONS})

add_executable(CordaCPP main.cpp main.h corda.cpp corda.h corda-std-serializers.h)
target_include_directories(CordaCPP PRIVATE include)
target_include_directories(CordaCPP PRIVATE .)