#ifndef COMPILER_H
#define COMPILER_H

#include "system.h"

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
  virtual ~Compiler() { }

  virtual Promise* machineIp() = 0;
  virtual Promise* machineIp(unsigned logicalIp) = 0;

  virtual Promise* poolAppend(intptr_t) = 0;
  virtual Promise* poolAppendPromise(Promise*) = 0;

  virtual Operand* constant(int64_t) = 0;
  virtual Operand* promiseConstant(Promise*) = 0;
  virtual Operand* absolute(Promise*) = 0;
  virtual Operand* memory(Operand* base, int displacement = 0,
                          Operand* index = 0, unsigned scale = 1) = 0;

  virtual Operand* select1(Operand*) = 0;
  virtual Operand* select2(Operand*) = 0;
  virtual Operand* select2z(Operand*) = 0;
  virtual Operand* select4(Operand*) = 0;
  virtual Operand* select8(Operand*) = 0;
  virtual Operand* select8From4(Operand*) = 0;

  virtual Operand* stack() = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;
  virtual Operand* indirectTarget() = 0;
  virtual Operand* temporary() = 0;
  virtual Operand* result() = 0;
  virtual void release(Operand*) = 0;

  virtual Operand* label() = 0;
  virtual void mark(Operand*) = 0;

  virtual Promise* indirectCall
  (Operand* address, unsigned argumentCount, ...) = 0;
  virtual void indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...) = 0;
  virtual Promise* directCall
  (Operand* address, unsigned argumentCount, ...) = 0;

  virtual void call(Operand*) = 0;
  virtual void alignedCall(Operand*) = 0;
  virtual void return_(Operand*) = 0;
  virtual void ret() = 0;

  virtual Stack* push(Stack*, unsigned count) = 0;
  virtual Stack* pushed(Stack*, unsigned count) = 0;
  virtual Stack* push(Stack*, Operand*) = 0;
  virtual Stack* pop(Stack*, unsigned count) = 0;
  virtual Stack* pop(Stack*, Operand*) = 0;
  virtual Operand* stack(Stack*, unsigned) = 0;

  virtual void mov(Operand* src, Operand* dst) = 0;
  virtual void cmp(Operand* subtrahend, Operand* minuend) = 0;
  virtual void jl(Operand*) = 0;
  virtual void jg(Operand*) = 0;
  virtual void jle(Operand*) = 0;
  virtual void jge(Operand*) = 0;
  virtual void je(Operand*) = 0;
  virtual void jne(Operand*) = 0;
  virtual void jmp(Operand*) = 0;
  virtual void add(Operand* v, Operand* dst) = 0;
  virtual void sub(Operand* v, Operand* dst) = 0;
  virtual void mul(Operand* v, Operand* dst) = 0;
  virtual void div(Operand* v, Operand* dst) = 0;
  virtual void rem(Operand* v, Operand* dst) = 0;
  virtual void shl(Operand* v, Operand* dst) = 0;
  virtual void shr(Operand* v, Operand* dst) = 0;
  virtual void ushr(Operand* v, Operand* dst) = 0;
  virtual void and_(Operand* v, Operand* dst) = 0;
  virtual void or_(Operand* v, Operand* dst) = 0;
  virtual void xor_(Operand* v, Operand* dst) = 0;
  virtual void neg(Operand*) = 0;

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
makeCompiler(System* system, void* indirectCaller);

} // namespace vm

#endif//COMPILER_H
