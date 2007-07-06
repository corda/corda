#ifndef VM_JNI_H
#define VM_JNI_H

#include "vm-declarations.h"

namespace vm {
namespace jni {

void
populate(JNIEnvVTable* table);

} // namespace jni
} // namespace vm

#endif//VM_JNI_H
