#ifndef JNIENV_H
#define JNIENV_H

#include "machine.h"

namespace vm {

namespace jni {

void
populate(JNIEnvVTable* table);

} // namespace jni

} // namespace vm

#endif//JNIENV_H
