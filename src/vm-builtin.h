#ifndef VM_BUITLIN_H
#define VM_BUITLIN_H

#include "vm-declarations.h"

namespace vm {
namespace builtin {

void
populate(Thread* t, object map);

} // namespace builtin
} // namespace vm

#endif//VM_BUILTIN_H
