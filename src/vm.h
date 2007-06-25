#ifndef VM_H
#define VM_H

#include "system.h"
#include "heap.h"
#include "class-finder.h"

namespace vm {

void
run(System* sys, Heap* heap, ClassFinder* classFinder,
    const char* className, int argc, const char** argv);

}

#endif//VM_H
