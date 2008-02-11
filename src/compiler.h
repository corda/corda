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

  virtual Operand* constant(intptr_t value) = 0;
  virtual Operand* constant8(int64_t value) = 0;
  virtual Operand* promiseConstant(Promise* value) = 0;
  virtual Operand* promiseConstant8(Promise* value) = 0;
  virtual Operand* address(Promise* address) = 0;
  virtual Operand* memory(Operand* base,
                          int displacement = 0,
                          Operand* index = 0,
                          unsigned scale = 1,
                          TraceHandler* traceHandler = 0) = 0;
  virtual Operand* stack() = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;

  virtual Operand* label() = 0;
  virtual void mark(Operand* label) = 0;

  virtual void push(Operand* value) = 0;
  virtual Operand* pop() = 0;
  virtual void push(unsigned count) = 0;
  virtual void pop(unsigned count) = 0;
  virtual Operand* peek(unsigned index) = 0;

  virtual Operand* call(Operand* address,
                        void* indirection,
                        unsigned flags,
                        TraceHandler* traceHandler,
                        unsigned resultSize,
                        unsigned argumentCount,
                        ...) = 0;
  virtual void return_(Operand* value) = 0;

  virtual void store(Operand* src, Operand* dst) = 0;
  virtual void store1(Operand* src, Operand* dst) = 0;
  virtual void store2(Operand* src, Operand* dst) = 0;
  virtual void store4(Operand* src, Operand* dst) = 0;
  virtual void store8(Operand* src, Operand* dst) = 0;
  virtual Operand* load(Operand* src) = 0;
  virtual Operand* load1(Operand* src) = 0;
  virtual Operand* load2(Operand* src) = 0;
  virtual Operand* load2z(Operand* src) = 0;
  virtual Operand* load4(Operand* src) = 0;
  virtual Operand* load8(Operand* src) = 0;
  virtual Operand* load4To8(Operand* src) = 0;
  virtual void cmp(Operand* a, Operand* b) = 0;
  virtual void jl(Operand* address) = 0;
  virtual void jg(Operand* address) = 0;
  virtual void jle(Operand* address) = 0;
  virtual void jge(Operand* address) = 0;
  virtual void je(Operand* address) = 0;
  virtual void jne(Operand* address) = 0;
  virtual void jmp(Operand* address) = 0;
  virtual Operand* add(Operand* a, Operand* b) = 0;
  virtual Operand* sub(Operand* a, Operand* b) = 0;
  virtual Operand* mul(Operand* a, Operand* b) = 0;
  virtual Operand* div(Operand* a, Operand* b) = 0;
  virtual Operand* rem(Operand* a, Operand* b) = 0;
  virtual Operand* shl(Operand* a, Operand* b) = 0;
  virtual Operand* shr(Operand* a, Operand* b) = 0;
  virtual Operand* ushr(Operand* a, Operand* b) = 0;
  virtual Operand* and_(Operand* a, Operand* b) = 0;
  virtual Operand* or_(Operand* a, Operand* b) = 0;
  virtual Operand* xor_(Operand* a, Operand* b) = 0;
  virtual Operand* neg(Operand* a) = 0;

  virtual unsigned compile() = 0;
  virtual unsigned poolSize() = 0;
  virtual void writeTo(uint8_t* dst) = 0;

  virtual void dispose() = 0;
};

Compiler*
makeCompiler(System* system, Assembler* assembler, Zone* zone);

} // namespace vm

#endif//COMPILER_H
