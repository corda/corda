#include "stdint.h"

#ifdef __MINGW32__
#  define EXPORT __declspec(dllexport)
#  define SYMBOL(x) binary_classpath_jar_##x
#else
#  define EXPORT __attribute__ ((visibility("default")))
#  define SYMBOL(x) _binary_classpath_jar_##x
#endif

extern "C" {

  extern const uint8_t SYMBOL(start)[];
  extern const uint8_t SYMBOL(size)[];

  EXPORT const uint8_t*
  vmClasspath(unsigned* size)
  {
    *size = reinterpret_cast<uintptr_t>(SYMBOL(size));
    return SYMBOL(start);
  }

} // extern "C"
