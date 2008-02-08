#ifndef ASSEMBLER_H
#define ASSEMBLER_H

namespace vm {

enum OperationType {
  Call,
  Return,
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
  JumpIfLess,
  JumpIfGreater,
  JumpIfLessOrEqual,
  JumpIfGreaterOrEqual,
  JumpIfEqual,
  JumpIfNotEqual,
  Jump,
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
  Xor,
  Negate
};

class Assembler {
 public:
  virtual unsigned registerCount() = 0;

  virtual int base() = 0;
  virtual int stack() = 0;
  virtual int thread() = 0;
  virtual int returnLow() = 0;
  virtual int returnHigh() = 0;

  virtual unsigned argumentRegisterCount() = 0;
  virtual int argumentRegister(unsigned index) = 0;

  virtual int stackSyncRegister(unsigned index) = 0;

  virtual void getTargets(OperationType op, unsigned size,
                          int* aLow, int* aHigh,
                          int* bLow, int* bHigh) = 0;

  virtual void appendC(OperationType op, unsigned size, Promise* value) = 0;
  virtual void appendR(OperationType op, unsigned size, int low, int high) = 0;
  virtual void appendM(OperationType op, unsigned size, int base, int offset,
                       int index, unsigned scale,
                       TraceHandler* traceHandler) = 0;

  virtual void appendCR(OperationType op, unsigned size, Promise* aValue,
                        int bLow, int bHigh) = 0;
  virtual void appendRR(OperationType op, unsigned size, int aLow, int aHigh,
                        int bLow, int bHigh) = 0;
  virtual void appendMR(OperationType op, unsigned size,
                        int aBase, int aOffset, int aIndex, unsigned aScale,
                        TraceHandler* aTraceHandler,
                        int bLow, int bHigh) = 0;

  virtual void appendCM(OperationType op, unsigned size, Promise* aValue,
                        int bBase, int bOffset, int bIndex, unsigned bScale,
                        TraceHandler* bTraceHandler) = 0;
  virtual void appendRM(OperationType op, unsigned size, int aLow, int aHigh,
                        int bBase, int bOffset, int bIndex, unsigned bScale,
                        TraceHandler* bTraceHandler) = 0;
  virtual void appendMM(OperationType op, unsigned size,
                        int aBase, int aOffset, int aIndex, unsigned aScale,
                        TraceHandler* aTraceHandler,
                        int bBase, int bOffset, int bIndex, unsigned bScale,
                        TraceHandler* bTraceHandler) = 0;
};

} // namespace vm

#endif//ASSEMBLER_H
