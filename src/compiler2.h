#ifndef COMPILER_H
#define COMPILER_H

#include "system.h"
#include "zone.h"
#include "assembler.h"

namespace vm {

class Compiler {
 public:
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

  virtual Operand* call(Operand* address,
                        unsigned resultSize = 4,
                        unsigned argumentCount = 0,
                        bool aligned = false,
                        TraceHandler* traceHandler = 0) = 0;
  virtual void return_(Operand* value) = 0;

  virtual void store1(Operand* src, Operand* dst) = 0;
  virtual void store2(Operand* src, Operand* dst) = 0;
  virtual void store4(Operand* src, Operand* dst) = 0;
  virtual void store8(Operand* src, Operand* dst) = 0;
  virtual Operand* load1(Operand* src) = 0;
  virtual Operand* load2(Operand* src) = 0;
  virtual Operand* load2z(Operand* src) = 0;
  virtual Operand* load4(Operand* src) = 0;
  virtual Operand* load8(Operand* src) = 0;
  virtual void jl(Operand* a, Operand* b, Operand* address) = 0;
  virtual void jg(Operand* a, Operand* b, Operand* address) = 0;
  virtual void jle(Operand* a, Operand* b, Operand* address) = 0;
  virtual void jge(Operand* a, Operand* b, Operand* address) = 0;
  virtual void je(Operand* a, Operand* b, Operand* address) = 0;
  virtual void jne(Operand* a, Operand* b, Operand* address) = 0;
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
makeCompiler(System* system, Allocator* allocator, Zone* zone,
             void* indirectCaller);

} // namespace vm

#endif//COMPILER_H
