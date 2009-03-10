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
  Return,
  LoadBarrier,
  StoreStoreBarrier,
  StoreLoadBarrier
};

const unsigned OperationCount = StoreLoadBarrier + 1;

enum UnaryOperation {
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
  JumpIfNotEqual
};

const unsigned UnaryOperationCount = JumpIfNotEqual + 1;

enum BinaryOperation {
  Move,
  MoveZ,
  Compare,
  Negate
};

const unsigned BinaryOperationCount = Negate + 1;

enum TernaryOperation {
  LongCompare,
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

const unsigned TernaryOperationCount = Xor + 1;

enum OperandType {
  ConstantOperand,
  AddressOperand,
  RegisterOperand,
  MemoryOperand
};

const unsigned OperandTypeCount = MemoryOperand + 1;

const int NoRegister = -1;

class Promise {
 public:
  class Listener {
   public:
    virtual void* resolve(int64_t value) = 0;

    Listener* next;
  };

  virtual int64_t value() = 0;
  virtual bool resolved() = 0;
  virtual Listener* listen(unsigned) { return 0; }
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

class ShiftMaskPromise: public Promise {
 public:
  ShiftMaskPromise(Promise* base, unsigned shift, int64_t mask):
    base(base), shift(shift), mask(mask)
  { }

  virtual int64_t value() {
    return (base->value() >> shift) & mask;
  }

  virtual bool resolved() {
    return base->resolved();
  }

  Promise* base;
  unsigned shift;
  int64_t mask;
};

class CombinedPromise: public Promise {
 public:
  CombinedPromise(Promise* low, Promise* high):
    low(low), high(high)
  { }

  virtual int64_t value() {
    return low->value() | (high->value() << 32);
  }

  virtual bool resolved() {
    return low->resolved() and high->resolved();
  }

  Promise* low;
  Promise* high;
};

class ListenPromise: public Promise {
 public:
  ListenPromise(System* s, Allocator* allocator):
    s(s), allocator(allocator), listener(0)
  { }

  virtual int64_t value() {
    abort(s);
  }

  virtual bool resolved() {
    return false;
  }

  virtual Listener* listen(unsigned sizeInBytes) {
    Listener* l = static_cast<Listener*>(allocator->allocate(sizeInBytes));
    l->next = listener;
    listener = l;
    return l;
  }

  System* s;
  Allocator* allocator;
  Listener* listener;
  Promise* promise;
};

class DelayedPromise: public ListenPromise {
 public:
  DelayedPromise(System* s, Allocator* allocator, Promise* basis,
                 DelayedPromise* next):
    ListenPromise(s, allocator), basis(basis), next(next)
  { }

  virtual int64_t value() {
    abort(s);
  }

  virtual bool resolved() {
    return false;
  }

  virtual Listener* listen(unsigned sizeInBytes) {
    Listener* l = static_cast<Listener*>(allocator->allocate(sizeInBytes));
    l->next = listener;
    listener = l;
    return l;
  }

  Promise* basis;
  DelayedPromise* next;
};

class TraceHandler {
 public:
  virtual void handleTrace(Promise* address, unsigned padIndex,
                           unsigned padding) = 0;
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
  };

  class Block {
   public:
    virtual unsigned resolve(unsigned start, Block* next) = 0;
  };

  class Architecture {
   public:
    virtual unsigned registerCount() = 0;

    virtual int stack() = 0;
    virtual int thread() = 0;
    virtual int returnLow() = 0;
    virtual int returnHigh() = 0;

    virtual bool condensedAddressing() = 0;

    virtual bool bigEndian() = 0;

    virtual bool reserved(int register_) = 0;

    virtual unsigned argumentFootprint(unsigned footprint) = 0;
    virtual unsigned argumentRegisterCount() = 0;
    virtual int argumentRegister(unsigned index) = 0;

    virtual void updateCall(UnaryOperation op, bool assertAlignment,
                            void* returnAddress, void* newTarget) = 0;

    virtual uintptr_t getConstant(const void* src) = 0;
    virtual void setConstant(void* dst, uintptr_t constant) = 0;

    virtual unsigned alignFrameSize(unsigned sizeInWords) = 0;

    virtual void* frameIp(void* stack) = 0;
    virtual unsigned frameHeaderSize() = 0;
    virtual unsigned frameReturnAddressSize() = 0;
    virtual unsigned frameFooterSize() = 0;
    virtual void nextFrame(void** stack, void** base) = 0;

    virtual void plan
    (UnaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     bool* thunk) = 0;

    virtual void plan
    (BinaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask,
     bool* thunk) = 0;

    virtual void plan
    (TernaryOperation op,
     unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
     unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask,
     unsigned cSize, uint8_t* cTypeMask, uint64_t* cRegisterMask,
     bool* thunk) = 0; 

    virtual void acquire() = 0;
    virtual void release() = 0;
  };

  virtual void setClient(Client* client) = 0;

  virtual Architecture* arch() = 0;

  virtual void saveFrame(unsigned stackOffset, unsigned baseOffset) = 0;
  virtual void pushFrame(unsigned argumentCount, ...) = 0;
  virtual void allocateFrame(unsigned footprint) = 0;
  virtual void popFrame() = 0;

  virtual void apply(Operation op) = 0;

  virtual void apply(UnaryOperation op,
                     unsigned aSize, OperandType aType, Operand* aOperand) = 0;

  virtual void apply(BinaryOperation op,
                     unsigned aSize, OperandType aType, Operand* aOperand,
                     unsigned bSize, OperandType bType, Operand* bOperand) = 0;

  virtual void apply(TernaryOperation op,
                     unsigned aSize, OperandType aType, Operand* aOperand,
                     unsigned bSize, OperandType bType, Operand* bOperand,
                     unsigned cSize, OperandType cType, Operand* cOperand) = 0;

  virtual void writeTo(uint8_t* dst) = 0;

  virtual Promise* offset() = 0;

  virtual Block* endBlock(bool startNew) = 0;

  virtual unsigned length() = 0;

  virtual void dispose() = 0;
};

Assembler::Architecture*
makeArchitecture(System* system);

Assembler*
makeAssembler(System* system, Allocator* allocator, Zone* zone,
              Assembler::Architecture* architecture);

} // namespace vm

#endif//ASSEMBLER_H
