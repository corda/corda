#include "compiler.h"
#include "vector.h"

using namespace vm;

namespace {

class IpMapping {
 public:
  IpMapping(unsigned ip, unsigned offset): ip(ip), offset(offset) { }

  const unsigned ip;
  const unsigned offset;
};

class Context {
 public:
  Context(System* s, void* indirectCaller):
    s(s),
    code(s, 1024),
    virtualStack(s, BytesPerWord * 32),
    operands(s, 8 * 1024),
    ipTable(s, sizeof(IpMapping) * 512),
    constantPool(s, BytesPerWord * 32),
    registerPool(s, BytesPerWord * 8),
    promises(s, 1024),
    indirectCaller(reinterpret_cast<intptr_t>(indirectCaller))
  { }

  void dispose() {
    promises.dispose();
    constantPool.dispose();
    registerPool.dispose();
    ipTable.dispose();
    operands.dispose();
    virtualStack.dispose();
    code.dispose();
  }

  System* s;
  Vector code;
  Vector<MyOperand*> virtualStack;
  Vector operands;
  Vector<IpMapping> ipTable;
  Vector<MyOperand*> constantPool;
  Vector<MyOperand*> registerPool;
  Vector promises;
  intptr_t indirectCaller;  
};

inline void NO_RETURN
abort(Context* c)
{
  abort(c->s);
}

#ifndef NDEBUG
inline void
assert(Context* c, bool v)
{
  assert(c->s, v);
}
#endif // not NDEBUG

inline void
expect(Context* c, bool v)
{
  expect(c->system, v);
}

class MyPromise: public Promise {
 public:
  enum PromiseType {
    PoolPromiseType,
    CodePromiseType,
    IpPromiseType
  };

  MyPromise(intptr_t value): resolved(false), value_(value) { }

  virtual unsigned value(System* s) {
    assert(s, resolved);
    return value_;
  }

  virtual PromiseType type() = 0;

  bool resolved;
  intptr_t value_;
};

class PoolPromise: public MyPromise {
 public:
  PoolPromise(intptr_t value): MyPromise(value) { }

  virtual PromiseType type() {
    return PoolPromiseType;
  }
};

class CodePromise: public MyPromise {
 public:
  PoolPromise(intptr_t value): MyPromise(value) { }

  virtual PromiseType type() {
    return CodePromiseType;
  }
};

class IpPromise: public MyPromise {
 public:
  PoolPromise(intptr_t value): MyPromise(value) { }

  virtual PromiseType type() {
    return IpPromiseType;
  }
};

class MyOperand: public Operand {
 public:
  enum OperandType {
    ImmediateOperandType,
    RegisterOperandType,
    MemoryOperandType
  };

  virtual ~MyOperand() { }

  virtual OperandType type() = 0;

  virtual unsigned footprint() {
    return BytesPerWord;
  }

  virtual unsigned size() = 0;

  virtual void push(Context* c) { abort(c); }

  virtual void mov(Context* c, MyOperand*) { abort(c); }

  virtual void cmp(Context* c, MyOperand*) { abort(c); }

  virtual void jl(Context* c) { abort(c); }

  virtual void jg(Context* c) { abort(c); }

  virtual void jle(Context* c) { abort(c); }

  virtual void jge(Context* c) { abort(c); }

  virtual void je(Context* c) { abort(c); }

  virtual void jne(Context* c) { abort(c); }

  virtual void jmp(Context* c) { abort(c); }

  virtual void add(Context* c, MyOperand*) { abort(c); }

  virtual void sub(Context* c, MyOperand*) { abort(c); }

  virtual void mul(Context* c, MyOperand*) { abort(c); }

  virtual void div(Context* c, MyOperand*) { abort(c); }

  virtual void rem(Context* c, MyOperand*) { abort(c); }

  virtual void shl(Context* c, MyOperand*) { abort(c); }

  virtual void shr(Context* c, MyOperand*) { abort(c); }

  virtual void ushr(Context* c, MyOperand*) { abort(c); }

  virtual void and_(Context* c, MyOperand*) { abort(c); }

  virtual void or_(Context* c, MyOperand*) { abort(c); }

  virtual void xor_(Context* c, MyOperand*) { abort(c); }

  virtual void neg(Context* c) { abort(c); }
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(intptr_t value):
    value(value)
  { }

  virtual OperandType type() {
    return ImmediateOperandType;
  }

  virtual unsigned size() {
    return sizeof(ImmediateOperand);
  }

  virtual void push(Context* c) {
    if (isInt8(value)) {
      c->code.append(0x6a);
      c->code.append(value);
    } else if (isInt32(value)) {
      c->code.append(0x68);
      c->code.append4(value);
    } else {
      RegisterOperand* tmp = temporary(c);
      mov(c, tmp);
      tmp->push(c);
      release(c, tmp);
    }
  }

  virtual void mov(Context* c, MyOperand* dst) {
    switch (dst->type()) {
    case RegisterOperandType:
      rex(c);
      c->code.append(0xb8 | registerValue(c, dst));
      c->code.appendAddress(value);
      break;

    case MemoryOperandType:
      rex(c);
      encode(c, 0xc7, 0, 0x40, 0x80, rax,
             asRegister(memoryBase(c, dst)),
             memoryDisplacement(c, dst));
      c->code.append4(v);
      break;

    default: abort(c);
    }
  }

  intptr_t value;
};

class AbsoluteOperand: public MyOperand {
 public:
  AbsoluteOperand(Promise* value):
    value(value)
  { }

  virtual OperandType type() {
    return AbsoluteOperandType;
  }

  virtual unsigned size() {
    return sizeof(AbsoluteOperand);
  }

  Promise* value;
};

class RegisterOperand: public MyOperand {
 public:
  RegisterOperand(Register value):
    value(value)
  { }

  virtual OperandType type() {
    return RegisterOperandType;
  }

  virtual unsigned size() {
    return sizeof(RegisterOperand);
  }

  virtual void push(Context* c) {
    c->code.append(0x50 | value);
  }

  virtual void mov(Context* c, MyOperand* dst) {
    switch (dst->type()) {
    case RegisterOperandType:
      if (value != registerValue(c, dst)) {
        rex(c);
        c->code.append(0x89);
        c->code.append(0xc0 | (value << 3) | registerValue(c, dst));
      }
      break;

    case MemoryOperandType:
      rex(c);
      encode(c, 0x89, 0, 0x40, 0x80, src,
             asRegister(memoryBase(c, dst)),
             memoryDisplacement(c, dst));
      break;

    default: abort(c);
    }
  }

  Register value;
};

RegisterOperand*
temporary(Context* c)
{
  return c->registerPool.pop<RegisterOperand*>();
}

void
release(Context* c, RegisterOperand* v)
{
  return c->registerPool.push(v);
}

void
encode(Context* c, uint8_t instruction, uint8_t zeroPrefix,
       uint8_t bytePrefix, uint8_t wordPrefix,
       Register a, Register b, int32_t offset)
{
  c->code.append(instruction);

  uint8_t prefix;
  if (offset == 0 and b != rbp) {
    prefix = zeroPrefix;
  } else if (isByte(offset)) {
    prefix = bytePrefix;
  } else {
    prefix = wordPrefix;
  }

  c->code.append(prefix | (a << 3) | b);

  if (b == rsp) {
    c->code.append(0x24);
  }

  if (offset == 0 and b != rbp) {
    // do nothing
  } else if (isInt8(offset)) {
    c->code.append(offset);
  } else {
    c->code.append4(offset);
  }
}

Register
asRegister(Context* c, MyOperand* v)
{
  if (v->type() == RegisterOperandType) {
    return registerValue(v);
  } else {
    assert(c, v->type() == MemoryOperandType);
 
    RegisterOperand* tmp = temporary(c);
    v->mov(c, tmp);
    Register r = tmp->value;
    release(c, tmp);
    return r;
  }
}

class MemoryOperand: public MyOperand {
 public:
  MemoryOperand(MyOperand* base, int displacement, MyOperand* index,
                unsigned scale):
    base(base),
    displacement(displacement),
    index(index),
    scale(scale)
  { }

  virtual OperandType type() {
    return MemoryOperandType;
  }

  virtual unsigned size() {
    return sizeof(MemoryOperand);
  }

  virtual bool isStackReference() {
    return false;
  }

  virtual void push(Context* c) {
    assert(c, index == 0);
    assert(c, scale == 0);

    encode(c, 0xff, 0x30, 0x70, 0xb0, rax, asRegister(c, base), displacement);
  }

  virtual void mov(Context* c, MyOperand* dst) {
    switch (dst->type()) {
    case RegisterOperandType:
      rex(c);
      encode(c, 0x8b, 0, 0x40, 0x80, registerValue(c, dst),
             base, displacement);
      break;

    case MemoryOperandType: {
      RegisterOperand* tmp = temporary(c);
      mov(c, tmp);
      tmp->mov(c, dst);
      release(c, tmp);
    } break;

    default: abort(t);
    }
  }

  MyOperand* base;
  int displacement;
  MyOperand* index;
  unsigned scale;
};

class StackOperand: public MemoryOperand {
 public:
  StackOperand(MyOperand* base, int displacement):
    MemoryOperand(base, displacement, 0, 1)
  { }

  virtual unsigned size() {
    return sizeof(StackOperand);
  }

  virtual bool isStackReference() {
    return true;
  }
};

class SelectionOperand: public MyOperand {
 public:
  SelectionOperand(Compiler::SelectionType type, MyOperand* base):
    type(type), base(base)
  { }

  virtual OperandType type() {
    return SelectionOperandType;
  }

  virtual unsigned footprint() {
    if (type == Compiler::S8Selection) {
      return 8;
    } else {
      return 4;
    }
  }

  virtual unsigned size() {
    return sizeof(SelectionOperand);
  }

  Compiler::SelectionType type;
  MyOperand* base;
};

ImmediateOperand*
immediate(Context* c, intptr_t v)
{
  return c->operands.push(ImmediateOperand(v));
}

AbsoluteOperand*
absolute(Context* c, intptr_t v)
{
  return c->operands.push(AbsoluteOperand(v));
}

RegisterOperand*
register_(Context* c, Register v)
{
  return c->operands.push(RegisterOperand(v));
}

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale)
{
  return c->operands.push(MemoryOperand(base, displacement, index, scale));
}

StackOperand*
stack(Context* c, int displacement)
{
  return c->operands.push(StackOperand(register_(c, rbp), displacement));
}

MyOperand*
selection(Context* c, Compiler::SelectionType type, MyOperand* base)
{
  if ((type == S4Selection and BytesPerWord == 4)
      or (type == S8Selection and BytesPerWord == 8))
  {
    return base;
  } else {
    return c->operands.push(SelectionOperand(type, base));
  }
}

bool
isStackReference(Context* c, MyOperand* v)
{
  return v->type() == MemoryOperandType
    and static_cast<MemoryOperand*>(v)->isStackReference();
}

void
flushStack(Context* c)
{
  Stack newVirtualStack;
  for (unsigned i = 0; i < c->virtualStack.length();) {
    MyOperand* v = c->virtualStack.peek<MyOperand*>(i);

    if (not isStackReference(c, v)) {
      v->push(c);

      if (v->footprint() / BytesPerWord == 2) {
        newVirtualStack.push(stack(c, c->stackIndex + 4));
      } else {
        newVirtualStack.push(stack(c, c->stackIndex));
      }
    } else {
      newVirtualStack.push(v, v->size());
    }

    i += v->size();
  }

  c->virtualStack.swap(&newVirtualStack);
}

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, void* indirectCaller):
    c(s, indirectCaller)
  { }

  virtual Promise* poolOffset() {
    return c.promises.push(PoolPromise(constantPool.length() / BytesPerWord));
  }

  virtual Promise* codeOffset() {
    return c.promises.push(CodePromise(code.length()));
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = absolute(&c, poolOffset());
    constantPool.push(static_cast<MyOperand*>(v));
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return immediate(&c, v);
  }

  virtual void push(Operand* v) {
    c.virtualStack.push(static_cast<MyOperand*>(v));
  }

  virtual void push2(Operand* v) {
    push(v);
    if (BytesPerWord == 8) push(0);
  }

  virtual Operand* stack(unsigned index) {
    return c.virtualStack.peek(stack.size() - index - 1);
  }

  virtual Operand* stack2(unsigned index) {
    return c.virtualStack.peek(stack.size() - index - 1);
  }

  virtual Operand* pop() {
    return pop(c);
  }

  virtual Operand* pop2() {
    if (BytesPerWord == 8) pop();
    return pop();
  }

  virtual void pop(Operand* dst) {
    pop(c)->mov(&c, static_cast<MyOperand*>(dst));
  }

  virtual void pop2(Operand* dst) {
    if (BytesPerWord == 8) pop();
    pop(dst);
  }

  virtual Operand* stack() {
    flushStack(&c);
    return register_(&c, rsp);
  }

  virtual Operand* base() {
    return register_(&c, rbp);
  }

  virtual Operand* thread() {
    return register_(&c, rbx);
  }

  virtual Operand* indirectTarget() {
    return register_(&c, rax);
  }

  virtual Operand* temporary() {
    return ::temporary(&c);
  }

  virtual void release(Operand* v) {
    assert(&c, static_cast<MyOperand>(v)->type() == RegisterOperandType);
    return ::release(&c, static_cast<RegisterOperand*>(v));
  }

  virtual Operand* label() {
    return absolute(&c, 0);
  }

  virtual void mark(Operand* label) {
    setAbsoluteValue(&c, static_cast<MyOperand*>(label), codeOffset());
  }

  virtual Operand* call(Operand* v) {
    flushStack(&c);
    static_cast<MyOperand*>(v)->call(&c);
    return register_(&c, rax);
  }

  virtual Operand* alignedCall(Operand* v) {
    flushStack(&c);
    static_cast<MyOperand*>(v)->alignedCall(&c);
    return register_(&c, rax);
  }

  virtual Operand* indirectCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->mov(&c, register_(rax));
    immediate(&c, indirectCaller)->call(&c);

    popArguments(&c, argumentCount);
  }

  virtual Operand* indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);    
    va_end(a);

    static_cast<MyOperand*>(address)->mov(&c, register_(rax));
    immediate(&c, indirectCaller)->call(&c);
  }

  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->call(&c);

    popArguments(&c, argumentCount);
  }

  virtual void return_(Operand* v) {
    static_cast<MyOperand*>(v)->mov(&c, register_(rax));
    ret(c);
  }

  virtual void ret() {
    ret(c);
  }

  virtual void mov(Operand* src, Operand* dst) {
    static_cast<MyOperand*>(src)->mov(&c, static_cast<MyOperand*>(dst));
  }

  virtual void cmp(Operand* subtrahend, Operand* minuend) {
    static_cast<MyOperand*>(subtrahend)->mov
      (&c, static_cast<MyOperand*>(minuend));
  }

  virtual void jl(Operand* v) {
    static_cast<MyOperand*>(v)->jl(&c);
  }

  virtual void jg(Operand* v) {
    static_cast<MyOperand*>(v)->jg(&c);
  }

  virtual void jle(Operand* v) {
    static_cast<MyOperand*>(v)->jle(&c);
  }

  virtual void jge(Operand* v) {
    static_cast<MyOperand*>(v)->jge(&c);
  }

  virtual void je(Operand* v) {
    static_cast<MyOperand*>(v)->je(&c);
  }

  virtual void jne(Operand* v) {
    static_cast<MyOperand*>(v)->jne(&c);
  }

  virtual void jmp(Operand* v) {
    static_cast<MyOperand*>(v)->jmp(&c);
  }

  virtual void add(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->add(&c, static_cast<MyOperand*>(dst));
  }

  virtual void sub(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->sub(&c, static_cast<MyOperand*>(dst));
  }

  virtual void mul(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->mul(&c, static_cast<MyOperand*>(dst));
  }

  virtual void div(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->div(&c, static_cast<MyOperand*>(dst));
  }

  virtual void rem(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->rem(&c, static_cast<MyOperand*>(dst));
  }

  virtual void shl(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->shl(&c, static_cast<MyOperand*>(dst));
  }

  virtual void shr(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->shr(&c, static_cast<MyOperand*>(dst));
  }

  virtual void ushr(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->ushr(&c, static_cast<MyOperand*>(dst));
  }

  virtual void and_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->and_(&c, static_cast<MyOperand*>(dst));
  }

  virtual void or_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->or_(&c, static_cast<MyOperand*>(dst));
  }

  virtual void xor_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->xor_(&c, static_cast<MyOperand*>(dst));
  }

  virtual void neg(Operand* v)  {
    static_cast<MyOperand*>(v)->neg(&c);
  }

  virtual Operand* memory(Operand* base, int displacement,
                          Operand* index, unsigned scale)
  {
    return memory(&c, base, displacement, index, scale);
  }

  virtual Operand* select(SelectionType type, Operand* v) {
    return selection(&c, type, v);
  }

  virtual void prologue() {
    register_(&c, rbp)->push(&c);
    register_(&c, rsp)->mov(&c, register_(&c, rbp));
  }

  virtual void epilogue() {
    register_(&c, rbp)->mov(&c, register_(&c, rsp));
    register_(&c, rbp)->pop(&c);
  }

  virtual void startLogicalIp(unsigned ip) {
    c.ipTable.push(IpMapping(ip, code.length()));
  }

  virtual Operand* logicalIp(unsigned ip) {
    return absolute(&c, promises.push(IpPromise(ip)));
  }

  virtual unsigned logicalIpToOffset(unsigned ip) {
    unsigned bottom = 0;
    unsigned top = c.ipTable.size();
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = c.ipTable.get(middle);

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
    return c.code.length();
  }

  virtual void writeTo(void* out) {
    // todo
  }

  virtual void updateCall(void* returnAddress, void* newTarget) {
    // todo
  }

  virtual void dispose() {
    c.dispose();

    s->free(this);
  }

  Context c;
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
