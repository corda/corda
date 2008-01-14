#ifndef COMPILER_H
#define COMPILER_H

#include "system.h"
#include "zone.h"

namespace vm {

class Operand { };

class Stack { };

class Compiler;

class Promise {
 public:
  virtual ~Promise() { }

  virtual intptr_t value(Compiler*) = 0;
};

class Compiler {
 public:
  class TraceHandler {
   public:
    virtual ~TraceHandler() { }

    virtual void handleTrace(Promise* address) = 0;
  };

  virtual ~Compiler() { }

  virtual Promise* machineIp(unsigned logicalIp) = 0;

  virtual Promise* poolAppend(intptr_t) = 0;
  virtual Promise* poolAppendPromise(Promise*) = 0;

  virtual Operand* constant(int64_t) = 0;
  virtual Operand* promiseConstant(Promise*) = 0;
  virtual Operand* absolute(Promise*) = 0;
  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1,
                          TraceHandler* traceHandler = 0) = 0;

  virtual Operand* stack() = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;
  virtual Operand* indirectTarget() = 0;
  virtual Operand* temporary() = 0;
  virtual Operand* result4() = 0;
  virtual Operand* result8() = 0;
  virtual void release(Operand*) = 0;

  virtual Operand* label() = 0;
  virtual void mark(Operand*) = 0;

  virtual void indirectCall
  (Operand* address, TraceHandler* traceHandler,
   unsigned argumentCount, ...) = 0;
  virtual void indirectCallNoReturn
  (Operand* address, TraceHandler* traceHandler,
   unsigned argumentCount, ...) = 0;
  virtual void directCall
  (Operand* address, unsigned argumentCount, ...) = 0;

  virtual void call(Operand*, TraceHandler*) = 0;
  virtual void alignedCall(Operand*, TraceHandler*) = 0;
  virtual void return4(Operand*) = 0;
  virtual void return8(Operand*) = 0;
  virtual void ret() = 0;

  virtual Stack* push(Stack*, unsigned count) = 0;
  virtual Stack* pushed(Stack*, unsigned count) = 0;
  virtual Stack* pop(Stack*, unsigned count) = 0;
  virtual Operand* stack(Stack*, unsigned) = 0;

  virtual Stack* push1(Stack*, Operand*) = 0;
  virtual Stack* push2(Stack*, Operand*) = 0;
  virtual Stack* push2z(Stack*, Operand*) = 0;
  virtual Stack* push4(Stack*, Operand*) = 0;
  virtual Stack* push8(Stack*, Operand*) = 0;
  virtual Stack* pop4(Stack*, Operand*) = 0;
  virtual Stack* pop8(Stack*, Operand*) = 0;
  virtual void mov1(Operand* src, Operand* dst) = 0;
  virtual void mov2(Operand* src, Operand* dst) = 0;
  virtual void mov4(Operand* src, Operand* dst) = 0;
  virtual void mov8(Operand* src, Operand* dst) = 0;
  virtual void mov1ToW(Operand* src, Operand* dst) = 0;
  virtual void mov2ToW(Operand* src, Operand* dst) = 0;
  virtual void mov2zToW(Operand* src, Operand* dst) = 0;
  virtual void mov4To8(Operand* src, Operand* dst) = 0;
  virtual void cmp4(Operand* subtrahend, Operand* minuend) = 0;
  virtual void cmp8(Operand* subtrahend, Operand* minuend) = 0;
  virtual void jl(Operand*) = 0;
  virtual void jg(Operand*) = 0;
  virtual void jle(Operand*) = 0;
  virtual void jge(Operand*) = 0;
  virtual void je(Operand*) = 0;
  virtual void jne(Operand*) = 0;
  virtual void jmp(Operand*) = 0;
  virtual void add4(Operand* v, Operand* dst) = 0;
  virtual void add8(Operand* v, Operand* dst) = 0;
  virtual void sub4(Operand* v, Operand* dst) = 0;
  virtual void sub8(Operand* v, Operand* dst) = 0;
  virtual void mul4(Operand* v, Operand* dst) = 0;
  virtual void mul8(Operand* v, Operand* dst) = 0;
  virtual void div4(Operand* v, Operand* dst) = 0;
  virtual void div8(Operand* v, Operand* dst) = 0;
  virtual void rem4(Operand* v, Operand* dst) = 0;
  virtual void rem8(Operand* v, Operand* dst) = 0;
  virtual void shl4(Operand* v, Operand* dst) = 0;
  virtual void shl8(Operand* v, Operand* dst) = 0;
  virtual void shr4(Operand* v, Operand* dst) = 0;
  virtual void shr8(Operand* v, Operand* dst) = 0;
  virtual void ushr4(Operand* v, Operand* dst) = 0;
  virtual void ushr8(Operand* v, Operand* dst) = 0;
  virtual void and4(Operand* v, Operand* dst) = 0;
  virtual void and8(Operand* v, Operand* dst) = 0;
  virtual void or4(Operand* v, Operand* dst) = 0;
  virtual void or8(Operand* v, Operand* dst) = 0;
  virtual void xor4(Operand* v, Operand* dst) = 0;
  virtual void xor8(Operand* v, Operand* dst) = 0;
  virtual void neg4(Operand*) = 0;
  virtual void neg8(Operand*) = 0;

  virtual void prologue() = 0;
  virtual void reserve(unsigned size) = 0;
  virtual void epilogue() = 0;

  virtual void startLogicalIp(unsigned) = 0;

  virtual unsigned codeSize() = 0;
  virtual unsigned poolSize() = 0;
  virtual void writeTo(uint8_t*) = 0;

  virtual void updateCall(void* returnAddress, void* newTarget) = 0;

  virtual void dispose() = 0;
};

Compiler*
makeCompiler(System* system, Allocator* allocator, Zone* zone,
             void* indirectCaller);

} // namespace vm

#endif//COMPILER_H
