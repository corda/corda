#ifndef RUN_H
#define RUN_H

#include "system.h"
#include "heap.h"
#include "class-finder.h"
#include "machine.h"

namespace vm {

object
run(Thread* t, const char* className, const char* methodName,
    const char* methodSpec, ...);

int
run(System* sys, Heap* heap, ClassFinder* classFinder,
    const char* className, int argc, const char** argv);

}

#endif//RUN_H
