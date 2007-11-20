#ifndef COMPILER_H
#define COMPILER_H

namespace vm {

class Operand { };

class Compiler {
 public:
  virtual ~Compiler() { }

  virtual Operand* append(Operand*) = 0;
  virtual Operand* constant(intptr_t) = 0;
  virtual unsigned poolOffset() = 0;
  virtual unsigned poolOffset(Operand*) = 0;
  virtual void push(Operand*) = 0;
  virtual void push2(Operand*) = 0;
  virtual Operand* stack() = 0;
  virtual Operand* stack(unsigned) = 0;
  virtual Operand* stack2(unsigned) = 0;
  virtual Operand* pop() = 0;
  virtual Operand* pop2() = 0;
  virtual void pop(Operand*) = 0;
  virtual void pop2(Operand*) = 0;
  virtual Operand* base() = 0;
  virtual Operand* thread() = 0;
  virtual Operand* temporary() = 0;
  virtual Operand* label() = 0;
  virtual Operand* call(Operand*) = 0;
  virtual Operand* alignedCall(Operand*) = 0;
  virtual Operand* indirectCall(Operand*, unsigned, ...) = 0;
  virtual Operand* indirectCallNoReturn(Operand*, unsigned, ...) = 0;
  virtual Operand* directCall(Operand*, unsigned, ...) = 0;
  virtual void mov(Operand*, Operand*) = 0;
  virtual void cmp(Operand*, Operand*) = 0;
  virtual void jl(Operand*) = 0;
  virtual void jg(Operand*) = 0;
  virtual void jle(Operand*) = 0;
  virtual void jge(Operand*) = 0;
  virtual void je(Operand*) = 0;
  virtual void jne(Operand*) = 0;
  virtual void jmp(Operand*) = 0;
  virtual void add(Operand*, Operand*) = 0;
  virtual void sub(Operand*, Operand*) = 0;
  virtual void mul(Operand*, Operand*) = 0;
  virtual void div(Operand*, Operand*) = 0;
  virtual void rem(Operand*, Operand*) = 0;
  virtual void shl(Operand*, Operand*) = 0;
  virtual void shr(Operand*, Operand*) = 0;
  virtual void ushr(Operand*, Operand*) = 0;
  virtual void and_(Operand*, Operand*) = 0;
  virtual void or_(Operand*, Operand*) = 0;
  virtual void xor_(Operand*, Operand*) = 0;
  virtual void neg(Operand*) = 0;
  virtual void mark(Operand*) = 0;
  virtual Operand* offset(Operand*, Operand*) = 0;
  virtual Operand* offset(Operand*, unsigned) = 0;
  virtual Operand* offset1(Operand*, unsigned) = 0;
  virtual Operand* offset2(Operand*, unsigned) = 0;
  virtual Operand* offset2z(Operand*, unsigned) = 0;
  virtual Operand* offset4(Operand*, unsigned) = 0;
  virtual Operand* offset8(Operand*, unsigned) = 0;
  virtual Operand* dereference(Operand*) = 0;
  virtual Operand* dereference1(Operand*) = 0;
  virtual Operand* dereference2(Operand*) = 0;
  virtual Operand* dereference2z(Operand*) = 0;
  virtual Operand* dereference4(Operand*) = 0;
  virtual Operand* dereference8(Operand*) = 0;
  virtual Operand* select(Operand*) = 0;
  virtual Operand* select1(Operand*) = 0;
  virtual Operand* select2(Operand*) = 0;
  virtual Operand* select2z(Operand*) = 0;
  virtual Operand* select4(Operand*) = 0;
  virtual Operand* select8(Operand*) = 0;
  virtual void prologue(unsigned, unsigned) = 0;
  virtual void epilogue(Operand*) = 0;
  virtual void epilogue() = 0;
  virtual Operand* logicalIp(unsigned) = 0;
  virtual void startLogicalIp(unsigned) = 0;
  virtual unsigned size() = 0;
  virtual unsigned writeTo(uintptr_t*) = 0;
};

} // namespace vm

#endif//COMPILER_H
