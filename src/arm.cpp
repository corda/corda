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

namespace isa {
// INSTRUCTION OPTIONS
enum CONDITION { EQ, NE, CS, CC, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL, NV };
enum SHIFTOP { LSL, LSR, ASR, ROR };
// INSTRUCTION FORMATS
inline int DATA(int cond, int opcode, int S, int Rn, int Rd, int shift, int Sh, int Rm)
{ return cond<<28 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | shift<<7 | Sh<<5 | Rm; }
inline int DATAS(int cond, int opcode, int S, int Rn, int Rd, int Rs, int Sh, int Rm)
{ return cond<<28 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | Rs<<8 | Sh<<5 | 1<<4 | Rm; }
inline int DATAI(int cond, int opcode, int S, int Rn, int Rd, int rot, int imm)
{ return cond<<28 | 1<<25 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | rot<<8 | imm; }
inline int BRANCH(int cond, int L, int offset)
{ return cond<<28 | 5<<25 | L<<24 | offset; }
inline int BRANCHX(int cond, int L, int Rm)
{ return cond<<28 | 0x4bffc<<6 | L<<5 | 1<<4 | Rm; }
inline int MULTIPLY(int cond, int mul, int S, int Rd, int Rn, int Rs, int Rm)
{ return cond<<28 | mul<<21 | S<<20 | Rd<<16 | Rn<<12 | Rs<<8 | 9<<4 | Rm; }
inline int XFER(int cond, int P, int U, int B, int W, int L, int Rn, int Rd, int shift, int Sh, int Rm)
{ return cond<<28 | 3<<25 | P<<24 | U<<23 | B<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | shift<<7 | Sh<<5 | Rm; }
inline int XFERI(int cond, int P, int U, int B, int W, int L, int Rn, int Rd, int offset)
{ return cond<<28 | 2<<25 | P<<24 | U<<23 | B<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | offset; }
inline int XFER2(int cond, int P, int U, int W, int L, int Rn, int Rd, int S, int H, int Rm)
{ return cond<<28 | P<<24 | U<<23 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | 1<<7 | S<<6 | H<<5 | 1<<4 | Rm; }
inline int XFER2I(int cond, int P, int U, int W, int L, int Rn, int Rd, int offsetH, int S, int H, int offsetL)
{ return cond<<28 | P<<24 | U<<23 | 1<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | offsetH<<8 | 1<<7 | S<<6 | H<<5 | 1<<4 | offsetL; }
inline int BLOCKXFER(int cond, int P, int U, int S, int W, int L, int Rn, int rlist)
{ return cond<<28 | 4<<25 | P<<24 | U<<23 | S<<22 | W<<21 | L<<20 | Rn<<16 | rlist; }
inline int SWI(int cond, int imm)
{ return cond<<28 | 0x0f<<24 | imm; }
inline int SWAP(int cond, int B, int Rn, int Rd, int Rm)
{ return cond<<28 | 1<<24 | B<<22 | Rn<<16 | Rd<<12 | 9<<4 | Rm; }
// INSTRUCTIONS
// The "cond" and "S" fields are set using the SETCOND() and SETS() functions
inline int b(int offset) { return BRANCH(AL, 0, offset); }
inline int bl(int offset) { return BRANCH(AL, 1, offset); }
inline int bx(int Rm) { return BRANCHX(AL, 0, Rm); }
inline int blx(int Rm) { return BRANCHX(AL, 1, Rm); }
inline int swi(int imm) { return SWI(AL, imm); }
inline int and_(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x0, 0, Rn, Rd, shift, Sh, Rm); }
inline int eor(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x1, 0, Rn, Rd, shift, Sh, Rm); }
inline int sub(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x2, 0, Rn, Rd, shift, Sh, Rm); }
inline int rsb(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x3, 0, Rn, Rd, shift, Sh, Rm); }
inline int add(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x4, 0, Rn, Rd, shift, Sh, Rm); }
inline int adc(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x5, 0, Rn, Rd, shift, Sh, Rm); }
inline int sbc(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x6, 0, Rn, Rd, shift, Sh, Rm); }
inline int rsc(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x7, 0, Rn, Rd, shift, Sh, Rm); }
inline int tst(int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x8, 0, Rn, 0, shift, Sh, Rm); }
inline int teq(int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x9, 0, Rn, 0, shift, Sh, Rm); }
inline int cmp(int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xa, 0, Rn, 0, shift, Sh, Rm); }
inline int cmn(int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xb, 0, Rn, 0, shift, Sh, Rm); }
inline int orr(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xc, 0, Rn, Rd, shift, Sh, Rm); }
inline int mov(int Rd, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xd, 0, 0, Rd, shift, Sh, Rm); }
inline int bic(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xe, 0, Rn, Rd, shift, Sh, Rm); }
inline int mvn(int Rd, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xf, 0, 0, Rd, shift, Sh, Rm); }
inline int andi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x0, 0, Rn, Rd, rot, imm); }
inline int eori(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x1, 0, Rn, Rd, rot, imm); }
inline int subi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x2, 0, Rn, Rd, rot, imm); }
inline int rsbi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x3, 0, Rn, Rd, rot, imm); }
inline int addi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x4, 0, Rn, Rd, rot, imm); }
inline int adci(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x5, 0, Rn, Rd, rot, imm); }
inline int cmpi(int Rn, int imm, int rot=0) { return DATAI(AL, 0x0, 0, Rn, 0, rot, imm); }
inline int orri(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0xc, 0, Rn, Rd, rot, imm); }
inline int movi(int Rd, int imm, int rot=0) { return DATAI(AL, 0xd, 0, 0, Rd, rot, imm); }
inline int movsh(int Rd, int Rm, int Rs, int Sh) { return DATAS(AL, 0xd, 0, 0, Rd, Rs, Sh, Rm); }
inline int mul(int Rd, int Rm, int Rs) { return MULTIPLY(AL, 0, 0, Rd, 0, Rs, Rm); }
inline int mla(int Rd, int Rm, int Rs, int Rn) { return MULTIPLY(AL, 1, 0, Rd, Rn, Rs, Rm); }
inline int umull(int RdLo, int RdHi, int Rm, int Rs) { return MULTIPLY(AL, 4, 0, RdLo, RdHi, Rs, Rm); }
inline int umlal(int RdLo, int RdHi, int Rm, int Rs) { return MULTIPLY(AL, 5, 0, RdLo, RdHi, Rs, Rm); }
inline int smull(int RdLo, int RdHi, int Rm, int Rs) { return MULTIPLY(AL, 6, 0, RdLo, RdHi, Rs, Rm); }
inline int smlal(int RdLo, int RdHi, int Rm, int Rs) { return MULTIPLY(AL, 7, 0, RdLo, RdHi, Rs, Rm); }
inline int ldr(int Rd, int Rn, int Rm) { return XFER(AL, 1, 1, 0, 0, 1, Rn, Rd, 0, 0, Rm); }
inline int ldri(int Rd, int Rn, int imm) { return XFERI(AL, 1, 1, 0, 0, 1, Rn, Rd, imm); }
inline int ldrb(int Rd, int Rn, int Rm) { return XFER(AL, 1, 1, 1, 0, 1, Rn, Rd, 0, 0, Rm); }
inline int ldrbi(int Rd, int Rn, int imm) { return XFERI(AL, 1, 1, 1, 0, 1, Rn, Rd, imm); }
inline int str(int Rd, int Rn, int Rm, int W=0) { return XFER(AL, 1, 1, 0, W, 0, Rn, Rd, 0, 0, Rm); }
inline int stri(int Rd, int Rn, int imm, int W=0) { return XFERI(AL, 1, 1, 0, W, 0, Rn, Rd, imm); }
inline int strb(int Rd, int Rn, int Rm) { return XFER(AL, 1, 1, 1, 0, 0, Rn, Rd, 0, 0, Rm); }
inline int strbi(int Rd, int Rn, int imm) { return XFERI(AL, 1, 1, 1, 0, 0, Rn, Rd, imm); }
inline int ldrh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 0, 1, Rm); }
inline int ldrhi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, 1, 0, 1, Rn, Rd, imm>>4 & 0xf, 0, 1, imm&0xf); }
inline int strh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 0, Rn, Rd, 0, 1, Rm); }
inline int strhi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, 1, 0, 0, Rn, Rd, imm>>4 & 0xf, 0, 1, imm&0xf); }
inline int ldrsh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 1, 1, Rm); }
inline int ldrshi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, 1, 0, 1, Rn, Rd, imm>>4 & 0xf, 1, 1, imm&0xf); }
inline int ldrsb(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 1, 0, Rm); }
inline int ldrsbi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, 1, 0, 1, Rn, Rd, imm>>4 & 0xf, 1, 0, imm&0xf); }
inline int ldmib(int Rn, int rlist) { return BLOCKXFER(AL, 1, 1, 0, 0, 1, Rn, rlist); }
inline int ldmia(int Rn, int rlist) { return BLOCKXFER(AL, 0, 1, 0, 0, 1, Rn, rlist); }
inline int stmib(int Rn, int rlist) { return BLOCKXFER(AL, 1, 1, 0, 0, 0, Rn, rlist); }
inline int stmdb(int Rn, int rlist) { return BLOCKXFER(AL, 1, 0, 0, 0, 0, Rn, rlist); }
inline int swp(int Rd, int Rm, int Rn) { return SWAP(AL, 0, Rn, Rd, Rm); }
inline int swpb(int Rd, int Rm, int Rn) { return SWAP(AL, 1, Rn, Rd, Rm); }
inline int SETCOND(int ins, int cond) { return ins&0x0fffffff | cond<<28; }
inline int SETS(int ins) { return ins | 1<<20; }
// PSEUDO-INSTRUCTIONS
inline int nop() { return mov(0, 0); }
inline int lsl(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, LSL); }
inline int lsli(int Rd, int Rm, int imm) { return mov(Rd, Rm, LSL, imm); }
inline int lsr(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, LSR); }
inline int lsri(int Rd, int Rm, int imm) { return mov(Rd, Rm, LSR, imm); }
inline int asr(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, ASR); }
inline int asri(int Rd, int Rm, int imm) { return mov(Rd, Rm, ASR, imm); }
inline int ror(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, ROR); }
}

const uint64_t MASK_LO32 = 0xffffffff;
const unsigned MASK_LO16 = 0xffff;
const unsigned MASK_LO8  = 0xff;
inline unsigned lo32(int64_t i) { return (unsigned)(i&MASK_LO32); }
inline unsigned hi32(int64_t i) { return (unsigned)(i>>32); }
inline unsigned lo16(int64_t i) { return (unsigned)(i&MASK_LO16); }
inline unsigned hi16(int64_t i) { return lo16(i>>16); }
inline unsigned lo8(int64_t i) { return (unsigned)(i&MASK_LO8); }
inline unsigned hi8(int64_t i) { return lo8(i>>8); }

inline bool isInt8(intptr_t v) { return v == static_cast<int8_t>(v); }
inline bool isInt16(intptr_t v) { return v == static_cast<int16_t>(v); }
inline bool isInt24(intptr_t v) { return v == v & 0xffffff; }
inline bool isInt32(intptr_t v) { return v == static_cast<int32_t>(v); }
inline int carry16(intptr_t v) { return static_cast<int16_t>(v) < 0 ? 1 : 0; }

const unsigned FrameFooterSize = 0;
const unsigned StackAlignmentInBytes = 8;
const unsigned StackAlignmentInWords = StackAlignmentInBytes / BytesPerWord;

const int StackRegister = 13;
const int ThreadRegister = 12;

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
updateOffset(System* s, uint8_t* instruction, bool conditional UNUSED, int64_t value)
{
  int32_t v = reinterpret_cast<uint8_t*>(value) - instruction;
   
  int32_t mask;
  expect(s, bounded(0, 8, v));
  mask = 0xFFFFFF;

  int32_t* p = reinterpret_cast<int32_t*>(instruction);
  *p = (v & mask) | ((~mask) & *p);

  return instruction + 4;
}

class OffsetListener: public Promise::Listener {
 public:
  OffsetListener(System* s, uint8_t* instruction, bool conditional):
    s(s),
    instruction(instruction),
    conditional(conditional)
  { }

  virtual bool resolve(int64_t value, void** location) {
    void* p = updateOffset(s, instruction, conditional, value);
    if (location) *location = p;
    return false;
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


// BEGIN OPERATION COMPILERS

using namespace isa;

// shortcut functions
inline void emit(Context* con, int code) { con->code.append4(code); }
inline int newTemp(Context* con) { return con->client->acquireTemporary(); }
inline void freeTemp(Context* con, int r) { con->client->releaseTemporary(r); }
inline int64_t getValue(Assembler::Constant c) { return c->value->value(); }


void shiftLeftR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t)
{
  if (size == 8) {
    int tmpHi = newTemp(con), tmpLo = newTemp(con);
    emit(con, SETS(rsbi(tmpHi, a->low, 32)));
    emit(con, lsl(t->high, b->high, a->low));
    emit(con, lsr(tmpLo, b->low, tmpHi));
    emit(con, orr(t->high, t->high, tmpLo));
    emit(con, addi(tmpHi, a->low, -32));
    emit(con, lsl(tmpLo, b->low, tmpHi));
    emit(con, orr(t->high, t->high, tmpLo));
    freeTemp(con, tmpHi); freeTemp(con, tmpLo);
  }
  emit(con, lsl(t->low, b->low, a->low));
}

void shiftLeftC(Context* con, unsigned size, Assembler::Constant a, Assembler::Register b, Assembler::Register t)
{
  assert(con, size == BytesPerWord);
  emit(con, lsli(t->low, b->low, getValue(a)));
}

void shiftRightR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t)
{
  if (size == 8) {
    int tmpHi = newTemp(con), tmpLo = newTemp(con);
    emit(con, SETS(rsbi(tmpHi, a->low, 32)));
    emit(con, lsr(t->low, b->low, a->low));
    emit(con, lsl(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, SETS(addi(tmpHi, a->low, -32)));
    emit(con, asr(tmpLo, b->high, tmpHi));
    emit(con, SETCOND(b(8), LE));
    emit(con, orri(t->low, tmpLo, 0));
    emit(con, asr(t->high, b->high, a->low));
    freeTemp(con, tmpHi); freeTemp(con, tmpLo);
  } else {
    emit(con, asr(t->low, b->low, a->low));
  }
}

void shiftRightC(Context* con, unsigned size, Assembler::Constant a, Assembler::Register b, Assembler::Register t)
{
  assert(con, size == BytesPerWord);
  emit(con, asri(t->low, b->low, getValue(a)));
}

void unsignedShiftRightR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t)
{
  emit(con, lsr(t->low, b->low, a->low));
  if (size == 8) {
    int tmpHi = newTemp(con), tmpLo = newTemp(con);
    emit(con, SETS(rsbi(tmpHi, a->low, 32)));
    emit(con, lsl(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, addi(tmpHi, a->low, -32));
    emit(con, lsr(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, lsr(t->high, b->high, a->low));
    freeTemp(con, tmpHi); freeTemp(con, tmpLo);
  }
}

void unsignedShiftRightC(Context* con, unsigned size, Assembler::Constant a, Assembler::Register b, Assembler::Register t)
{
  assert(con, size == BytesPerWord);
  emit(con, lsri(t->low, b->low, getValue(a)));
}

void
updateImmediate(System* s, void* dst, int64_t src, unsigned size)
{
  switch (size) {
  case 4: {
    int32_t* p = static_cast<int32_t*>(dst);
    int r = (p[0] >> 12) & 15;

    p[0] = movi(r, lo8(src));
    p[1] = orri(r, r, hi8(src), 12);
    p[2] = orri(r, r, lo8(hi16(src)), 8);
    p[3] = orri(r, r, hi8(hi16(src)), 4);
  } break;

  default: abort(s);
  }
}

class ImmediateListener: public Promise::Listener {
 public:
  ImmediateListener(System* s, void* dst, unsigned size, unsigned offset):
    s(s), dst(dst), size(size), offset(offset)
  { }

  virtual bool resolve(int64_t value, void** location) {
    updateImmediate(s, dst, value, size);
    if (location) *location = static_cast<uint8_t*>(dst) + offset;
    return false;
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

void
jumpR(Context* c, unsigned size UNUSED, Assembler::Register* target)
{
  assert(c, size == BytesPerWord);
  emit(c, bx(target->low));
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
  c->client->releaseTemporary(tmp.low);
}

void
moveRR(Context* c, unsigned srcSize, Assembler::Register* src,
       unsigned dstSize, Assembler::Register* dst)
{
  switch (srcSize) {
  case 1:
    emit(c, lsli(dst->low, src->low, 24));
    emit(c, asri(dst->low, dst->low, 24));
    break;
    
  case 2:
    emit(c, lsli(dst->low, src->low, 16));
    emit(c, asri(dst->low, dst->low, 16));
    break;
    
  case 4:
  case 8:
    if (srcSize == 4 and dstSize == 8) {
      moveRR(c, 4, src, 4, dst);
      emit(c, asri(dst->high, src->low, 31));
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
      emit(c, mov(dst->low, src->low));
    }
    break;

  default: abort(c);
  }
}

void
moveZRR(Context* c, unsigned srcSize, Assembler::Register* src,
        unsigned, Assembler::Register* dst)
{
  switch (srcSize) {
  case 2:
    emit(c, lsli(dst->low, src->low, 16));
    emit(c, lsri(dst->low, src->low, 16));
    break;

  default: abort(c);
  }
}

void
moveCR2(Context* c, unsigned, Assembler::Constant* src,
       unsigned dstSize, Assembler::Register* dst, unsigned promiseOffset)
{
  if (dstSize <= 4) {
    if (src->value->resolved()) {
      int32_t i = getValue(c);
      emit(c, movi(dst->low, lo8(i)));
      if (!isInt8(i)) {
        emit(c, orri(dst->low, dst->low, hi8(i), 12));
        if (!isInt16(i)) {
          emit(c, orri(dst->low, dst->low, lo8(hi16(i)), 8));
          if (!isInt24(i)) {
            emit(c, orri(dst->low, dst->low, hi8(hi16(i)), 4));
          }
        }
      }
    } else {
      appendImmediateTask
        (c, src->value, offset(c), BytesPerWord, promiseOffset);
      emit(c, movi(dst->low, 0));
      emit(c, orri(dst->low, dst->low, 0, 12));
      emit(c, orri(dst->low, dst->low, 0, 8));
      emit(c, orri(dst->low, dst->low, 0, 4));
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

void addR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t) {
  if (size == 8) {
    emit(con, SETS(addc(t->low, a->low, b->low)));
    emit(con, adc(t->high, a->high, b->high));
  } else {
    emit(con, add(t->low, a->low, b->low));
  }
}

void addC(Context* con, unsigned size, Assembler::Constant a, Assembler::Register b, Assembler::Register t) {
  assert(con, size == BytesPerWord);

  int32_t i = getValue(a);
  if (i) {
    emit(con, addi(t->low, b->low, lo8(i)));
    if (!isInt8(i)) {
      emit(con, addi(t->low, b->low, hi8(i), 12));
      if (!isInt16(i)) {
        emit(con, addi(t->low, b->low, lo8(hi16(i)), 8));
        if (!isInt24(i)) {
          emit(con, addi(t->low, b->low, hi8(hi16(i)), 4));
        }
      }
    }
  } else {
    moveRR(con, size, b, size, t);
  }
}

void subR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t) {
  if (size == 8) {
    emit(con, SETS(rsb(t->low, a->low, b->low)));
    emit(con, rsc(t->high, a->high, b->high));
  } else {
    emit(con, rsb(t->low, a->low, b->low));
  }
}

void subC(Context* c, unsigned size, Assembler::Constant a, Assembler::Register b, Assembler::Register t) {
  assert(c, size == BytesPerWord);

  ResolvedPromise promise(- a->value->value());
  Assembler::Constant constant(&promise);
  addC(c, size, &constant, b, t);
}

void multiplyR(Context* con, unsigned size, Assembler::Register a, Assembler::Register b, Assembler::Register t) {
  if (size == 8) {
    emit(con, mul(t->high, a->low, b->high));
    emit(con, mla(t->high, a->high, b->low, t->high));
    emit(con, smlal(t->low, t->high, a->low, b->low));
  } else {
    emit(con, mul(t->low, a->low, b->low));
  }
}

int
normalize(Context* c, int offset, int index, unsigned scale, 
          bool* preserveIndex, bool* release)
{
  if (offset != 0 or scale != 1) {
    Assembler::Register normalizedIndex
      (*preserveIndex ? c->client->acquireTemporary() : index);
    
    if (*preserveIndex) {
      *release = true;
      *preserveIndex = false;
    } else {
      *release = false;
    }

    int scaled;

    if (scale != 1) {
      Assembler::Register unscaledIndex(index);

      ResolvedPromise scalePromise(log(scale));
      Assembler::Constant scaleConstant(&scalePromise);
      
      shiftLeftC(c, BytesPerWord, &scaleConstant,
                 &unscaledIndex, &normalizedIndex);

      scaled = normalizedIndex.low;
    } else {
      scaled = index;
    }

    if (offset != 0) {
      Assembler::Register untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      Assembler::Constant offsetConstant(&offsetPromise);

      addC(c, BytesPerWord, &offsetConstant,
           &untranslatedIndex, &normalizedIndex);
    }

    return normalizedIndex.low;
  } else {
    *release = false;
    return index;
  }
}

void
store(Context* c, unsigned size, Assembler::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex)
{
  if (index != NoRegister) {
    bool release;
    int normalized = normalize
      (c, offset, index, scale, &preserveIndex, &release);

    switch (size) {
    case 1:
      emit(c, strb(src->low, base, normalized));
      break;

    case 2:
      emit(c, strh(src->low, base, normalized));
      break;

    case 4:
      emit(c, str(src->low, base, normalized));
      break;

    case 8: {
      Assembler::Register srcHigh(src->high);
      store(c, 4, &srcHigh, base, 0, normalized, 1, preserveIndex);
      store(c, 4, src, base, 4, normalized, 1, preserveIndex);
    } break;

    default: abort(c);
    }

    if (release) c->client->releaseTemporary(normalized);
  } else {
    switch (size) {
    case 1:
      emit(c, strbi(src->low, base, offset));
      break;

    case 2:
      emit(c, strhi(src->low, base, offset));
      break;

    case 4:
      emit(c, stri(src->low, base, offset));
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
moveAndUpdateRM(Context* c, unsigned srcSize UNUSED, Assembler::Register* src,
                unsigned dstSize UNUSED, Assembler::Memory* dst)
{
  assert(c, srcSize == BytesPerWord);
  assert(c, dstSize == BytesPerWord);

  if (dst->index == NoRegister) {
    emit(c, stri(src->low, dst->base, dst->offset, 1));
  } else {
    assert(c, dst->offset == 0);
    assert(c, dst->scale == 1);
    
    emit(c, str(src->low, dst->base, dst->index, 1));
  }
}

void
load(Context* c, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, Assembler::Register* dst,
     bool preserveIndex, bool signExtend)
{
  if (index != NoRegister) {
    bool release;
    int normalized = normalize
      (c, offset, index, scale, &preserveIndex, &release);

    switch (srcSize) {
    case 1:
      if (signExtend) {
        emit(c, ldrsb(dst->low, base, normalized));
      } else {
        emit(c, ldrb(dst->low, base, normalized));
      }
      break;

    case 2:
      if (signExtend) {
        emit(c, ldrsh(dst->low, base, normalized));
      } else {
        emit(c, ldrh(dst->low, base, normalized));
      }
      break;

    case 4:
    case 8: {
      if (srcSize == 4 and dstSize == 8) {
        load(c, 4, base, 0, normalized, 1, 4, dst, preserveIndex, false);
        moveRR(c, 4, dst, 8, dst);
      } else if (srcSize == 8 and dstSize == 8) {
        Assembler::Register dstHigh(dst->high);
        load(c, 4, base, 0, normalized, 1, 4, &dstHigh, preserveIndex, false);
        load(c, 4, base, 4, normalized, 1, 4, dst, preserveIndex, false);
      } else {
        emit(c, ldr(dst->low, base, normalized));
      }
    } break;

    default: abort(c);
    }

    if (release) c->client->releaseTemporary(normalized);
  } else {
    switch (srcSize) {
    case 1:
      if (signExtend) {
        emit(c, ldrsbi(dst->low, base, offset));
      } else {
        emit(c, ldrbi(dst->low, base, offset));
      }
      break;

    case 2:
      if (signExtend) {
        emit(c, ldrshi(dst->low, base, offset));
      } else {
        emit(c, ldrhi(dst->low, base, offset));
      }
      break;

    case 4:
      emit(c, ldri(dst->low, base, offset));
      break;

    case 8: {
      if (dstSize == 8) {
        Assembler::Register dstHigh(dst->high);
        load(c, 4, base, offset, NoRegister, 1, 4, &dstHigh, false, false);
        load(c, 4, base, offset + 4, NoRegister, 1, 4, dst, false, false);
      } else {
        emit(c, ldri(dst->low, base, offset));
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
       dstSize, dst, true, true);
}

void
moveZMR(Context* c, unsigned srcSize, Assembler::Memory* src,
        unsigned dstSize, Assembler::Register* dst)
{
  load(c, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true, false);
}

void
andR(Context* c, unsigned size, Assembler::Register* a,
     Assembler::Register* b, Assembler::Register* dst)
{
  if (size == 8) emit(c, and_(dst->high, a->high, b->high));
  emit(c, and_(dst->low, a->low, b->low));
}

void
andC(Context* c, unsigned size, Assembler::Constant* a,
     Assembler::Register* b, Assembler::Register* dst)
{
  assert(con, size == BytesPerWord);

  int32_t i = getValue(a);
  if (i) {
    emit(con, andi(t->low, b->low, lo8(i)));
    emit(con, andi(t->low, b->low, hi8(i), 12));
    emit(con, andi(t->low, b->low, lo8(hi16(i)), 8));
    emit(con, andi(t->low, b->low, hi8(hi16(i)), 4));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void
orR(Context* c, unsigned size, Assembler::Register* a,
    Assembler::Register* b, Assembler::Register* dst)
{
  if (size == 8) orr(dst->high, a->high, b->high);
  emit(c, orr(dst->low, a->low, b->low));
}

void
orC(Context* c, unsigned size, Assembler::Constant* a,
    Assembler::Register* b, Assembler::Register* dst)
{
  assert(con, size == BytesPerWord);

  int32_t i = getValue(a);
  if (i) {
    emit(con, orri(t->low, b->low, lo8(i)));
    if (!isInt8(i)) {
      emit(con, orri(t->low, b->low, hi8(i), 12));
      if (!isInt16(i)) {
        emit(con, orri(t->low, b->low, lo8(hi16(i)), 8));
        if (!isInt24(i)) {
          emit(con, orri(t->low, b->low, hi8(hi16(i)), 4));
        }
      }
    }
  } else {
    moveRR(con, size, b, size, t);
  }
}

void
xorR(Context* com, unsigned size, Assembler::Register* a,
     Assembler::Register* b, Assembler::Register* dst)
{
  if (size == 8) emit(com, eor(dst->high, a->high, b->high));
  emit(com, eor(dst->low, a->low, b->low));
}

void
xorC(Context* com, unsigned size, Assembler::Constant* a,
     Assembler::Register* b, Assembler::Register* dst)
{
  assert(con, size == BytesPerWord);

  int32_t i = getValue(a);
  if (i) {
    emit(con, eori(t->low, b->low, lo8(i)));
    if (!isInt8(i)) {
      emit(con, eori(t->low, b->low, hi8(i), 12));
      if (!isInt16(i)) {
        emit(con, eori(t->low, b->low, lo8(hi16(i)), 8));
        if (!isInt24(i)) {
          emit(con, eori(t->low, b->low, hi8(hi16(i)), 4));
        }
      }
    }
  } else {
    moveRR(con, size, b, size, t);
  }
}

void
moveAR(Context* c, unsigned srcSize, Assembler::Address* src,
       unsigned dstSize, Assembler::Register* dst)
{
  assert(c, srcSize == 4 and dstSize == 4);

  Assembler::Constant constant(src->address);
  Assembler::Memory memory(dst->low, 0, -1, 0);

  moveCR(c, srcSize, &constant, dstSize, dst);
  moveMR(c, dstSize, &memory, dstSize, dst);
}

void
compareRR(Context* c, unsigned aSize UNUSED, Assembler::Register* a,
          unsigned bSize UNUSED, Assembler::Register* b)
{
  assert(c, aSize == 4 and bSize == 4);
  emit(c, cmp(b->low, a->low));
}

void
compareCR(Context* c, unsigned aSize, Assembler::Constant* a,
          unsigned bSize, Assembler::Register* b)
{
  assert(c, aSize == 4 and bSize == 4);

  if (a->value->resolved() and isInt16(a->value->value())) {
    emit(c, cmpi(b->low, a->value->value()));
  } else {
    Assembler::Register tmp(c->client->acquireTemporary());
    moveCR(c, aSize, a, bSize, &tmp);
    compareRR(c, bSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void
compareCM(Context* c, unsigned aSize, Assembler::Constant* a,
          unsigned bSize, Assembler::Memory* b)
{
  assert(c, aSize == 4 and bSize == 4);

  Assembler::Register tmp(c->client->acquireTemporary());
  moveMR(c, bSize, b, bSize, &tmp);
  compareCR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void
compareRM(Context* c, unsigned aSize, Assembler::Register* a,
          unsigned bSize, Assembler::Memory* b)
{
  assert(c, aSize == 4 and bSize == 4);

  Assembler::Register tmp(c->client->acquireTemporary());
  moveMR(c, bSize, b, bSize, &tmp);
  compareRR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void
longCompare(Context* c, Assembler::Operand* al, Assembler::Operand* ah,
            Assembler::Operand* bl, Assembler::Operand* bh,
            Assembler::Register* dst, BinaryOperationType compareSigned,
            BinaryOperationType compareUnsigned)
{
  ResolvedPromise negativePromise(-1);
  Assembler::Constant negative(&negativePromise);

  ResolvedPromise zeroPromise(0);
  Assembler::Constant zero(&zeroPromise);

  ResolvedPromise positivePromise(1);
  Assembler::Constant positive(&positivePromise);

  compareSigned(c, 4, ah, 4, bh);

  unsigned less = c->code.length();
  emit(c, blt(0));

  unsigned greater = c->code.length();
  emit(c, bgt(0));

  compareUnsigned(c, 4, al, 4, bl);

  unsigned above = c->code.length();
  emit(c, bgt(0));

  unsigned below = c->code.length();
  emit(c, blt(0));

  moveCR(c, 4, &zero, 4, dst);

  unsigned nextFirst = c->code.length();
  emit(c, b(0));

  updateOffset
    (c->s, c->code.data + less, true, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));

  updateOffset
    (c->s, c->code.data + below, true, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));

  moveCR(c, 4, &negative, 4, dst);

  unsigned nextSecond = c->code.length();
  emit(c, b(0));

  updateOffset
    (c->s, c->code.data + greater, true, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));

  updateOffset
    (c->s, c->code.data + above, true, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));

  moveCR(c, 4, &positive, 4, dst);

  updateOffset
    (c->s, c->code.data + nextFirst, false, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));

  updateOffset
    (c->s, c->code.data + nextSecond, false, reinterpret_cast<intptr_t>
     (c->code.data + c->code.length()));
}

void
longCompareR(Context* c, unsigned size UNUSED, Assembler::Register* a,
             Assembler::Register* b, Assembler::Register* dst)
{
  assert(c, size == 8);
  
  Assembler::Register ah(a->high);
  Assembler::Register bh(b->high);
  
  longCompare(c, a, &ah, b, &bh, dst, CAST2(compareRR),
              CAST2(compareUnsignedRR));
}

void
longCompareC(Context* c, unsigned size UNUSED, Assembler::Constant* a,
             Assembler::Register* b, Assembler::Register* dst)
{
  assert(c, size == 8);

  int64_t v = a->value->value();

  ResolvedPromise low(v & ~static_cast<uintptr_t>(0));
  Assembler::Constant al(&low);
  
  ResolvedPromise high((v >> 32) & ~static_cast<uintptr_t>(0));
  Assembler::Constant ah(&high);
  
  Assembler::Register bh(b->high);
  
  longCompare(c, &al, &ah, b, &bh, dst, CAST2(compareCR),
              CAST2(compareUnsignedCR));
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
    c->client->releaseTemporary(tmp.low);
  }
}

void
negateRR(Context* c, unsigned srcSize, Assembler::Register* src,
         unsigned dstSize UNUSED, Assembler::Register* dst)
{
  assert(c, srcSize == dstSize);

  emit(c, mvn(dst->low, src->low));
  emit(c, SETS(addi(dst->low, dst->low, 1)));
  if (srcSize == 8) {
    emit(c, mvn(dst->high, src->high));
    emit(c, adci(dst->high, dst->high, 0));
  }
}

void
callR(Context* c, unsigned size UNUSED, Assembler::Register* target)
{
  assert(c, size == BytesPerWord);
  emit(c, blx(target->low));
}

void
callC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), false);
  emit(c, bl(0));
}

void
longCallC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  Assembler::Register tmp(0);
  moveCR2(c, BytesPerWord, target, BytesPerWord, &tmp, 12);
  callR(c, BytesPerWord, &tmp);
}

void
longJumpC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  Assembler::Register tmp(0);
  moveCR2(c, BytesPerWord, target, BytesPerWord, &tmp, 12);
  jumpR(c, BytesPerWord, &tmp);
}

void
jumpC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), false);
  emit(c, b(0));
}

void
jumpIfEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), EQ));
}

void
jumpIfNotEqualC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), NE));
}

void
jumpIfGreaterC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), GT));
}

void
jumpIfGreaterOrEqualC(Context* c, unsigned size UNUSED,
                      Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), GE));
}

void
jumpIfLessC(Context* c, unsigned size UNUSED, Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), LS));
}

void
jumpIfLessOrEqualC(Context* c, unsigned size UNUSED,
                   Assembler::Constant* target)
{
  assert(c, size == BytesPerWord);

  appendOffsetTask(c, target->value, offset(c), true);
  emit(c, SETCOND(b(0), LE));
}

void
return_(Context* c)
{
  emit(c, mov(15, 14));
}

void
memoryBarrier(Context* c) {}

// END OPERATION COMPILERS

void
populateTables(ArchitectureContext* c)
{
  const OperandType C = ConstantOperand;
  const OperandType A = AddressOperand;
  const OperandType R = RegisterOperand;
  const OperandType M = MemoryOperand;

  OperationType* zo = c->operations;
  UnaryOperationType* uo = c->unaryOperations;
  BinaryOperationType* bo = c->binaryOperations;
  TernaryOperationType* to = c->ternaryOperations;

  zo[Return] = return_;
  zo[LoadBarrier] = memoryBarrier;
  zo[StoreStoreBarrier] = memoryBarrier;
  zo[StoreLoadBarrier] = memoryBarrier;

  uo[index(LongCall, C)] = CAST1(longCallC);

  uo[index(LongJump, C)] = CAST1(longJumpC);

  uo[index(Jump, R)] = CAST1(jumpR);
  uo[index(Jump, C)] = CAST1(jumpC);

  uo[index(AlignedJump, R)] = CAST1(jumpR);
  uo[index(AlignedJump, C)] = CAST1(jumpC);

  uo[index(JumpIfEqual, C)] = CAST1(jumpIfEqualC);
  uo[index(JumpIfNotEqual, C)] = CAST1(jumpIfNotEqualC);
  uo[index(JumpIfGreater, C)] = CAST1(jumpIfGreaterC);
  uo[index(JumpIfGreaterOrEqual, C)] = CAST1(jumpIfGreaterOrEqualC);
  uo[index(JumpIfLess, C)] = CAST1(jumpIfLessC);
  uo[index(JumpIfLessOrEqual, C)] = CAST1(jumpIfLessOrEqualC);

  uo[index(Call, C)] = CAST1(callC);
  uo[index(Call, R)] = CAST1(callR);

  uo[index(AlignedCall, C)] = CAST1(callC);
  uo[index(AlignedCall, R)] = CAST1(callR);

  bo[index(Move, R, R)] = CAST2(moveRR);
  bo[index(Move, C, R)] = CAST2(moveCR);
  bo[index(Move, C, M)] = CAST2(moveCM);
  bo[index(Move, M, R)] = CAST2(moveMR);
  bo[index(Move, R, M)] = CAST2(moveRM);
  bo[index(Move, A, R)] = CAST2(moveAR);

  bo[index(MoveZ, R, R)] = CAST2(moveZRR);
  bo[index(MoveZ, M, R)] = CAST2(moveZMR);
  bo[index(MoveZ, C, R)] = CAST2(moveCR);

  bo[index(Compare, R, R)] = CAST2(compareRR);
  bo[index(Compare, C, R)] = CAST2(compareCR);
  bo[index(Compare, R, M)] = CAST2(compareRM);
  bo[index(Compare, C, M)] = CAST2(compareCM);

  bo[index(Negate, R, R)] = CAST2(negateRR);

  to[index(Add, R)] = CAST3(addR);
  to[index(Add, C)] = CAST3(addC);

  to[index(Subtract, R)] = CAST3(subR);
  to[index(Subtract, C)] = CAST3(subC);

  to[index(Multiply, R)] = CAST3(multiplyR);

  to[index(Divide, R)] = CAST3(divideR);

  to[index(Remainder, R)] = CAST3(remainderR);

  to[index(ShiftLeft, R)] = CAST3(shiftLeftR);
  to[index(ShiftLeft, C)] = CAST3(shiftLeftC);

  to[index(ShiftRight, R)] = CAST3(shiftRightR);
  to[index(ShiftRight, C)] = CAST3(shiftRightC);

  to[index(UnsignedShiftRight, R)] = CAST3(unsignedShiftRightR);
  to[index(UnsignedShiftRight, C)] = CAST3(unsignedShiftRightC);

  to[index(And, C)] = CAST3(andC);
  to[index(And, R)] = CAST3(andR);

  to[index(Or, C)] = CAST3(orC);
  to[index(Or, R)] = CAST3(orR);

  to[index(Xor, C)] = CAST3(xorC);
  to[index(Xor, R)] = CAST3(xorR);

  to[index(LongCompare, R)] = CAST3(longCompareR);
  to[index(LongCompare, C)] = CAST3(longCompareC);
}

// TODO
class MyArchitecture: public Assembler::Architecture {
 public:
  MyArchitecture(System* system): c(system), referenceCount(0) {
    populateTables(&c);
  }

  virtual unsigned registerCount() {
    return 16;
  }

  virtual int stack() {
    return StackRegister;
  }

  virtual int thread() {
    return ThreadRegister;
  }

  virtual int returnLow() {
    return 4;
  }

  virtual int returnHigh() {
    return (BytesPerWord == 4 ? 3 : NoRegister);
  }

  virtual int virtualCallTarget() {
    return 4;
  }

  virtual int virtualCallIndex() {
    return 3;
  }

  virtual bool condensedAddressing() {
    return false;
  }

  virtual bool bigEndian() {
    return false;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case StackRegister:
    case ThreadRegister:
    case 15:
      return true;

    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint) {
    return max(footprint, StackAlignmentInWords);
  }

  virtual unsigned argumentFootprint(unsigned footprint) {
    return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
  }

  virtual unsigned argumentRegisterCount() {
    return 4;
  }

  virtual int argumentRegister(unsigned index) {
    assert(&c, index < argumentRegisterCount());

    return index + 0;
  }

  virtual unsigned stackAlignmentInWords() {
    return StackAlignmentInWords;
  }

  virtual bool matchCall(void* returnAddress, void* target) {
    uint32_t* instruction = static_cast<uint32_t*>(returnAddress) - 1;

    return *instruction == static_cast<uint32_t>
      (bl(static_cast<uint8_t*>(target)
          - reinterpret_cast<uint8_t*>(instruction)));
  }

  virtual void updateCall(UnaryOperation op UNUSED,
                          bool assertAlignment UNUSED, void* returnAddress,
                          void* newTarget)
  {
    switch (op) {
    case Call:
    case Jump: {
      updateOffset(c.s, static_cast<uint8_t*>(returnAddress) - 4, false,
                   reinterpret_cast<intptr_t>(newTarget));
    } break;

    case LongCall:
    case LongJump: {
      updateImmediate(c.s, static_cast<uint8_t*>(returnAddress) - 12,
                      reinterpret_cast<intptr_t>(newTarget), BytesPerWord);
    } break;

    default: abort(&c);
    }
  }

  virtual unsigned constantCallSize() {
    return 4;
  }

  virtual uintptr_t getConstant(const void* src) {
    const int32_t* p = static_cast<const int32_t*>(src);
    return (p[0] << 16) | (p[1] & 0xFFFF);    
  }

  virtual void setConstant(void* dst, uintptr_t constant) {
    updateImmediate(c.s, dst, constant, BytesPerWord);
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    const unsigned alignment = StackAlignmentInBytes / BytesPerWord;
    return (ceiling(sizeInWords + FrameFooterSize, alignment) * alignment);
  }

  virtual void* frameIp(void* stack) {
    return stack ? static_cast<void**>(stack)[2] : 0;
  }

  virtual unsigned frameHeaderSize() {
    return 0;
  }

  virtual unsigned frameReturnAddressSize() {
    return 0;
  }

  virtual unsigned frameFooterSize() {
    return FrameFooterSize;
  }

  virtual int returnAddressOffset() {
    return 8 / BytesPerWord;
  }

  virtual int framePointerOffset() {
    return 0;
  }

  virtual void nextFrame(void** stack, void**) {
    assert(&c, *static_cast<void**>(*stack) != *stack);

    *stack = *static_cast<void**>(*stack);
  }

  virtual void plan
  (UnaryOperation,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   bool* thunk)
  {
    *aTypeMask = (1 << RegisterOperand) | (1 << ConstantOperand);
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
    case Add:
    case Subtract:
      if (aSize == 8) {
        *aTypeMask = *bTypeMask = (1 << RegisterOperand);
      }
      break;

    case Multiply:
      *aTypeMask = *bTypeMask = (1 << RegisterOperand);
      break;

    case LongCompare:
      *bTypeMask = (1 << RegisterOperand);
      break;

    case Divide:
    case Remainder:
      *bTypeMask = ~0;
      *thunk = true;
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
    emit(&c, mov(returnAddress.low, 14));

    Memory returnAddressDst(StackRegister, 8);
    moveRM(&c, BytesPerWord, &returnAddress, BytesPerWord, &returnAddressDst);

    Register stack(StackRegister);
    Memory stackDst(StackRegister, -footprint * BytesPerWord);
    moveAndUpdateRM(&c, BytesPerWord, &stack, BytesPerWord, &stackDst);
  }

  virtual void adjustFrame(unsigned footprint) {
    Register nextStack(0);
    Memory stackSrc(StackRegister, 0);
    moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &nextStack);

    Memory stackDst(StackRegister, -footprint * BytesPerWord);
    moveAndUpdateRM(&c, BytesPerWord, &nextStack, BytesPerWord, &stackDst);
  }

  virtual void popFrame() {
    Register stack(StackRegister);
    Memory stackSrc(StackRegister, 0);
    moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &stack);

    Register returnAddress(0);
    Memory returnAddressSrc(StackRegister, 8);
    moveMR(&c, BytesPerWord, &returnAddressSrc, BytesPerWord, &returnAddress);
    
    emit(&c, mov(14, returnAddress.low));
  }

  virtual void popFrameForTailCall(unsigned footprint,
                                   int offset,
                                   int returnAddressSurrogate,
                                   int framePointerSurrogate)
  {
    if (TailCalls) {
      if (offset) {
        Register tmp(0);
        Memory returnAddressSrc(StackRegister, 8 + (footprint * BytesPerWord));
        moveMR(&c, BytesPerWord, &returnAddressSrc, BytesPerWord, &tmp);
    
        emit(&c, mov(14, tmp.low));

        Memory stackSrc(StackRegister, footprint * BytesPerWord);
        moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &tmp);

        Memory stackDst(StackRegister, (footprint - offset) * BytesPerWord);
        moveAndUpdateRM(&c, BytesPerWord, &tmp, BytesPerWord, &stackDst);

        if (returnAddressSurrogate != NoRegister) {
          assert(&c, offset > 0);

          Register ras(returnAddressSurrogate);
          Memory dst(StackRegister, 8 + (offset * BytesPerWord));
          moveRM(&c, BytesPerWord, &ras, BytesPerWord, &dst);
        }

        if (framePointerSurrogate != NoRegister) {
          assert(&c, offset > 0);

          Register fps(framePointerSurrogate);
          Memory dst(StackRegister, offset * BytesPerWord);
          moveRM(&c, BytesPerWord, &fps, BytesPerWord, &dst);
        }
      } else {
        popFrame();
      }
    } else {
      abort(&c);
    }
  }

  virtual void popFrameAndPopArgumentsAndReturn(unsigned argumentFootprint) {
    popFrame();

    assert(&c, argumentFootprint >= StackAlignmentInWords);
    assert(&c, (argumentFootprint % StackAlignmentInWords) == 0);

    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      Register tmp(0);
      Memory stackSrc(StackRegister, 0);
      moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &tmp);

      Memory stackDst(StackRegister,
                      (argumentFootprint - StackAlignmentInWords)
                      * BytesPerWord);
      moveAndUpdateRM(&c, BytesPerWord, &tmp, BytesPerWord, &stackDst);
    }

    return_(&c);
  }

  virtual void popFrameAndUpdateStackAndReturn(unsigned stackOffsetFromThread)
  {
    popFrame();

    Register tmp1(0);
    Memory stackSrc(StackRegister, 0);
    moveMR(&c, BytesPerWord, &stackSrc, BytesPerWord, &tmp1);

    Register tmp2(5);
    Memory newStackSrc(ThreadRegister, stackOffsetFromThread);
    moveMR(&c, BytesPerWord, &newStackSrc, BytesPerWord, &tmp2);

    Register stack(StackRegister);
    subR(&c, BytesPerWord, &stack, &tmp2, &tmp2);

    Memory stackDst(StackRegister, 0, tmp2.low);
    moveAndUpdateRM(&c, BytesPerWord, &tmp1, BytesPerWord, &stackDst);

    return_(&c);
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
                     unsigned cSize UNUSED, OperandType cType UNUSED,
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
