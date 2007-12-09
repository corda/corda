#include "compiler.h"
#include "vector.h"

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
    logicalStack(s, BytesPerWord * 32),
    operands(s, 8 * 1024),
    ipTable(s, sizeof(IpMapping) * 512),
    constantPool(s, BytesPerWord * 32),
    registerPool(s, BytesPerWord * 8),
    promises(s, 1024),
    indirectCaller(reinterpret_cast<intptr_t>(indirectCaller)),
    stackIndex(- BytesPerWord)
  { }

  void dispose() {
    promises.dispose();
    constantPool.dispose();
    registerPool.dispose();
    ipTable.dispose();
    operands.dispose();
    logicalStack.dispose();
    code.dispose();
  }

  System* s;
  Vector code;
  Vector logicalStack;
  Vector operands;
  Vector ipTable;
  Vector constantPool;
  Vector registerPool;
  Vector promises;
  intptr_t indirectCaller;
  int stackIndex;
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
  expect(c->s, v);
}

class MyPromise: public Promise {
 public:
  enum PromiseType {
    PoolPromiseType,
    CodePromiseType,
    IpPromiseType
  };

  MyPromise(intptr_t value): resolved(false), value_(value) { }

  virtual unsigned value(System* s UNUSED) {
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
  CodePromise(intptr_t value): MyPromise(value) { }

  virtual PromiseType type() {
    return CodePromiseType;
  }
};

class IpPromise: public MyPromise {
 public:
  IpPromise(intptr_t value): MyPromise(value) { }

  virtual PromiseType type() {
    return IpPromiseType;
  }
};

class MyOperand: public Operand {
 public:
  enum OperandType {
    ImmediateOperandType,
    AbsoluteOperandType,
    RegisterOperandType,
    MemoryOperandType,
    SelectionOperandType
  };

  virtual ~MyOperand() { }

  virtual OperandType type() = 0;

  virtual unsigned footprint() {
    return BytesPerWord;
  }

  virtual unsigned size() = 0;

  virtual void logicalPush(Context* c) { abort(c); }

  virtual void logicalFlush(Context* c) { abort(c); }

  virtual void push(Context* c) { abort(c); }

  virtual void pop(Context* c) { abort(c); }

  virtual void mov(Context* c, MyOperand*) { abort(c); }

  virtual void cmp(Context* c, MyOperand*) { abort(c); }

  virtual void call(Context* c) { abort(c); }

  virtual void alignedCall(Context* c) { abort(c); }

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

inline bool
isInt8(intptr_t v)
{
  return v == static_cast<int8_t>(v);
}

inline bool
isInt32(intptr_t v)
{
  return v == static_cast<int32_t>(v);
}

Register
registerValue(Context* c, MyOperand* v);

Promise*
absoluteValue(Context* c, MyOperand* v);

void
setAbsoluteValue(Context* c, MyOperand* v, Promise* value);

MyOperand*
memoryBase(Context* c, MyOperand* v);

int
memoryDisplacement(Context* c, MyOperand* v);

MyOperand*
memoryIndex(Context* c, MyOperand* v);

unsigned
memoryScale(Context* c, MyOperand* v);

Register
asRegister(Context* c, MyOperand* v);

void
rex(Context* c)
{
  if (BytesPerWord == 8) {
    c->code.append(0x48);
  }
}

void
ret(Context* c)
{
  c->code.append(0xc3);
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
  } else if (isInt8(offset)) {
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
      encode(c, 0x89, 0, 0x40, 0x80, value,
             asRegister(c, memoryBase(c, dst)),
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
  c->registerPool.push(v);
}

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
             asRegister(c, memoryBase(c, dst)),
             memoryDisplacement(c, dst));
      c->code.append4(value);
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

Register
asRegister(Context* c, MyOperand* v)
{
  if (v->type() == MyOperand::RegisterOperandType) {
    return registerValue(c, v);
  } else {
    assert(c, v->type() == MyOperand::MemoryOperandType);
 
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
             asRegister(c, base), displacement);
      break;

    case MemoryOperandType: {
      RegisterOperand* tmp = temporary(c);
      mov(c, tmp);
      tmp->mov(c, dst);
      release(c, tmp);
    } break;

    default: abort(c);
    }
  }

  MyOperand* base;
  int displacement;
  MyOperand* index;
  unsigned scale;
};

class SelectionOperand: public MyOperand {
 public:
  enum SelectionType {
    S1Selection,
    S2Selection,
    Z2Selection,
    S4Selection,
    S8Selection
  };

  SelectionOperand(SelectionType type, MyOperand* base):
    selectionType(type), base(base)
  { }

  virtual OperandType type() {
    return SelectionOperandType;
  }

  virtual unsigned footprint() {
    if (selectionType == S8Selection) {
      return 8;
    } else {
      return 4;
    }
  }

  virtual unsigned size() {
    return sizeof(SelectionOperand);
  }

  SelectionType selectionType;
  MyOperand* base;
};

ImmediateOperand*
immediate(Context* c, intptr_t v)
{
  return c->operands.push(ImmediateOperand(v));
}

AbsoluteOperand*
absolute(Context* c, Promise* v)
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

MemoryOperand*
stack(Context* c, int displacement)
{
  return c->operands.push
    (MemoryOperand(register_(c, rbp), displacement, 0, 1));
}

MyOperand*
selection(Context* c, SelectionOperand::SelectionType type, MyOperand* base)
{
  if ((type == SelectionOperand::S4Selection and BytesPerWord == 4)
      or (type == SelectionOperand::S8Selection and BytesPerWord == 8))
  {
    return base;
  } else {
    return c->operands.push(SelectionOperand(type, base));
  }
}

void
flushStack(Context* c)
{
  for (unsigned i = 0; i < c->logicalStack.length(); i += BytesPerWord) {
    (*c->logicalStack.peek<MyOperand*>(i))->logicalFlush(c);
  }
}

Register
gpRegister(Context* c, unsigned index)
{
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

unsigned
pushArguments(Context* c, unsigned count, va_list list)
{
  flushStack(c);
  
  MyOperand* arguments[count];
  unsigned footprint = 0;
  for (unsigned i = 0; i < count; ++i) {
    arguments[i] = va_arg(list, MyOperand*);
    footprint += pad(arguments[i]->footprint());
  }

  const int GprCount = 6;
  for (int i = count - 1; i >= 0; --i) {
    if (BytesPerWord == 8 and i < GprCount) {
      arguments[i]->mov(c, register_(c, gpRegister(c, i)));
    } else {
      arguments[i]->push(c);
    }
  }

  if (BytesPerWord == 8) {
    if (footprint > GprCount * BytesPerWord) {
      return footprint - GprCount * BytesPerWord;
    } else {
      return 0;
    }
  } else {
    return footprint;
  }
}

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, void* indirectCaller):
    c(s, indirectCaller)
  { }

  virtual Promise* poolOffset() {
    return c.promises.push
      (PoolPromise(c.constantPool.length() / BytesPerWord));
  }

  virtual Promise* codeOffset() {
    return c.promises.push(CodePromise(c.code.length()));
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = absolute(&c, poolOffset());
    c.constantPool.push(static_cast<MyOperand*>(v));
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return immediate(&c, v);
  }

  virtual void push(Operand* v) {
    static_cast<MyOperand*>(v)->logicalPush(&c);
  }

  virtual void push2(Operand* v) {
    push(v);
    if (BytesPerWord == 8) push(immediate(&c, 0));
  }

  virtual Operand* stack(unsigned index) {
    return c.logicalStack.peek<MyOperand>
      (c.logicalStack.length() - ((index + 1) * BytesPerWord));
  }

  virtual Operand* stack2(unsigned index) {
    return stack(index);
  }

  virtual Operand* pop() {
    return c.logicalStack.pop<MyOperand*>();
  }

  virtual Operand* pop2() {
    if (BytesPerWord == 8) pop();
    return pop();
  }

  virtual void pop(Operand* dst) {
    c.logicalStack.pop<MyOperand*>()->mov(&c, static_cast<MyOperand*>(dst));
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
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->mov(&c, register_(&c, rax));
    immediate(&c, c.indirectCaller)->call(&c);

    immediate(&c, footprint)->sub(&c, register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);    
    va_end(a);

    static_cast<MyOperand*>(address)->mov(&c, register_(&c, rax));
    immediate(&c, c.indirectCaller)->call(&c);
  }

  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->call(&c);

    immediate(&c, footprint)->sub(&c, register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void return_(Operand* v) {
    static_cast<MyOperand*>(v)->mov(&c, register_(&c, rax));
    ret();
  }

  virtual void ret() {
    ::ret(&c);
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
    return ::memory(&c, static_cast<MyOperand*>(base), displacement,
                    static_cast<MyOperand*>(index), scale);
  }

  virtual Operand* select1(Operand* v) {
    return selection(&c, SelectionOperand::S1Selection,
                     static_cast<MyOperand*>(v));
  }

  virtual Operand* select2(Operand* v) {
    return selection(&c, SelectionOperand::S2Selection, 
                     static_cast<MyOperand*>(v));
  }

  virtual Operand* select2z(Operand* v) {
    return selection(&c, SelectionOperand::Z2Selection, 
                     static_cast<MyOperand*>(v));
  }

  virtual Operand* select4(Operand* v) {
    return selection(&c, SelectionOperand::S4Selection, 
                     static_cast<MyOperand*>(v));
  }

  virtual Operand* select8(Operand* v) {
    return selection(&c, SelectionOperand::S8Selection, 
                     static_cast<MyOperand*>(v));
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
    c.ipTable.push(IpMapping(ip, c.code.length()));
  }

  virtual Operand* logicalIp(unsigned ip) {
    return absolute(&c, c.promises.push(IpPromise(ip)));
  }

  virtual unsigned logicalIpToOffset(unsigned ip) {
    unsigned bottom = 0;
    unsigned top = c.ipTable.length() / sizeof(IpMapping);
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = c.ipTable.peek<IpMapping>
        (middle * sizeof(IpMapping));

      if (ip == mapping->ip) {
        return mapping->offset;
      } else if (ip < mapping->ip) {
        top = middle;
      } else if (ip > mapping->ip) {
        bottom = middle + 1;
      }
    }

    abort(&c);
  }

  virtual unsigned size() {
    return c.code.length();
  }

  virtual void writeTo(void*) {
    // todo
  }

  virtual void updateCall(void*, void*) {
    // todo
  }

  virtual void dispose() {
    c.dispose();

    c.s->free(this);
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
