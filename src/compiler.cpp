#include "compiler.h"

using namespace vm;

namespace {

class MyPromise: public Promise {
 public:
  MyPromise(int value): resolved(false), value_(value) { }

  virtual unsigned value(System* s) {
    assert(s, resolved);
    return value_;
  }

  bool resolved;
  int value_;
};

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, void* indirectCaller):
    s(s), indirectCaller(reinterpret_cast<intptr_t>(indirectCaller))
  { }

  virtual Promise* poolOffset() {
    return promises.push(PoolPromise(constantPool.length() / BytesPerWord));
  }

  virtual Promise* codeOffset() {
    return promises.push(CodePromise(code.length()));
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = operands.push
      (PoolEntry(constantPool.length() / BytesPerWord));
    constantPool.push(v);
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return operands.push(Constant(v));
  }

  virtual void push(Operand* v) {
    stack_.push(v);
  }

  virtual void push2(Operand* v) {
    stack_.push(v);
    stack_.push(0);
  }

  virtual Operand* stack(unsigned index) {
    return stack_.peek(stack.size() - index - 1);
  }

  virtual Operand* stack2(unsigned index) {
    return stack_.peek(stack.size() - index - 1);
  }

  virtual Operand* pop() {
    return stack_.pop();
  }

  virtual Operand* pop2() {
    stack_.pop();
    return stack_.pop();
  }

  virtual void pop(Operand* dst) {
    mov(stack_.pop(), dst);
  }

  virtual void pop2(Operand* dst) {
    stack_.pop();
    mov(stack_.pop(), dst);
  }

  virtual Operand* stack() {
    flushStack();
    return operands.push(Register(rsp));
  }

  virtual Operand* base() {
    return operands.push(Register(rbp));
  }

  virtual Operand* thread() {
    return operands.push(Register(rbx));
  }

  virtual Operand* indirectTarget() {
    return operands.push(Register(rax));
  }

  virtual Operand* temporary() {
    return registerPool.pop();
  }

  virtual void release(Operand* v) {
    return registerPool.push(v);
  }

  virtual Operand* label() {
    return operands.push(Label());
  }

  virtual void mark(Operand* label) {
    setLabelValue(s, label, codeOffset());
  }

  virtual Operand* call(Operand* v) {
    flushStack();
    static_cast<MyOperand*>(v)->call(a);
    return operands.push(Register(rax));
  }

  virtual Operand* alignedCall(Operand* v) {
    flushStack();
    static_cast<MyOperand*>(v)->alignedCall(a);
    return operands.push(Register(rax));    
  }

  virtual Operand* indirectCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(argumentCount, a);
    va_end(a);

    mov(address, operands.push(Register(rax)));
    constant(indirectCaller)->call(a);

    popArguments(argumentCount);
  }

  virtual Operand* indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(argumentCount, a);    
    va_end(a);

    mov(address, operands.push(Register(rax)));
    constant(indirectCaller)->call(a);
  }

  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->call(a);

    popArguments(argumentCount);
  }

  virtual void return_(Operand* v) {
    mov(v, operands.push(Register(rax)));
    a->ret();
  }

  virtual void ret() {
    a->ret();
  }

  virtual void mov(Operand* src, Operand* dst) {
    static_cast<MyOperand>(src)->mov(a, static_cast<MyOperand>(dst));
  }

  virtual void cmp(Operand* subtrahend, Operand* minuend) {
    static_cast<MyOperand>(subtrahend)->mov
      (a, static_cast<MyOperand>(minuend));
  }

  virtual void jl(Operand* v) {
    static_cast<MyOperand>(v)->jl(a);
  }

  virtual void jg(Operand* v) {
    static_cast<MyOperand>(v)->jg(a);
  }

  virtual void jle(Operand* v) {
    static_cast<MyOperand>(v)->jle(a);
  }

  virtual void jge(Operand* v) {
    static_cast<MyOperand>(v)->jge(a);
  }

  virtual void je(Operand* v) {
    static_cast<MyOperand>(v)->je(a);
  }

  virtual void jne(Operand* v) {
    static_cast<MyOperand>(v)->jne(a);
  }

  virtual void jmp(Operand* v) {
    static_cast<MyOperand>(v)->jmp(a);
  }

  virtual void add(Operand* v, Operand* dst) {
    static_cast<MyOperand>(v)->add(a, static_cast<MyOperand>(dst));
  }

  virtual void sub(Operand* v, Operand* dst) {
    static_cast<MyOperand>(v)->sub(a, static_cast<MyOperand>(dst));
  }

  virtual void mul(Operand* v, Operand* dst) {
    static_cast<MyOperand>(v)->mul(a, static_cast<MyOperand>(dst));
  }

  virtual void div(Operand* v, Operand* dst) {
    static_cast<MyOperand>(v)->div(a, static_cast<MyOperand>(dst));
  }

  virtual void rem(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->rem(a, static_cast<MyOperand>(dst));
  }

  virtual void shl(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->shl(a, static_cast<MyOperand>(dst));
  }

  virtual void shr(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->shr(a, static_cast<MyOperand>(dst));
  }

  virtual void ushr(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->ushr(a, static_cast<MyOperand>(dst));
  }

  virtual void and_(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->and_(a, static_cast<MyOperand>(dst));
  }

  virtual void or_(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->or_(a, static_cast<MyOperand>(dst));
  }

  virtual void xor_(Operand* v, Operand* dst)  {
    static_cast<MyOperand>(v)->xor_(a, static_cast<MyOperand>(dst));
  }

  virtual void neg(Operand* v)  {
    static_cast<MyOperand>(v)->neg(a);
  }

  virtual Operand* memory(Operand* base) {
    return operands.push(Memory(base, 0, 0, 1));
  }

  virtual Operand* memory(Operand* base, unsigned displacement) {
    return operands.push(Memory(base, displacement, 0, 1));
  }

  virtual Operand* memory(Operand* base, unsigned displacement,
                          Operand* index, unsigned scale)
  {
    return operands.push(Memory(base, displacement, index, scale));
  }

  virtual Operand* select1(Operand* v) {
    return operands.push(Selection(S1Selection, v));
  }

  virtual Operand* select2(Operand* v) {
    return operands.push(Selection(S2Selection, v));
  }

  virtual Operand* select2z(Operand* v) {
    return operands.push(Selection(Z2Selection, v));
  }

  virtual Operand* select4(Operand* v) {
    return operands.push(Selection(S4Selection, v));
  }

  virtual Operand* select8(Operand* v) {
    return operands.push(Selection(S8Selection, v));
  }

  virtual void prologue() {
    a->push(rbp);
    a->mov(rsp, rbp);
  }

  virtual void epilogue() {
    a->mov(rbp, rsp);
    a->pop(rbp);
  }

  virtual void startLogicalIp(unsigned v) {
    ipTable.append(IpMapping(v, code.length()));
  }

  virtual Operand* logicalIp(unsigned v) {
    return operands.push(Label(promises.push(IpPromise(v))));
  }

  virtual unsigned logicalIpToOffset(unsigned ip) {
    unsigned bottom = 0;
    unsigned top = ipTable.size();
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = ipTable.get(middle);

      if (ip == mapping->ip) {
        return mapping->offset;
      } else if (ip < mapping->ip) {
        top = middle;
      } else if (ip > mapping->ip) {
        bottom = middle + 1;
      }
    }

    abort(s);
  }

  virtual unsigned size() {
    return code.length();
  }

  virtual void writeTo(void* out) {
    // todo
  }

  virtual void updateCall(void* returnAddress, void* newTarget);

  virtual void dispose() {
    promises.dispose();
    constantPool.dispose();
    registerPool.dispose();
    ipTable.dispose();
    operands.dispose();
    code.dispose();

    s->free(this);
  }

  System* s;
  Stack code;
  Stack operands;
  Stack ipTable;
  Stack constantPool;
  Stack registerPool;
  Stack promises;
  intptr_t indirectCaller;
};

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, void* indirectCaller)
{
  return new (system->allocate(sizeof(MyCompiler)))
    MyCompiler(system, indirectCaller);
}

} // namespace vm
