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

const unsigned RegisterCount = BytesPerWord * 2;

class Context;
class ImmediateOperand;
class AbsoluteOperand;
class RegisterOperand;
class MemoryOperand;
class StackOperand;

void NO_RETURN abort(Context*);

#ifndef NDEBUG
void assert(Context*, bool);
#endif // not NDEBUG

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

class IpTask {
 public:
  enum Priority {
    LowPriority,
    HighPriority
  };

  IpTask(IpTask* next): next(next) { }

  virtual ~IpTask() { }

  virtual void run(Context* c, unsigned ip, unsigned start, unsigned end,
                   unsigned offset) = 0;

  virtual Priority priority() {
    return LowPriority;
  }
  
  IpTask* next;
};

class IpMapping {
 public:
  IpMapping(int ip, int start):
    ip(ip), start(start), end(-1), task(0)
  { }

  const int ip;
  const int start;
  int end;
  IpTask* task;
};

int
compareIpMappingPointers(const void* a, const void* b)
{
  return (*static_cast<IpMapping* const*>(a))->ip
    - (*static_cast<IpMapping* const*>(b))->ip;
}

class MyPromise: public Promise {
 public:
  MyPromise(intptr_t key): key(key) { }

  virtual intptr_t value(Compiler*);

  virtual intptr_t value(Context*) = 0;

  virtual bool resolved(Context*) = 0;

  intptr_t key;
};

class ResolvedPromise: public MyPromise {
 public:
  ResolvedPromise(intptr_t key): MyPromise(key) { }

  virtual intptr_t value(Context*) {
    return key;
  }

  virtual bool resolved(Context*) {
    return true;
  }
};

class PoolPromise: public MyPromise {
 public:
  PoolPromise(intptr_t key): MyPromise(key) { }

  virtual intptr_t value(Context*);
  virtual bool resolved(Context*);
};

class CodePromise: public MyPromise {
 public:
  CodePromise(intptr_t key, bool absolute):
    MyPromise(key), absolute(absolute)
  { }

  virtual intptr_t value(Context*);
  virtual bool resolved(Context*);

  bool absolute;
};

class CodePromiseTask: public IpTask {
 public:
  CodePromiseTask(CodePromise* p, IpTask* next): IpTask(next), p(p) { }

  virtual void run(Context*, unsigned, unsigned start, unsigned,
                   unsigned offset)
  {
    p->key = offset + (p->key - start);
  }

  virtual Priority priority() {
    return HighPriority;
  }

  CodePromise* p;
};

class IpPromise: public MyPromise {
 public:
  IpPromise(intptr_t key, bool absolute):
    MyPromise(key), absolute(absolute)
  { }

  virtual intptr_t value(Context*);
  virtual bool resolved(Context*);

  bool absolute;
};

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

  virtual Register asRegister(Context* c) { abort(c); }

  virtual void release(Context*) { /* ignore */ }

  virtual void setImmediate(Context* c, intptr_t) { abort(c); }

  virtual void apply(Context* c, Operation) { abort(c); }

  virtual void apply(Context* c, Operation, MyOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, RegisterOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, ImmediateOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, AbsoluteOperand*) { abort(c); }

  virtual void accept(Context* c, Operation, MemoryOperand*) { abort(c); }
};

class RegisterOperand: public MyOperand {
 public:
  RegisterOperand(Register value):
    value(value), reserved(false)
  { }

  virtual Register asRegister(Context*) {
    return value;
  }

  virtual void release(Context* c UNUSED) {
    assert(c, reserved);
    reserved = false;
  }

  virtual void apply(Context*, Operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);
  virtual void accept(Context*, Operation, MemoryOperand*);

  Register value;
  bool reserved;
};

class ImmediateOperand: public MyOperand {
 public:
  ImmediateOperand(MyPromise* value):
    value(value)
  { }

  virtual void setImmediate(Context*, intptr_t v) {
    value->key = v;
  }

  virtual void apply(Context* c, Operation operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  MyPromise* value;
};

class AbsoluteOperand: public MyOperand {
 public:
  AbsoluteOperand(MyPromise* value):
    value(value)
  { }

  virtual void apply(Context* c, Operation operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
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
  {
    assert(static_cast<System*>(0), index == 0); // todo
    assert(static_cast<System*>(0), scale == 1); // todo
  }

  virtual Register asRegister(Context*);

  virtual void apply(Context* c, Operation operation);

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    operand->accept(c, operation, this);
  }

  virtual void accept(Context*, Operation, RegisterOperand*);
  virtual void accept(Context*, Operation, ImmediateOperand*);
  virtual void accept(Context*, Operation, AbsoluteOperand*);

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
  StackOperand(MyOperand* base, int index, StackOperand* next):
    base(base), index(index), next(next)
  { }

  virtual unsigned footprint() {
    return base->footprint();
  }

  virtual Register asRegister(Context* c) {
    return base->asRegister(c);
  }

  virtual void apply(Context* c, Operation operation) {
    base->apply(c, operation);
  }

  virtual void apply(Context* c, Operation operation, MyOperand* operand) {
    base->apply(c, operation, operand);
  }

  virtual void accept(Context* c, Operation operation,
                      RegisterOperand* operand)
  {
    base->accept(c, operation, operand);
  }

  MyOperand* base;
  int index;
  StackOperand* next;
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
    ipTable(0),
    reserved(0),
    output(0)
  {
    ipMappings.appendAddress
      (new (zone.allocate(sizeof(IpMapping))) IpMapping(-1, 0));

    for (unsigned i = 0; i < RegisterCount; ++i) {
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
    if (ipTable) s->free(ipTable);
  }

  System* s;
  Vector code;
  Vector constantPool;
  Vector ipMappings;
  Zone zone;
  intptr_t indirectCaller;
  StackOperand* stack;
  IpMapping** ipTable;
  unsigned reserved;
  uint8_t* output;
  RegisterOperand* registers[RegisterCount];
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
immediate(Context* c, MyPromise* p)
{
  return new (c->zone.allocate(sizeof(ImmediateOperand))) ImmediateOperand(p);
}

ImmediateOperand*
immediate(Context* c, intptr_t v)
{
  return immediate(c, new (c->zone.allocate(sizeof(ResolvedPromise)))
                   ResolvedPromise(v));
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

IpMapping*
currentMapping(Context* c)
{
  IpMapping* mapping;
  c->ipMappings.get
    (c->ipMappings.length() - BytesPerWord, &mapping, BytesPerWord);
  return mapping;
}

RegisterOperand*
temporary(Context* c)
{
  // we don't yet support using r9-r15
  for (unsigned i = 0; i < 8/*RegisterCount*/; ++i) {
    if (not c->registers[i]->reserved) {
      c->registers[i]->reserved = true;
      return c->registers[i];
    }
  }

  abort(c);
}

StackOperand*
pushed(Context* c)
{
  int index = (c->stack ?
               c->stack->index + (c->stack->footprint() / BytesPerWord) :
               0);

  MyOperand* base = memory
    (c, register_(c, rbp), - (c->reserved + index + 1) * BytesPerWord, 0, 1);

  return c->stack = new (c->zone.allocate(sizeof(StackOperand)))
    StackOperand(base, index, c->stack);
}

void
push(Context* c, int count)
{
  immediate(c, count * BytesPerWord)->apply
    (c, MyOperand::sub, register_(c, rsp));

  while (count) {
    -- count;
    pushed(c);
  }
}

StackOperand*
push(Context* c, MyOperand* v)
{
  v->apply(c, MyOperand::push);
  return pushed(c);
}

void
pop(Context* c, int count)
{
  immediate(c, count * BytesPerWord)->apply
    (c, MyOperand::add, register_(c, rsp));

  while (count) {
    count -= (c->stack->footprint() / BytesPerWord);
    assert(c, count >= 0);
    c->stack = c->stack->next;
  }
}

void
pop(Context* c, MyOperand* dst)
{
  dst->apply(c, MyOperand::pop);
  c->stack = c->stack->next;
}

MyOperand*
pop(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->apply(c, MyOperand::pop);
  MyOperand* r = tmp;
  c->stack = c->stack->next;
  return r;
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

void
RegisterOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    c->code.append(0xff);
    c->code.append(0xd0 | value);
    break;

  case jmp:
    c->code.append(0xff);
    c->code.append(0xe0 | value);
    break;

  case pop:
    c->code.append(0x58 | value);
    break;

  case push:
    c->code.append(0x50 | value);
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        RegisterOperand* operand)
{
  switch (operation) {
  case add:
    rex(c);
    c->code.append(0x01);
    c->code.append(0xc0 | (operand->value << 3) | value);
    break;

  case cmp:
    rex(c);
    c->code.append(0x39);
    c->code.append(0xc0 | (operand->value << 3) | value);
    break;

  case mov:
    if (value != operand->value) {
      rex(c);
      c->code.append(0x89);
      c->code.append(0xc0 | (operand->value << 3) | value);
    }
    break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        ImmediateOperand* operand)
{
  switch (operation) {
  case add: {
    intptr_t v = operand->value->value(c);
    if (v) {
      assert(c, isInt8(v)); // todo

      rex(c);
      c->code.append(0x83);
      c->code.append(0xc0 | value);
      c->code.append(v);
    }
  } break;

  case and_: {
    intptr_t v = operand->value->value(c);
    if (v) {
      rex(c);
      if (isInt8(v)) {
        c->code.append(0x83);
        c->code.append(0xe0 | value);
        c->code.append(v);
      } else {
        assert(c, isInt32(v));

        c->code.append(0x81);
        c->code.append(0xe0 | value);
        c->code.append(v);
      }
    }
  } break;

  case cmp: {
    intptr_t v = operand->value->value(c);
    assert(c, isInt8(v)); // todo

    rex(c);
    c->code.append(0x83);
    c->code.append(0xf8 | value);
    c->code.append(v);
  } break;

  case mov: {
    intptr_t v = operand->value->value(c);
    rex(c);
    c->code.append(0xb8 | value);
    c->code.appendAddress(v);
  } break;

  case sub: {
    intptr_t v = operand->value->value(c);
    if (v) {
      assert(c, isInt8(v)); // todo

      rex(c);
      c->code.append(0x83);
      c->code.append(0xe8 | value);
      c->code.append(v);
    }
  } break;

  default: abort(c);
  }
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        MemoryOperand* operand)
{
  switch (operation) {
  case cmp:
    rex(c);
    encode(c, 0x3b, 0, 0x40, 0x80, value, operand->base->asRegister(c),
           operand->displacement);
    break;

  case mov:
    rex(c);
    encode(c, 0x8b, 0, 0x40, 0x80, value, operand->base->asRegister(c),
           operand->displacement);
    break;

  default: abort(c);
  }
}

class AbsoluteMovTask: public IpTask {
 public:
  AbsoluteMovTask(unsigned start, MyPromise* promise, IpTask* next):
    IpTask(next), start(start), promise(promise)
  { }

  virtual void run(Context* c UNUSED, unsigned, unsigned start, unsigned,
                   unsigned offset)
  {
    uint8_t* instruction = c->output + offset + (this->start - start);
    intptr_t v = reinterpret_cast<intptr_t>(c->output + promise->value(c));
    memcpy(instruction + (BytesPerWord / 8) + 1, &v, BytesPerWord);
  }

  unsigned start;
  MyPromise* promise;
};

void
addAbsoluteMovTask(Context* c, MyPromise* p)
{
  IpMapping* mapping = currentMapping(c);
  mapping->task = new (c->zone.allocate(sizeof(AbsoluteMovTask)))
    AbsoluteMovTask(c->code.length(), p, mapping->task);
}

void
RegisterOperand::accept(Context* c, Operation operation,
                        AbsoluteOperand* operand)
{
  switch (operation) {
  case cmp: {
    RegisterOperand* tmp = temporary(c);
    addAbsoluteMovTask(c, operand->value);
    tmp->accept(c, mov, immediate(c, 0));
    accept(c, cmp, memory(c, tmp, 0, 0, 1));
    tmp->release(c);
  } break;

  case mov: {
    addAbsoluteMovTask(c, operand->value);
    
    accept(c, mov, immediate(c, 0));
    accept(c, mov, memory(c, this, 0, 0, 1));
  } break;

  default: abort(c);
  }
}

class DirectJumpTask: public IpTask {
 public:
  DirectJumpTask(unsigned start, unsigned offset, MyPromise* address,
                 IpTask* next):
    IpTask(next), start(start), offset(offset), address(address)
  { }

  virtual void run(Context* c UNUSED, unsigned, unsigned start, unsigned,
                   unsigned offset)
  {
    uint8_t* instruction = c->output + offset + (this->start - start);
    intptr_t v = reinterpret_cast<uint8_t*>(address->value(c))
      - instruction - 4 - this->offset;
    assert(c, isInt32(v));

    int32_t v32 = v;
    memcpy(instruction + this->offset, &v32, 4);
  }

  unsigned start;
  unsigned offset;
  MyPromise* address;
};

void
addDirectJumpTask(Context* c, unsigned offset, MyPromise* p)
{
  IpMapping* mapping = currentMapping(c);
  mapping->task = new (c->zone.allocate(sizeof(DirectJumpTask)))
    DirectJumpTask(c->code.length(), offset, p, mapping->task);
}

void
immediateConditional(Context* c, unsigned condition,
                     ImmediateOperand* operand)
{
  addDirectJumpTask(c, 2, operand->value);
  
  c->code.append(0x0f);
  c->code.append(condition);
  c->code.append4(0);
}

void
ImmediateOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case alignedCall: {
    while ((c->code.length() + 1) % 4) {
      c->code.append(0x90);
    }
    apply(c, call);
  } break;

  case call:
    addDirectJumpTask(c, 1, value);
    
    c->code.append(0xe8);
    c->code.append4(0);
    break;

  case jmp:
    addDirectJumpTask(c, 1, value);
    
    c->code.append(0xe9);
    c->code.append4(0);
    break;

  case je:
    immediateConditional(c, 0x84, this);
    break;

  case jne:
    immediateConditional(c, 0x85, this);
    break;

  case jg:
    immediateConditional(c, 0x8f, this);
    break;

  case jge:
    immediateConditional(c, 0x8d, this);
    break;

  case jl:
    immediateConditional(c, 0x8c, this);
    break;

  case jle:
    immediateConditional(c, 0x8e, this);
    break;

  case push: {
    intptr_t v = value->value(c);
    if (isInt8(v)) {
      c->code.append(0x6a);
      c->code.append(v);
    } else if (isInt32(v)) {
      c->code.append(0x68);
      c->code.append4(v);
    } else {
      RegisterOperand* tmp = temporary(c);
      tmp->accept(c, mov, this);
      tmp->apply(c, push);
      tmp->release(c);
    }
  } break;
    
  default: abort(c);
  }
}

void
absoluteApply(Context* c, MyOperand::Operation operation,
              AbsoluteOperand* operand)
{
  addAbsoluteMovTask(c, operand->value);
    
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, MyOperand::mov, immediate(c, 0));
  memory(c, tmp, 0, 0, 1)->apply(c, operation);
  tmp->release(c);
}

void
AbsoluteOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case push:
    absoluteApply(c, operation, this);
    break;

  default: abort(c);
  }
}

Register
MemoryOperand::asRegister(Context* c)
{
  RegisterOperand* tmp = temporary(c);
  tmp->accept(c, mov, this);
  tmp->release(c);
  return tmp->value;
}

void
MemoryOperand::apply(Context* c, Operation operation)
{
  switch (operation) {
  case call:
    encode(c, 0xff, 0x10, 0x50, 0x90, rax, base->asRegister(c), displacement);
    break;

  case pop:
    encode(c, 0x8f, 0, 0x40, 0x80, rax, base->asRegister(c), displacement);
    break;

  case push:
    encode(c, 0xff, 0x30, 0x70, 0xb0, rax, base->asRegister(c), displacement);
    break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      RegisterOperand* operand)
{
  switch (operation) {
  case add:
    rex(c);
    encode(c, 0x01, 0, 0x40, 0x80, operand->value, base->asRegister(c),
           displacement);
    break;

  case mov:
    rex(c);
    encode(c, 0x89, 0, 0x40, 0x80, operand->value, base->asRegister(c),
           displacement);
    break;

  case sub:
    rex(c);
    encode(c, 0x29, 0, 0x40, 0x80, operand->value, base->asRegister(c),
           displacement);
    break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      ImmediateOperand* operand)
{
  switch (operation) {
  case add: {
    rex(c);
    intptr_t v = operand->value->value(c);
    unsigned i = (isInt8(v) ? 0x83 : 0x81);
    encode(c, i, 0, 0x40, 0x80, rax, base->asRegister(c), displacement);
    if (isInt8(v)) {
      c->code.append(v);
    } else if (isInt32(v)) {
      c->code.append4(v);
    } else {
      abort(c);
    }
  } break;

  case mov: {
    intptr_t v = operand->value->value(c);
    assert(c, isInt32(v)); // todo

    rex(c);
    encode(c, 0xc7, 0, 0x40, 0x80, rax, base->asRegister(c), displacement);
    c->code.append4(v);
  } break;

  default: abort(c);
  }
}

void
MemoryOperand::accept(Context* c, Operation operation,
                      AbsoluteOperand* operand)
{
  switch (operation) {
  case mov: {
    RegisterOperand* tmp = temporary(c);
    
    tmp->accept(c, mov, operand);
    accept(c, mov, tmp);

    tmp->release(c);
  } break;

  default: abort(c);
  }
}

intptr_t
PoolPromise::value(Context* c)
{
  if (resolved(c)) {
    return c->code.length() + key;
  }

  abort(c);
}

bool
PoolPromise::resolved(Context* c)
{
  return c->output != 0;
}

intptr_t
CodePromise::value(Context* c)
{
  if (resolved(c)) {
    if (absolute) {
      return reinterpret_cast<intptr_t>(c->output + key);
    } else {
      return key;
    }
  }

  abort(c);
}

bool
CodePromise::resolved(Context* c)
{
  return c->output != 0;
}

intptr_t
IpPromise::value(Context* c)
{
  if (resolved(c)) {
    unsigned bottom = 0;
    unsigned top = c->ipMappings.length() / BytesPerWord;
    for (unsigned span = top - bottom; span; span = top - bottom) {
      unsigned middle = bottom + (span / 2);
      IpMapping* mapping = c->ipTable[middle];

      if (key == mapping->ip) {
        if (absolute) {
          return reinterpret_cast<intptr_t>(c->output + mapping->start);
        } else {
          return mapping->start;
        }
      } else if (key < mapping->ip) {
        top = middle;
      } else if (key > mapping->ip) {
        bottom = middle + 1;
      }
    }
  }

  abort(c);
}

bool
IpPromise::resolved(Context* c)
{
  return c->output != 0;
}

void
runTasks(Context* c, IpTask::Priority priority)
{
  uint8_t* p = c->output;
  for (unsigned i = 0; i < c->ipMappings.length() / BytesPerWord; ++i) {
    IpMapping* mapping = c->ipTable[i];
    int length = mapping->end - mapping->start;

    for (IpTask* t = mapping->task; t; t = t->next) {
      if (t->priority() == priority) {
        t->run(c, mapping->ip, mapping->start, mapping->end, p - c->output);
      }
    }

    p += length;
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
    if (c.code.length() == 0) {
      return new (c.zone.allocate(sizeof(CodePromise))) CodePromise(0, false);
    } else {
      CodePromise* p = new (c.zone.allocate(sizeof(CodePromise)))
        CodePromise(c.code.length(), false);

      IpMapping* mapping = currentMapping(&c);
      mapping->task = new (c.zone.allocate(sizeof(CodePromiseTask)))
        CodePromiseTask(p, mapping->task);

      return p;
    }
  }

  virtual Operand* poolAppend(Operand* v) {
    Operand* r = absolute(&c, static_cast<MyPromise*>(poolOffset()));
    c.constantPool.appendAddress(v);
    return r;
  }

  virtual Operand* constant(intptr_t v) {
    return immediate(&c, v);
  }

  virtual void push(unsigned count) {
    ::push(&c, count);
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

  virtual void pop(unsigned count) {
    ::pop(&c, count);
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
    return immediate
      (&c, new (c.zone.allocate(sizeof(CodePromise))) CodePromise(0, true));
  }

  virtual void mark(Operand* label) {
    static_cast<MyOperand*>(label)->setImmediate(&c, c.code.length());
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

    immediate(&c, footprint)->apply(&c, MyOperand::add, register_(&c, rsp));

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

    immediate(&c, footprint)->apply(&c, MyOperand::add, register_(&c, rsp));

    return register_(&c, rax);
  }

  virtual void return_(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::mov, register_(&c, rax));
    epilogue();
    ret();
  }

  virtual Operand* call(Operand* v) {
    static_cast<MyOperand*>(v)->apply(&c, MyOperand::call);
    return register_(&c, rax);
  }

  virtual Operand* alignedCall(Operand* v) {
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

  virtual void reserve(unsigned size) {
    immediate(&c, size * BytesPerWord)->apply
      (&c, MyOperand::sub, register_(&c, rsp));
    c.reserved = size;
  }

  virtual void epilogue() {
    register_(&c, rbp)->apply(&c, MyOperand::mov, register_(&c, rsp));
    register_(&c, rbp)->apply(&c, MyOperand::pop);
  }

  virtual void startLogicalIp(unsigned ip) {
    c.ipMappings.appendAddress
      (new (c.zone.allocate(sizeof(IpMapping)))
       IpMapping(ip, c.code.length()));
  }

  virtual Operand* logicalIp(unsigned ip) {
    return immediate
      (&c, new (c.zone.allocate(sizeof(IpPromise))) IpPromise(ip, true));
  }

  virtual Promise* logicalIpToOffset(unsigned ip) {
    return new (c.zone.allocate(sizeof(IpPromise))) IpPromise(ip, false);
  }

  virtual unsigned codeSize() {
    return c.code.length();
  }

  virtual unsigned poolSize() {
    return c.constantPool.length();
  }

  virtual void writeTo(uint8_t* out) {
    c.output = out;

    unsigned tableSize = (c.ipMappings.length() / BytesPerWord);

    c.ipTable = static_cast<IpMapping**>
      (c.s->allocate(c.ipMappings.length()));

    for (unsigned i = 0; i < tableSize; ++i) {
      IpMapping* mapping;
      c.ipMappings.get(i * BytesPerWord, &mapping, BytesPerWord);

      if (i + 1 < tableSize) {
        IpMapping* next;
        c.ipMappings.get((i + 1) * BytesPerWord, &next, BytesPerWord);
        mapping->end = next->start;
      } else {
        mapping->end = c.code.length();
      }

      c.ipTable[i] = mapping;
    }

    qsort(c.ipTable, tableSize, BytesPerWord, compareIpMappingPointers);

    uint8_t* p = out;

    for (unsigned i = 0; i < tableSize; ++i) {
      IpMapping* mapping = c.ipTable[i];
      int length = mapping->end - mapping->start;

      memcpy(p, c.code.data + mapping->start, length);

      p += length;
    }

    memcpy(p, c.constantPool.data, c.constantPool.length());

    runTasks(&c, IpTask::HighPriority);
    runTasks(&c, IpTask::LowPriority);
  }

  virtual void updateCall(void* returnAddress, void* newTarget) {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    assert(&c, *instruction == 0xE8);
    assert(&c, reinterpret_cast<uintptr_t>(instruction + 1) % 4 == 0);

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

intptr_t
MyPromise::value(Compiler* compiler)
{
  return value(&(static_cast<MyCompiler*>(compiler)->c));
}

} // namespace

namespace vm {

Compiler*
makeCompiler(System* system, void* indirectCaller)
{
  return new (system->allocate(sizeof(MyCompiler)))
    MyCompiler(system, indirectCaller);
}

} // namespace v
