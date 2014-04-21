/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_LIR_H
#define AVIAN_CODEGEN_LIR_H

namespace avian {
namespace codegen {
class Promise;

namespace lir {
enum Operation {
  #define LIR_OP_0(x) x,
  #define LIR_OP_1(x)
  #define LIR_OP_2(x)
  #define LIR_OP_3(x)
  #include "lir-ops.inc.cpp"
  #undef LIR_OP_0
  #undef LIR_OP_1
  #undef LIR_OP_2
  #undef LIR_OP_3
};

const unsigned OperationCount = Trap + 1;

enum UnaryOperation {
  #define LIR_OP_0(x)
  #define LIR_OP_1(x) x,
  #define LIR_OP_2(x)
  #define LIR_OP_3(x)
  #include "lir-ops.inc.cpp"
  #undef LIR_OP_0
  #undef LIR_OP_1
  #undef LIR_OP_2
  #undef LIR_OP_3
  
  NoUnaryOperation = -1
};

const unsigned UnaryOperationCount = AlignedJump + 1;

enum BinaryOperation {
  #define LIR_OP_0(x)
  #define LIR_OP_1(x)
  #define LIR_OP_2(x) x,
  #define LIR_OP_3(x)
  #include "lir-ops.inc.cpp"
  #undef LIR_OP_0
  #undef LIR_OP_1
  #undef LIR_OP_2
  #undef LIR_OP_3
  
  NoBinaryOperation = -1
};

const unsigned BinaryOperationCount = Absolute + 1;

enum TernaryOperation {
  #define LIR_OP_0(x)
  #define LIR_OP_1(x)
  #define LIR_OP_2(x)
  #define LIR_OP_3(x) x,
  #include "lir-ops.inc.cpp"
  #undef LIR_OP_0
  #undef LIR_OP_1
  #undef LIR_OP_2
  #undef LIR_OP_3
  
  NoTernaryOperation = -1
};

const unsigned TernaryOperationCount
= JumpIfFloatGreaterOrEqualOrUnordered + 1;

const unsigned NonBranchTernaryOperationCount = FloatMin + 1;
const unsigned BranchOperationCount
= JumpIfFloatGreaterOrEqualOrUnordered - FloatMin;

enum OperandType {
  ConstantOperand,
  AddressOperand,
  RegisterOperand,
  MemoryOperand
};

enum ValueType {
  ValueGeneral,
  ValueFloat
};

const unsigned OperandTypeCount = MemoryOperand + 1;

const int NoRegister = -1;

inline bool isBranch(lir::TernaryOperation op) {
  return op > FloatMin;
}

inline bool isFloatBranch(lir::TernaryOperation op) {
  return op > JumpIfNotEqual;
}

inline bool isGeneralBranch(lir::TernaryOperation op) {
  return isBranch(op) && !isFloatBranch(op);
}

inline bool isGeneralBinaryOp(lir::TernaryOperation op) {
  return op < FloatAdd;
}

inline bool isFloatBinaryOp(lir::TernaryOperation op) {
  return op >= FloatAdd && op <= FloatMin;
}

inline bool isGeneralUnaryOp(lir::BinaryOperation op) {
  return op == Negate || op == Absolute;
}

inline bool isFloatUnaryOp(lir::BinaryOperation op) {
  return op == FloatNegate || op == FloatSquareRoot || op == FloatAbsolute;
}

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
  Memory(int base, int offset, int index = NoRegister, unsigned scale = 1):
    base(base), offset(offset), index(index), scale(scale)
  { }

  int base;
  int offset;
  int index;
  unsigned scale;
};

} // namespace lir
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_LIR_H
