#ifndef JNIENV_H
#define JNIENV_H

#include "machine.h"

namespace vm {

void
populateJNITable(JNIEnvVTable* table);

} // namespace vm

#endif//JNIENV_H
