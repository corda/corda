/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "assembler.h"
#include "vector.h"

using namespace vm;

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

const unsigned FrameHeaderSize = 2;

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
class AlignmentPadding;

unsigned
padding(AlignmentPadding* p, unsigned index, unsigned offset, unsigned limit);

class MyBlock: public Assembler::Block {
 public:
  MyBlock(unsigned offset):
    next(0), firstPadding(0), lastPadding(0), offset(offset), start(~0),
    size(0)
  { }

  virtual unsigned resolve(unsigned start, Assembler::Block* next) {
    this->start = start;
    this->next = static_cast<MyBlock*>(next);

    return start + size + padding(firstPadding, start, offset, ~0);
  }

  MyBlock* next;
  AlignmentPadding* firstPadding;
  AlignmentPadding* lastPadding;
  unsigned offset;
  unsigned start;
  unsigned size;
};

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

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, Assembler::Operand*);

typedef void (*BinaryOperationType)
(Context*, unsigned, Assembler::Operand*, unsigned, Assembler::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(System* s): s(s) { }

  System* s;
  OperationType operations[OperationCount];
  UnaryOperationType unaryOperations[UnaryOperationCount
                                     * OperandTypeCount];
  BinaryOperationType binaryOperations
  [(BinaryOperationCount + TernaryOperationCount)
   * OperandTypeCount
   * OperandTypeCount];
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

ResolvedPromise*
resolved(Context* c, int64_t value)
{
  return new (c->zone->allocate(sizeof(ResolvedPromise)))
    ResolvedPromise(value);
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

    return block->start + (offset - block->offset)
      + padding(block->firstPadding, block->start, block->offset, offset);
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

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual ~Task() { }

  virtual void run(Context* c) = 0;

  Task* next;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset,
             unsigned instructionSize):
    Task(next),
    promise(promise),
    instructionOffset(instructionOffset),
    instructionSize(instructionSize)
  { }

  virtual void run(Context* c) {
    uint8_t* instruction = c->result + instructionOffset->value();
    intptr_t v = reinterpret_cast<uint8_t*>(promise->value())
      - instruction - instructionSize;
    
    expect(c, isInt32(v));
    
    int32_t v4 = v;
    memcpy(instruction + instructionSize - 4, &v4, 4);
  }

  Promise* promise;
  Promise* instructionOffset;
  unsigned instructionSize;
};

void
appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset,
                 unsigned instructionSize)
{
  c->tasks = new (c->zone->allocate(sizeof(OffsetTask))) OffsetTask
    (c->tasks, promise, instructionOffset, instructionSize);
}

class ImmediateTask: public Task {
 public:
  ImmediateTask(Task* next, Promise* promise, Promise* offset):
    Task(next),
    promise(promise),
    offset(offset)
  { }

  virtual void run(Context* c) {
    intptr_t v = promise->value();
    memcpy(c->result + offset->value(), &v, BytesPerWord);
  }

  Promise* promise;
  Promise* offset;
};

void
appendImmediateTask(Context* c, Promise* promise, Promise* offset)
{
  c->tasks = new (c->zone->allocate(sizeof(ImmediateTask))) ImmediateTask
    (c->tasks, promise, offset);
}

class AlignmentPadding {
 public:
  AlignmentPadding(Context* c): offset(c->code.length()), next(0) {
    if (c->lastBlock->firstPadding) {
      c->lastBlock->lastPadding->next = this;
    } else {
      c->lastBlock->firstPadding = this;
    }
    c->lastBlock->lastPadding = this;
  }

  unsigned offset;
  AlignmentPadding* next;
};

unsigned
padding(AlignmentPadding* p, unsigned index, unsigned offset, unsigned limit)
{
  unsigned padding = 0;
  for (; p; p = p->next) {
    if (p->offset <= limit) {
      index += p->offset - offset;
      while ((index + padding + 1) % 4) {
        ++ padding;
      }
    }
  }
  return padding;
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
rex(Context* c, uint8_t mask, int r)
{
  if (BytesPerWord == 8) {
    c->code.append(mask | ((r & 8) >> 3));
  }
}

void
rex(Context* c)
{
  rex(c, 0x48, rax);
}

void
encode(Context* c, uint8_t instruction, int a, Assembler::Memory* b, bool rex)
{
  if (rex) {
    ::rex(c);
  }

  encode(c, &instruction, 1, a, b->base, b->offset, b->index, b->scale);
}

void
encode2(Context* c, uint16_t instruction, int a, Assembler::Memory* b,
        bool rex)
{
  if (rex) {
    ::rex(c);
  }

  uint8_t i[2] = { instruction >> 8, instruction & 0xff };
  encode(c, i, 2, a, b->base, b->offset, b->index, b->scale);
}

void
return_(Context* c)
{
  c->code.append(0xc3);
}

void
unconditional(Context* c, unsigned jump, Assembler::Constant* a)
{
  appendOffsetTask(c, a->value, offset(c), 5);

  c->code.append(jump);
  c->code.append4(0);
}

void
conditional(Context* c, unsigned condition, Assembler::Constant* a)
{
  appendOffsetTask(c, a->value, offset(c), 6);
  
  c->code.append(0x0f);
  c->code.append(condition);
  c->code.append4(0);
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
    + ((BinaryOperationCount + TernaryOperationCount) * operand1)
    + ((BinaryOperationCount + TernaryOperationCount)
       * OperandTypeCount * operand2);
}

inline unsigned
index(TernaryOperation operation,
      OperandType operand1,
      OperandType operand2)
{
  return BinaryOperationCount + operation
    + ((BinaryOperationCount + TernaryOperationCount) * operand1)
    + ((BinaryOperationCount + TernaryOperationCount)
       * OperandTypeCount * operand2);
}

void
jumpR(Context* c, unsigned size UNUSED, Assembler::Register* a)
{
  assert(c, size == BytesPerWord);

  if (a->low & 8) rex(c, 0x40, a->low);
  c->code.append(0xff);
  c->code.append(0xe0 | (a->low & 7));
}

void
jumpC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  unconditional(c, 0xe9, a);
}

void
jumpM(Context* c, unsigned size UNUSED, Assembler::Memory* a)
{
  assert(c, size == BytesPerWord);

  encode(c, 0xff, 4, a, false);
}

void
jumpIfEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x84, a);
}

void
jumpIfNotEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x85, a);
}

void
jumpIfGreaterC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x8f, a);
}

void
jumpIfGreaterOrEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x8d, a);
}

void
jumpIfLessC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x8c, a);
}

void
jumpIfLessOrEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  conditional(c, 0x8e, a);
}

void
moveCR(Context* c, unsigned aSize, Assembler::Constant* a,
       unsigned bSize, Assembler::Register* b);

void
longJumpC(Context* c, unsigned size, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  if (BytesPerWord == 8) {
    Assembler::Register r(r10);
    moveCR(c, size, a, size, &r);
    jumpR(c, size, &r);
  } else {
    jumpC(c, size, a);
  }
}

void
callR(Context* c, unsigned size UNUSED, Assembler::Register* a)
{
  assert(c, size == BytesPerWord);

  if (a->low & 8) rex(c, 0x40, a->low);
  c->code.append(0xff);
  c->code.append(0xd0 | (a->low & 7));
}

void
callC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  unconditional(c, 0xe8, a);
}

void
alignedCallC(Context* c, unsigned size, Assembler::Constant* a)
{
  new (c->zone->allocate(sizeof(AlignmentPadding))) AlignmentPadding(c);
  callC(c, size, a);
}

void
longCallC(Context* c, unsigned size, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  if (BytesPerWord == 8) {
    Assembler::Register r(r10);
    moveCR(c, size, a, size, &r);
    callR(c, size, &r);
  } else {
    callC(c, size, a);
  }
}

void
pushR(Context* c, unsigned size, Assembler::Register* a)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);

    pushR(c, 4, &ah);
    pushR(c, 4, a);
  } else {
    c->code.append(0x50 | a->low);      
  }
}

void
moveRR(Context* c, unsigned aSize, Assembler::Register* a,
       unsigned bSize, Assembler::Register* b);

void
popR(Context* c, unsigned size, Assembler::Register* a)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);

    popR(c, 4, a);
    popR(c, 4, &ah);
  } else {
    c->code.append(0x58 | a->low);
    if (BytesPerWord == 8 and size == 4) {
      moveRR(c, 4, a, 8, a);
    }
  }
}

void
moveRR(Context* c, unsigned aSize, Assembler::Register* a,
       unsigned bSize, Assembler::Register* b)
{
  if (BytesPerWord == 4 and aSize == 8 and bSize == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    moveRR(c, 4, a, 4, b);
    moveRR(c, 4, &ah, 4, &bh);
  } else {
    switch (aSize) {
    case 1:
      if (BytesPerWord == 4 and a->low > rbx) {
        assert(c, b->low <= rbx);

        moveRR(c, BytesPerWord, a, BytesPerWord, b);
        moveRR(c, 1, b, BytesPerWord, b);
      } else {
        rex(c);
        c->code.append(0x0f);
        c->code.append(0xbe);
        c->code.append(0xc0 | (b->low << 3) | a->low);
      }
      break;

    case 2:
      rex(c);
      c->code.append(0x0f);
      c->code.append(0xbf);
      c->code.append(0xc0 | (b->low << 3) | a->low);
      break;

    case 8:
    case 4:
      if (aSize == 4 and bSize == 8) {
        if (BytesPerWord == 8) {
          rex(c);
          c->code.append(0x63);
          c->code.append(0xc0 | (b->low << 3) | a->low);
        } else {
          if (a->low == rax and b->low == rax and b->high == rdx) {
            c->code.append(0x99); // cdq
          } else {
            assert(c, b->low == rax and b->high == rdx);

            moveRR(c, 4, a, 4, b);
            moveRR(c, 4, b, 8, b);
          }
        }
      } else {
        if (a->low != b->low) {
          rex(c);
          c->code.append(0x89);
          c->code.append(0xc0 | (a->low << 3) | b->low);
        }
      }
      break;
    }
  }  
}

void
moveMR(Context* c, unsigned aSize, Assembler::Memory* a,
       unsigned bSize, Assembler::Register* b)
{
  switch (aSize) {
  case 1:
    encode2(c, 0x0fbe, b->low, a, true);
    break;

  case 2:
    encode2(c, 0x0fbf, b->low, a, true);
    break;

  case 4:
  case 8:
    if (aSize == 4 and bSize == 8) {
      if (BytesPerWord == 8) {
        encode(c, 0x63, b->low, a, true);
      } else {
        assert(c, b->low == rax and b->high == rdx);
        
        moveMR(c, 4, a, 4, b);
        moveRR(c, 4, b, 8, b);
      }
    } else {
      if (BytesPerWord == 4 and aSize == 8) {
        Assembler::Memory ah(a->base, a->offset + 4, a->index, a->scale);
        Assembler::Register bh(b->high);

        moveMR(c, 4, a, 4, b);    
        moveMR(c, 4, &ah, 4, &bh);
      } else if (BytesPerWord == 8 and aSize == 4) {
        encode(c, 0x63, b->low, a, true);
      } else {
        encode(c, 0x8b, b->low, a, true);
      }
    }
    break;

  default: abort(c);
  }
}

void
moveRM(Context* c, unsigned aSize, Assembler::Register* a,
       unsigned bSize UNUSED, Assembler::Memory* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    Assembler::Register ah(a->high);
    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);

    moveRM(c, 4, a, 4, b);    
    moveRM(c, 4, &ah, 4, &bh);
  } else if (BytesPerWord == 8 and aSize == 4) {
    encode(c, 0x89, a->low, b, false);
  } else {
    switch (aSize) {
    case 1:
      if (BytesPerWord == 8) {
        if (a->low > rbx) {
          encode2(c, 0x4088, a->low, b, false);
        } else {
          encode(c, 0x88, a->low, b, false);
        }
      } else {
        assert(c, a->low <= rbx);

        encode(c, 0x88, a->low, b, false);
      }
      break;

    case 2:
      encode2(c, 0x6689, a->low, b, false);
      break;

    case BytesPerWord:
      encode(c, 0x89, a->low, b, true);
      break;

    default: abort(c);
    }
  }
}

void
moveMM(Context* c, unsigned aSize, Assembler::Memory* a,
       unsigned bSize, Assembler::Memory* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 8 or aSize <= 4) {
    uint32_t mask;
    if (BytesPerWord == 4 and aSize == 1) {
      mask = (1 << rax) | (1 << rcx) | (1 << rdx) | (1 << rbx);
    } else {
      mask = ~static_cast<uint32_t>(0);
    }

    Assembler::Register tmp(c->client->acquireTemporary(mask));
    moveMR(c, aSize, a, aSize, &tmp);
    moveRM(c, aSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  } else {
    Assembler::Register tmp(c->client->acquireTemporary(),
                            c->client->acquireTemporary());
    moveMR(c, aSize, a, aSize, &tmp);
    moveRM(c, aSize, &tmp, bSize, b);    
    c->client->releaseTemporary(tmp.low);
    c->client->releaseTemporary(tmp.high);
  }
}

void
moveAR(Context* c, unsigned aSize, Assembler::Address* a,
       unsigned bSize, Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or (aSize == 4 and bSize == 4));

  Assembler::Constant constant(a->address);
  Assembler::Memory memory(b->low, 0, -1, 0);

  moveCR(c, aSize, &constant, bSize, b);
  moveMR(c, bSize, &memory, bSize, b);
}

void
moveAM(Context* c, unsigned aSize, Assembler::Address* a,
       unsigned bSize, Assembler::Memory* b)
{
  assert(c, BytesPerWord == 8 or (aSize == 4 and bSize == 4));

  Assembler::Register tmp(c->client->acquireTemporary());
  moveAR(c, aSize, a, aSize, &tmp);
  moveRM(c, aSize, &tmp, bSize, b);
  c->client->releaseTemporary(tmp.low);
}

void
moveCR(Context* c, unsigned aSize, Assembler::Constant* a,
       unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    int64_t v = a->value->value();

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    Assembler::Register bh(b->high);

    moveCR(c, 4, &al, 4, b);
    moveCR(c, 4, &ah, 4, &bh);
  } else {
    rex(c, 0x48, b->low);
    c->code.append(0xb8 | b->low);
    if (a->value->resolved()) {
      c->code.appendAddress(a->value->value());
    } else {
      appendImmediateTask(c, a->value, offset(c));
      c->code.appendAddress(static_cast<uintptr_t>(0));
    }
  }
}

void
moveCM(Context* c, unsigned aSize UNUSED, Assembler::Constant* a,
       unsigned bSize, Assembler::Memory* b)
{
  int64_t v = a->value->value();

  switch (bSize) {
  case 1:
    encode(c, 0xc6, 0, b, false);
    c->code.append(a->value->value());
    break;

  case 2:
    encode2(c, 0x66c7, 0, b, false);
    c->code.append2(a->value->value());
    break;

  case 4:
    encode(c, 0xc7, 0, b, false);
    c->code.append4(a->value->value());
    break;

  case 8: {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);

    moveCM(c, 4, &al, 4, b);
    moveCM(c, 4, &ah, 4, &bh);
  } break;

  default: abort(c);
  }
}

void
moveZRR(Context* c, unsigned aSize, Assembler::Register* a,
        unsigned bSize UNUSED, Assembler::Register* b)
{
  switch (aSize) {
  case 2:
    rex(c);
    c->code.append(0x0f);
    c->code.append(0xb7);
    c->code.append(0xc0 | (b->low << 3) | a->low);
    break;

  default: abort(c);
  }
}

void
swapRR(Context* c, unsigned aSize UNUSED, Assembler::Register* a,
       unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);
  assert(c, aSize == BytesPerWord);
  
  rex(c);
  c->code.append(0x87);
  c->code.append(0xc0 | (b->low << 3) | a->low);
}

void
compareRR(Context* c, unsigned aSize, Assembler::Register* a,
          unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);
  assert(c, BytesPerWord == 8 or aSize == 4);

  if (aSize == 8) rex(c);
  c->code.append(0x39);
  c->code.append(0xc0 | (a->low << 3) | b->low);
}

void
compareCR(Context* c, unsigned aSize, Assembler::Constant* a,
          unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == bSize);
  assert(c, BytesPerWord == 8 or aSize == 4);
  
  int64_t v = a->value->value();

  if (isInt32(v)) {
    if (aSize == 8) rex(c);
    if (isInt8(v)) {
      c->code.append(0x83);
      c->code.append(0xf8 | b->low);
      c->code.append(v);
    } else {
      c->code.append(0x81);
      c->code.append(0xf8 | b->low);
      c->code.append4(v);
    }
  } else {
    Assembler::Register tmp(c->client->acquireTemporary());
    moveCR(c, aSize, a, aSize, &tmp);
    compareRR(c, aSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void
compareRM(Context* c, unsigned aSize, Assembler::Register* a,
          unsigned bSize UNUSED, Assembler::Memory* b)
{
  assert(c, aSize == bSize);
  assert(c, BytesPerWord == 8 or aSize == 4);
  
  if (BytesPerWord == 8 and aSize == 4) {
    moveRR(c, 4, a, 8, a);
  }
  encode(c, 0x39, a->low, b, true);
}

void
compareCM(Context* c, unsigned aSize UNUSED, Assembler::Constant* a,
          unsigned bSize UNUSED, Assembler::Memory* b)
{
  assert(c, aSize == bSize);
  assert(c, BytesPerWord == 8 or aSize == 4);
  
  int64_t v = a->value->value();

  encode(c, isInt8(v) ? 0x83 : 0x81, 7, b, true);

  if (isInt8(v)) {
    c->code.append(v);
  } else if (isInt32(v)) {
    c->code.append4(v);
  } else {
    abort(c);
  }
}

void
addCarryRR(Context* c, unsigned size, Assembler::Register* a,
           Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);
  
  if (size == 8) rex(c);
  c->code.append(0x11);
  c->code.append(0xc0 | (a->low << 3) | b->low);
}

void
addRR(Context* c, unsigned aSize, Assembler::Register* a,
      unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    addRR(c, 4, a, 4, b);
    addCarryRR(c, 4, &ah, &bh);
  } else {
    if (aSize == 8) rex(c);
    c->code.append(0x01);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
addCarryCR(Context* c, unsigned size UNUSED, Assembler::Constant* a,
           Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);
  
  int64_t v = a->value->value();
  if (isInt8(v)) {
    c->code.append(0x83);
    c->code.append(0xd0 | b->low);
    c->code.append(v);
  } else {
    abort(c);
  }
}

void
addCR(Context* c, unsigned aSize, Assembler::Constant* a,
      unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (BytesPerWord == 4 and aSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      Assembler::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      Assembler::Constant al(&low);

      Assembler::Register bh(b->high);

      addCR(c, 4, &al, 4, b);
      addCarryCR(c, 4, &ah, &bh);
    } else {
      if (aSize == 8) rex(c);
      if (isInt8(v)) {
        c->code.append(0x83);
        c->code.append(0xc0 | b->low);
        c->code.append(v);
      } else if (isInt32(v)) {
        c->code.append(0x81);
        c->code.append(0xc0 | b->low);
        c->code.append4(v);        
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, aSize, a, aSize, &tmp);
        addRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void
subtractBorrowCR(Context* c, unsigned size UNUSED, Assembler::Constant* a,
                 Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);
  
  int64_t v = a->value->value();
  if (isInt8(v)) {
    c->code.append(0x83);
    c->code.append(0xd8 | b->low);
    c->code.append(v);
  } else {
    abort(c);
  }
}

void
subtractRR(Context* c, unsigned aSize, Assembler::Register* a,
           unsigned bSize, Assembler::Register* b);

void
subtractCR(Context* c, unsigned aSize, Assembler::Constant* a,
           unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (BytesPerWord == 4 and aSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      Assembler::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      Assembler::Constant al(&low);

      Assembler::Register bh(b->high);

      subtractCR(c, 4, &al, 4, b);
      subtractBorrowCR(c, 4, &ah, &bh);
    } else {
      if (aSize == 8) rex(c);
      if (isInt8(v)) {
        c->code.append(0x83);
        c->code.append(0xe8 | b->low);
        c->code.append(v);
      } else if (isInt32(v)) {
        c->code.append(0x81);
        c->code.append(0xe8 | b->low);
        c->code.append4(v);        
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, aSize, a, aSize, &tmp);
        subtractRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void
subtractBorrowRR(Context* c, unsigned size, Assembler::Register* a,
                 Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);
  
  if (size == 8) rex(c);
  c->code.append(0x19);
  c->code.append(0xc0 | (a->low << 3) | b->low);
}

void
subtractRR(Context* c, unsigned aSize, Assembler::Register* a,
           unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    subtractRR(c, 4, a, 4, b);
    subtractBorrowRR(c, 4, &ah, &bh);
  } else {
    if (aSize == 8) rex(c);
    c->code.append(0x29);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
andRR(Context* c, unsigned aSize, Assembler::Register* a,
      unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    andRR(c, 4, a, 4, b);
    andRR(c, 4, &ah, 4, &bh);
  } else {
    if (aSize == 8) rex(c);
    c->code.append(0x21);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
andCR(Context* c, unsigned aSize, Assembler::Constant* a,
      unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  int64_t v = a->value->value();

  if (BytesPerWord == 4 and aSize == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    Assembler::Register bh(b->high);

    andCR(c, 4, &al, 4, b);
    andCR(c, 4, &ah, 4, &bh);
  } else {
    if (isInt32(v)) {
      if (aSize == 8) rex(c);
      if (isInt8(v)) {
        c->code.append(0x83);
        c->code.append(0xe0 | b->low);
        c->code.append(v);
      } else {
        c->code.append(0x81);
        c->code.append(0xe0 | b->low);
        c->code.append4(v);
      }
    } else {
      Assembler::Register tmp(c->client->acquireTemporary());
      moveCR(c, aSize, a, aSize, &tmp);
      andRR(c, aSize, &tmp, bSize, b);
      c->client->releaseTemporary(tmp.low);
    }
  }
}

void
multiplyRR(Context* c, unsigned aSize, Assembler::Register* a,
           unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == bSize);

  if (BytesPerWord == 4 and aSize == 8) {
    assert(c, b->high == rdx);
    assert(c, b->low != rax);
    assert(c, a->low != rax);
    assert(c, a->high != rax);

    c->client->save(rax);

    Assembler::Register axdx(rax, rdx);
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    moveRR(c, 4, b, 4, &axdx);
    multiplyRR(c, 4, &ah, 4, b);
    multiplyRR(c, 4, a, 4, &bh);
    addRR(c, 4, &bh, 4, b);
    
    // mul a->low,%eax%edx
    c->code.append(0xf7);
    c->code.append(0xe0 | a->low);
    
    addRR(c, 4, b, 4, &bh);
    moveRR(c, 4, &axdx, 4, b);

    c->client->restore(rax);
  } else {
    if (aSize == 8) rex(c);
    c->code.append(0x0f);
    c->code.append(0xaf);
    c->code.append(0xc0 | (b->low << 3) | a->low);
  }
}

void
populateTables(ArchitectureContext* c)
{
#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)

  const OperandType C = ConstantOperand;
  const OperandType A = AddressOperand;
  const OperandType R = RegisterOperand;
  const OperandType M = MemoryOperand;

  OperationType* zo = c->operations;
  UnaryOperationType* uo = c->unaryOperations;
  BinaryOperationType* bo = c->binaryOperations;

  zo[Return] = return_;

  uo[index(Call, C)] = CAST1(callC);
  uo[index(Call, R)] = CAST1(callR);

  uo[index(AlignedCall, C)] = CAST1(alignedCallC);

  uo[index(LongCall, C)] = CAST1(longCallC);

  uo[index(Jump, R)] = CAST1(jumpR);
  uo[index(Jump, C)] = CAST1(jumpC);
  uo[index(Jump, M)] = CAST1(jumpM);

  uo[index(JumpIfEqual, C)] = CAST1(jumpIfEqualC);
  uo[index(JumpIfNotEqual, C)] = CAST1(jumpIfNotEqualC);
  uo[index(JumpIfGreater, C)] = CAST1(jumpIfGreaterC);
  uo[index(JumpIfGreaterOrEqual, C)] = CAST1(jumpIfGreaterOrEqualC);
  uo[index(JumpIfLess, C)] = CAST1(jumpIfLessC);
  uo[index(JumpIfLessOrEqual, C)] = CAST1(jumpIfLessOrEqualC);

  uo[index(LongJump, C)] = CAST1(longJumpC);

  bo[index(Move, R, R)] = CAST2(moveRR);
  bo[index(Move, C, R)] = CAST2(moveCR);
  bo[index(Move, M, R)] = CAST2(moveMR);
  bo[index(Move, R, M)] = CAST2(moveRM);
  bo[index(Move, C, M)] = CAST2(moveCM);
  bo[index(Move, A, M)] = CAST2(moveAM);
  bo[index(Move, A, R)] = CAST2(moveAR);
  bo[index(Move, M, M)] = CAST2(moveMM);

  bo[index(MoveZ, R, R)] = CAST2(moveZRR);

  bo[index(Swap, R, R)] = CAST2(swapRR);

  bo[index(Compare, R, R)] = CAST2(compareRR);
  bo[index(Compare, C, R)] = CAST2(compareCR);
  bo[index(Compare, C, M)] = CAST2(compareCM);
  bo[index(Compare, R, M)] = CAST2(compareRM);

  bo[index(Add, R, R)] = CAST2(addRR);
  bo[index(Add, C, R)] = CAST2(addCR);

  bo[index(Subtract, C, R)] = CAST2(subtractCR);

  bo[index(And, C, R)] = CAST2(andCR);

  bo[index(Multiply, R, R)] = CAST2(multiplyRR);
}

class MyArchitecture: public Assembler::Architecture {
 public:
  MyArchitecture(System* system): c(system), referenceCount(0) {
    populateTables(&c);
  }

  virtual unsigned registerCount() {
    return 8;//BytesPerWord == 4 ? 8 : 16;
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

  virtual bool condensedAddressing() {
    return true;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case rbp:
    case rsp:
    case rbx:
      return true;

    default:
      return false;
    }
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

  virtual void updateCall(void* returnAddress, void* newTarget) {
    uint8_t* instruction = static_cast<uint8_t*>(returnAddress) - 5;
    assert(&c, *instruction == 0xE8);
    assert(&c, reinterpret_cast<uintptr_t>(instruction + 1) % 4 == 0);

    int32_t v = static_cast<uint8_t*>(newTarget)
      - static_cast<uint8_t*>(returnAddress);
    memcpy(instruction + 1, &v, 4);    
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    const unsigned alignment = 16 / BytesPerWord;
    return (ceiling(sizeInWords + FrameHeaderSize, alignment) * alignment)
      - FrameHeaderSize;
  }

  virtual void* frameIp(void* stack) {
    return *static_cast<void**>(stack);
  }

  virtual unsigned frameHeaderSize() {
    return FrameHeaderSize;
  }

  virtual unsigned frameFooterSize() {
    return 0;
  }

  virtual void nextFrame(void** stack, void** base) {
    *stack = static_cast<void**>(*base) + 1;
    *base = *static_cast<void**>(*base);
  }

  virtual void* popReturnAddress(void* stack) {
    return static_cast<void**>(stack) + 1;
  }

  virtual void plan
  (UnaryOperation,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   bool* thunk)
  {
    *aTypeMask = (1 << RegisterOperand) | (1 << MemoryOperand);
    *aRegisterMask = ~static_cast<uint64_t>(0);
    *thunk = false;
  }

  virtual void plan
  (BinaryOperation op,
   unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask,
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

    case Move:
      if (BytesPerWord == 4) {
        if (aSize == 4 and bSize == 8) {
          const uint32_t mask = ~((1 << rax) | (1 << rdx));
          *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
          *bRegisterMask = (static_cast<uint64_t>(1) << (rdx + 32))
            | (static_cast<uint64_t>(1) << rax);        
        } else if (aSize == 1) {
          const uint32_t mask
            = (1 << rax) | (1 << rcx) | (1 << rdx) | (1 << rbx);
          *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
          *bRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;        
        }
      }
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
    case Multiply:
      if (BytesPerWord == 4 and aSize == 8) { 
        const uint32_t mask = ~((1 << rax) | (1 << rdx));
        *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
        *bRegisterMask = (static_cast<uint64_t>(1) << (rdx + 32)) | mask;
      }
      break;

    case Divide:
      if (BytesPerWord == 4 and aSize == 8) {
        *bTypeMask = ~0;
        *thunk = true;        
      } else {
        *aRegisterMask = ~((1 << rax) | (1 << rdx));
        *bRegisterMask = 1 << rax;      
      }
      break;

    case Remainder:
      if (BytesPerWord == 4 and aSize == 8) {
        *bTypeMask = ~0;
        *thunk = true;
      } else {
        *aRegisterMask = ~((1 << rax) | (1 << rdx));
        *bRegisterMask = 1 << rax;      
      }
      break;

    case ShiftLeft:
    case ShiftRight:
    case UnsignedShiftRight: {
      *aRegisterMask = (~static_cast<uint64_t>(0) << 32)
        | (static_cast<uint64_t>(1) << rcx);
      const uint32_t mask = ~(1 << rcx);
      *bRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
    } break;

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

  virtual void saveFrame(unsigned stackOffset, unsigned baseOffset) {
    Register stack(rsp);
    Memory stackDst(rbx, stackOffset);
    apply(Move, BytesPerWord, RegisterOperand, &stack,
          BytesPerWord, MemoryOperand, &stackDst);

    Register base(rbp);
    Memory baseDst(rbx, baseOffset);
    apply(Move, BytesPerWord, RegisterOperand, &base,
          BytesPerWord, MemoryOperand, &baseDst);
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
      } else {
        Memory dst(rsp, offset * BytesPerWord);
        apply(Move,
              arguments[i].size, arguments[i].type, arguments[i].operand,
              pad(arguments[i].size), MemoryOperand, &dst);
        offset += ceiling(arguments[i].size, BytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint) {
    Register base(rbp);
    pushR(&c, BytesPerWord, &base);

    Register stack(rsp);
    apply(Move, BytesPerWord, RegisterOperand, &stack,
          BytesPerWord, RegisterOperand, &base);

    Constant footprintConstant(resolved(&c, footprint * BytesPerWord));
    apply(Subtract, BytesPerWord, ConstantOperand, &footprintConstant,
          BytesPerWord, RegisterOperand, &stack,
          BytesPerWord, RegisterOperand, &stack);
  }

  virtual void popFrame() {
    Register base(rbp);
    Register stack(rsp);
    apply(Move, BytesPerWord, RegisterOperand, &base,
          BytesPerWord, RegisterOperand, &stack);

    popR(&c, BytesPerWord, &base);
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
                     unsigned aSize, OperandType aType, Operand* aOperand,
                     unsigned bSize, OperandType bType, Operand* bOperand,
                     unsigned cSize, OperandType cType, Operand* cOperand)
  {
    assert(&c, bSize == cSize);
    assert(&c, bType == cType);
    assert(&c, bOperand == cOperand);

    arch_->c.binaryOperations[index(op, aType, bType)]
      (&c, aSize, aOperand, bSize, bOperand);
  }

  virtual void writeTo(uint8_t* dst) {
    c.result = dst;
    
    for (MyBlock* b = c.firstBlock; b; b = b->next) {
      unsigned index = 0;
      unsigned padding = 0;
      for (AlignmentPadding* p = b->firstPadding; p; p = p->next) {
        unsigned size = p->offset - b->offset;
        memcpy(dst + b->start + index + padding,
               c.code.data + b->offset + index,
               size);
        index += size;
        while ((b->start + index + padding + 1) % 4) {
          *(dst + b->start + index + padding) = 0x90;
          ++ padding;
        }
      }

      memcpy(dst + b->start + index + padding,
             c.code.data + b->offset + index,
             b->size - index);
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
