#include "assembler.h"
#include "vector.h"

using namespace vm;

#define INDEX1(a, b) ((a) + (UnaryOperationCount * (b)))

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)

#define INDEX2(a, b, c) \
  ((a) \
   + (BinaryOperationCount * (b)) \
   + (BinaryOperationCount * OperandTypeCount * (c)))

#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)

namespace {

/*
 * SIMPLE TYPES
 */
typedef uint8_t byte;
typedef uint16_t hword;
typedef uint32_t word;
typedef uint64_t dword;

/*
 * BITFIELD MASKS
 */
const word MASK_LOW16 0x0ffff;
const word MASK_LOW8  0x0ff;

/*
 * BITFIELD HANDLERS
 */
inline word low32(dword i) {
  return (word)(i & 0x0ffffffff);
}
inline word high32(dword i) {
  return low32(i >> 32);
}
inline hword low16(dword i) {
  return (hword)(i & 0x0ffff);
}
inline hword high16(dword i) {
  return low16(i >> 16);
}
inline hword higher16(dword i) {
  return low16(i >> 32);
}
inline hword highest16(dword i) {
  return low16(i >> 48);
}

/*
 * INSTRUCTION FORMATS
 */
inline word ifD(word op, word rt, word ra, word d) {
  return op<<26|rt<<21|ra<<16|d;
}
inline word ifDS(word op, word rt, word ra, word ds, word xo) {
  return op<<26|rt<<21|ra<<16|ds<<2|xo;
}
inline word ifI(word op, word li, word aa, word lk) {
  return op<<26|li<<2|aa<<1|lk;
}
inline word ifB(word op, word bo, word bi, word bd, word aa, word lk) {
  return op<<26|bo<<21|bi<<16|bd<<2|aa<<1|lk;
}
inline word ifSC(word op, word lev) {
  return op<<26|lev<<5|2;
}
inline word ifX(word op, word rt, word ra, word rb, word xo, word rc) {
  return op<<26|rt<<21|ra<<16|rb<<11|xo<<1|rc;
}
inline word ifXL(word op, word bt, word ba, word bb, word xo, word lk) {
  return op<<26|bt<<21|ba<<16|bb<<11|xo<<1|lk;
}
inline word ifXFX(word op, word rt, word spr, word xo) {
  return op<<26|rt<<21|spr<<11|xo<<1;
}
inline word ifXFL(word op, word flm, word frb, word xo, word rc) {
  return op<<26|flm<<17|frb<<11|xo<<1|rc;
}
inline word ifXS(word op, word rs, word ra, word sh, word xo, word sh2, word rc) {
  return op<<26|rs<<21|ra<<16|sh<<11|xo<<2|sh2<<1|rc;
}
inline word ifXO(word op, word rt, word ra, word rb, word oe, word xo, word rc) {
  return op<<26|rt<<21|ra<<16|rb<<11|oe<<10|xo<<1|rc;
}
inline word ifA(word op, word frt, word fra, word frb, word frc, word xo, word rc) {
  return op<<26|frt<<21|fra<<16|frb<<11|frc<<6|xo<<1|rc;
}
inline word ifM(word op, word rs, word ra, word rb, word mb, word me, word rc) {
  return op<<26|rs<<21|ra<<16|rb<<11|mb<<6|me<<1|rc;
}
inline word ifMD(word op, word rs, word ra, word sh, word mb, word xo, word sh2, word rc) {
  return op<<26|rs<<21|ra<<16|sh<<11|mb<<5|xo<<2|sh2<<1|rc;
}
inline word ifMDS(word op, word rs, word ra, word rb, word mb, word xo, word rc) {
  return op<<26|rs<<21|ra<<16|rb<<11|mb<<5|xo<<1|rc;
}

/*
 * PROGRAMMING MODEL
 */
inline void 
enum {
  r0,
  r1,
  r2,
  r3,
  r4,
  r5,
  r6,
  r7,
  r8,
  r9,
  r10,
  r11,
  r12,
  r13,
  r14,
  r15,
  r16,
  r17,
  r18,
  r19,
  r20,
  r21,
  r22,
  r23,
  r24,
  r25,
  r26,
  r27,
  r28,
  r29,
  r30,
  r31
};

/*
 * INSTRUCTIONS
 */
inline void asLbz(Context* c, int rt, int ra, int i) {
  int mc = ifD(34, rt, ra, i);
  c->code.append4(mc);
}
inline void asLhz(Context* c, int rt, int ra, int i) {
  int mc = ifD(40, rt, ra, i);
  c->code.append4(mc);
}
inline void asLwz(Context* c, int rt, int ra, int i) {
  int mc = ifD(32, rt, ra, i);
  c->code.append4(mc);
}
inline void asStb(Context* c, int rs, int ra, int i) {
  int mc = ifD(38, rs, ra, i);
  c->code.append4(mc);
}
inline void asSth(Context* c, int rs, int ra, int i) {
  int mc = ifD(44, rs, ra, i);
  c->code.append4(mc);
}
inline void asStw(Context* c, int rs, int ra, int i) {
  int mc = ifD(36, rs, ra, i);
  c->code.append4(mc);
}
inline void asAdd(Context* c, int rt, int ra, int rb) {
  int mc = ifXO(31, rt, ra, rb, 0, 266, 0);
  c->code.append4(mc);
}
inline void asAddc(Context* c, int rt, int ra, int rb) {
  int mc = ifXO(31, rt, ra, rb, 0, 10, 0);
  c->code.append4(mc);
}
inline void asAdde(Context* c, int rt, int ra, int rb) {
  int mc = ifXO(31, rt, ra, rb, 0, 138, 0);
  c->code.append4(mc);
}
inline void asAddi(Context* c, int rt, int ra, int i) {
  int mc = ifD(14, rt, ra, i);
  c->code.append4(mc);
}
inline void asAddis(Context* c, int rt, int ra, int i) {
  int mc = ifD(15, rt, ra, i);
  c->code.append4(mc);
}
inline void asSubf(Context* c, int rt, int ra, int rb) {
  int mc = ifXO(31, rt, ra, rb, 0, 40, 0);
  c->code.append4(mc);
}
inline void asSubfc(Context* c, int rt, int ra, int rb) {
  int mc = ifXO(31, rt, ra, rb, 0, 8, 0);
  c->code.append4(mc);
}
inline void asSubfe() {
  int mc = ifXO(31, rt, ra, rb, 0, 136, 0);
  c->code.append4(mc);
}
inline void asAnd(Context* c, int rt, int ra, int rb) {
  int mc = ifX(31, ra, rt, rb, 28, 0);
  c->code.append4(mc);
}
inline void asAndi(Context* c, int rt, int ra, int rb) {
  int mc = ifD(28, ra, rt, i);
  c->code.append4(mc);
}
inline void asAndis(Context* c, int rt, int ra, int rb) {
  int mc = ifD(29, ra, rt, i);
  c->code.append4(mc);
}
inline void asOr(Context* c, int rt, int ra, int rb) {
  int mc = ifX(31, ra, rt, rb, 444, 0);
  c->code.append4(mc);
}
inline void asOri(Context* c, int rt, int ra, int i) {
  int mc = ifD(24, rt, ra, i);
  c->code.append4(mc);
}
inline void asOris(Context* c, int rt, int ra, int i) {
  int mc = ifD(25, rt, ra, i);
  c->code.append4(mc);
}
inline void asRlwinm(Context* c, int rt, int ra, int i, int mb, int me) {
  int mc = ifM(21, ra, rt, i, mb, me, 0);
  c->code.append4(mc);
}
inline void asRlwimi(Context* c, int rt, int ra, int i, int mb, int me) {
  int mc = ifM(20, ra, rt, sh, mb, me, 0);
  c->code.append4(mc);
}
inline void asSlw(Context* c, int rt, int ra, int sh) {
  int mc = ifX(31, ra, rt, sh, 21, 0);
  c->code.append4(mc);
}
inline void asSld(Context* c, int rt, int ra, int rb) {
  int mc = ifX(31, ra, rt, rb, 27, 0);
  c->code.append4(mc);
}
inline void asSrw(Context* c, int rt, int ra, int sh) {
  int mc = ifX(31, ra, rt, sh, 536, 0);
  c->code.append4(mc);
}
inline void asSraw(Context* c, int rt, int ra, int sh) {
  int mc = ifX(31, ra, rt, sh, 792, 0);
  c->code.append4(mc);
}
inline void asSrawi(Context* c, int rt, int ra, int sh) {
  int mc = ifX(31, ra, rt, sh, 824, 0);
  c->code.append4(mc);
}

/*
 * PSEUDO-INSTRUCTIONS
 */
inline void asLi(Context* c, int rt, int i) { asOri(c, rt, 0, i); }
inline void asLis(Context* c, int rt, int i) { asOris(c, rt, 0, i); }
inline void asMr(Context* c, int rt, int ra) { asOr(c, rt, ra, ra); }
inline void asSlwi(Context* c, int rt, int ra, int i) { asRlwinm(c, rt, ra, i, 0, 31-i); }
inline void asSrwi(Context* c, int rt, int ra, int i) { asRlwinm(c, rt, ra, 32-i, i, 31); }
inline void asSub(Context* c, int rt, int ra, int rb) { asSubf(c, rt, rb, ra); }
inline void asSubc(Context* c, int rt, int ra, int rb) { asSubfc(c, rt, rb, ra); }
inline void asSubi() { asAddi(c, rt, ra, -i); }


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

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual void run(Context* c) = 0;

  Task* next;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, unsigned instructionOffset,
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
  unsigned instructionOffset;
  unsigned instructionSize;
};

void
appendOffsetTask(Context* c, Promise* promise, int instructionOffset,
                 unsigned instructionSize)
{
  c->tasks = new (c->zone->allocate(sizeof(OffsetTask))) OffsetTask
    (c->tasks, promise, instructionOffset, instructionSize);
}

class ImmediateTask: public Task {
 public:
  ImmediateTask(Task* next, Promise* promise, unsigned offset):
    Task(next),
    promise(promise),
    offset(offset)
  { }

  virtual void run(Context* c) {
    intptr_t v = promise->value();
    memcpy(c->result + offset, &v, BytesPerWord);
  }

  Promise* promise;
  unsigned offset;
};

void
appendImmediateTask(Context* c, Promise* promise, unsigned offset)
{
  c->tasks = new (c->zone->allocate(sizeof(ImmediateTask))) ImmediateTask
    (c->tasks, promise, offset);
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
moveCR(Context*, unsigned, Assembler::Constant*, Assembler::Register*);

void
callR(Context*, unsigned, Assembler::Register*);

void
callC(Context* c, unsigned size UNUSED, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  unconditional(c, 0xe8, a);
}

void
longCallC(Context* c, unsigned size, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  if (BytesPerWord == 8) {
    Assembler::Register r(r10);
    moveCR(c, size, a, &r);
    callR(c, size, &r);
  } else {
    callC(c, size, a);
  }
}

void
alignedCallC(Context* c, unsigned size, Assembler::Constant* a)
{
  while ((c->code.length() + 1) % 4) {
    c->code.append(0x90);
  }
  callC(c, size, a);
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
callM(Context* c, unsigned size UNUSED, Assembler::Memory* a)
{
  assert(c, size == BytesPerWord);

  encode(c, 0xff, 2, a, false);
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
longJumpC(Context* c, unsigned size, Assembler::Constant* a)
{
  assert(c, size == BytesPerWord);

  if (BytesPerWord == 8) {
    Assembler::Register r(r10);
    moveCR(c, size, a, &r);
    jumpR(c, size, &r);
  } else {
    jumpC(c, size, a);
  }
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
pushR(Context*, unsigned, Assembler::Register*);

void
pushC(Context* c, unsigned size, Assembler::Constant* a)
{
  if (BytesPerWord == 4 and size == 8) {
    int64_t v = a->value->value();

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    pushC(c, 4, &ah);
    pushC(c, 4, &al);
  } else {
    if (a->value->resolved()) {
      int64_t v = a->value->value();
      if (isInt8(v)) {
        c->code.append(0x6a);
        c->code.append(v);
      } else if (isInt32(v)) {
        c->code.append(0x68);
        c->code.append4(v);
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, size, a, &tmp);
        pushR(c, size, &tmp);
        c->client->releaseTemporary(tmp.low);
      }
    } else {
      if (BytesPerWord == 4) {
        c->code.append(0x68);
        appendImmediateTask(c, a->value, c->code.length());
        c->code.appendAddress(static_cast<uintptr_t>(0));
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, size, a, &tmp);
        pushR(c, size, &tmp);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void
moveAR(Context*, unsigned, Assembler::Address*, Assembler::Register* b);

void
pushA(Context* c, unsigned size, Assembler::Address* a)
{
  assert(c, BytesPerWord == 8 or size == 4); // todo
  
  Assembler::Register tmp(c->client->acquireTemporary());
  moveAR(c, size, a, &tmp);
  pushR(c, size, &tmp);
  c->client->releaseTemporary(tmp.low);
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
pushM(Context* c, unsigned size, Assembler::Memory* a)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Memory ah(a->base, a->offset + 4, a->index, a->scale);

    pushM(c, 4, &ah);
    pushM(c, 4, a);
  } else {
    assert(c, BytesPerWord == 4 or size == 8);

    encode(c, 0xff, 6, a, false);    
  }
}

void
move4To8RR(Context* c, unsigned size, Assembler::Register* a,
           Assembler::Register* b);

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
      move4To8RR(c, 0, a, a);
    }
  }
}

void
popM(Context* c, unsigned size, Assembler::Memory* a)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Memory ah(a->base, a->offset + 4, a->index, a->scale);

    popM(c, 4, a);
    popM(c, 4, &ah);
  } else {
    assert(c, BytesPerWord == 4 or size == 8);

    encode(c, 0x8f, 0, a, false);
  }
}

void
moveRR(Context* c, unsigned size, Assembler::Register* a,
       Assembler::Register* b);

void
xorRR(Context* c, unsigned size, Assembler::Register* a,
      Assembler::Register* b);

void
negateR(Context* c, unsigned size, Assembler::Register* a)
{
  if (BytesPerWord == 4 and size == 8) {
    assert(c, a->low == rax and a->high == rdx);

    ResolvedPromise zeroPromise(0);
    Assembler::Constant zero(&zeroPromise);

    Assembler::Register ah(a->high);

    negateR(c, 4, a);
    //addCarryCR(c, 4, &zero, &ah);
    negateR(c, 4, &ah);
  } else {
    if (size == 8) rex(c);
    c->code.append(0xf7);
    c->code.append(0xd8 | a->low);
  }
}

void
leaMR(Context* c, unsigned size, Assembler::Memory* b, Assembler::Register* a)
{
  if (BytesPerWord == 8 and size == 4) {
    encode(c, 0x8d, a->low, b, false);
  } else {
    assert(c, BytesPerWord == 8 or size == 4);

    encode(c, 0x8d, a->low, b, true);
  }
}

void
moveCR(Context* c, unsigned size, Assembler::Constant* a,
       Assembler::Register* b)
{
  int64_t imm = a->value->value();
  
  if(size == 8) {
    Assembler::Register bh(b->high);
    ResolvedPromise low(low32(imm));
    Assembler::Constant al(&low);
    ResolvedPromise high(high32(imm));
    Assembler::Constant ah(&high);
 
    moveCR(c, 4, &al, b);
    moveCR(c, 4, &ah, &bh);
  } else {
    int rt = b->low;
    asLis(c, rt, high16(imm));
    asOri(c, rt, rt, low16(imm));
  }
}

void
moveCM(Context* c, unsigned size, Assembler::Constant* a,
       Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  moveCR(c, size, a, tmp);
  moveRM(c, size, tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
moveRR(Context* c, unsigned size, Assembler::Register* a,
       Assembler::Register* b)
{
  if(a->low == b->low) return; // trivial case - and not a NOP in PPC!

  if (size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    moveRR(c, 4, a, b);
    moveRR(c, 4, &ah, &bh);
  } else {
    asMr(c, b->low, a->low);
  }
}

void
moveRM(Context* c, unsigned size, Assembler::Register* a, Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  int d = b->offset;
  int ra = b->base;
  int rs = a->low;

  if(b->index != NoRegister) {
    asSlwi(c, tmp, b->index, b->scale);
    asAdd(c, tmp, tmp, ra);
    ra = tmp;
  }

  switch (size) {
    case 1:
      asStb(c, rs, ra, d);
      break;

    case 2:
      asSth(c, rs, ra, d);
      break;

    case 4:
      asStw(c, rs, ra, d);
      break;

    case 8:
      Assembler::Register ah(a->high);
      Assembler::Memory bl(b->base, b->offset + 4, b->index, b->scale);
      moveRM(c, 4, a, &bl);    
      moveRM(c, 4, &ah, b);
      break;

    default: abort(c);
    }
  }

  c->client->releaseTemporary(tmp.low);
}

void
move4To8RR(Context* c, unsigned, Assembler::Register* a,
           Assembler::Register* b)
{
  Assembler::Register bh(b->high);
  moveRR(c, 4, a, b);
  moveRR(c, 4, a, &bh);
  asSrawi(c, bh.low, bh.low, 31);
}

void
moveMR(Context* c, unsigned size, Assembler::Memory* a, Assembler::Register* b)
{
  int d = a->offset;
  int rt = b->low;
  int ra = a->base; // register part of the address

  if(a->index != NoRegister) { // include the index in the EA
    asSlwi(c, rt, a->index, a->scale);
    asAdd(c, rt, rt, ra);
    ra = rt
  }
 
  switch (size) {
  case 1:
    asLbz(c, rt, ra, d);
    break;

  case 2:
    asLhz(c, rt, ra, d);
    break;

  case 4:
    asLwz(c, rt, ra, d);
    break;

  case 8:
    Assembler::Memory al(a->base, a->offset+4, a->index, a->scale);
    Assembler::Register bh(b->high);
    moveMR(c, 4, &al, b);
    moveMR(c, 4, a, &bh);
    break;

  default: abort(c);
  }  
}

void
moveAR(Context* c, unsigned size, Assembler::Address* a,
       Assembler::Register* b)
{
  Assembler::Constant constant(a->address);
  Assembler::Memory memory(b->low, 0, -1, 0);
  moveCR(c, size, &constant, b);
  moveMR(c, size, &memory, b);
}

void
moveAM(Context* c, unsigned size, Assembler::Address* a,
       Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  moveAR(c, size, a, &tmp);
  moveRM(c, size, &tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
moveMM(Context* c, unsigned size, Assembler::Memory* a,
       Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  moveMR(c, size, a, tmp);
  moveRM(c, size, tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
move4To8MR(Context* c, unsigned, Assembler::Memory* a, Assembler::Register* b)
{
  moveMR(c, 4, a, b);
  move4To8RR(c, 0, b, b);
}

void
moveZMR(Context* c, unsigned size, Assembler::Memory* a,
        Assembler::Register* b)
{
  moveMR(c, size, a, b);
}

void
moveZRR(Context* c, unsigned size, Assembler::Register* a,
        Assembler::Register* b)
{
  switch(size) {
    case 1:
      asAndi(c, b->low, a->low, MASK_LOW8);
      break;

    case 2:
      asAndi(c, b->low, a->low, MASK_LOW16);
      break;

    case 4:
      moveRR(c, size, a, b);
      break;

    case 8:
      Assembler::Register ah(a->high);
      Assembler::Register bh(b->high);
      moveZRR(c, 4, a, b);
      moveZRR(c, 4, &ah, &bh);
      break;

    default:
      abort(c);
  }
}

void
swapRR(Context* c, unsigned size, Assembler::Register* a, Assembler::Register* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  moveRR(c, size, a, &tmp);
  moveRR(c, size, b, a);
  moveRR(c, size, &tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
addCM(Context* c, unsigned size UNUSED, Assembler::Constant* a,
      Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());

  moveMR(c, 4, b, &tmp);
  addCR(c, 4, a, &tmp);
  moveRM(c, 4, &tmp, b);

  c->client->releaseTemporary(tmp.low);
}

void
addCR(Context* c, unsigned size, Assembler::Constant* a,
      Assembler::Register* b)
{
  int64_t imm = a->value->value();

  if(imm) {
    if(size == 8) { // 64-bit add (PowerPC not conducive to multi-precision constant arithmetic
      Assembler::Register tmp(c->client->acquireTemporary(),
          c->client->acquireTemporary());

      moveCR(c, 8, a, tmp);
      addRR(c, 8, tmp, b);

      c->client->releaseTemporary(tmp.low);
      c->client->releaseTemporary(tmp.high);
    } else { // 32-bit add
      int rt = b->low;
      asAddi(c, rt, rt, low16(imm));
      asAddis(c, rt, rt, high16(imm));
    }
  }
}

void
subtractCR(Context* c, unsigned size, Assembler::Constant* a,
           Assembler::Register* b)
{
  ResolvedPromise neg(-a->value->value());
  Assembler::Constant aneg(&neg);
  addCR(c, size, &aneg, b);
}

void
subtractRR(Context* c, unsigned size, Assembler::Register* a,
           Assembler::Register* b)
{
  if(size == 8) {
    asSubc(c, b->low, b->low, a->low);
    asSubfe(c, b->high, a->high, b->high);
  } else
    asSub(c, b->low, b->low, a->low);
}

void
addRR(Context* c, unsigned size, Assembler::Register* a,
      Assembler::Register* b)
{
  if(size == 8) {
    asAddc(c, b->low, b->low, a->low);
    asAdde(c, b->high, b->high, a->high);
  } else
    asAdd(c, b->low, b->low, a->low);
}

void
addRM(Context* c, unsigned size UNUSED, Assembler::Register* a,
      Assembler::Memory* b)
{
  Assembler::Register tmp(c->client->acquireTemporary());
  moveMR(c, size, b, tmp);
  addRR(c, size, a, tmp);
  moveRM(c, size, tmp, b);
  c->client->releaseTemporary();
}

void
multiplyRR(Context* c, unsigned size, Assembler::Register* a,
           Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    assert(c, b->high == rdx);
    assert(c, b->low != rax);
    assert(c, a->low != rax);
    assert(c, a->high != rax);

    c->client->save(rax);

    Assembler::Register axdx(rax, rdx);
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    moveRR(c, 4, b, &axdx);
    multiplyRR(c, 4, &ah, b);
    multiplyRR(c, 4, a, &bh);
    addRR(c, 4, &bh, b);
    
    // mul a->low,%eax%edx
    c->code.append(0xf7);
    c->code.append(0xe0 | a->low);
    
    addRR(c, 4, b, &bh);
    moveRR(c, 4, &axdx, b);

    c->client->restore(rax);
  } else {
    if (size == 8) rex(c);
    c->code.append(0x0f);
    c->code.append(0xaf);
    c->code.append(0xc0 | (b->low << 3) | a->low);
  }
}

void
multiplyCR(Context* c, unsigned size, Assembler::Constant* a,
           Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    const uint32_t mask = ~((1 << rax) | (1 << rdx));
    Assembler::Register tmp(c->client->acquireTemporary(mask),
                            c->client->acquireTemporary(mask));

    moveCR(c, size, a, &tmp);
    multiplyRR(c, size, &tmp, b);

    c->client->releaseTemporary(tmp.low);
    c->client->releaseTemporary(tmp.high);
  } else {
    int64_t v = a->value->value();
    if (v) {
      if (isInt32(v)) {
        if (size == 8) rex(c);
        if (isInt8(v)) {
          c->code.append(0x6b);
          c->code.append(0xc0 | (b->low << 3) | b->low);
          c->code.append(v);
        } else {
          c->code.append(0x69);
          c->code.append(0xc0 | (b->low << 3) | b->low);
          c->code.append4(v);        
        }
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, size, a, &tmp);
        multiplyRR(c, size, &tmp, b);
        c->client->releaseTemporary(tmp.low);      
      }
    }
  }
}

void
divideRR(Context* c, unsigned size, Assembler::Register* a,
         Assembler::Register* b UNUSED)
{
  assert(c, BytesPerWord == 8 or size == 4);

  assert(c, b->low == rax);
  assert(c, a->low != rdx);

  c->client->save(rdx);
    
  if (size == 8) rex(c);
  c->code.append(0x99); // cdq
  if (size == 8) rex(c);
  c->code.append(0xf7);
  c->code.append(0xf8 | a->low);

  c->client->restore(rdx);
}

void
divideCR(Context* c, unsigned size, Assembler::Constant* a,
         Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);

  const uint32_t mask = ~((1 << rax) | (1 << rdx));
  Assembler::Register tmp(c->client->acquireTemporary(mask));
  moveCR(c, size, a, &tmp);
  divideRR(c, size, &tmp, b);
  c->client->releaseTemporary(tmp.low);  
}

void
remainderRR(Context* c, unsigned size, Assembler::Register* a,
            Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);

  assert(c, b->low == rax);
  assert(c, a->low != rdx);

  c->client->save(rdx);
    
  if (size == 8) rex(c);
  c->code.append(0x99); // cdq
  if (size == 8) rex(c);
  c->code.append(0xf7);
  c->code.append(0xf8 | a->low);

  Assembler::Register dx(rdx);
  moveRR(c, BytesPerWord, &dx, b);

  c->client->restore(rdx);
}

void
remainderCR(Context* c, unsigned size, Assembler::Constant* a,
            Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4);

  const uint32_t mask = ~((1 << rax) | (1 << rdx));
  Assembler::Register tmp(c->client->acquireTemporary(mask));
  moveCR(c, size, a, &tmp);
  remainderRR(c, size, &tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
andRR(Context* c, unsigned size, Assembler::Register* a,
      Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    andRR(c, 4, a, b);
    andRR(c, 4, &ah, &bh);
  } else {
    if (size == 8) rex(c);
    c->code.append(0x21);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
andCR(Context* c, unsigned size, Assembler::Constant* a,
      Assembler::Register* b)
{
  int64_t v = a->value->value();

  if (BytesPerWord == 4 and size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    Assembler::Register bh(b->high);

    andCR(c, 4, &al, b);
    andCR(c, 4, &ah, &bh);
  } else {
    if (isInt32(v)) {
      if (size == 8) rex(c);
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
      moveCR(c, size, a, &tmp);
      andRR(c, size, &tmp, b);
      c->client->releaseTemporary(tmp.low);
    }
  }
}

void
andCM(Context* c, unsigned size UNUSED, Assembler::Constant* a,
      Assembler::Memory* b)
{
  assert(c, BytesPerWord == 8 or size == 4);

  int64_t v = a->value->value();

  encode(c, isInt8(a->value->value()) ? 0x83 : 0x81, 5, b, true);
  if (isInt8(v)) {
    c->code.append(v);
  } else if (isInt32(v)) {
    c->code.append4(v);
  } else {
    abort(c);
  }
}

void
orRR(Context* c, unsigned size, Assembler::Register* a,
     Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    orRR(c, 4, a, b);
    orRR(c, 4, &ah, &bh);
  } else {
    if (size == 8) rex(c);
    c->code.append(0x09);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
orCR(Context* c, unsigned size, Assembler::Constant* a,
     Assembler::Register* b)
{
  int64_t v = a->value->value();
  if (v) {
    if (BytesPerWord == 4 and size == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      Assembler::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      Assembler::Constant al(&low);

      Assembler::Register bh(b->high);

      orCR(c, 4, &al, b);
      orCR(c, 4, &ah, &bh);
    } else {
      if (isInt32(v)) {
        if (size == 8) rex(c);
        if (isInt8(v)) {
          c->code.append(0x83);
          c->code.append(0xc8 | b->low);
          c->code.append(v);
        } else {
          c->code.append(0x81);
          c->code.append(0xc8 | b->low);
          c->code.append4(v);        
        }
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, size, a, &tmp);
        orRR(c, size, &tmp, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void
xorRR(Context* c, unsigned size, Assembler::Register* a,
      Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    xorRR(c, 4, a, b);
    xorRR(c, 4, &ah, &bh);
  } else {
    if (size == 8) rex(c);
    c->code.append(0x31);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
xorCR(Context* c, unsigned size, Assembler::Constant* a,
      Assembler::Register* b)
{
  int64_t v = a->value->value();
  if (v) {
    if (BytesPerWord == 4 and size == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      Assembler::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      Assembler::Constant al(&low);

      Assembler::Register bh(b->high);

      xorCR(c, 4, &al, b);
      xorCR(c, 4, &ah, &bh);
    } else {
      if (isInt32(v)) {
        if (size == 8) rex(c);
        if (isInt8(v)) {
          c->code.append(0x83);
          c->code.append(0xf0 | b->low);
          c->code.append(v);
        } else {
          c->code.append(0x81);
          c->code.append(0xf0 | b->low);
          c->code.append4(v);        
        }
      } else {
        Assembler::Register tmp(c->client->acquireTemporary());
        moveCR(c, size, a, &tmp);
        xorRR(c, size, &tmp, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void
compareCR(Context* c, unsigned size, Assembler::Constant* a,
          Assembler::Register* b);

void
shiftLeftRR(Context* c, unsigned size, Assembler::Register* a,
            Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSlw(c, a->low, a->low, b->low);
}

void
shiftLeftCR(Context* c, unsigned size, Assembler::Constant* a,
            Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSlwi(c, b->low, b->low, a->value->value());
}

void
shiftRightRR(Context* c, unsigned size, Assembler::Register* a,
             Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSraw(c, b->low, b->low, a->low);
}

void
shiftRightCR(Context* c, unsigned size, Assembler::Constant* a,
             Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSrawi(c, b->low, b->low, a->value->value());
}

void
unsignedShiftRightRR(Context* c, unsigned size, Assembler::Register* a,
                     Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSrw(c, b->low, b->low, a->low);
}

void
unsignedShiftRightCR(Context* c, unsigned size, Assembler::Constant* a,
                     Assembler::Register* b)
{
  if(size == 8) {
  } else
    asSrwi(c, b->low, b->low, a->value->value());
}

void
multiwordCompare(Context* c, Assembler::Operand* al, Assembler::Operand* ah,
                 Assembler::Operand* bl, Assembler::Operand* bh,
                 BinaryOperationType op)
{
  op(c, BytesPerWord, ah, bh);

  // if the high order bits are equal, we compare the low order
  // bits; otherwise, we jump past that comparison
  c->code.append(0x0f);
  c->code.append(0x85); // jne

  unsigned comparisonOffset = c->code.length();
  c->code.append4(0);

  op(c, BytesPerWord, al, bl);

  int32_t comparisonSize = c->code.length() - comparisonOffset - 4;
  c->code.set(comparisonOffset, &comparisonSize, 4);
}

void
compareRR(Context* c, unsigned size, Assembler::Register* a,
          Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Register bh(b->high);

    multiwordCompare(c, a, &ah, b, &bh, CAST2(compareRR));
  } else {
    if (size == 8) rex(c);
    c->code.append(0x39);
    c->code.append(0xc0 | (a->low << 3) | b->low);
  }
}

void
compareAR(Context* c, unsigned size, Assembler::Address* a,
          Assembler::Register* b)
{
  assert(c, BytesPerWord == 8 or size == 4); // todo
  
  Assembler::Register tmp(c->client->acquireTemporary());
  moveAR(c, size, a, &tmp);
  compareRR(c, size, &tmp, b);
  c->client->releaseTemporary(tmp.low);
}

void
compareCR(Context* c, unsigned size, Assembler::Constant* a,
          Assembler::Register* b)
{
  int64_t v = a->value->value();

  if (BytesPerWord == 4 and size == 8) {
    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    Assembler::Register bh(b->high);

    multiwordCompare(c, &al, &ah, b, &bh, CAST2(compareCR));
  } else {
    if (isInt32(v)) {
      if (size == 8) rex(c);
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
      moveCR(c, size, a, &tmp);
      compareRR(c, size, &tmp, b);
      c->client->releaseTemporary(tmp.low);
    }
  }
}

void
compareCM(Context* c, unsigned size, Assembler::Constant* a,
          Assembler::Memory* b)
{
  int64_t v = a->value->value();

  if (BytesPerWord == 4 and size == 8) {
    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant al(&low);

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant ah(&high);

    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);

    multiwordCompare(c, &al, &ah, b, &bh, CAST2(compareCM));
  } else {
    encode(c, isInt8(v) ? 0x83 : 0x81, 7, b, true);

    if (isInt8(v)) {
      c->code.append(v);
    } else if (isInt32(v)) {
      c->code.append4(v);
    } else {
      abort(c);
    }
  }
}

void
compareRM(Context* c, unsigned size, Assembler::Register* a,
          Assembler::Memory* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);
    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);

    multiwordCompare(c, a, &ah, b, &bh, CAST2(compareRM));
  } else {
    if (BytesPerWord == 8 and size == 4) {
      move4To8RR(c, size, a, a);
    }
    encode(c, 0x39, a->low, b, true);
  }
}

void
compareMR(Context* c, unsigned size, Assembler::Memory* a,
          Assembler::Register* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Memory ah(a->base, a->offset + 4, a->index, a->scale);
    Assembler::Register bh(b->high);

    multiwordCompare(c, a, &ah, b, &bh, CAST2(compareMR));
  } else {
    if (BytesPerWord == 8 and size == 4) {
      move4To8RR(c, size, b, b);
    }
    encode(c, 0x3b, b->low, a, true);
  }
}

void
compareMM(Context* c, unsigned size, Assembler::Memory* a,
          Assembler::Memory* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Memory ah(a->base, a->offset + 4, a->index, a->scale);
    Assembler::Memory bh(b->base, b->offset + 4, b->index, b->scale);

    multiwordCompare(c, a, &ah, b, &bh, CAST2(compareMM));
  } else {
    Assembler::Register tmp(c->client->acquireTemporary());
    moveMR(c, size, a, &tmp);
    compareRM(c, size, &tmp, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void
compareRC(Context* c, unsigned size, Assembler::Register* a,
          Assembler::Constant* b)
{
  if (BytesPerWord == 4 and size == 8) {
    Assembler::Register ah(a->high);

    int64_t v = b->value->value();

    ResolvedPromise low(v & 0xFFFFFFFF);
    Assembler::Constant bl(&low);

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    Assembler::Constant bh(&high);

    multiwordCompare(c, a, &ah, &bl, &bh, CAST2(compareRC));
  } else {
    Assembler::Register tmp(c->client->acquireTemporary());
    moveCR(c, size, b, &tmp);
    compareRR(c, size, a, &tmp);
    c->client->releaseTemporary(tmp.low);
  }
}

void
populateTables()
{
  Operations[Return] = return_;

  const int Constant = ConstantOperand;
  const int Address = AddressOperand;
  const int Register = RegisterOperand;
  const int Memory = MemoryOperand;

  UnaryOperations[INDEX1(Call, Constant)] = CAST1(callC);
  UnaryOperations[INDEX1(Call, Register)] = CAST1(callR);
  UnaryOperations[INDEX1(Call, Memory)] = CAST1(callM);

  UnaryOperations[INDEX1(LongCall, Constant)] = CAST1(longCallC);

  UnaryOperations[INDEX1(AlignedCall, Constant)] = CAST1(alignedCallC);

  UnaryOperations[INDEX1(Jump, Constant)] = CAST1(jumpC);
  UnaryOperations[INDEX1(Jump, Register)] = CAST1(jumpR);
  UnaryOperations[INDEX1(Jump, Memory)] = CAST1(jumpM);

  UnaryOperations[INDEX1(LongJump, Constant)] = CAST1(longJumpC);

  UnaryOperations[INDEX1(JumpIfEqual, Constant)] = CAST1(jumpIfEqualC);
  UnaryOperations[INDEX1(JumpIfNotEqual, Constant)] = CAST1(jumpIfNotEqualC);
  UnaryOperations[INDEX1(JumpIfGreater, Constant)] = CAST1(jumpIfGreaterC);
  UnaryOperations[INDEX1(JumpIfGreaterOrEqual, Constant)]
    = CAST1(jumpIfGreaterOrEqualC);
  UnaryOperations[INDEX1(JumpIfLess, Constant)] = CAST1(jumpIfLessC);
  UnaryOperations[INDEX1(JumpIfLessOrEqual, Constant)]
    = CAST1(jumpIfLessOrEqualC);

  UnaryOperations[INDEX1(Push, Constant)] = CAST1(pushC);
  UnaryOperations[INDEX1(Push, Address)] = CAST1(pushA);
  UnaryOperations[INDEX1(Push, Register)] = CAST1(pushR);
  UnaryOperations[INDEX1(Push, Memory)] = CAST1(pushM);

  UnaryOperations[INDEX1(Pop, Register)] = CAST1(popR);
  UnaryOperations[INDEX1(Pop, Memory)] = CAST1(popM);

  UnaryOperations[INDEX1(Negate, Register)] = CAST1(negateR);

  BinaryOperations[INDEX2(LoadAddress, Memory, Register)] = CAST2(leaMR);

  BinaryOperations[INDEX2(Move, Constant, Register)] = CAST2(moveCR);
  BinaryOperations[INDEX2(Move, Constant, Memory)] = CAST2(moveCM);
  BinaryOperations[INDEX2(Move, Register, Memory)] = CAST2(moveRM);
  BinaryOperations[INDEX2(Move, Register, Register)] = CAST2(moveRR);
  BinaryOperations[INDEX2(Move, Memory, Register)] = CAST2(moveMR);
  BinaryOperations[INDEX2(Move, Address, Register)] = CAST2(moveAR);
  BinaryOperations[INDEX2(Move, Address, Memory)] = CAST2(moveAM);
  BinaryOperations[INDEX2(Move, Memory, Memory)] = CAST2(moveMM);

  BinaryOperations[INDEX2(Move4To8, Register, Register)] = CAST2(move4To8RR);
  BinaryOperations[INDEX2(Move4To8, Memory, Register)] = CAST2(move4To8MR);

  BinaryOperations[INDEX2(MoveZ, Memory, Register)] = CAST2(moveZMR);
  BinaryOperations[INDEX2(MoveZ, Register, Register)] = CAST2(moveZRR);

  BinaryOperations[INDEX2(Swap, Register, Register)] = CAST2(swapRR);

  BinaryOperations[INDEX2(Add, Constant, Register)] = CAST2(addCR);
  BinaryOperations[INDEX2(Add, Register, Register)] = CAST2(addRR);
  BinaryOperations[INDEX2(Add, Register, Memory)] = CAST2(addRM);
  BinaryOperations[INDEX2(Add, Constant, Memory)] = CAST2(addCM);

  BinaryOperations[INDEX2(Multiply, Register, Register)] = CAST2(multiplyRR);
  BinaryOperations[INDEX2(Multiply, Constant, Register)] = CAST2(multiplyCR);

  BinaryOperations[INDEX2(Divide, Register, Register)] = CAST2(divideRR);
  BinaryOperations[INDEX2(Divide, Constant, Register)] = CAST2(divideCR);

  BinaryOperations[INDEX2(Remainder, Constant, Register)] = CAST2(remainderCR);
  BinaryOperations[INDEX2(Remainder, Register, Register)] = CAST2(remainderRR);

  BinaryOperations[INDEX2(And, Register, Register)] = CAST2(andRR);
  BinaryOperations[INDEX2(And, Constant, Register)] = CAST2(andCR);
  BinaryOperations[INDEX2(And, Constant, Memory)] = CAST2(andCM);

  BinaryOperations[INDEX2(Or, Register, Register)] = CAST2(orRR);
  BinaryOperations[INDEX2(Or, Constant, Register)] = CAST2(orCR);

  BinaryOperations[INDEX2(Xor, Register, Register)] = CAST2(xorRR);
  BinaryOperations[INDEX2(Xor, Constant, Register)] = CAST2(xorCR);

  BinaryOperations[INDEX2(ShiftLeft, Register, Register)] = CAST2(shiftLeftRR);
  BinaryOperations[INDEX2(ShiftLeft, Constant, Register)] = CAST2(shiftLeftCR);

  BinaryOperations[INDEX2(ShiftRight, Register, Register)]
    = CAST2(shiftRightRR);
  BinaryOperations[INDEX2(ShiftRight, Constant, Register)]
    = CAST2(shiftRightCR);

  BinaryOperations[INDEX2(UnsignedShiftRight, Register, Register)]
    = CAST2(unsignedShiftRightRR);
  BinaryOperations[INDEX2(UnsignedShiftRight, Constant, Register)]
    = CAST2(unsignedShiftRightCR);

  BinaryOperations[INDEX2(Subtract, Constant, Register)] = CAST2(subtractCR);
  BinaryOperations[INDEX2(Subtract, Register, Register)] = CAST2(subtractRR);

  BinaryOperations[INDEX2(Compare, Constant, Register)] = CAST2(compareCR);
  BinaryOperations[INDEX2(Compare, Register, Constant)] = CAST2(compareRC);
  BinaryOperations[INDEX2(Compare, Register, Register)] = CAST2(compareRR);
  BinaryOperations[INDEX2(Compare, Address, Register)] = CAST2(compareAR);
  BinaryOperations[INDEX2(Compare, Register, Memory)] = CAST2(compareRM);
  BinaryOperations[INDEX2(Compare, Memory, Register)] = CAST2(compareMR);
  BinaryOperations[INDEX2(Compare, Constant, Memory)] = CAST2(compareCM);
  BinaryOperations[INDEX2(Compare, Memory, Memory)] = CAST2(compareMM);
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

  virtual void setClient(Client* client) {
    assert(&c, c.client == 0);
    c.client = client;
  }

  virtual unsigned registerCount() {
    return 8;//BytesPerWord == 4 ? 8 : 16;
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

  virtual void plan(UnaryOperation op, unsigned size, uint8_t* typeMask,
                    uint64_t* registerMask, bool* thunk)
  {
    if (op == Negate and BytesPerWord == 4 and size == 8) {
      *typeMask = 1 << RegisterOperand;
      *registerMask = (static_cast<uint64_t>(1) << (rdx + 32))
        | (static_cast<uint64_t>(1) << rax);
    } else {
      *typeMask = (1 << RegisterOperand) | (1 << MemoryOperand);
      *registerMask = ~static_cast<uint64_t>(0);
    }
    *thunk = false;
  }

  virtual void plan(BinaryOperation op, unsigned size, uint8_t* aTypeMask,
                    uint64_t* aRegisterMask, uint8_t* bTypeMask,
                    uint64_t* bRegisterMask, bool* thunk)
  {
    *aTypeMask = ~0;
    *aRegisterMask = ~static_cast<uint64_t>(0);

    *bTypeMask = (1 << RegisterOperand) | (1 << MemoryOperand);
    *bRegisterMask = ~static_cast<uint64_t>(0);

    *thunk = false;

    switch (op) {
    case Compare:
      if (BytesPerWord == 8 and size != 8) {
        *aTypeMask = ~(1 << MemoryOperand);
        *bTypeMask = ~(1 << MemoryOperand);
      } else {
        *bTypeMask = ~(1 << ConstantOperand);
      }
      break;

    case Move:
      if (BytesPerWord == 4 and size == 1) {
        const uint32_t mask
          = (1 << rax) | (1 << rcx) | (1 << rdx) | (1 << rbx);
        *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
        *bRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
      }
      break;

    case Move4To8:
      if (BytesPerWord == 4) {
        const uint32_t mask = ~((1 << rax) | (1 << rdx));
        *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
        *bRegisterMask = (static_cast<uint64_t>(1) << (rdx + 32))
          | (static_cast<uint64_t>(1) << rax);
      }
      break;

    case Multiply:
      if (BytesPerWord == 4 and size == 8) { 
        const uint32_t mask = ~((1 << rax) | (1 << rdx));
        *aRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
        *bRegisterMask = (static_cast<uint64_t>(1) << (rdx + 32)) | mask;
      }
      break;

    case Divide:
      if (BytesPerWord == 4 and size == 8) {
        *bTypeMask = ~0;
        *thunk = true;        
      } else {
        *aRegisterMask = ~((1 << rax) | (1 << rdx));
        *bRegisterMask = 1 << rax;      
      }
      break;

    case Remainder:
      if (BytesPerWord == 4 and size == 8) {
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
      *aTypeMask = (1 << RegisterOperand) | (1 << ConstantOperand);
      *aRegisterMask = (~static_cast<uint64_t>(0) << 32)
        | (static_cast<uint64_t>(1) << rcx);
      const uint32_t mask = ~(1 << rcx);
      *bRegisterMask = (static_cast<uint64_t>(mask) << 32) | mask;
    } break;

    default:
      break;
    }
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
