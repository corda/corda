/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef COMPILER_H
#define COMPILER_H

#include "system.h"
#include "zone.h"
#include "assembler.h"

namespace vm {

class Compiler {
 public:
  static const unsigned Aligned  = 1 << 0;
  static const unsigned NoReturn = 1 << 1;

  class Operand { };

  virtual ~Compiler() { }

  virtual void pushState() = 0;
  virtual void popState() = 0;

  virtual void init(unsigned logicalCodeSize, unsigned localFootprint) = 0;

  virtual void visitLogicalIp(unsigned logicalIp) = 0;
  virtual void startLogicalIp(unsigned logicalIp) = 0;

  virtual Promise* machineIp(unsigned logicalIp) = 0;

  virtual Promise* poolAppend(intptr_t value) = 0;
  virtual Promise* poolAppendPromise(Promise* value) = 0;

  virtual Operand* constant(int64_t value) = 0;
  virtual Operand* promiseConstant(Promise* value) = 0;
  virtual Operand* address(Promise* address) = 0;
  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1) = 0;

  virtual Operand* stack() = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;

  virtual bool isConstant(Operand* value) = 0;
  virtual int64_t constantValue(Operand* value) = 0;

  virtual Operand* label() = 0;
  virtual void mark(Operand* label) = 0;

  virtual void push(unsigned size, Operand* value) = 0;
  virtual Operand* pop(unsigned size) = 0;
  virtual void pushed(unsigned count) = 0;
  virtual void popped(unsigned count) = 0;
  virtual Operand* peek(unsigned size, unsigned index) = 0;

  virtual Operand* call(Operand* address,
                        void* indirection,
                        unsigned flags,
                        TraceHandler* traceHandler,
                        unsigned resultSize,
                        unsigned argumentCount,
                        ...) = 0;

  virtual void return_(unsigned size, Operand* value) = 0;

  virtual void store(unsigned size, Operand* src, Operand* dst) = 0;
  virtual Operand* load(unsigned size, Operand* src) = 0;
  virtual Operand* loadz(unsigned size, Operand* src) = 0;
  virtual Operand* load4To8(Operand* src) = 0;
  virtual Operand* dup(unsigned size, Operand* src) = 0;
  virtual void cmp(unsigned size, Operand* a, Operand* b) = 0;
  virtual void jl(Operand* address) = 0;
  virtual void jg(Operand* address) = 0;
  virtual void jle(Operand* address) = 0;
  virtual void jge(Operand* address) = 0;
  virtual void je(Operand* address) = 0;
  virtual void jne(Operand* address) = 0;
  virtual void jmp(Operand* address) = 0;
  virtual Operand* add(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* sub(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* mul(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* div(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* rem(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* shl(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* shr(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* ushr(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* and_(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* or_(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* xor_(unsigned size, Operand* a, Operand* b) = 0;
  virtual Operand* neg(unsigned size, Operand* a) = 0;

  virtual unsigned compile() = 0;
  virtual unsigned poolSize() = 0;
  virtual void writeTo(uint8_t* dst) = 0;

  virtual void dispose() = 0;
};

Compiler*
makeCompiler(System* system, Assembler* assembler, Zone* zone);

} // namespace vm

#endif//COMPILER_H
