/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_CONTEXT_H
#define AVIAN_CODEGEN_COMPILER_CONTEXT_H

#include <avian/codegen/assembler.h>
#include <avian/codegen/compiler.h>
#include <avian/util/list.h>

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
class Block;

template <class T>
List<T>* reverseDestroy(List<T>* cell)
{
  List<T>* previous = 0;
  while (cell) {
    List<T>* next = cell->next;
    cell->next = previous;
    previous = cell;
    cell = next;
  }
  return previous;
}

class LogicalCode {
 private:
  util::Slice<LogicalInstruction*> logicalCode;

 public:
  LogicalCode() : logicalCode(0, 0)
  {
  }

  void init(vm::Zone* zone, size_t count)
  {
    // leave room for logical instruction -1
    size_t realCount = count + 1;

    logicalCode
        = util::Slice<LogicalInstruction*>::allocAndSet(zone, realCount, 0);
  }

  void extend(vm::Zone* zone, size_t more)
  {
    util::Slice<LogicalInstruction*> newCode
        = logicalCode.cloneAndSet(zone, logicalCode.count + more, 0);

    for (size_t i = 0; i < logicalCode.count; i++) {
      assertT((vm::System*)0, logicalCode[i] == newCode[i]);
    }

    logicalCode = newCode;
  }

  size_t count()
  {
    return logicalCode.count - 1;
  }

  LogicalInstruction*& operator[](int index)
  {
    // leave room for logical instruction -1
    return logicalCode[index + 1];
  }
};

class Context {
 public:
  Context(vm::System* system,
          Assembler* assembler,
          vm::Zone* zone,
          Compiler::Client* client);

  vm::System* system;
  Assembler* assembler;
  Architecture* arch;
  vm::Zone* zone;
  Compiler::Client* client;
  Stack* stack;
  Local* locals;
  List<Value*>* saved;
  Event* predecessor;
  LogicalCode logicalCode;
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
  Block* firstBlock;
  int logicalIp;
  unsigned constantCount;
  unsigned parameterFootprint;
  unsigned localFootprint;
  unsigned machineCodeSize;
  unsigned alignedFrameSize;
  unsigned availableGeneralRegisterCount;
  ir::TargetInfo targetInfo;
};

inline Aborter* getAborter(Context* c)
{
  return c->system;
}

template <class T>
List<T>* cons(Context* c, const T& value, List<T>* next)
{
  return new (c->zone) List<T>(value, next);
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_CONTEXT_H
