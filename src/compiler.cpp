#include "compiler.h"
#include "vector.h"
#include "zone.h"

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

const unsigned RegisterCount32 = 8;
const unsigned RegisterCount64 = 16;

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

class IpMapping {
 public:
  IpMapping(unsigned ip, unsigned start): ip(ip), start(start), end(-1) { }

  const int ip;
  const int start;
  int end;
  int offset;
};

int
compareIpMappingPointers(const void* a, const void* b)
{
  return (*static_cast<IpMapping* const*>(a))->ip
    - (*static_cast<IpMapping* const*>(b))->ip;
}

class MyPromise: public Promise {
 public:
  MyPromise(intptr_t value): resolved(false), value_(value) { }

  bool resolved;
  intptr_t value_;
};

class PoolPromise: public MyPromise {
 public:
  PoolPromise(intptr_t value): MyPromise(value) { }

  virtual unsigned value(Compiler*);
};

class CodePromise: public MyPromise {
 public:
  CodePromise(intptr_t value): MyPromise(value) { }

  virtual unsigned value(Compiler*);
};

class IpPromise: public MyPromise {
 public:
  IpPromise(intptr_t value): MyPromise(value) { }

  virtual unsigned value(Compiler*);
};

class Context;
class ImmediateOperand;
class RegisterOperand;
class MemoryOperand;
class StackOperand;

void NO_RETURN abort(Context*);

#ifndef NDEBUG
void assert(Context*, bool);
#endif // not NDEBUG

class MyOperand: public Operand {
 public:
  enum Operation {
    push,
    pop,
    call,
    alignedCall,
    ret,
    mov,
    cmp,
    jl,
    jg,
    jle,
    jge,
    je,
    jne,
    jmp,
    add,
    sub,
    mul,
    div,
    rem,
    shl,
    shr,
    ushr,
    and_,
    or_,
    xor_,
    neg
  };

  virtual ~MyOperand() { }

  virtual unsigned footprint() {
    return BytesPerWord;
  }

  virtual StackOperand* logicalPush(Context* c) { abort(c); }

  virtual void logicalFlush(Context*, StackOperand*) { /* ignore */ }

  virtual Register asRegister(Context* c) { abort(c); }

  virtual void release(Context*) { /* ignore */ }

  virtual void setAbsolute(Context* c, intptr_t) { abort(c); }

  virtual void apply(Context* c, Operation) { abort(c); }

  virtual void apply(Context* c, Operation, MyOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
};

class StackOperand;

class RegisterOperand: public MyOperand {
 public:
  RegisterOperand(Register value):
    value(value), reserved(false), stack(0)
  { }

  virtual void logicalFlush(Context* c UNUSED, StackOperand* s UNUSED) {
    assert(c, stack == s);
    stack = 0;
  }

  virtual void release(Context* c UNUSED) {
    assert(c, reserved);
    reserved = false;
  }

  virtual void apply(Context* c, Operation) { abort(c); }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  Register value;
  bool reserved;
  StackOperand* stack;
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(intptr_t value):
    value(value)
  { }

  virtual void apply(Context* c, Operation) { abort(c); }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  intptr_t value;
};

class AbsoluteOperand: public MyOperand {
 public:
  AbsoluteOperand(MyPromise* value):
    value(value)
  { }

  virtual void setAbsolute(Context*, intptr_t v) {
    value->value_ = v;
  }

  MyPromise* value;
};

class MemoryOperand: public MyOperand {
 public:
  MemoryOperand(MyOperand* base, int displacement, MyOperand* index,
                unsigned scale):
    base(base),
    displacement(displacement),
    index(index),
    scale(scale)
  { }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
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

  virtual unsigned footprint() {
    if (selectionType == S8Selection) {
      return 8;
    } else {
      return 4;
    }
  }

  SelectionType selectionType;
  MyOperand* base;
};

class StackOperand: public MyOperand {
 public:
  StackOperand(MyOperand* base, StackOperand* next):
    base(base), next(next), flushed(false)
  {
    if (next) {
      index = next->index + (next->footprint() / BytesPerWord);
    } else {
      index = 0;
    }
  }

  MyOperand* base;
  StackOperand* next;
  int index;
  bool flushed;
};

class Context {
 public:
  Context(System* s, void* indirectCaller):
    s(s),
    code(s, 1024),
    constantPool(s, BytesPerWord * 32),
    ipMappings(s, 1024),
    zone(s, 8 * 1024),
    indirectCaller(reinterpret_cast<intptr_t>(indirectCaller)),
    stack(0),
    machineTable(0),
    logicalTable(0)
  {
    for (unsigned i = 0; i < RegisterCount64; ++i) {
      registers[i] = new (zone.allocate(sizeof(RegisterOperand)))
        RegisterOperand(static_cast<Register>(i));
    }

    registers[rsp]->reserved = true;
    registers[rbp]->reserved = true;
    registers[rbx]->reserved = true;
  }

  void dispose() {
    zone.dispose();
    ipMappings.dispose();
    constantPool.dispose();
    code.dispose();
    if (machineTable) s->free(machineTable);
    if (logicalTable) s->free(logicalTable);
  }

  System* s;
  Vector code;
  Vector constantPool;
  Vector ipMappings;
  Zone zone;
  intptr_t indirectCaller;
  StackOperand* stack;
  IpMapping** machineTable;
  IpMapping** logicalTable;
  RegisterOperand* registers[RegisterCount64];
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

ImmediateOperand*
immediate(Context* c, intptr_t v)
{
  return new (c->zone.allocate(sizeof(ImmediateOperand))) ImmediateOperand(v);
}

AbsoluteOperand*
absolute(Context* c, MyPromise* v)
{
  return new (c->zone.allocate(sizeof(AbsoluteOperand))) AbsoluteOperand(v);
}

RegisterOperand*
register_(Context* c, Register v)
{
  return c->registers[v];
}

MemoryOperand*
memory(Context* c, MyOperand* base, int displacement,
       MyOperand* index, unsigned scale)
{
  return new (c->zone.allocate(sizeof(MemoryOperand)))
    MemoryOperand(base, displacement, index, scale);
}

void
flush(Context* c, StackOperand* s)
{
  s->base->apply(c, MyOperand::push);

  s->base->logicalFlush(c, s);

  s->base = memory
    (c, register_(c, rbp), - (s->index + 1) * BytesPerWord, 0, 1);
  s->flushed = true;
}

RegisterOperand*
temporary(Context* c)
{
  RegisterOperand* r = 0;
  for (unsigned i = 0; i < RegisterCount32; ++i) {
    if (not c->registers[i]->reserved) {
      if (not c->registers[i]->stack) {
        c->registers[i]->reserved = true;
        return c->registers[i];
      } else if (r == 0 or r->stack->index > c->registers[i]->stack->index) {
        r = c->registers[i];
      }
    }
  }

  if (r) {
    flush(c, r->stack);
    return r;
  } else {
    abort(c);
  }
}

void
release(Context* c UNUSED, RegisterOperand* v)
{
  assert(c, v->reserved);
  v->reserved = false;
}

StackOperand*
push(Context* c, MyOperand* base)
{
  return base->logicalPush(c);
}

void
pop(Context* c, MyOperand* dst)
{
  if (c->stack->flushed) {
    dst->apply(c, MyOperand::pop);
  } else {
    c->stack->base->apply(c, MyOperand::mov, dst);
  }
  c->stack = c->stack->next;
}

MyOperand*
pop(Context* c)
{
  if (c->stack->flushed) {
    RegisterOperand* tmp = temporary(c);
    tmp->apply(c, MyOperand::pop);
    return tmp;
  } else {
    return c->stack->base;
  }
  c->stack = c->stack->next;
}

MyOperand*
selection(Context* c, SelectionOperand::SelectionType type, MyOperand* base)
{
  if ((type == SelectionOperand::S4Selection and BytesPerWord == 4)
      or (type == SelectionOperand::S8Selection and BytesPerWord == 8))
  {
    return base;
  } else {
    return new (c->zone.allocate(sizeof(SelectionOperand)))
      SelectionOperand(type, base);
  }
}

void
flushStack(Context* c)
{
  if (c->stack) {
    StackOperand* stack[c->stack->index + 1];
    int index = c->stack->index + 1;
    for (StackOperand* s = c->stack; s and not s->flushed; s = s->next) {
      stack[-- index] = s;
    }

    for (; index < c->stack->index + 1; ++ index) {
      flush(c, stack[index]);
    }
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
      arguments[i]->apply(c, MyOperand::mov, register_(c, gpRegister(c, i)));
    } else {
      arguments[i]->apply(c, MyOperand::push);
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

class MyCompiler: public Compiler {
 public:
  MyCompiler(System* s, void* indirectCaller):
    c(s, indirectCaller)
  { }

  virtual Promise* poolOffset() {
    return new (c.zone.allocate(sizeof(PoolPromise)))
      PoolPromise(c.constantPool.length());
  }

  virtual Promise* codeOffset() {
    return new (c.zone.allocate(sizeof(CodePromise)))
      CodePromise(c.code.length());
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = absolute(&c, static_cast<MyPromise*>(poolOffset()));
    c.constantPool.appendAddress(v);
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return immediate(&c, v);
  }

  virtual void push(Operand* v) {
    ::push(&c, static_cast<MyOperand*>(v));
  }

  virtual void push2(Operand* v) {
    push(v);
    if (BytesPerWord == 8) push(immediate(&c, 0));
  }

  virtual Operand* stack(unsigned index) {
    StackOperand* s = c.stack;
    unsigned i = 0;
    if (s->footprint() / BytesPerWord == 2) ++ i;

    for (; i < index; ++i) {
      s = s->next;
      if (s->footprint() / BytesPerWord == 2) ++ i;
    }

    return s;
  }

  virtual Operand* pop() {
    return ::pop(&c);
  }

  virtual Operand* pop2() {
    if (BytesPerWord == 8) pop();
    return pop();
  }

  virtual void pop(Operand* dst) {
    ::pop(&c, static_cast<MyOperand*>(dst));
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
    static_cast<MyOperand*>(v)->release(&c);
  }

  virtual Operand* label() {
    return absolute
      (&c, new (c.zone.allocate(sizeof(CodePromise))) CodePromise(0));
  }

  virtual void mark(Operand* label) {
    static_cast<MyOperand*>(label)->setAbsolute(&c, c.code.length());
  }

  virtual Operand* indirectCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->apply
      (&c, MyOperand::mov, register_(&c, rax));

    immediate(&c, c.indirectCaller)->apply(&c, MyOperand::call);

    immediate(&c, footprint)->apply(&c, MyOperand::sub, register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void indirectCallNoReturn
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    pushArguments(&c, argumentCount, a);    
    va_end(a);

    static_cast<MyOperand*>(address)->apply
      (&c, MyOperand::mov, register_(&c, rax));

    immediate(&c, c.indirectCaller)->apply(&c, MyOperand::call);
  }

  virtual Operand* directCall
  (Operand* address, unsigned argumentCount, ...)
  {
    va_list a; va_start(a, argumentCount);
    unsigned footprint = pushArguments(&c, argumentCount, a);
    va_end(a);

    static_cast<MyOperand*>(address)->apply(&c, MyOperand::call);

    immediate(&c, footprint)->apply(&c, MyOperand::sub, register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void return_(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::mov, register_(&c, rax));
    ret();
  }

  virtual Operand* call(Operand* v) {
    flushStack(&c);
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::call);
    return register_(&c, rax);
  }

  virtual Operand* alignedCall(Operand* v) {
    flushStack(&c);
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::alignedCall);
    return register_(&c, rax);
  }

  virtual void ret() {
    ::ret(&c);
  }

  virtual void mov(Operand* src, Operand* dst) {
    static_cast<MyOperand*>(src)->apply
      (&c, MyOperand::mov, static_cast<MyOperand*>(dst));
  }

  virtual void cmp(Operand* subtrahend, Operand* minuend) {
    static_cast<MyOperand*>(subtrahend)->apply
      (&c, MyOperand::cmp, static_cast<MyOperand*>(minuend));
  }

  virtual void jl(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jl);
  }

  virtual void jg(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jg);
  }

  virtual void jle(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jle);
  }

  virtual void jge(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jge);
  }

  virtual void je(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::je);
  }

  virtual void jne(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jne);
  }

  virtual void jmp(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::jmp);
  }

  virtual void add(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::add, static_cast<MyOperand*>(dst));
  }

  virtual void sub(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::sub, static_cast<MyOperand*>(dst));
  }

  virtual void mul(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::mul, static_cast<MyOperand*>(dst));
  }

  virtual void div(Operand* v, Operand* dst) {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::div, static_cast<MyOperand*>(dst));
  }

  virtual void rem(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::rem, static_cast<MyOperand*>(dst));
  }

  virtual void shl(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::shl, static_cast<MyOperand*>(dst));
  }

  virtual void shr(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::shr, static_cast<MyOperand*>(dst));
  }

  virtual void ushr(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::ushr, static_cast<MyOperand*>(dst));
  }

  virtual void and_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::and_, static_cast<MyOperand*>(dst));
  }

  virtual void or_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::or_, static_cast<MyOperand*>(dst));
  }

  virtual void xor_(Operand* v, Operand* dst)  {
    static_cast<MyOperand*>(v)->apply
      (&c, MyOperand::xor_, static_cast<MyOperand*>(dst));
  }

  virtual void neg(Operand* v)  {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::neg);
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
    register_(&c, rbp)->apply(&c, MyOperand::push);
    register_(&c, rsp)->apply(&c, MyOperand::mov, register_(&c, rbp));
  }

  virtual void epilogue() {
    register_(&c, rbp)->apply(&c, MyOperand::mov, register_(&c, rsp));
    register_(&c, rbp)->apply(&c, MyOperand::push);
  }

  virtual void startLogicalIp(unsigned ip) {
    new (c.ipMappings.allocate(sizeof(IpMapping)))
      IpMapping(ip, c.code.length());
  }

  virtual Operand* logicalIp(unsigned ip) {
    return absolute(&c, static_cast<MyPromise*>(logicalIpToOffset(ip)));
  }

  virtual Promise* logicalIpToOffset(unsigned ip) {
    return new (c.zone.allocate(sizeof(IpPromise))) IpPromise(ip);
  }

  virtual unsigned size() {
    return c.code.length() + c.constantPool.length();
  }

  virtual void writeTo(void* out) {
    unsigned tableSize = (c.ipMappings.length() / sizeof(IpMapping));

    c.machineTable = static_cast<IpMapping**>
      (c.s->allocate(tableSize * BytesPerWord));
    c.logicalTable = static_cast<IpMapping**>
      (c.s->allocate(tableSize * BytesPerWord));

    for (unsigned i = 0; i < tableSize; ++i) {
      IpMapping* mapping = c.ipMappings.peek<IpMapping>(i * sizeof(IpMapping));

      if (i + 1 < c.ipMappings.length()) {
        mapping->end = c.ipMappings.peek<IpMapping>
          ((i + 1) * sizeof(IpMapping))->start;
      } else {
        mapping->end = c.code.length();
      }

      c.machineTable[i] = mapping;
      c.logicalTable[i] = mapping;
    }

    qsort(c.logicalTable, c.ipMappings.length() / sizeof(IpMapping),
          BytesPerWord, compareIpMappingPointers);

    uint8_t* p = static_cast<uint8_t*>(out);

    for (unsigned i = 0; i < tableSize; ++i) {
      IpMapping* mapping = c.logicalTable[i];
      mapping->offset = (p - static_cast<uint8_t*>(out));

      int length = mapping->end - mapping->start;
      memcpy(p, c.code.data + mapping->start, length);
      p += length;
    }

    memcpy(p, c.constantPool.data, c.constantPool.length());
  }

  virtual void updateCall(void* returnAddress, void* newTarget) {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    assert(&c, *instruction == 0xE8);

    int32_t v = static_cast<uint8_t*>(newTarget)
      - static_cast<uint8_t*>(returnAddress);
    memcpy(instruction + 1, &v, 4);
  }

  virtual void dispose() {
    c.dispose();

    c.s->free(this);
  }

  Context c;
};

unsigned
PoolPromise::value(Compiler* compiler)
{
  Context* c = &(static_cast<MyCompiler*>(compiler)->c);

  if (c->logicalTable) {
    return c->code.length() + value_;
  }

  abort(c);
}

unsigned
CodePromise::value(Compiler* compiler)
{
  Context* c = &(static_cast<MyCompiler*>(compiler)->c);

  if (c->logicalTable) {
    unsigned bottom = 0;
    unsigned top = c->ipMappings.length() / sizeof(IpMapping);
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = c->machineTable[middle];

      if (value_ >= mapping->start and value_ < mapping->end) {
        return mapping->offset + (value_ - mapping->start);
      } else if (value_ < mapping->start) {
        top = middle;
      } else if (value_ > mapping->start) {
        bottom = middle + 1;
      }
    }    
  }

  abort(c);
}

unsigned
IpPromise::value(Compiler* compiler)
{
  Context* c = &(static_cast<MyCompiler*>(compiler)->c);

  if (c->logicalTable) {
    unsigned bottom = 0;
    unsigned top = c->ipMappings.length() / sizeof(IpMapping);
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = c->logicalTable[middle];

      if (value_ == mapping->ip) {
        return mapping->start;
      } else if (value_ < mapping->ip) {
        top = middle;
      } else if (value_ > mapping->ip) {
        bottom = middle + 1;
      }
    }
  }

  abort(c);
}

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, void* indirectCaller)
{
  return new (system->allocate(sizeof(MyCompiler)))
    MyCompiler(system, indirectCaller);
}

} // namespace vm
