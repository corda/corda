#ifndef BUITLIN_H
#define BUITLIN_H

#include "machine.h"

namespace vm {

void
populateBuiltinMap(Thread* t, object map);

} // namespace vm

#endif//BUILTIN_H
