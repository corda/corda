#ifndef RUN_H
#define RUN_H

#include "system.h"
#include "heap.h"
#include "class-finder.h"
#include "machine.h"

namespace vm {

object
run(Thread* t, const char* className, const char* methodName,
    const char* methodSpec, object this_, ...);

int
run(System* sys, Heap* heap, ClassFinder* classFinder,
    const char* className, int argc, const char** argv);

} // namespace vm

#endif//RUN_H
