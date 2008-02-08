#ifndef ASSEMBLER_H
#define ASSEMBLER_H

namespace vm {

enum Operation {
  Return
};

enum UnaryOperation {
  Call,
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
  virtual int64_t value() = 0;
  virtual bool resolved() = 0;
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
    Register(int low, int high): low(low), high(high) { }

    int low;
    int high;
  };

  class Memory: public Operand {
   public:
    Memory(int base, int offset, int index, unsigned scale,
           TraceHandler* traceHandler):
      base(base), offset(offset), index(index), scale(scale),
      traceHandler(traceHandler)
    { }

    int base;
    int offset;
    int index;
    unsigned scale;
    TraceHandler* traceHandler;
  };

  virtual unsigned registerCount() = 0;

  virtual int base() = 0;
  virtual int stack() = 0;
  virtual int thread() = 0;
  virtual int returnLow() = 0;
  virtual int returnHigh() = 0;

  virtual unsigned argumentRegisterCount() = 0;
  virtual int argumentRegister(unsigned index) = 0;

  virtual int stackSyncRegister(unsigned index) = 0;

  virtual void getTargets(BinaryOperation op, unsigned size,
                          Register* a, Register* b) = 0;

  virtual void apply(Operation op) = 0;

  virtual void apply(UnaryOperation op, unsigned size, OperandType type,
                     Operand* operand) = 0;

  virtual void apply(BinaryOperation op, unsigned size, OperandType aType,
                     OperandType bType, Operand* a, Operand* b) = 0;

  virtual void writeTo(uint8_t* dst) = 0;
};

Assembler*
makeAssembler(System* system, Allocator* allocator, Zone* zone);

} // namespace vm

#endif//ASSEMBLER_H
