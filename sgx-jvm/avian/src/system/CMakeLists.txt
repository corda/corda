
if (MSVC)
  #todo: support mingw compiler
  add_library(avian_system windows.cpp windows/crash.cpp)
else()
  add_library(avian_system posix.cpp posix/crash.cpp)
endif()
