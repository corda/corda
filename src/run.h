#ifndef RUN_H
#define RUN_H

#include "system.h"
#include "heap.h"
#include "finder.h"
#include "machine.h"

namespace vm {

object
run(Thread* t, object method, object this_, ...);

object
run2(Thread* t, object method, object this_, object arguments);

object
run(Thread* t, const char* className, const char* methodName,
    const char* methodSpec, object this_, ...);

int
run(System* sys, Heap* heap, Finder* finder,
    const char* className, int argc, const char** argv);

} // namespace vm

#endif//RUN_H
