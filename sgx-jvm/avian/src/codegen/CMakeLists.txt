add_library (avian_codegen
  compiler.cpp
  runtime.cpp
  targets.cpp

  compiler/context.cpp
  compiler/event.cpp
  compiler/frame.cpp
  compiler/ir.cpp
  compiler/promise.cpp
  compiler/read.cpp
  compiler/regalloc.cpp
  compiler/resource.cpp
  compiler/site.cpp
  compiler/value.cpp
)

add_subdirectory(target)
