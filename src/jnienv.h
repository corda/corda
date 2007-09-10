#ifndef JNIENV_H
#define JNIENV_H

#include "machine.h"

namespace vm {

void
populateJNITables(JavaVMVTable* vmTable, JNIEnvVTable* envTable);

} // namespace vm

#endif//JNIENV_H
