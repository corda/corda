add_executable(type_generator main.cpp)

target_link_libraries(type_generator
  avian_jvm_finder
  avian_system
  avian_util
  ${ZLIB_LIBRARIES}
  ${PLATFORM_LIBS}
)
