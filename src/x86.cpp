#include "assembler.h"

using namespace vm;

namespace {

enum Register {
  rax = 0,
  rcx = 1,
  rdx = 2,
  rbx = 3,
  rsp = 4,
  rbp = 5,
  rsi = 6,
  rdi = 7,
  r8 = 8,
  r9 = 9,
  r10 = 10,
  r11 = 11,
  r12 = 12,
  r13 = 13,
  r14 = 14,
  r15 = 15,
};

class Task {
 public:
  virtual void run(uint8_t* code) = 0;
};

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s): s(s), code(s, s, 1024), tasks(0) { }

  virtual unsigned registerCount() {
    return BytesPerWord == 4 ? 8 : 16;
  }

  virtual int base() {
    return rbp;
  }

  virtual int stack() {
    return rsp;
  }

  virtual int thread() {
    return rbx;
  }

  virtual int returnLow() {
    return rax;
  }

  virtual int returnHigh() {
    return (BytesPerWord == 4 ? rdx : NoRegister);
  }

  virtual unsigned argumentRegisterCount() {
    return (BytesPerWord == 4 ? 0 : 6);
  }

  virtual int argumentRegister(unsigned index) {
    assert(s, BytesPerWord == 8);

    switch (index) {
    case 0:
      return rdi;
    case 1:
      return rsi;
    case 2:
      return rdx;
    case 3:
      return rcx;
    case 4:
      return r8;
    case 5:
      return r9;
    default:
      abort(c);
    }
  }

  virtual int stackSyncRegister(unsigned index) {
    switch (index) {
    case 0:
      return rax;
    case 1:
      return rcx;
    case 2:
      return rdx;
    case 3:
      return rsi;
    case 4:
      return rdi;
    default:
      abort(c);
    }
  }

  virtual void getTargets(OperationType op, unsigned size,
                          int* aLow, int* aHigh,
                          int* bLow, int* bHigh)
  {
    // todo
    *aLow = NoRegister;
    *aHigh = NoRegister;
    *bLow = NoRegister;
    *bHigh = NoRegister;
  }

  virtual void apply(Operation op) {
    // todo
    abort(s);
  }

  virtual void apply(UnaryOperation op, unsigned size, OperandType type,
                     Operand* operand)
  {
    // todo
    abort(s);
  }

  virtual void apply(BinaryOperation op, unsigned size, OperandType aType,
                     OperandType bType, Operand* a, Operand* b)
  {
    // todo
    abort(s);
  }

  virtual void writeTo(uint8_t* dst) {
    memcpy(dst, code.data, code.length());
    
    for (Task* t = tasks; t; t = t->next) {
      t->run(dst);
    }
  }

  System* s;
  Vector code;
  Task* tasks;
};

} // namespace

namespace vm {

Assembler*
makeAssembler(System* system, Zone* zone)
{
  return new (zone->allocate(sizeof(MyAssembler)))
    MyAssembler(system);  
}

} // namespace vm
