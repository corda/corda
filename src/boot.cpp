#include "stdint.h"
#include "stdlib.h"

// since we don't link against libstdc++, we must implement some dummy
// functions:
extern "C" void __cxa_pure_virtual(void) { abort(); }
void operator delete(void*) { abort(); }

#ifdef __MINGW32__
#  define EXPORT __declspec(dllexport)
#  define SYMBOL(x) binary_classpath_jar_##x
#else
#  define EXPORT __attribute__ ((visibility("default")))
#  define SYMBOL(x) _binary_classpath_jar_##x
#endif

extern "C" {

  extern const uint8_t SYMBOL(start)[];
  extern const uint8_t SYMBOL(end)[];

  EXPORT const uint8_t*
  classpathJar(unsigned* size)
  {
    *size = SYMBOL(end) - SYMBOL(start);
    return SYMBOL(start);
  }

}
