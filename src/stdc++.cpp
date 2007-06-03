#include "stdlib.h"

extern "C" void
__cxa_pure_virtual(void)
{
  abort();
}

void
operator delete(void*)
{
  abort();
}
