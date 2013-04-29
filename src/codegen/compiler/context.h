/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_CONTEXT_H
#define AVIAN_CODEGEN_COMPILER_CONTEXT_H

#include <avian/vm/codegen/assembler.h>
#include <avian/vm/codegen/compiler.h>

#include "regalloc.h"

using namespace avian::util;

namespace avian {
namespace codegen {
namespace compiler {

class Stack;
class Local;
class Event;
class LogicalInstruction;

class Resource;
class RegisterResource;
class FrameResource;

class ConstantPoolNode;

class ForkState;
class MySubroutine;
class Block;

template<class T>
class Cell {
 public:
  Cell(Cell<T>* next, T* value): next(next), value(value) { }

  Cell<T>* next;
  T* value;
};

template<class T>
unsigned count(Cell<T>* c) {
  unsigned count = 0;
  while (c) {
    ++ count;
    c = c->next;
  }
  return count;
}

template<class T>
Cell<T>* reverseDestroy(Cell<T>* cell) {
  Cell<T>* previous = 0;
  while (cell) {
    Cell<T>* next = cell->next;
    cell->next = previous;
    previous = cell;
    cell = next;
  }
  return previous;
}

class Context {
 public:
  Context(vm::System* system, Assembler* assembler, vm::Zone* zone,
          Compiler::Client* client);

  vm::System* system;
  Assembler* assembler;
  Architecture* arch;
  vm::Zone* zone;
  Compiler::Client* client;
  Stack* stack;
  Local* locals;
  Cell<Value>* saved;
  Event* predecessor;
  LogicalInstruction** logicalCode;
  const RegisterFile* regFile;
  RegisterAllocator regAlloc;
  RegisterResource* registerResources;
  FrameResource* frameResources;
  Resource* acquiredResources;
  ConstantPoolNode* firstConstant;
  ConstantPoolNode* lastConstant;
  uint8_t* machineCode;
  Event* firstEvent;
  Event* lastEvent;
  ForkState* forkState;
  MySubroutine* subroutine;
  Block* firstBlock;
  int logicalIp;
  unsigned constantCount;
  unsigned logicalCodeLength;
  unsigned parameterFootprint;
  unsigned localFootprint;
  unsigned machineCodeSize;
  unsigned alignedFrameSize;
  unsigned availableGeneralRegisterCount;
};

inline Aborter* getAborter(Context* c) {
  return c->system;
}

template<class T>
Cell<T>* cons(Context* c, T* value, Cell<T>* next) {
  return new (c->zone) Cell<T>(next, value);
}

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_CONTEXT_H
