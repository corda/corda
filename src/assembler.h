#ifndef ASSEMBLER_H
#define ASSEMBLER_H

#include "system.h"
#include "zone.h"

namespace vm {

enum Operation {
  Return
};

enum UnaryOperation {
  Call,
  Push,
  JumpIfLess,
  JumpIfGreater,
  JumpIfLessOrEqual,
  JumpIfGreaterOrEqual,
  JumpIfEqual,
  JumpIfNotEqual,
  Jump,
  Negate
};

enum BinaryOperation {
  LoadAddress,
  Move,
  Store1,
  Store2,
  Store4,
  Store8,
  Load1,
  Load2,
  Load2z,
  Load4,
  Load8,
  Load4To8,
  Compare,
  Add,
  Subtract,
  Multiply,
  Divide,
  Remainder,
  ShiftLeft,
  ShiftRight,
  UnsignedShiftRight,
  And,
  Or,
  Xor
};

enum OperandType {
  Constant,
  Address,
  Register,
  Memory
};

const int NoRegister = -1;
const int AnyRegister = -2;

class Promise {
 public:
  virtual ~Promise() { }

  virtual int64_t value() = 0;
  virtual bool resolved() = 0;
};

class ResolvedPromise: public Promise {
 public:
  ResolvedPromise(int64_t value): value_(value) { }

  virtual int64_t value() {
    return value_;
  }

  virtual bool resolved() {
    return true;
  }

  int64_t value_;
};

class TraceHandler {
 public:
  virtual ~TraceHandler() { }

  virtual void handleTrace(Promise* address) = 0;
};

class Assembler {
 public:
  class Operand { };

  class Constant: public Operand {
   public:
    Constant(Promise* value): value(value) { }

    Promise* value;
  };

  class Address: public Operand {
   public:
    Address(Promise* address): address(address) { }

    Promise* address;
  };

  class Register: public Operand {
   public:
    Register(int low, int high = NoRegister): low(low), high(high) { }

    int low;
    int high;
  };

  class Memory: public Operand {
   public:
    Memory(int base, int offset, int index = NoRegister, unsigned scale = 0,
           TraceHandler* traceHandler = 0):
      base(base), offset(offset), index(index), scale(scale),
      traceHandler(traceHandler)
    { }

    int base;
    int offset;
    int index;
    unsigned scale;
    TraceHandler* traceHandler;
  };

  virtual ~Assembler() { }

  virtual unsigned registerCount() = 0;

  virtual int base() = 0;
  virtual int stack() = 0;
  virtual int thread() = 0;
  virtual int returnLow() = 0;
  virtual int returnHigh() = 0;

  virtual unsigned argumentRegisterCount() = 0;
  virtual int argumentRegister(unsigned index) = 0;

  virtual int stackSyncRegister(unsigned index) = 0;

  virtual void getTargets(UnaryOperation op, unsigned size,
                          Register* a) = 0;

  virtual void getTargets(BinaryOperation op, unsigned size,
                          Register* a, Register* b) = 0;

  virtual void apply(Operation op) = 0;

  virtual void apply(UnaryOperation op, unsigned size, OperandType type,
                     Operand* operand) = 0;

  virtual void apply(BinaryOperation op, unsigned size, OperandType aType,
                     Operand* a, OperandType bType, Operand* b) = 0;

  virtual void writeTo(uint8_t* dst) = 0;

  virtual unsigned length() = 0;

  virtual void updateCall(void* returnAddress, void* newTarget) = 0;

  virtual void dispose() = 0;
};

Assembler*
makeAssembler(System* system, Allocator* allocator, Zone* zone);

} // namespace vm

#endif//ASSEMBLER_H
