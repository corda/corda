#ifndef COMPILER_H
#define COMPILER_H

#include "system.h"

namespace vm {

class Operand { };

class Promise {
 public:
  virtual ~Promise() { }

  virtual unsigned value(System* s) = 0;
};

class Compiler {
 public:
  enum SelectionType {
    S1Selection,
    S2Selection,
    Z2Selection,
    S4Selection,
    S8Selection
  };

  virtual ~Compiler() { }

  virtual Promise* poolOffset() = 0;
  virtual Promise* codeOffset() = 0;

  virtual Operand* poolAppend(Operand*) = 0;

  virtual Operand* constant(intptr_t) = 0;

  virtual void push(Operand*) = 0;
  virtual void push2(Operand*) = 0;
  virtual Operand* stack(unsigned) = 0;
  virtual Operand* stack2(unsigned) = 0;
  virtual Operand* pop() = 0;
  virtual Operand* pop2() = 0;
  virtual void pop(Operand*) = 0;
  virtual void pop2(Operand*) = 0;

  virtual Operand* stack() = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;
  virtual Operand* indirectTarget() = 0;
  virtual Operand* temporary() = 0;
  virtual void release(Operand*) = 0;

  virtual Operand* label() = 0;
  virtual void mark(Operand*) = 0;

  virtual Operand* call(Operand*) = 0;
  virtual Operand* alignedCall(Operand*) = 0;
  virtual Operand* indirectCall
  (Operand* address, unsigned argumentCount, ...) = 0;
  virtual Operand* indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...) = 0;
  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...) = 0;

  virtual void return_(Operand*) = 0;
  virtual void ret() = 0;

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

  virtual Operand* memory(Operand* base, int displacement = 0,
                          Operand* index = 0, unsigned scale = 1) = 0;

  virtual Operand* select(SelectionType, Operand*) = 0;

  virtual void prologue() = 0;
  virtual void epilogue() = 0;

  virtual void startLogicalIp(unsigned) = 0;
  virtual Operand* logicalIp(unsigned) = 0;
  virtual unsigned logicalIpToOffset(unsigned) = 0;

  virtual unsigned size() = 0;
  virtual void writeTo(void*) = 0;

  virtual void updateCall(void* returnAddress, void* newTarget);

  virtual void dispose() = 0;
};

Compiler*
makeCompiler(System* system, void* indirectCaller);

} // namespace vm

#endif//COMPILER_H
