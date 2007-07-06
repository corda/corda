#ifndef BUITLIN_H
#define BUITLIN_H

#include "machine.h"

namespace vm {
namespace builtin {

void
populate(Thread* t, object map);

} // namespace builtin
} // namespace vm

#endif//BUILTIN_H
