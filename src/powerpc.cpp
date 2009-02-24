/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "assembler.h"
#include "vector.h"

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)
#define CAST3(x) reinterpret_cast<TernaryOperationType>(x)

using namespace vm;

namespace {

const unsigned FrameFooterSize = 6;

const int StackRegister = 1;
const int ThreadRegister = 20;

class MyBlock: public Assembler::Block {
 public:
  MyBlock(unsigned offset):
    next(0), offset(offset), start(~0), size(0)
  { }

  virtual unsigned resolve(unsigned start, Assembler::Block* next) {
    this->start = start;
    this->next = static_cast<MyBlock*>(next);

    return start + size;
  }

  MyBlock* next;
  unsigned offset;
  unsigned start;
  unsigned size;
};

class Task;

class Context {
 public:
  Context(System* s, Allocator* a, Zone* zone):
    s(s), zone(zone), client(0), code(s, a, 1024), tasks(0), result(0),
    firstBlock(new (zone->allocate(sizeof(MyBlock))) MyBlock(0)),
    lastBlock(firstBlock)
  { }

  System* s;
  Zone* zone;
  Assembler::Client* client;
  Vector code;
  Task* tasks;
  uint8_t* result;
  MyBlock* firstBlock;
  MyBlock* lastBlock;
};

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual void run(Context* c) = 0;

  Task* next;
};

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, Assembler::Operand*);

typedef void (*BinaryOperationType)
(Context*, unsigned, Assembler::Operand*, unsigned, Assembler::Operand*);

typedef void (*TernaryOperationType)
(Context*, unsigned, Assembler::Operand*, Assembler::Operand*,
 Assembler::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(System* s): s(s) { }

  System* s;
  OperationType operations[OperationCount];
  UnaryOperationType unaryOperations[UnaryOperationCount
                                     * OperandTypeCount];
  BinaryOperationType binaryOperations
  [BinaryOperationCount * OperandTypeCount * OperandTypeCount];
  TernaryOperationType ternaryOperations
  [TernaryOperationCount * OperandTypeCount];
};

inline void NO_RETURN
abort(Context* c)
{
  abort(c->s);
}

inline void NO_RETURN
abort(ArchitectureContext* c)
{
  abort(c->s);
}

#ifndef NDEBUG
inline void
assert(Context* c, bool v)
{
  assert(c->s, v);
}

inline void
assert(ArchitectureContext* c, bool v)
{
  assert(c->s, v);
}
#endif // not NDEBUG

inline void
expect(Context* c, bool v)
{
  expect(c->s, v);
}

class Offset: public Promise {
 public:
  Offset(Context* c, MyBlock* block, unsigned offset):
    c(c), block(block), offset(offset)
  { }

  virtual bool resolved() {
    return block->start != static_cast<unsigned>(~0);
  }
  
  virtual int64_t value() {
    assert(c, resolved());

    return block->start + (offset - block->offset);
  }

  Context* c;
  MyBlock* block;
  unsigned offset;
};

Promise*
offset(Context* c)
{
  return new (c->zone->allocate(sizeof(Offset)))
    Offset(c, c->lastBlock, c->code.length());
}

bool
bounded(int right, int left, int32_t v)
{
  return ((v << left) >> left) == v and ((v >> right) << right) == v;
}

void*
updateOffset(System* s, uint8_t* instruction, bool conditional, int64_t value)
{
  int32_t v = reinterpret_cast<uint8_t*>(value) - instruction - 4;
    
  if (conditional) {
    expect(s, bounded(2, 16, v));
    *reinterpret_cast<int32_t*>(instruction) |= v & 0xFFFC;
  } else {
    expect(s, bounded(2, 6, v));
    *reinterpret_cast<int32_t*>(instruction) |= v & 0x3FFFFFC;
  }

  *reinterpret_cast<int32_t*>(instruction) |= v;

  return instruction + 4;
}

class OffsetListener: public Promise::Listener {
 public:
  OffsetListener(System* s, uint8_t* instruction, bool conditional):
    s(s),
    instruction(instruction),
    conditional(conditional)
  { }

  virtual void* resolve(int64_t value) {
    return updateOffset(s, instruction, conditional, value);
  }

  System* s;
  uint8_t* instruction;
  bool conditional;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset,
             bool conditional):
    Task(next),
    promise(promise),
    instructionOffset(instructionOffset),
    conditional(conditional)
  { }

  virtual void run(Context* c) {
    if (promise->resolved()) {
      updateOffset
        (c->s, c->result + instructionOffset->value(), conditional,
         promise->value());
    } else {
      new (promise->listen(sizeof(OffsetListener)))
        OffsetListener(c->s, c->result + instructionOffset->value(),
                       conditional);
    }
  }

  Promise* promise;
  Promise* instructionOffset;
  bool conditional;
};

void
appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset,
                 bool conditional)
{
  c->tasks = new (c->zone->allocate(sizeof(OffsetTask))) OffsetTask
    (c->tasks, promise, instructionOffset, conditional);
}

void
updateImmediate(System* s, void* dst, int64_t src, unsigned size)
{
  switch (size) {
  case 4: {
    static_cast<int32_t*>(dst)[0] |= src & 0xFFFF;
    static_cast<int32_t*>(dst)[1] |= src >> 16;
  } break;

  default: abort(s);
  }
}

class ImmediateListener: public Promise::Listener {
 public:
  ImmediateListener(System* s, void* dst, unsigned size, unsigned offset):
    s(s), dst(dst), size(size), offset(offset)
  { }

  virtual void* resolve(int64_t value) {
    updateImmediate(s, dst, value, size);
    return static_cast<uint8_t*>(dst) + offset;
  }

  System* s;
  void* dst;
  unsigned size;
  unsigned offset;
};

class ImmediateTask: public Task {
 public:
  ImmediateTask(Task* next, Promise* promise, Promise* offset, unsigned size,
                unsigned promiseOffset):
    Task(next),
    promise(promise),
    offset(offset),
    size(size),
    promiseOffset(promiseOffset)
  { }

  virtual void run(Context* c) {
    if (promise->resolved()) {
      updateImmediate
        (c->s, c->result + offset->value(), promise->value(), size);
    } else {
      new (promise->listen(sizeof(ImmediateListener))) ImmediateListener
        (c->s, c->result + offset->value(), size, promiseOffset);
    }
  }

  Promise* promise;
  Promise* offset;
  unsigned size;
  unsigned promiseOffset;
};

void
appendImmediateTask(Context* c, Promise* promise, Promise* offset,
                    unsigned size, unsigned promiseOffset = 0)
{
  c->tasks = new (c->zone->allocate(sizeof(ImmediateTask))) ImmediateTask
    (c->tasks, promise, offset, size, promiseOffset);
}

inline unsigned
index(UnaryOperation operation, OperandType operand)
{
  return operation + (UnaryOperationCount * operand);
}

inline unsigned
index(BinaryOperation operation,
      OperandType operand1,
      OperandType operand2)
{
  return operation
    + (BinaryOperationCount * operand1)
    + (BinaryOperationCount * OperandTypeCount * operand2);
}

inline unsigned
index(TernaryOperation operation,
      OperandType operand1)
{
  return operation + (TernaryOperationCount * operand1);
}

namespace isa {

// formats:

inline int32_t
formatD(int32_t op, int32_t rt, int32_t ra, int32_t d)
{
  return op<<26|rt<<21|ra<<16|(d & 0xFFFF);
}

inline int32_t
formatI(int32_t op, int32_t li, int32_t aa, int32_t lk)
{
  return op<<26|li<<2|aa<<1|lk;
}

inline int32_t
formatM(int32_t op, int32_t rs, int32_t ra, int32_t rb, int32_t mb, int32_t me,
        int32_t rc)
{
  return op<<26|rs<<21|ra<<16|rb<<11|mb<<6|me<<1|rc;
}

inline int32_t
formatX(int32_t op, int32_t rt, int32_t ra, int32_t rb, int32_t xo, int32_t rc) {
  return op<<26|rt<<21|ra<<16|rb<<11|xo<<1|rc;
}

inline int32_t
formatXL(int32_t op, int32_t bt, int32_t ba, int32_t bb, int32_t xo,
         int32_t lk)
{
  return op<<26|bt<<21|ba<<16|bb<<11|xo<<1|lk;
}

inline int32_t
formatXFX(int32_t op, int32_t rt, int32_t spr, int32_t xo)
{
  return op<<26|rt<<21|spr<<11|xo<<1;
}

// instructions:

inline void
addi(Context* c, int rt, int ra, int i)
{
  c->code.append4(formatD(14, rt, ra, i));
}

inline void
addis(Context* c, int rt, int ra, int i)
{
  c->code.append4(formatD(15, rt, ra, i));
}

inline void
ba(Context* c, int i)
{
  c->code.append4(formatI(18, i, 1, 0));
}

inline void
bcctr(Context* c, int bo, int bi, int lk)
{
  c->code.append4(formatXL(19, bo, bi, 0, 528, lk));  
}

inline void
bclr(Context* c, int bo, int bi, int lk)
{
  c->code.append4(formatXL(19, bo, bi, 0, 16, lk));  
}

inline void
extsb(Context* c, int ra, int rs)
{
  c->code.append4(formatX(31, rs, ra, 0, 954, 0));
}

inline void
extsh(Context* c, int ra, int rs)
{
  c->code.append4(formatX(31, rs, ra, 0, 922, 0));
}

inline void
lbz(Context* c, int rd, int ra, int i)
{
  c->code.append4(formatD(34, rd, ra, i));
}

inline void
lbzx(Context* c, int rd, int ra, int rb)
{
  c->code.append4(formatX(31, rd, ra, rb, 87, 0));
}

inline void
lha(Context* c, int rd, int ra, int i)
{
  c->code.append4(formatD(42, rd, ra, i));
}

inline void
lhax(Context* c, int rd, int ra, int rb)
{
  c->code.append4(formatX(31, rd, ra, rb, 343, 0));
}

inline void
lwz(Context* c, int rd, int ra, int i)
{
  c->code.append4(formatD(32, rd, ra, i));
}

inline void
lwzx(Context* c, int rd, int ra, int rb)
{
  c->code.append4(formatX(31, rd, ra, rb, 23, 0));
}

inline void
mfspr(Context* c, int rd, int spr)
{
  c->code.append4(formatXFX(31, rd, spr, 339));
}

inline void
mtspr(Context* c, int spr, int rd)
{
  c->code.append4(formatXFX(31, rd, spr, 467));
}

inline void
or_(Context* c, int rt, int ra, int rb)
{
  c->code.append4(formatX(31, ra, rt, rb, 444, 0));
}

inline void
rlwinm(Context* c, int rt, int ra, int i, int mb, int me)
{
  c->code.append4(formatM(21, ra, rt, i, mb, me, 0));
}

inline void
srawi(Context* c, int rt, int ra, int sh)
{
  c->code.append4(formatX(31, ra, rt, sh, 824, 0));
}

inline void
stb(Context* c, int rs, int ra, int i)
{
  c->code.append4(formatD(38, rs, ra, i));
}

inline void
stbx(Context* c, int rs, int ra, int rb)
{
  c->code.append4(formatX(31, rs, ra, rb, 215, 0));
}

inline void
sth(Context* c, int rs, int ra, int i)
{
  c->code.append4(formatD(44, rs, ra, i));
}

inline void
sthx(Context* c, int rs, int ra, int rb)
{
  c->code.append4(formatX(31, rs, ra, rb, 407, 0));
}

inline void
stw(Context* c, int rs, int ra, int i)
{
  c->code.append4(formatD(36, rs, ra, i));
}

inline void
stwu(Context* c, int rs, int ra, int i)
{
  c->code.append4(formatD(37, rs, ra, i));
}

inline void
stwx(Context* c, int rs, int ra, int rb)
{
  c->code.append4(formatX(31, rs, ra, rb, 151, 0));
}

// mnemonics:

inline void
bctr(Context* c)
{
  bcctr(c, 20, 0, 0);
}

inline void
bctrl(Context* c)
{
  bcctr(c, 20, 0, 1);
}

inline void
blr(Context* c)
{
  bclr(c, 20, 0, 0);
}

inline void
li(Context* c, int rd, int i)
{
  addi(c, rd, 0, i);
}

inline void
lis(Context* c, int rd, int i)
{
  addis(c, rd, 0, i);
}

inline void
mflr(Context* c, int rd)
{
  mfspr(c, rd, 8);
}

inline void
mr(Context* c, int rt, int ra)
{
  return or_(c, rt, ra, ra);
}

inline void
mtlr(Context* c, int rd)
{
  mtspr(c, 8, rd);
}

inline void
mtctr(Context* c, int rd)
{
  mtspr(c, 9, rd);
}

inline void
slwi(Context* c, int rt, int ra, int i)
{
  rlwinm(c, rt, ra, i, 0, 31 - i);
}

} // namespace isa

using namespace isa;

void
return_(Context* c)
{
  blr(c);
}

void
jumpR(Context* c, unsigned size UNUSED, Assembler::Register* target)
{
  assert(c, size == BytesPerWord);

  mtctr(c, target->low);
  bctr(c);
}

void
shiftLeftCRR(Context* c, unsigned size, Assembler::Constant* shift,
             Assembler::Register* src, Assembler::Register* dst)
{
  if (size == 4) {
    slwi(c, dst->low, src->low, shift->value->value());
  } else {
    abort(c); // todo
  }
}

void
addCRR(Context* c, unsigned size, Assembler::Constant* addend,
       Assembler::Register* src, Assembler::Register* dst)
{
  if (size == 4) {
    int32_t v = addend->value->value();
    addi(c, dst->low, src->low, v);
    addis(c, dst->low, src->low, v >> 16);
  } else {
    abort(c); // todo
  }
}

void
moveRR(Context* c, unsigned srcSize, Assembler::Register* src,
       unsigned dstSize, Assembler::Register* dst);

void
swapRR(Context* c, unsigned aSize, Assembler::Register* a,
       unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == BytesPerWord);
  assert(c, bSize == BytesPerWord);

  Assembler::Register tmp(c->client->acquireTemporary());
  moveRR(c, aSize, a, bSize, &tmp);
  moveRR(c, bSize, b, aSize, a);
  moveRR(c, bSize, &tmp, bSize, b);
}

void
moveRR(Context* c, unsigned srcSize, Assembler::Register* src,
       unsigned dstSize, Assembler::Register* dst)
{
  switch (srcSize) {
  case 1:
    extsb(c, src->low, dst->low);
    break;
    
  case 2:
    extsh(c, src->low, dst->low);
    break;
    
  case 4:
  case 8:
    if (srcSize == 4 and dstSize == 8) {
      Assembler::Register dstHigh(dst->high);
      moveRR(c, 4, src, 4, dst);
      moveRR(c, 4, src, 4, &dstHigh);
      srawi(c, dst->high, dst->high, 31);
    } else if (srcSize == 8 and dstSize == 8) {
      Assembler::Register srcHigh(src->high);
      Assembler::Register dstHigh(dst->high);

      if (src->high == dst->low) {
        if (src->low == dst->high) {
          swapRR(c, 4, src, 4, dst);
        } else {
          moveRR(c, 4, &srcHigh, 4, &dstHigh);
          moveRR(c, 4, src, 4, dst);
        }
      } else {
        moveRR(c, 4, src, 4, dst);
        moveRR(c, 4, &srcHigh, 4, &dstHigh);
      }
    } else if (src->low != dst->low) {
      mr(c, dst->low, src->low);
    }
    break;
  }
}

int
normalize(Context* c, int offset, int index, unsigned scale, 
          bool* preserveIndex)
{
  if (offset != 0 or scale != 1) {
    Assembler::Register normalizedIndex
      (*preserveIndex ? c->client->acquireTemporary() : index);
    
    *preserveIndex = false;

    int scaled;

    if (scale != 1) {
      Assembler::Register unscaledIndex(index);

      ResolvedPromise scalePromise(log(scale));
      Assembler::Constant scaleConstant(&scalePromise);
      
      shiftLeftCRR
        (c, BytesPerWord, &scaleConstant, &unscaledIndex, &normalizedIndex);

      scaled = normalizedIndex.low;
    } else {
      scaled = index;
    }

    if (offset != 0) {
      Assembler::Register untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      Assembler::Constant offsetConstant(&offsetPromise);

      addCRR
        (c, BytesPerWord, &offsetConstant, &untranslatedIndex,
         &normalizedIndex);
    }

    return normalizedIndex.low;
  } else {
    return index;
  }
}

void
store(Context* c, unsigned size, Assembler::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex)
{
  if (index != NoRegister) {
    int normalized = normalize(c, offset, index, scale, &preserveIndex);

    switch (size) {
    case 1:
      stbx(c, src->low, base, normalized);
      break;

    case 2:
      sthx(c, src->low, base, normalized);
      break;

    case 4:
      stwx(c, src->low, base, normalized);
      break;

    case 8: {
      Assembler::Register srcHigh(src->high);
      store(c, 4, &srcHigh, base, 0, normalized, 1, preserveIndex);
      store(c, 4, src, base, 4, normalized, 1, preserveIndex);
    } break;

    default: abort(c);
    }
  } else {
    switch (size) {
    case 1:
      stb(c, src->low, base, offset);
      break;

    case 2:
      sth(c, src->low, base, offset);
      break;

    case 4:
      stw(c, src->low, base, offset);
      break;

    case 8: {
      Assembler::Register srcHigh(src->high);
      store(c, 4, &srcHigh, base, offset, NoRegister, 1, false);
      store(c, 4, src, base, offset + 4, NoRegister, 1, false);
    } break;

    default: abort(c);
    }
  }
}

void
moveRM(Context* c, unsigned srcSize, Assembler::Register* src,
       unsigned dstSize UNUSED, Assembler::Memory* dst)
{
  assert(c, srcSize == dstSize);

  store(c, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
}

void
moveAndUpdateRM(Context* c, unsigned srcSize, Assembler::Register* src,
                unsigned dstSize UNUSED, Assembler::Memory* dst)
{
  assert(c, srcSize == BytesPerWord);
  assert(c, dstSize == BytesPerWord);
  assert(c, dst->index == NoRegister);

  stwu(c, src->low, dst->base, dst->offset);  
}

void
load(Context* c, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, Assembler::Register* dst,
     bool preserveIndex)
{
  if (index != NoRegister) {
    int normalized = normalize(c, offset, index, scale, &preserveIndex);

    switch (srcSize) {
    case 1:
      lbzx(c, dst->low, base, normalized);
      moveRR(c, 1, dst, BytesPerWord, dst);
      break;

    case 2:
      lhax(c, dst->low, base, normalized);
      break;

    case 4:
    case 8: {
      if (srcSize == 4 and dstSize == 8) {
        load(c, 4, base, 0, normalized, 1, 4, dst, preserveIndex);
        moveRR(c, 4, dst, 8, dst);
      } else if (srcSize == 8 and dstSize == 8) {
        Assembler::Register dstHigh(dst->high);
        load(c, 4, base, 0, normalized, 1, 4, &dstHigh, preserveIndex);
        load(c, 4, base, 4, normalized, 1, 4, dst, preserveIndex);
      } else {
        lwzx(c, dst->low, base, offset);
      }
    } break;

    default: abort(c);
    }
  } else {
    switch (srcSize) {
    case 1:
      lbz(c, dst->low, base, offset);
      extsb(c, dst->low, dst->low);
      break;

    case 2:
      lha(c, dst->low, base, offset);
      break;

    case 4:
      lwz(c, dst->low, base, offset);
      break;

    case 8: {
      if (srcSize == 4 and dstSize == 8) {
        load(c, 4, base, offset, NoRegister, 1, 4, dst, false);
        moveRR(c, 4, dst, 8, dst);
      } else if (srcSize == 8 and dstSize == 8) {
        Assembler::Register dstHigh(dst->high);
        load(c, 4, base, offset, NoRegister, 1, 4, &dstHigh, false);
        load(c, 4, base, offset + 4, NoRegister, 1, 4, dst, false);
      } else {
        lwzx(c, dst->low, base, offset);
      }
    } break;

    default: abort(c);
    }
  }
}

void
moveMR(Context* c, unsigned srcSize, Assembler::Memory* src,
       unsigned dstSize, Assembler::Register* dst)
{
  load(c, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true);
}

void
moveCR2(Context* c, unsigned, Assembler::Constant* src,
       unsigned dstSize, Assembler::Register* dst, unsigned promiseOffset)
{
  if (dstSize == 4) {
    if (src->value->resolved()) {
      int32_t v = src->value->value();
      li(c, dst->low, v);
      if (v >> 16) {
        lis(c, dst->low, v >> 16);
      }
    } else {
      appendImmediateTask
        (c, src->value, offset(c), BytesPerWord, promiseOffset);
      li(c, dst->low, 0);
      lis(c, dst->low, 0);
    }
  } else {
    abort(c); // todo
  }
}

void
moveCR(Context* c, unsigned srcSize, Assembler::Constant* src,
       unsigned dstSize, Assembler::Register* dst)
{
  moveCR2(c, srcSize, src, dstSize, dst, 0);
}

ShiftMaskPromise*
shiftMaskPromise(Context* c, Promise* base, unsigned shift, int64_t mask)
{
  return new (c->zone->allocate(sizeof(ShiftMaskPromise)))
    ShiftMaskPromise(base, shift, mask);
}

void
moveCM(Context* c, unsigned srcSize, Assembler::Constant* src,
       unsigned dstSize, Assembler::Memory* dst)
{
  switch (dstSize) {
  case 8: {
    Assembler::Constant srcHigh
      (shiftMaskPromise(c, src->value, 32, 0xFFFFFFFF));
    Assembler::Constant srcLow
      (shiftMaskPromise(c, src->value, 0, 0xFFFFFFFF));
    
    Assembler::Memory dstLow
      (dst->base, dst->offset + 4, dst->index, dst->scale);
    
    moveCM(c, 4, &srcLow, 4, &dstLow);
    moveCM(c, 4, &srcHigh, 4, dst);
  } break;

  default:
    Assembler::Register tmp(c->client->acquireTemporary());
    moveCR(c, srcSize, src, dstSize, &tmp);
    moveRM(c, dstSize, &tmp, dstSize, dst);
  }
}

void
callR(Context* c, unsigned size, Assembler::Register* target)
{
  assert(c, size == BytesPerWord);

  mtctr(c, target->low);
  bctrl(c);
}

void
callC(Context* c, unsigned size, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), false);
  ba(c, 0);
}

void
longCallC(Context* c, unsigned size, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  Assembler::Register tmp(0);
  moveCR2(c, BytesPerWord, target, BytesPerWord, &tmp, 12);
  callR(c, BytesPerWord, &tmp);
}

void
longJumpC(Context* c, unsigned size, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  Assembler::Register tmp(0);
  moveCR2(c, BytesPerWord, target, BytesPerWord, &tmp, 12);
  jumpR(c, BytesPerWord, &tmp);
}

void
populateTables(ArchitectureContext* c)
{
  const OperandType C = ConstantOperand;
//   const OperandType A = AddressOperand;
  const OperandType R = RegisterOperand;
  const OperandType M = MemoryOperand;

  OperationType* zo = c->operations;
  UnaryOperationType* uo = c->unaryOperations;
  BinaryOperationType* bo = c->binaryOperations;
//   TernaryOperationType* to = c->ternaryOperations;

  zo[Return] = return_;

  uo[index(LongCall, C)] = CAST1(longCallC);

  uo[index(LongJump, C)] = CAST1(longJumpC);

  uo[index(Jump, R)] = CAST1(jumpR);

  uo[index(Call, C)] = CAST1(callC);
  uo[index(AlignedCall, C)] = CAST1(callC);

  bo[index(Move, R, R)] = CAST2(moveRR);
  bo[index(Move, C, R)] = CAST2(moveCR);
  bo[index(Move, C, M)] = CAST2(moveCM);
  bo[index(Move, M, R)] = CAST2(moveMR);
  bo[index(Move, R, M)] = CAST2(moveRM);
}

class MyArchitecture: public Assembler::Architecture {
 public:
  MyArchitecture(System* system): c(system), referenceCount(0) {
    populateTables(&c);
  }

  virtual unsigned registerCount() {
    return 32;
  }

  virtual int stack() {
    return StackRegister;
  }

  virtual int thread() {
    return ThreadRegister;
  }

  virtual int returnLow(unsigned size) {
    return (size > BytesPerWord ? 4 : 3);
  }

  virtual int returnHigh() {
    return (BytesPerWord == 4 ? 3 : NoRegister);
  }

  virtual bool condensedAddressing() {
    return false;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case StackRegister:
    case ThreadRegister:
      return true;

    default:
      return false;
    }
  }

  virtual unsigned argumentRegisterCount() {
    return 8;
  }

  virtual int argumentRegister(unsigned index) {
    assert(&c, index < argumentRegisterCount());

    return index + 3;
  }

  virtual void updateCall(UnaryOperation op UNUSED,
                          bool assertAlignment UNUSED, void* /*returnAddress*/,
                          void* /*newTarget*/)
  {
    // todo
    abort(&c);
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    const unsigned alignment = 16 / BytesPerWord;
    return (ceiling(sizeInWords + FrameFooterSize, alignment) * alignment)
      - FrameFooterSize;
  }

  virtual void* frameIp(void* stack) {
    return stack ? *static_cast<void**>(stack) : 0;
  }

  virtual unsigned frameHeaderSize() {
    return 0;
  }

  virtual unsigned frameReturnAddressSize() {
    return 1;
  }

  virtual unsigned frameFooterSize() {
    return FrameFooterSize;
  }

  virtual void nextFrame(void** stack, void**) {
    *stack = static_cast<void**>(*stack);
  }

  virtual void plan
  (UnaryOperation,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   bool* thunk)
  {
    *aTypeMask = (1 << RegisterOperand);
    *aRegisterMask = ~static_cast<uint64_t>(0);
    *thunk = false;
  }

  virtual void plan
  (BinaryOperation op,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   unsigned, uint8_t* bTypeMask, uint64_t* bRegisterMask,
   bool* thunk)
  {
    *aTypeMask = ~0;
    *aRegisterMask = ~static_cast<uint64_t>(0);

    *bTypeMask = (1 << RegisterOperand) | (1 << MemoryOperand);
    *bRegisterMask = ~static_cast<uint64_t>(0);

    *thunk = false;

    switch (op) {
    case Compare:
      *aTypeMask = (1 << RegisterOperand) | (1 << ConstantOperand);
      *bTypeMask = (1 << RegisterOperand);
      break;

    case Negate:
      *aTypeMask = (1 << RegisterOperand);
      *bTypeMask = (1 << RegisterOperand);
      break;

    default:
      break;
    }
  }

  virtual void plan
  (TernaryOperation op,
   unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   unsigned, uint8_t* bTypeMask, uint64_t* bRegisterMask,
   unsigned, uint8_t* cTypeMask, uint64_t* cRegisterMask,
   bool* thunk)
  {
    *aTypeMask = (1 << RegisterOperand) | (1 << ConstantOperand);
    *aRegisterMask = ~static_cast<uint64_t>(0);

    *bTypeMask = (1 << RegisterOperand);
    *bRegisterMask = ~static_cast<uint64_t>(0);

    *thunk = false;

    switch (op) {
    case Divide:
      if (BytesPerWord == 4 and aSize == 8) {
        *bTypeMask = ~0;
        *thunk = true;        
      }
      break;

    case Remainder:
      if (BytesPerWord == 4 and aSize == 8) {
        *bTypeMask = ~0;
        *thunk = true;
      }
      break;

    default:
      break;
    }

    *cTypeMask = *bTypeMask;
    *cRegisterMask = *bRegisterMask;
  }

  virtual void acquire() {
    ++ referenceCount;
  }

  virtual void release() {
    if (-- referenceCount == 0) {
      c.s->free(this);
    }
  }

  ArchitectureContext c;
  unsigned referenceCount;
};

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s, Allocator* a, Zone* zone, MyArchitecture* arch):
    c(s, a, zone), arch_(arch)
  { }

  virtual void setClient(Client* client) {
    assert(&c, c.client == 0);
    c.client = client;
  }

  virtual Architecture* arch() {
    return arch_;
  }

  virtual void saveFrame(unsigned stackOffset, unsigned) {
    Register stack(StackRegister);
    Memory stackDst(ThreadRegister, stackOffset);
    moveRM(&c, BytesPerWord, &stack, BytesPerWord, &stackDst);
  }

  virtual void pushFrame(unsigned argumentCount, ...) {
    struct {
      unsigned size;
      OperandType type;
      Operand* operand;
    } arguments[argumentCount];

    va_list a; va_start(a, argumentCount);
    unsigned footprint = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      arguments[i].size = va_arg(a, unsigned);
      arguments[i].type = static_cast<OperandType>(va_arg(a, int));
      arguments[i].operand = va_arg(a, Operand*);
      footprint += ceiling(arguments[i].size, BytesPerWord);
    }
    va_end(a);

    allocateFrame(arch_->alignFrameSize(footprint));
    
    unsigned offset = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      if (i < arch_->argumentRegisterCount()) {
        Register dst(arch_->argumentRegister(i));

        apply(Move,
              arguments[i].size, arguments[i].type, arguments[i].operand,
              pad(arguments[i].size), RegisterOperand, &dst);

        offset += ceiling(arguments[i].size, BytesPerWord);
      } else {
        Memory dst(ThreadRegister, (offset + FrameFooterSize) * BytesPerWord);

        apply(Move,
              arguments[i].size, arguments[i].type, arguments[i].operand,
              pad(arguments[i].size), MemoryOperand, &dst);

        offset += ceiling(arguments[i].size, BytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint) {
    Register returnAddress(0);
    mflr(&c, returnAddress.low);

    Memory returnAddressDst(StackRegister, 8);
    moveRM(&c, BytesPerWord, &returnAddress, BytesPerWord, &returnAddressDst);

    Register stack(StackRegister);
    Memory stackDst(StackRegister, -footprint * BytesPerWord);
    moveAndUpdateRM(&c, BytesPerWord, &stack, BytesPerWord, &stackDst);
  }

  virtual void popFrame() {
    Register stack(StackRegister);
    Memory stackSrc(StackRegister, 0);
    moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &stack);

    Register returnAddress(0);
    Memory returnAddressSrc(StackRegister, 8);
    moveMR(&c, BytesPerWord, &returnAddressSrc, BytesPerWord, &returnAddress);
    
    mtlr(&c, returnAddress.low);
  }

  virtual void apply(Operation op) {
    arch_->c.operations[op](&c);
  }

  virtual void apply(UnaryOperation op,
                     unsigned aSize, OperandType aType, Operand* aOperand)
  {
    arch_->c.unaryOperations[index(op, aType)](&c, aSize, aOperand);
  }

  virtual void apply(BinaryOperation op,
                     unsigned aSize, OperandType aType, Operand* aOperand,
                     unsigned bSize, OperandType bType, Operand* bOperand)
  {
    arch_->c.binaryOperations[index(op, aType, bType)]
      (&c, aSize, aOperand, bSize, bOperand);
  }

  virtual void apply(TernaryOperation op,
                     unsigned, OperandType aType, Operand* aOperand,
                     unsigned bSize, OperandType bType UNUSED,
                     Operand* bOperand,
                     unsigned cSize, OperandType cType UNUSED,
                     Operand* cOperand)
  {
    assert(&c, bSize == cSize);
    assert(&c, bType == RegisterOperand);
    assert(&c, cType == RegisterOperand);

    arch_->c.ternaryOperations[index(op, aType)]
      (&c, bSize, aOperand, bOperand, cOperand);
  }

  virtual void writeTo(uint8_t* dst) {
    c.result = dst;
    
    for (MyBlock* b = c.firstBlock; b; b = b->next) {
      memcpy(dst + b->start, c.code.data + b->offset, b->size);
    }
    
    for (Task* t = c.tasks; t; t = t->next) {
      t->run(&c);
    }
  }

  virtual Promise* offset() {
    return ::offset(&c);
  }

  virtual Block* endBlock(bool startNew) {
    MyBlock* b = c.lastBlock;
    b->size = c.code.length() - b->offset;
    if (startNew) {
      c.lastBlock = new (c.zone->allocate(sizeof(MyBlock)))
        MyBlock(c.code.length());
    } else {
      c.lastBlock = 0;
    }
    return b;
  }

  virtual unsigned length() {
    return c.code.length();
  }

  virtual void dispose() {
    c.code.dispose();
  }

  Context c;
  MyArchitecture* arch_;
};

} // namespace

namespace vm {

Assembler::Architecture*
makeArchitecture(System* system)
{
  return new (allocate(system, sizeof(MyArchitecture))) MyArchitecture(system);
}

Assembler*
makeAssembler(System* system, Allocator* allocator, Zone* zone,
              Assembler::Architecture* architecture)
{
  return new (zone->allocate(sizeof(MyAssembler)))
    MyAssembler(system, allocator, zone,
                static_cast<MyArchitecture*>(architecture));
}

} // namespace vm
