#include "stdint.h"

#ifdef __MINGW32__
#  define EXPORT __declspec(dllexport)
#else
#  define EXPORT __attribute__ ((visibility("default")))
#endif

extern "C" {

extern const uint8_t _binary_classpath_jar_start[];
extern const uint8_t _binary_classpath_jar_size[];

EXPORT const uint8_t*
vmClasspath(unsigned* size)
{
  *size = reinterpret_cast<uintptr_t>(_binary_classpath_jar_size);
  return _binary_classpath_jar_start;
}

} // extern "C"
