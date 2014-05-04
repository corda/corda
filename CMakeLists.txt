# NOTE that this CMake file doesn't current build all of avian.
# It only builds what's required for example/kaleidoscope.

cmake_minimum_required (VERSION 2.6)
project (avian)

include_directories (include src)

add_definitions (
  -DAVIAN_TARGET_FORMAT=AVIAN_FORMAT_MACHO

  -DAVIAN_TARGET_ARCH=AVIAN_ARCH_X86_64

  -DTARGET_BYTES_PER_WORD=8
  -D__STDC_LIMIT_MACROS
  -D__STDC_CONSTANT_MACROS
)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror -fno-exceptions")

include ("cmake/Platform.cmake")
include (CTest)

# Sadly, we can't use the 'test' target, as that's coopted by ctest
add_custom_target(check ${CMAKE_CTEST_COMMAND} -V)

add_subdirectory (src)
add_subdirectory (unittest)
