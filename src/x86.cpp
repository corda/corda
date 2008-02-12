#include "assembler.h"
#include "vector.h"

using namespace vm;

#define INDEX1(a, b) ((a) + (BinaryOperationCount * (b)))

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)

#define INDEX2(a, b, c) \
  ((a) \
   + (BinaryOperationCount * (b)) \
   + (BinaryOperationCount * OperandTypeCount * (c)))

#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)

namespace {

enum {
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

class Task;

class Context {
 public:
  Context(System* s, Allocator* a, Zone* zone):
    s(s), zone(zone), code(s, a, 1024), tasks(0), result(0)
  { }

  System* s;
  Zone* zone;
  Vector code;
  Task* tasks;
  uint8_t* result;
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

class CodePromise: public Promise {
 public:
  CodePromise(Context* c, unsigned offset): c(c), offset(offset) { }

  virtual int64_t value() {
    if (resolved()) {
      return reinterpret_cast<intptr_t>(c->result + offset);
    }
    
    abort(c);
  }

  virtual bool resolved() {
    return c->result != 0;
  }

  Context* c;
  unsigned offset;
};

CodePromise*
codePromise(Context* c, unsigned offset)
{
  return new (c->zone->allocate(sizeof(CodePromise))) CodePromise(c, offset);
}

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual ~Task() { }

  virtual void run(Context* c) = 0;

  Task* next;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, int instructionOffset,
             unsigned instructionSize):
    Task(next),
    promise(promise),
    instructionOffset(instructionOffset),
    instructionSize(instructionSize)
  { }

  virtual void run(Context* c) {
    uint8_t* instruction = c->result + instructionOffset;
    intptr_t v = reinterpret_cast<uint8_t*>(promise->value())
      - instruction - instructionSize;
    
    expect(c, isInt32(v));
    
    int32_t v4 = v;
    memcpy(instruction + instructionSize - 4, &v4, 4);
  }

  Promise* promise;
  int instructionOffset;
  unsigned instructionSize;
};

void
appendOffsetTask(Context* c, Promise* promise, int instructionOffset,
                 unsigned instructionSize)
{
  c->tasks = new (c->zone->allocate(sizeof(OffsetTask))) OffsetTask
    (c->tasks, promise, instructionOffset, instructionSize);
}

void
encode(Context* c, uint8_t* instruction, unsigned length, int a, int b,
       int32_t displacement, int index, unsigned scale)
{
  c->code.append(instruction, length);

  uint8_t width;
  if (displacement == 0 and b != rbp) {
    width = 0;
  } else if (isInt8(displacement)) {
    width = 0x40;
  } else {
    width = 0x80;
  }

  if (index == -1) {
    c->code.append(width | (a << 3) | b);
    if (b == rsp) {
      c->code.append(0x24);
    }
  } else {
    assert(c, b != rsp);
    c->code.append(width | (a << 3) | 4);
    c->code.append((log(scale) << 6) | (index << 3) | b);
  }

  if (displacement == 0 and b != rbp) {
    // do nothing
  } else if (isInt8(displacement)) {
    c->code.append(displacement);
  } else {
    c->code.append4(displacement);
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
encode(Context* c, uint8_t instruction, int a, Assembler::Memory* b, bool rex)
{
  if (b->traceHandler) {
    b->traceHandler->handleTrace(codePromise(c, c->code.length()));
  }

  if (rex) {
    ::rex(c);
  }

  encode(c, &instruction, 1, a, b->base, b->offset, b->index, b->scale);
}

void
encode2(Context* c, uint16_t instruction, int a, Assembler::Memory* b,
        bool rex)
{
  if (b->traceHandler) {
    b->traceHandler->handleTrace(codePromise(c, c->code.length()));
  }

  if (rex) {
    ::rex(c);
  }

  uint8_t i[2] = { instruction >> 8, instruction & 0xff };
  encode(c, i, 2, a, b->base, b->offset, b->index, b->scale);
}

typedef void (*OperationType)(Context*);
OperationType
Operations[OperationCount];

typedef void (*UnaryOperationType)(Context*, unsigned, Assembler::Operand*);
UnaryOperationType
UnaryOperations[UnaryOperationCount * OperandTypeCount];

typedef void (*BinaryOperationType)
(Context*, unsigned, Assembler::Operand*, Assembler::Operand*);
BinaryOperationType
BinaryOperations[BinaryOperationCount * OperandTypeCount * OperandTypeCount];

void
return_(Context* c)
{
  c->code.append(0xc3);
}

void
unconditional(Context* c, unsigned jump, Assembler::Constant* a)
{
  appendOffsetTask(c, a->value, c->code.length(), 5);

  c->code.append(jump);
  c->code.append4(0);
}

void
conditional(Context* c, unsigned condition, Assembler::Constant* a)
{
  appendOffsetTask(c, a->value, c->code.length(), 6);
  
  c->code.append(0x0f);
  c->code.append(condition);
  c->code.append4(0);
}

void
callC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  unconditional(c, 0xe8, a);  
}

void
jumpR(Context* c, unsigned size UNUSED, Assembler::Register* a)
{
  assert(c, size == BytesPerWord);

  c->code.append(0xff);
  c->code.append(0xd0 | a->low);
}

void
moveCR(Context* c, unsigned size UNUSED, Assembler::Constant* a,
       Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4); // todo

  rex(c);
  c->code.append(0xb8 | b->low);
  c->code.appendAddress(a->value->value());
}

void
moveCM(Context* c, unsigned size UNUSED, Assembler::Constant* a,
       Assembler::Memory* b)
{
  assert(c, isInt32(a->value->value())); // todo
  assert(c, BytesPerWord == 8 or size == 4); // todo

  encode(c, 0xc7, 0, b, true);
  c->code.append4(a->value->value());
}

void
moveRM(Context* c, unsigned size, Assembler::Register* a, Assembler::Memory* b)
{
  if (BytesPerWord == 4 and size == 8) {
    moveRM(c, 4, a, b);
    
    Assembler::Register ah(a->high);
    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);
    moveRM(c, 4, &ah, &bh);
  } else if (BytesPerWord == 8 and size == 4) {
    encode(c, 0x89, a->low, b, false);
  } else {
    encode(c, 0x89, a->low, b, true);
  }
}

void
moveRR(Context* c, unsigned size, Assembler::Register* a,
       Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    moveRR(c, 4, a, b);
    
    Assembler::Register ah(a->low);
    Assembler::Register bh(b->low);
    moveRR(c, 4, &ah, &bh);
  } else {
    rex(c);
    c->code.append(0x89);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
move4To8MR(Context* c, unsigned, Assembler::Memory* a, Assembler::Register* b)
{
  assert(c, BytesPerWord == 8); // todo

  encode(c, 0x63, b->low, a, true);
}

void
addRR(Context* c, unsigned size, Assembler::Register* a,
       Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4); // todo

  rex(c);
  c->code.append(0x01);
  c->code.append(0xc0 | (a->low << 3) | b->low);
}

void
populateTables()
{
  Operations[Return] = return_;

  UnaryOperations[INDEX1(Call, Constant)] = CAST1(callC);
  UnaryOperations[INDEX1(Jump, Register)] = CAST1(jumpR);

  BinaryOperations[INDEX2(Move4, Constant, Register)] = CAST2(moveCR);
  BinaryOperations[INDEX2(Move8, Constant, Register)] = CAST2(moveCR);
  BinaryOperations[INDEX2(Move4, Constant, Memory)] = CAST2(moveCM);
  BinaryOperations[INDEX2(Move8, Constant, Memory)] = CAST2(moveCM);
  BinaryOperations[INDEX2(Move4, Register, Memory)] = CAST2(moveRM);
  BinaryOperations[INDEX2(Move8, Register, Memory)] = CAST2(moveRM);
  BinaryOperations[INDEX2(Move4, Register, Register)] = CAST2(moveRR);
  BinaryOperations[INDEX2(Move8, Register, Register)] = CAST2(moveRR);
  BinaryOperations[INDEX2(Move4To8, Memory, Register)] = CAST2(move4To8MR);
  BinaryOperations[INDEX2(Add, Register, Register)] = CAST2(addRR);
}

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s, Allocator* a, Zone* zone): c(s, a, zone) {
    static bool populated = false;
    if (not populated) {
      populated = true;
      populateTables();
    }
  }

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
    assert(&c, BytesPerWord == 8);

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
      abort(&c);
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
      abort(&c);
    }
  }

  virtual void getTargets(UnaryOperation /*op*/, unsigned /*size*/,
                          Register* r)
  {
    // todo
    r->low = NoRegister;
    r->high = NoRegister;
  }

  virtual void getTargets(BinaryOperation /*op*/, unsigned /*size*/,
                          Register* a, Register* b)
  {
    // todo
    a->low = NoRegister;
    a->high = NoRegister;
    b->low = NoRegister;
    b->high = NoRegister;
  }

  virtual void apply(Operation op) {
    Operations[op](&c);
  }

  virtual void apply(UnaryOperation op, unsigned size,
                     OperandType type, Operand* operand)
  {
    UnaryOperations[INDEX1(op, type)](&c, size, operand);
  }

  virtual void apply(BinaryOperation op, unsigned size,
                     OperandType aType, Operand* a,
                     OperandType bType, Operand* b)
  {
    BinaryOperations[INDEX2(op, aType, bType)](&c, size, a, b);
  }

  virtual void writeTo(uint8_t* dst) {
    c.result = dst;
    memcpy(dst, c.code.data, c.code.length());
    
    for (Task* t = c.tasks; t; t = t->next) {
      t->run(&c);
    }
  }

  virtual unsigned length() {
    return c.code.length();
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
    c.code.dispose();
  }

  Context c;
};

} // namespace

namespace vm {

Assembler*
makeAssembler(System* system, Allocator* allocator, Zone* zone)
{
  return new (zone->allocate(sizeof(MyAssembler)))
    MyAssembler(system, allocator, zone);  
}

} // namespace vm
