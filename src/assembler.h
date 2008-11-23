/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef ASSEMBLER_H
#define ASSEMBLER_H

#include "system.h"
#include "zone.h"

namespace vm {

enum Operation {
  Return
};

const unsigned OperationCount = Return + 1;

enum UnaryOperation {
  Push,
  Pop,
  Call,
  LongCall,
  AlignedCall,
  Jump,
  LongJump,
  JumpIfLess,
  JumpIfGreater,
  JumpIfLessOrEqual,
  JumpIfGreaterOrEqual,
  JumpIfEqual,
  JumpIfNotEqual,
  Negate
};

const unsigned UnaryOperationCount = Negate + 1;

enum BinaryOperation {
  LoadAddress,
  Move,
  MoveZ,
  Move4To8,
  Swap,
  LongCompare,
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

const unsigned BinaryOperationCount = Xor + 1;

enum OperandType {
  ConstantOperand,
  AddressOperand,
  RegisterOperand,
  MemoryOperand
};

const unsigned OperandTypeCount = MemoryOperand + 1;

const int NoRegister = -1;
const int AnyRegister = -2;

class Promise {
 public:
  virtual int64_t value() = 0;
  virtual bool resolved() = 0;
  virtual bool offer(void*) { return false; }
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

class OfferPromise: public Promise {
 public:
  OfferPromise(System* s): s(s), offset(0) { }

  virtual int64_t value() {
    abort(s);
  }

  virtual bool resolved() {
    return false;
  }

  virtual bool offer(void* offset) {
    this->offset = offset;
    return true;
  }

  System* s;
  void* offset;
};

class TraceHandler {
 public:
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
    Memory(int base, int offset, int index = NoRegister, unsigned scale = 0):
      base(base), offset(offset), index(index), scale(scale)
    { }

    int base;
    int offset;
    int index;
    unsigned scale;
  };

  class Client {
   public:
    virtual int acquireTemporary
    (uint32_t mask = ~static_cast<uint32_t>(0)) = 0;
    virtual void releaseTemporary(int r) = 0;

    virtual void save(int r) = 0;
    virtual void restore(int r) = 0;
  };

  virtual void setClient(Client* client) = 0;

  virtual unsigned registerCount() = 0;

  virtual int base() = 0;
  virtual int stack() = 0;
  virtual int thread() = 0;
  virtual int returnLow() = 0;
  virtual int returnHigh() = 0;

  virtual unsigned argumentRegisterCount() = 0;
  virtual int argumentRegister(unsigned index) = 0;

  virtual void plan(UnaryOperation op, unsigned size, uint8_t* typeMask,
                    uint64_t* registerMask, bool* thunk) = 0;

  virtual void plan(BinaryOperation op, unsigned size, uint8_t* aTypeMask,
                    uint64_t* aRegisterMask, uint8_t* bTypeMask,
                    uint64_t* bRegisterMask, bool* thunk) = 0;

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
