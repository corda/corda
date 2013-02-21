/* Copyright (c) 2010-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/vm/codegen/assembler.h>
#include <avian/vm/codegen/registers.h>

#include "alloc-vector.h"
#include <avian/util/abort.h>

#include <avian/util/runtime-array.h>

#define CAST1(x) reinterpret_cast<UnaryOperationType>(x)
#define CAST2(x) reinterpret_cast<BinaryOperationType>(x)
#define CAST3(x) reinterpret_cast<TernaryOperationType>(x)
#define CAST_BRANCH(x) reinterpret_cast<BranchOperationType>(x)

using namespace vm;
using namespace avian::codegen;
using namespace avian::util;

namespace local {

namespace isa {
// SYSTEM REGISTERS
const int FPSID = 0x0;
const int FPSCR = 0x1;
const int FPEXC = 0x8;
// INSTRUCTION OPTIONS
enum CONDITION { EQ, NE, CS, CC, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL, NV };
enum SHIFTOP { LSL, LSR, ASR, ROR };
// INSTRUCTION FORMATS
inline int DATA(int cond, int opcode, int S, int Rn, int Rd, int shift, int Sh, int Rm)
{ return cond<<28 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | shift<<7 | Sh<<5 | Rm; }
inline int DATAS(int cond, int opcode, int S, int Rn, int Rd, int Rs, int Sh, int Rm)
{ return cond<<28 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | Rs<<8 | Sh<<5 | 1<<4 | Rm; }
inline int DATAI(int cond, int opcode, int S, int Rn, int Rd, int rot, int imm)
{ return cond<<28 | 1<<25 | opcode<<21 | S<<20 | Rn<<16 | Rd<<12 | rot<<8 | (imm&0xff); }
inline int BRANCH(int cond, int L, int offset)
{ return cond<<28 | 5<<25 | L<<24 | (offset&0xffffff); }
inline int BRANCHX(int cond, int L, int Rm)
{ return cond<<28 | 0x4bffc<<6 | L<<5 | 1<<4 | Rm; }
inline int MULTIPLY(int cond, int mul, int S, int Rd, int Rn, int Rs, int Rm)
{ return cond<<28 | mul<<21 | S<<20 | Rd<<16 | Rn<<12 | Rs<<8 | 9<<4 | Rm; }
inline int XFER(int cond, int P, int U, int B, int W, int L, int Rn, int Rd, int shift, int Sh, int Rm)
{ return cond<<28 | 3<<25 | P<<24 | U<<23 | B<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | shift<<7 | Sh<<5 | Rm; }
inline int XFERI(int cond, int P, int U, int B, int W, int L, int Rn, int Rd, int offset)
{ return cond<<28 | 2<<25 | P<<24 | U<<23 | B<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | (offset&0xfff); }
inline int XFER2(int cond, int P, int U, int W, int L, int Rn, int Rd, int S, int H, int Rm)
{ return cond<<28 | P<<24 | U<<23 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | 1<<7 | S<<6 | H<<5 | 1<<4 | Rm; }
inline int XFER2I(int cond, int P, int U, int W, int L, int Rn, int Rd, int offsetH, int S, int H, int offsetL)
{ return cond<<28 | P<<24 | U<<23 | 1<<22 | W<<21 | L<<20 | Rn<<16 | Rd<<12 | offsetH<<8 | 1<<7 | S<<6 | H<<5 | 1<<4 | (offsetL&0xf); }
inline int COOP(int cond, int opcode_1, int CRn, int CRd, int cp_num, int opcode_2, int CRm)
{ return cond<<28 | 0xe<<24 | opcode_1<<20 | CRn<<16 | CRd<<12 | cp_num<<8 | opcode_2<<5 | CRm; }
inline int COXFER(int cond, int P, int U, int N, int W, int L, int Rn, int CRd, int cp_num, int offset) // offset is in words, not bytes
{ return cond<<28 | 0x6<<25 | P<<24 | U<<23 | N<<22 | W<<21 | L<<20 | Rn<<16 | CRd<<12 | cp_num<<8 | (offset&0xff)>>2; }
inline int COREG(int cond, int opcode_1, int L, int CRn, int Rd, int cp_num, int opcode_2, int CRm)
{ return cond<<28 | 0xe<<24 | opcode_1<<21 | L<<20 | CRn<<16 | Rd<<12 | cp_num<<8 | opcode_2<<5 | 1<<4 | CRm; }
inline int COREG2(int cond, int L, int Rn, int Rd, int cp_num, int opcode, int CRm)
{ return cond<<28 | 0xc4<<20 | L<<20 | Rn<<16 | Rd<<12 | cp_num<<8 | opcode<<4 | CRm;}
// FIELD CALCULATORS
inline int calcU(int imm) { return imm >= 0 ? 1 : 0; }
// INSTRUCTIONS
// The "cond" and "S" fields are set using the SETCOND() and SETS() functions
inline int b(int offset) { return BRANCH(AL, 0, offset); }
inline int bl(int offset) { return BRANCH(AL, 1, offset); }
inline int bx(int Rm) { return BRANCHX(AL, 0, Rm); }
inline int blx(int Rm) { return BRANCHX(AL, 1, Rm); }
inline int and_(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x0, 0, Rn, Rd, shift, Sh, Rm); }
inline int eor(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x1, 0, Rn, Rd, shift, Sh, Rm); }
inline int rsb(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x3, 0, Rn, Rd, shift, Sh, Rm); }
inline int add(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x4, 0, Rn, Rd, shift, Sh, Rm); }
inline int adc(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x5, 0, Rn, Rd, shift, Sh, Rm); }
inline int rsc(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0x7, 0, Rn, Rd, shift, Sh, Rm); }
inline int cmp(int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xa, 1, Rn, 0, shift, Sh, Rm); }
inline int orr(int Rd, int Rn, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xc, 0, Rn, Rd, shift, Sh, Rm); }
inline int mov(int Rd, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xd, 0, 0, Rd, shift, Sh, Rm); }
inline int mvn(int Rd, int Rm, int Sh=0, int shift=0) { return DATA(AL, 0xf, 0, 0, Rd, shift, Sh, Rm); }
inline int andi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x0, 0, Rn, Rd, rot, imm); }
inline int subi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x2, 0, Rn, Rd, rot, imm); }
inline int rsbi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x3, 0, Rn, Rd, rot, imm); }
inline int addi(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x4, 0, Rn, Rd, rot, imm); }
inline int adci(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0x5, 0, Rn, Rd, rot, imm); }
inline int bici(int Rd, int Rn, int imm, int rot=0) { return DATAI(AL, 0xe, 0, Rn, Rd, rot, imm); }
inline int cmpi(int Rn, int imm, int rot=0) { return DATAI(AL, 0xa, 1, Rn, 0, rot, imm); }
inline int movi(int Rd, int imm, int rot=0) { return DATAI(AL, 0xd, 0, 0, Rd, rot, imm); }
inline int orrsh(int Rd, int Rn, int Rm, int Rs, int Sh) { return DATAS(AL, 0xc, 0, Rn, Rd, Rs, Sh, Rm); }
inline int movsh(int Rd, int Rm, int Rs, int Sh) { return DATAS(AL, 0xd, 0, 0, Rd, Rs, Sh, Rm); }
inline int mul(int Rd, int Rm, int Rs) { return MULTIPLY(AL, 0, 0, Rd, 0, Rs, Rm); }
inline int mla(int Rd, int Rm, int Rs, int Rn) { return MULTIPLY(AL, 1, 0, Rd, Rn, Rs, Rm); }
inline int umull(int RdLo, int RdHi, int Rm, int Rs) { return MULTIPLY(AL, 4, 0, RdHi, RdLo, Rs, Rm); }
inline int ldr(int Rd, int Rn, int Rm, int W=0) { return XFER(AL, 1, 1, 0, W, 1, Rn, Rd, 0, 0, Rm); }
inline int ldri(int Rd, int Rn, int imm, int W=0) { return XFERI(AL, 1, calcU(imm), 0, W, 1, Rn, Rd, abs(imm)); }
inline int ldrb(int Rd, int Rn, int Rm) { return XFER(AL, 1, 1, 1, 0, 1, Rn, Rd, 0, 0, Rm); }
inline int ldrbi(int Rd, int Rn, int imm) { return XFERI(AL, 1, calcU(imm), 1, 0, 1, Rn, Rd, abs(imm)); }
inline int str(int Rd, int Rn, int Rm, int W=0) { return XFER(AL, 1, 1, 0, W, 0, Rn, Rd, 0, 0, Rm); }
inline int stri(int Rd, int Rn, int imm, int W=0) { return XFERI(AL, 1, calcU(imm), 0, W, 0, Rn, Rd, abs(imm)); }
inline int strb(int Rd, int Rn, int Rm) { return XFER(AL, 1, 1, 1, 0, 0, Rn, Rd, 0, 0, Rm); }
inline int strbi(int Rd, int Rn, int imm) { return XFERI(AL, 1, calcU(imm), 1, 0, 0, Rn, Rd, abs(imm)); }
inline int ldrh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 0, 1, Rm); }
inline int ldrhi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, calcU(imm), 0, 1, Rn, Rd, abs(imm)>>4 & 0xf, 0, 1, abs(imm)&0xf); }
inline int strh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 0, Rn, Rd, 0, 1, Rm); }
inline int strhi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, calcU(imm), 0, 0, Rn, Rd, abs(imm)>>4 & 0xf, 0, 1, abs(imm)&0xf); }
inline int ldrsh(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 1, 1, Rm); }
inline int ldrshi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, calcU(imm), 0, 1, Rn, Rd, abs(imm)>>4 & 0xf, 1, 1, abs(imm)&0xf); }
inline int ldrsb(int Rd, int Rn, int Rm) { return XFER2(AL, 1, 1, 0, 1, Rn, Rd, 1, 0, Rm); }
inline int ldrsbi(int Rd, int Rn, int imm) { return XFER2I(AL, 1, calcU(imm), 0, 1, Rn, Rd, abs(imm)>>4 & 0xf, 1, 0, abs(imm)&0xf); }
// breakpoint instruction, this really has its own instruction format
inline int bkpt(int16_t immed) { return 0xe1200070 | (((unsigned)immed & 0xffff) >> 4 << 8) | (immed & 0xf); }
// COPROCESSOR INSTRUCTIONS
inline int mcr(int coproc, int opcode_1, int Rd, int CRn, int CRm, int opcode_2=0) { return COREG(AL, opcode_1, 0, CRn, Rd, coproc, opcode_2, CRm); }
inline int mcrr(int coproc, int opcode, int Rd, int Rn, int CRm) { return COREG2(AL, 0, Rn, Rd, coproc, opcode, CRm); }
inline int mrc(int coproc, int opcode_1, int Rd, int CRn, int CRm, int opcode_2=0) { return COREG(AL, opcode_1, 1, CRn, Rd, coproc, opcode_2, CRm); }
inline int mrrc(int coproc, int opcode, int Rd, int Rn, int CRm) { return COREG2(AL, 1, Rn, Rd, coproc, opcode, CRm); }
// VFP FLOATING-POINT INSTRUCTIONS
inline int fmuls(int Sd, int Sn, int Sm) { return COOP(AL, (Sd&1)<<2|2, Sn>>1, Sd>>1, 10, (Sn&1)<<2|(Sm&1), Sm>>1); }
inline int fadds(int Sd, int Sn, int Sm) { return COOP(AL, (Sd&1)<<2|3, Sn>>1, Sd>>1, 10, (Sn&1)<<2|(Sm&1), Sm>>1); }
inline int fsubs(int Sd, int Sn, int Sm) { return COOP(AL, (Sd&1)<<2|3, Sn>>1, Sd>>1, 10, (Sn&1)<<2|(Sm&1)|2, Sm>>1); }
inline int fdivs(int Sd, int Sn, int Sm) { return COOP(AL, (Sd&1)<<2|8, Sn>>1, Sd>>1, 10, (Sn&1)<<2|(Sm&1), Sm>>1); }
inline int fmuld(int Dd, int Dn, int Dm) { return COOP(AL, 2, Dn, Dd, 11, 0, Dm); }
inline int faddd(int Dd, int Dn, int Dm) { return COOP(AL, 3, Dn, Dd, 11, 0, Dm); }
inline int fsubd(int Dd, int Dn, int Dm) { return COOP(AL, 3, Dn, Dd, 11, 2, Dm); }
inline int fdivd(int Dd, int Dn, int Dm) { return COOP(AL, 8, Dn, Dd, 11, 0, Dm); }
inline int fcpys(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 0, Sd>>1, 10, 2|(Sm&1), Sm>>1); }
inline int fabss(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 0, Sd>>1, 10, 6|(Sm&1), Sm>>1); }
inline int fnegs(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 1, Sd>>1, 10, 2|(Sm&1), Sm>>1); }
inline int fsqrts(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 1, Sd>>1, 10, 6|(Sm&1), Sm>>1); }
inline int fcmps(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 4, Sd>>1, 10, 2|(Sm&1), Sm>>1); }
inline int fcvtds(int Dd, int Sm) { return COOP(AL, 0xb, 7, Dd, 10, 6|(Sm&1), Sm>>1); }
inline int fsitos(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 8, Sd>>1, 10, 6|(Sm&1), Sm>>1); }
inline int ftosizs(int Sd, int Sm) { return COOP(AL, 0xb|(Sd&1)<<2, 0xd, Sd>>1, 10, 6|(Sm&1), Sm>>1); }
inline int fcpyd(int Dd, int Dm) { return COOP(AL, 0xb, 0, Dd, 11, 2, Dm); }
inline int fabsd(int Dd, int Dm) { return COOP(AL, 0xb, 0, Dd, 11, 6, Dm); }
inline int fnegd(int Dd, int Dm) { return COOP(AL, 0xb, 1, Dd, 11, 2, Dm); }
inline int fsqrtd(int Dd, int Dm) { return COOP(AL, 0xb, 1, Dd, 11, 6, Dm); }
// double-precision comparison instructions
inline int fcmpd(int Dd, int Dm) { return COOP(AL, 0xb, 4, Dd, 11, 2, Dm); }
// double-precision conversion instructions
inline int fcvtsd(int Sd, int Dm) { return COOP(AL, 0xb|(Sd&1)<<2, 7, Sd>>1, 11, 6, Dm); }
inline int fsitod(int Dd, int Sm) { return COOP(AL, 0xb, 8, Dd, 11, 6|(Sm&1), Sm>>1); }
inline int ftosizd(int Sd, int Dm) { return COOP(AL, 0xb|(Sd&1)<<2, 0xd, Sd>>1, 11, 6, Dm); }
// single load/store instructions for both precision types
inline int flds(int Sd, int Rn, int offset=0) { return COXFER(AL, 1, 1, Sd&1, 0, 1, Rn, Sd>>1, 10, offset); };
inline int fldd(int Dd, int Rn, int offset=0) { return COXFER(AL, 1, 1, 0, 0, 1, Rn, Dd, 11, offset); };
inline int fsts(int Sd, int Rn, int offset=0) { return COXFER(AL, 1, 1, Sd&1, 0, 0, Rn, Sd>>1, 10, offset); };
inline int fstd(int Dd, int Rn, int offset=0) { return COXFER(AL, 1, 1, 0, 0, 0, Rn, Dd, 11, offset); };
// move between GPRs and FPRs
inline int fmsr(int Sn, int Rd) { return mcr(10, 0, Rd, Sn>>1, 0, (Sn&1)<<2); }
inline int fmrs(int Rd, int Sn) { return mrc(10, 0, Rd, Sn>>1, 0, (Sn&1)<<2); }
// move to/from VFP system registers
inline int fmrx(int Rd, int reg) { return mrc(10, 7, Rd, reg, 0); }
// these move around pairs of single-precision registers
inline int fmdrr(int Dm, int Rd, int Rn) { return mcrr(11, 1, Rd, Rn, Dm); }
inline int fmrrd(int Rd, int Rn, int Dm) { return mrrc(11, 1, Rd, Rn, Dm); }
// FLAG SETTERS
inline int SETCOND(int ins, int cond) { return ((ins&0x0fffffff) | (cond<<28)); }
inline int SETS(int ins) { return ins | 1<<20; }
// PSEUDO-INSTRUCTIONS
inline int lsl(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, LSL); }
inline int lsli(int Rd, int Rm, int imm) { return mov(Rd, Rm, LSL, imm); }
inline int lsr(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, LSR); }
inline int lsri(int Rd, int Rm, int imm) { return mov(Rd, Rm, LSR, imm); }
inline int asr(int Rd, int Rm, int Rs) { return movsh(Rd, Rm, Rs, ASR); }
inline int asri(int Rd, int Rm, int imm) { return mov(Rd, Rm, ASR, imm); }
inline int beq(int offset) { return SETCOND(b(offset), EQ); }
inline int bne(int offset) { return SETCOND(b(offset), NE); }
inline int bls(int offset) { return SETCOND(b(offset), LS); }
inline int bhi(int offset) { return SETCOND(b(offset), HI); }
inline int blt(int offset) { return SETCOND(b(offset), LT); }
inline int bgt(int offset) { return SETCOND(b(offset), GT); }
inline int ble(int offset) { return SETCOND(b(offset), LE); }
inline int bge(int offset) { return SETCOND(b(offset), GE); }
inline int blo(int offset) { return SETCOND(b(offset), CC); }
inline int bhs(int offset) { return SETCOND(b(offset), CS); }
inline int bpl(int offset) { return SETCOND(b(offset), PL); }
inline int fmstat() { return fmrx(15, FPSCR); }
// HARDWARE FLAGS
bool vfpSupported() {
  // TODO: Use at runtime detection
#if defined(__ARM_PCS_VFP)  
  // armhf
  return true;
#else
  // armel
  // TODO: allow VFP use for -mfloat-abi=softfp armel builds.
  // GCC -mfloat-abi=softfp flag allows use of VFP while remaining compatible
  // with soft-float code. 
  return false;
#endif
}
}

const uint64_t MASK_LO32 = 0xffffffff;
const unsigned MASK_LO16 = 0xffff;
const unsigned MASK_LO8  = 0xff;
inline unsigned lo8(int64_t i) { return (unsigned)(i&MASK_LO8); }

inline bool isOfWidth(int64_t i, int size) { return static_cast<uint64_t>(i) >> size == 0; }

const int N_GPRS = 16;
const int N_FPRS = 16;
const uint32_t GPR_MASK = 0xffff;
const uint32_t FPR_MASK = 0xffff0000;
// for source-to-destination masks
const uint64_t GPR_MASK64 = GPR_MASK | (uint64_t)GPR_MASK << 32;
// making the following const somehow breaks debug symbol output in GDB
/* const */ uint64_t FPR_MASK64 = FPR_MASK | (uint64_t)FPR_MASK << 32;

const RegisterFile MyRegisterFileWithoutFloats(GPR_MASK, 0);
const RegisterFile MyRegisterFileWithFloats(GPR_MASK, FPR_MASK);

inline bool isFpr(lir::Register* reg) {
  return reg->low >= N_GPRS;
}

inline int fpr64(int reg) { return reg - N_GPRS; }
inline int fpr64(lir::Register* reg) { return fpr64(reg->low); }
inline int fpr32(int reg) { return fpr64(reg) << 1; }
inline int fpr32(lir::Register* reg) { return fpr64(reg) << 1; }

const unsigned FrameHeaderSize = 1;

const unsigned StackAlignmentInBytes = 8;
const unsigned StackAlignmentInWords
= StackAlignmentInBytes / TargetBytesPerWord;

const int ThreadRegister = 8;
const int StackRegister = 13;
const int LinkRegister = 14;
const int ProgramCounter = 15;

const int32_t PoolOffsetMask = 0xFFF;

const bool DebugPool = false;

class Context;
class MyBlock;
class PoolOffset;
class PoolEvent;

void
resolve(MyBlock*);

unsigned
padding(MyBlock*, unsigned);

class MyBlock: public Assembler::Block {
 public:
  MyBlock(Context* context, unsigned offset):
    context(context), next(0), poolOffsetHead(0), poolOffsetTail(0),
    lastPoolOffsetTail(0), poolEventHead(0), poolEventTail(0),
    lastEventOffset(0), offset(offset), start(~0), size(0)
  { }

  virtual unsigned resolve(unsigned start, Assembler::Block* next) {
    this->start = start;
    this->next = static_cast<MyBlock*>(next);

    local::resolve(this);

    return start + size + padding(this, size);
  }

  Context* context;
  MyBlock* next;
  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  PoolOffset* lastPoolOffsetTail;
  PoolEvent* poolEventHead;
  PoolEvent* poolEventTail;
  unsigned lastEventOffset;
  unsigned offset;
  unsigned start;
  unsigned size;
};

class Task;
class ConstantPoolEntry;

class Context {
 public:
  Context(System* s, Allocator* a, Zone* zone):
    s(s), zone(zone), client(0), code(s, a, 1024), tasks(0), result(0),
    firstBlock(new(zone) MyBlock(this, 0)),
    lastBlock(firstBlock), poolOffsetHead(0), poolOffsetTail(0),
    constantPool(0), constantPoolCount(0)
  { }

  System* s;
  Zone* zone;
  Assembler::Client* client;
  Vector code;
  Task* tasks;
  uint8_t* result;
  MyBlock* firstBlock;
  MyBlock* lastBlock;
  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  ConstantPoolEntry* constantPool;
  unsigned constantPoolCount;
};

class Task {
 public:
  Task(Task* next): next(next) { }

  virtual void run(Context* con) = 0;

  Task* next;
};

typedef void (*OperationType)(Context*);

typedef void (*UnaryOperationType)(Context*, unsigned, lir::Operand*);

typedef void (*BinaryOperationType)
(Context*, unsigned, lir::Operand*, unsigned, lir::Operand*);

typedef void (*TernaryOperationType)
(Context*, unsigned, lir::Operand*, lir::Operand*,
 lir::Operand*);

typedef void (*BranchOperationType)
(Context*, lir::TernaryOperation, unsigned, lir::Operand*,
 lir::Operand*, lir::Operand*);

class ArchitectureContext {
 public:
  ArchitectureContext(System* s): s(s) { }

  System* s;
  OperationType operations[lir::OperationCount];
  UnaryOperationType unaryOperations[lir::UnaryOperationCount
                                     * lir::OperandTypeCount];
  BinaryOperationType binaryOperations
  [lir::BinaryOperationCount * lir::OperandTypeCount * lir::OperandTypeCount];
  TernaryOperationType ternaryOperations
  [lir::NonBranchTernaryOperationCount * lir::OperandTypeCount];
  BranchOperationType branchOperations
  [lir::BranchOperationCount * lir::OperandTypeCount * lir::OperandTypeCount];
};

inline Aborter* getAborter(Context* con) {
  return con->s;
}

inline Aborter* getAborter(ArchitectureContext* con) {
  return con->s;
}

class Offset: public Promise {
 public:
  Offset(Context* con, MyBlock* block, unsigned offset, bool forTrace):
    con(con), block(block), offset(offset), forTrace(forTrace)
  { }

  virtual bool resolved() {
    return block->start != static_cast<unsigned>(~0);
  }
  
  virtual int64_t value() {
    assert(con, resolved());

    unsigned o = offset - block->offset;
    return block->start + padding
      (block, forTrace ? o - TargetBytesPerWord : o) + o;
  }

  Context* con;
  MyBlock* block;
  unsigned offset;
  bool forTrace;
};

Promise*
offset(Context* con, bool forTrace = false)
{
  return new(con->zone) Offset(con, con->lastBlock, con->code.length(), forTrace);
}

bool
bounded(int right, int left, int32_t v)
{
  return ((v << left) >> left) == v and ((v >> right) << right) == v;
}

void*
updateOffset(System* s, uint8_t* instruction, int64_t value)
{
  // ARM's PC is two words ahead, and branches drop the bottom 2 bits.
  int32_t v = (reinterpret_cast<uint8_t*>(value) - (instruction + 8)) >> 2;

  int32_t mask;
  expect(s, bounded(0, 8, v));
  mask = 0xFFFFFF;

  int32_t* p = reinterpret_cast<int32_t*>(instruction);
  *p = (v & mask) | ((~mask) & *p);

  return instruction + 4;
}

class OffsetListener: public Promise::Listener {
 public:
  OffsetListener(System* s, uint8_t* instruction):
    s(s),
    instruction(instruction)
  { }

  virtual bool resolve(int64_t value, void** location) {
    void* p = updateOffset(s, instruction, value);
    if (location) *location = p;
    return false;
  }

  System* s;
  uint8_t* instruction;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset):
    Task(next),
    promise(promise),
    instructionOffset(instructionOffset)
  { }

  virtual void run(Context* con) {
    if (promise->resolved()) {
      updateOffset
        (con->s, con->result + instructionOffset->value(), promise->value());
    } else {
      new (promise->listen(sizeof(OffsetListener)))
        OffsetListener(con->s, con->result + instructionOffset->value());
    }
  }

  Promise* promise;
  Promise* instructionOffset;
};

void
appendOffsetTask(Context* con, Promise* promise, Promise* instructionOffset)
{
  con->tasks = new(con->zone) OffsetTask(con->tasks, promise, instructionOffset);
}

inline unsigned
index(ArchitectureContext*, lir::UnaryOperation operation, lir::OperandType operand)
{
  return operation + (lir::UnaryOperationCount * operand);
}

inline unsigned
index(ArchitectureContext*,
      lir::BinaryOperation operation,
      lir::OperandType operand1,
      lir::OperandType operand2)
{
  return operation
    + (lir::BinaryOperationCount * operand1)
    + (lir::BinaryOperationCount * lir::OperandTypeCount * operand2);
}

inline unsigned
index(ArchitectureContext* con UNUSED,
      lir::TernaryOperation operation,
      lir::OperandType operand1)
{
  assert(con, not isBranch(operation));

  return operation + (lir::NonBranchTernaryOperationCount * operand1);
}

unsigned
branchIndex(ArchitectureContext* con UNUSED, lir::OperandType operand1,
            lir::OperandType operand2)
{
  return operand1 + (lir::OperandTypeCount * operand2);
}

// BEGIN OPERATION COMPILERS

using namespace isa;

// shortcut functions
inline void emit(Context* con, int code) { con->code.append4(code); }

inline int newTemp(Context* con) {
  return con->client->acquireTemporary(GPR_MASK);
}

inline int newTemp(Context* con, unsigned mask) {
  return con->client->acquireTemporary(mask);
}

inline void freeTemp(Context* con, int r) {
  con->client->releaseTemporary(r);
}

inline int64_t getValue(lir::Constant* con) {
  return con->value->value();
}

inline lir::Register makeTemp(Context* con) {
  lir::Register tmp(newTemp(con));
  return tmp;
}

inline lir::Register makeTemp64(Context* con) {
  lir::Register tmp(newTemp(con), newTemp(con));
  return tmp;
}

inline void freeTemp(Context* con, const lir::Register& tmp) {
  if (tmp.low != lir::NoRegister) freeTemp(con, tmp.low);
  if (tmp.high != lir::NoRegister) freeTemp(con, tmp.high);
}

inline void
write4(uint8_t* dst, uint32_t v)
{
  memcpy(dst, &v, 4);
}

void
andC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void shiftLeftR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t)
{
  if (size == 8) {
    int tmp1 = newTemp(con), tmp2 = newTemp(con), tmp3 = newTemp(con);
    ResolvedPromise maskPromise(0x3F);
    lir::Constant mask(&maskPromise);
    lir::Register dst(tmp3);
    andC(con, 4, &mask, a, &dst);
    emit(con, lsl(tmp1, b->high, tmp3));
    emit(con, rsbi(tmp2, tmp3, 32));
    emit(con, orrsh(tmp1, tmp1, b->low, tmp2, LSR));
    emit(con, SETS(subi(t->high, tmp3, 32)));
    emit(con, SETCOND(mov(t->high, tmp1), MI));
    emit(con, SETCOND(lsl(t->high, b->low, t->high), PL));
    emit(con, lsl(t->low, b->low, tmp3));
    freeTemp(con, tmp1); freeTemp(con, tmp2); freeTemp(con, tmp3);
  } else {
    int tmp = newTemp(con);
    ResolvedPromise maskPromise(0x1F);
    lir::Constant mask(&maskPromise);
    lir::Register dst(tmp);
    andC(con, size, &mask, a, &dst);
    emit(con, lsl(t->low, b->low, tmp));
    freeTemp(con, tmp);
  }
}

void
moveRR(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void shiftLeftC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t)
{
  assert(con, size == TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, lsli(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void shiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t)
{
  if (size == 8) {
    int tmp1 = newTemp(con), tmp2 = newTemp(con), tmp3 = newTemp(con);
    ResolvedPromise maskPromise(0x3F);
    lir::Constant mask(&maskPromise);
    lir::Register dst(tmp3);
    andC(con, 4, &mask, a, &dst);
    emit(con, lsr(tmp1, b->low, tmp3));
    emit(con, rsbi(tmp2, tmp3, 32));
    emit(con, orrsh(tmp1, tmp1, b->high, tmp2, LSL));
    emit(con, SETS(subi(t->low, tmp3, 32)));
    emit(con, SETCOND(mov(t->low, tmp1), MI));
    emit(con, SETCOND(asr(t->low, b->high, t->low), PL));
    emit(con, asr(t->high, b->high, tmp3));
    freeTemp(con, tmp1); freeTemp(con, tmp2); freeTemp(con, tmp3);
  } else {
    int tmp = newTemp(con);
    ResolvedPromise maskPromise(0x1F);
    lir::Constant mask(&maskPromise);
    lir::Register dst(tmp);
    andC(con, size, &mask, a, &dst);
    emit(con, asr(t->low, b->low, tmp));
    freeTemp(con, tmp);
  }
}

void shiftRightC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t)
{
  assert(con, size == TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, asri(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void unsignedShiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t)
{
  int tmpShift = newTemp(con);
  ResolvedPromise maskPromise(size == 8 ? 0x3F : 0x1F);
  lir::Constant mask(&maskPromise);
  lir::Register dst(tmpShift);
  andC(con, 4, &mask, a, &dst);
  emit(con, lsr(t->low, b->low, tmpShift));
  if (size == 8) {
    int tmpHi = newTemp(con), tmpLo = newTemp(con);
    emit(con, SETS(rsbi(tmpHi, tmpShift, 32)));
    emit(con, lsl(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, addi(tmpHi, tmpShift, -32));
    emit(con, lsr(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, lsr(t->high, b->high, tmpShift));
    freeTemp(con, tmpHi); freeTemp(con, tmpLo);
  }
  freeTemp(con, tmpShift);
}

void unsignedShiftRightC(Context* con, unsigned size UNUSED, lir::Constant* a, lir::Register* b, lir::Register* t)
{
  assert(con, size == TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, lsri(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

class ConstantPoolEntry: public Promise {
 public:
  ConstantPoolEntry(Context* con, Promise* constant, ConstantPoolEntry* next,
                    Promise* callOffset):
    con(con), constant(constant), next(next), callOffset(callOffset),
    address(0)
  { }

  virtual int64_t value() {
    assert(con, resolved());

    return reinterpret_cast<int64_t>(address);
  }

  virtual bool resolved() {
    return address != 0;
  }

  Context* con;
  Promise* constant;
  ConstantPoolEntry* next;
  Promise* callOffset;
  void* address;
  unsigned constantPoolCount;
};

class ConstantPoolListener: public Promise::Listener {
 public:
  ConstantPoolListener(System* s, target_uintptr_t* address,
                       uint8_t* returnAddress):
    s(s),
    address(address),
    returnAddress(returnAddress)
  { }

  virtual bool resolve(int64_t value, void** location) {
    *address = value;
    if (location) {
      *location = returnAddress ? static_cast<void*>(returnAddress) : address;
    }
    return true;
  }

  System* s;
  target_uintptr_t* address;
  uint8_t* returnAddress;
};

class PoolOffset {
 public:
  PoolOffset(MyBlock* block, ConstantPoolEntry* entry, unsigned offset):
    block(block), entry(entry), next(0), offset(offset)
  { }

  MyBlock* block;
  ConstantPoolEntry* entry;
  PoolOffset* next;
  unsigned offset;
};

class PoolEvent {
 public:
  PoolEvent(PoolOffset* poolOffsetHead, PoolOffset* poolOffsetTail,
            unsigned offset):
    poolOffsetHead(poolOffsetHead), poolOffsetTail(poolOffsetTail), next(0),
    offset(offset)
  { }

  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  PoolEvent* next;
  unsigned offset;
};

void
appendConstantPoolEntry(Context* con, Promise* constant, Promise* callOffset)
{
  if (constant->resolved()) {
    // make a copy, since the original might be allocated on the
    // stack, and we need our copy to live until assembly is complete
    constant = new(con->zone) ResolvedPromise(constant->value());
  }

  con->constantPool = new(con->zone) ConstantPoolEntry(con, constant, con->constantPool, callOffset);

  ++ con->constantPoolCount;

  PoolOffset* o = new(con->zone) PoolOffset(con->lastBlock, con->constantPool, con->code.length() - con->lastBlock->offset);

  if (DebugPool) {
    fprintf(stderr, "add pool offset %p %d to block %p\n",
            o, o->offset, con->lastBlock);
  }

  if (con->lastBlock->poolOffsetTail) {
    con->lastBlock->poolOffsetTail->next = o;
  } else {
    con->lastBlock->poolOffsetHead = o;
  }
  con->lastBlock->poolOffsetTail = o;
}

void
appendPoolEvent(Context* con, MyBlock* b, unsigned offset, PoolOffset* head,
                PoolOffset* tail)
{
  PoolEvent* e = new(con->zone) PoolEvent(head, tail, offset);

  if (b->poolEventTail) {
    b->poolEventTail->next = e;
  } else {
    b->poolEventHead = e;
  }
  b->poolEventTail = e;
}

bool
needJump(MyBlock* b)
{
  return b->next or b->size != (b->size & PoolOffsetMask);
}

unsigned
padding(MyBlock* b, unsigned offset)
{
  unsigned total = 0;
  for (PoolEvent* e = b->poolEventHead; e; e = e->next) {
    if (e->offset <= offset) {
      if (needJump(b)) {
        total += TargetBytesPerWord;
      }
      for (PoolOffset* o = e->poolOffsetHead; o; o = o->next) {
        total += TargetBytesPerWord;
      }
    } else {
      break;
    }
  }
  return total;
}

void
resolve(MyBlock* b)
{
  Context* con = b->context;

  if (b->poolOffsetHead) {
    if (con->poolOffsetTail) {
      con->poolOffsetTail->next = b->poolOffsetHead;
    } else {
      con->poolOffsetHead = b->poolOffsetHead;
    }
    con->poolOffsetTail = b->poolOffsetTail;
  }

  if (con->poolOffsetHead) {
    bool append;
    if (b->next == 0 or b->next->poolEventHead) {
      append = true;
    } else {
      int32_t v = (b->start + b->size + b->next->size + TargetBytesPerWord - 8)
        - (con->poolOffsetHead->offset + con->poolOffsetHead->block->start);

      append = (v != (v & PoolOffsetMask));

      if (DebugPool) {
        fprintf(stderr,
                "current %p %d %d next %p %d %d\n",
                b, b->start, b->size, b->next, b->start + b->size,
                b->next->size);
        fprintf(stderr,
                "offset %p %d is of distance %d to next block; append? %d\n",
                con->poolOffsetHead, con->poolOffsetHead->offset, v, append);
      }
    }

    if (append) {
#ifndef NDEBUG
      int32_t v = (b->start + b->size - 8)
        - (con->poolOffsetHead->offset + con->poolOffsetHead->block->start);
      
      expect(con, v == (v & PoolOffsetMask));
#endif // not NDEBUG

      appendPoolEvent(con, b, b->size, con->poolOffsetHead, con->poolOffsetTail);

      if (DebugPool) {
        for (PoolOffset* o = con->poolOffsetHead; o; o = o->next) {
          fprintf(stderr,
                  "include %p %d in pool event %p at offset %d in block %p\n",
                  o, o->offset, b->poolEventTail, b->size, b);
        }
      }

      con->poolOffsetHead = 0;
      con->poolOffsetTail = 0;
    }
  }
}

void
jumpR(Context* con, unsigned size UNUSED, lir::Register* target)
{
  assert(con, size == TargetBytesPerWord);
  emit(con, bx(target->low));
}

void
swapRR(Context* con, unsigned aSize, lir::Register* a,
       unsigned bSize, lir::Register* b)
{
  assert(con, aSize == TargetBytesPerWord);
  assert(con, bSize == TargetBytesPerWord);

  lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
  moveRR(con, aSize, a, bSize, &tmp);
  moveRR(con, bSize, b, aSize, a);
  moveRR(con, bSize, &tmp, bSize, b);
  con->client->releaseTemporary(tmp.low);
}

void
moveRR(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst)
{
  bool srcIsFpr = isFpr(src);
  bool dstIsFpr = isFpr(dst);
  if (srcIsFpr || dstIsFpr) {   // FPR(s) involved
    assert(con, srcSize == dstSize);
    const bool dprec = srcSize == 8;
    if (srcIsFpr && dstIsFpr) { // FPR to FPR
      if (dprec) emit(con, fcpyd(fpr64(dst), fpr64(src))); // double
      else       emit(con, fcpys(fpr32(dst), fpr32(src))); // single
    } else if (srcIsFpr) {      // FPR to GPR
      if (dprec) emit(con, fmrrd(dst->low, dst->high, fpr64(src)));
      else       emit(con, fmrs(dst->low, fpr32(src)));
    } else {                    // GPR to FPR
      if (dprec) emit(con, fmdrr(fpr64(dst->low), src->low, src->high));
      else       emit(con, fmsr(fpr32(dst), src->low));
    }
    return;
  }

  switch (srcSize) {
  case 1:
    emit(con, lsli(dst->low, src->low, 24));
    emit(con, asri(dst->low, dst->low, 24));
    break;

  case 2:
    emit(con, lsli(dst->low, src->low, 16));
    emit(con, asri(dst->low, dst->low, 16));
    break;

  case 4:
  case 8:
    if (srcSize == 4 and dstSize == 8) {
      moveRR(con, 4, src, 4, dst);
      emit(con, asri(dst->high, src->low, 31));
    } else if (srcSize == 8 and dstSize == 8) {
      lir::Register srcHigh(src->high);
      lir::Register dstHigh(dst->high);

      if (src->high == dst->low) {
        if (src->low == dst->high) {
          swapRR(con, 4, src, 4, dst);
        } else {
          moveRR(con, 4, &srcHigh, 4, &dstHigh);
          moveRR(con, 4, src, 4, dst);
        }
      } else {
        moveRR(con, 4, src, 4, dst);
        moveRR(con, 4, &srcHigh, 4, &dstHigh);
      }
    } else if (src->low != dst->low) {
      emit(con, mov(dst->low, src->low));
    }
    break;

  default: abort(con);
  }
}

void
moveZRR(Context* con, unsigned srcSize, lir::Register* src,
        unsigned, lir::Register* dst)
{
  switch (srcSize) {
  case 2:
    emit(con, lsli(dst->low, src->low, 16));
    emit(con, lsri(dst->low, dst->low, 16));
    break;

  default: abort(con);
  }
}

void moveCR(Context* con, unsigned size, lir::Constant* src,
            unsigned, lir::Register* dst);

void
moveCR2(Context* con, unsigned size, lir::Constant* src,
        lir::Register* dst, Promise* callOffset)
{
  if (isFpr(dst)) { // floating-point
    lir::Register tmp = size > 4 ? makeTemp64(con) :
                                         makeTemp(con);
    moveCR(con, size, src, size, &tmp);
    moveRR(con, size, &tmp, size, dst);
    freeTemp(con, tmp);
  } else if (size > 4) { 
    uint64_t value = (uint64_t)src->value->value();
    ResolvedPromise loBits(value & MASK_LO32);
    lir::Constant srcLo(&loBits);
    ResolvedPromise hiBits(value >> 32); 
    lir::Constant srcHi(&hiBits);
    lir::Register dstHi(dst->high);
    moveCR(con, 4, &srcLo, 4, dst);
    moveCR(con, 4, &srcHi, 4, &dstHi);
  } else if (src->value->resolved() and isOfWidth(getValue(src), 8)) {
    emit(con, movi(dst->low, lo8(getValue(src)))); // fits in immediate
  } else {
    appendConstantPoolEntry(con, src->value, callOffset);
    emit(con, ldri(dst->low, ProgramCounter, 0)); // load 32 bits
  }
}

void
moveCR(Context* con, unsigned size, lir::Constant* src,
       unsigned, lir::Register* dst)
{
  moveCR2(con, size, src, dst, 0);
}

void addR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    emit(con, SETS(add(t->low, a->low, b->low)));
    emit(con, adc(t->high, a->high, b->high));
  } else {
    emit(con, add(t->low, a->low, b->low));
  }
}

void subR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    emit(con, SETS(rsb(t->low, a->low, b->low)));
    emit(con, rsc(t->high, a->high, b->high));
  } else {
    emit(con, rsb(t->low, a->low, b->low));
  }
}

void
addC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst)
{
  assert(con, size == TargetBytesPerWord);

  int32_t v = a->value->value();
  if (v) {
    if (v > 0 and v < 256) {
      emit(con, addi(dst->low, b->low, v));
    } else if (v > 0 and v < 1024 and v % 4 == 0) {
      emit(con, addi(dst->low, b->low, v >> 2, 15));
    } else {
      // todo
      abort(con);
    }
  } else {
    moveRR(con, size, b, size, dst);
  }
}

void
subC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst)
{
  assert(con, size == TargetBytesPerWord);

  int32_t v = a->value->value();
  if (v) {
    if (v > 0 and v < 256) {
      emit(con, subi(dst->low, b->low, v));
    } else if (v > 0 and v < 1024 and v % 4 == 0) {
      emit(con, subi(dst->low, b->low, v >> 2, 15));
    } else {
      // todo
      abort(con);
    }
  } else {
    moveRR(con, size, b, size, dst);
  }
}

void multiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    bool useTemporaries = b->low == t->low;
    int tmpLow  = useTemporaries ? con->client->acquireTemporary(GPR_MASK) : t->low;
    int tmpHigh = useTemporaries ? con->client->acquireTemporary(GPR_MASK) : t->high;

    emit(con, umull(tmpLow, tmpHigh, a->low, b->low));
    emit(con, mla(tmpHigh, a->low, b->high, tmpHigh));
    emit(con, mla(tmpHigh, a->high, b->low, tmpHigh));

    if (useTemporaries) {
      emit(con, mov(t->low, tmpLow));
      emit(con, mov(t->high, tmpHigh));
      con->client->releaseTemporary(tmpLow);
      con->client->releaseTemporary(tmpHigh);
    }
  } else {
    emit(con, mul(t->low, a->low, b->low));
  }
}

void floatAbsoluteRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b) {
  if (size == 8) {
    emit(con, fabsd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fabss(fpr32(b), fpr32(a)));
  }
}

void floatNegateRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b) {
  if (size == 8) {
    emit(con, fnegd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fnegs(fpr32(b), fpr32(a)));
  }
}

void float2FloatRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b) {
  if (size == 8) {
    emit(con, fcvtsd(fpr32(b), fpr64(a)));
  } else {
    emit(con, fcvtds(fpr64(b), fpr32(a)));
  }
}

void float2IntRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b) {
  int tmp = newTemp(con, FPR_MASK);
  int ftmp = fpr32(tmp);
  if (size == 8) { // double to int
    emit(con, ftosizd(ftmp, fpr64(a)));
  } else {         // float to int
    emit(con, ftosizs(ftmp, fpr32(a)));
  }                // else thunked
  emit(con, fmrs(b->low, ftmp));
  freeTemp(con, tmp);
}

void int2FloatRR(Context* con, unsigned, lir::Register* a, unsigned size, lir::Register* b) {
  emit(con, fmsr(fpr32(b), a->low));
  if (size == 8) { // int to double
    emit(con, fsitod(fpr64(b), fpr32(b)));
  } else {         // int to float
    emit(con, fsitos(fpr32(b), fpr32(b)));
  }                // else thunked
}

void floatSqrtRR(Context* con, unsigned size, lir::Register* a, unsigned, lir::Register* b) {
  if (size == 8) {
    emit(con, fsqrtd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fsqrts(fpr32(b), fpr32(a)));
  }
}

void floatAddR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    emit(con, faddd(fpr64(t), fpr64(a), fpr64(b)));
  } else {
    emit(con, fadds(fpr32(t), fpr32(a), fpr32(b)));
  }
}

void floatSubtractR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    emit(con, fsubd(fpr64(t), fpr64(b), fpr64(a)));
  } else {
    emit(con, fsubs(fpr32(t), fpr32(b), fpr32(a)));
  }
}

void floatMultiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) {
    emit(con, fmuld(fpr64(t), fpr64(a), fpr64(b)));
  } else {
    emit(con, fmuls(fpr32(t), fpr32(a), fpr32(b)));
  }
}

void floatDivideR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if (size == 8) { 
    emit(con, fdivd(fpr64(t), fpr64(b), fpr64(a)));
  } else {
    emit(con, fdivs(fpr32(t), fpr32(b), fpr32(a)));
  }
}

int
normalize(Context* con, int offset, int index, unsigned scale, 
          bool* preserveIndex, bool* release)
{
  if (offset != 0 or scale != 1) {
    lir::Register normalizedIndex
      (*preserveIndex ? con->client->acquireTemporary(GPR_MASK) : index);
    
    if (*preserveIndex) {
      *release = true;
      *preserveIndex = false;
    } else {
      *release = false;
    }

    int scaled;

    if (scale != 1) {
      lir::Register unscaledIndex(index);

      ResolvedPromise scalePromise(log(scale));
      lir::Constant scaleConstant(&scalePromise);
      
      shiftLeftC(con, TargetBytesPerWord, &scaleConstant,
                 &unscaledIndex, &normalizedIndex);

      scaled = normalizedIndex.low;
    } else {
      scaled = index;
    }

    if (offset != 0) {
      lir::Register untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      lir::Constant offsetConstant(&offsetPromise);

      lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
      moveCR(con, TargetBytesPerWord, &offsetConstant, TargetBytesPerWord, &tmp);
      addR(con, TargetBytesPerWord, &tmp, &untranslatedIndex, &normalizedIndex);
      con->client->releaseTemporary(tmp.low);
    }

    return normalizedIndex.low;
  } else {
    *release = false;
    return index;
  }
}

void
store(Context* con, unsigned size, lir::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex)
{
  if (index != lir::NoRegister) {
    bool release;
    int normalized = normalize
      (con, offset, index, scale, &preserveIndex, &release);

    if (!isFpr(src)) { // GPR store
      switch (size) {
      case 1:
        emit(con, strb(src->low, base, normalized));
        break;

      case 2:
        emit(con, strh(src->low, base, normalized));
        break;

      case 4:
        emit(con, str(src->low, base, normalized));
        break;

      case 8: { // split into 2 32-bit stores
        lir::Register srcHigh(src->high);
        store(con, 4, &srcHigh, base, 0, normalized, 1, preserveIndex);
        store(con, 4, src, base, 4, normalized, 1, preserveIndex);
      } break;

      default: abort(con);
      }
    } else { // FPR store
      lir::Register base_(base),
                          normalized_(normalized),
                          absAddr = makeTemp(con);
      // FPR stores have only bases, so we must add the index
      addR(con, TargetBytesPerWord, &base_, &normalized_, &absAddr);
      // double-precision
      if (size == 8) emit(con, fstd(fpr64(src), absAddr.low));
      // single-precision
      else           emit(con, fsts(fpr32(src), absAddr.low));
      freeTemp(con, absAddr);
    }

    if (release) con->client->releaseTemporary(normalized);
  } else if (size == 8
             or abs(offset) == (abs(offset) & 0xFF)
             or (size != 2 and abs(offset) == (abs(offset) & 0xFFF)))
  {
    if (!isFpr(src)) { // GPR store
      switch (size) {
      case 1:
        emit(con, strbi(src->low, base, offset));
        break;

      case 2:
        emit(con, strhi(src->low, base, offset));
        break;

      case 4:
        emit(con, stri(src->low, base, offset));
        break;

      case 8: { // split into 2 32-bit stores
        lir::Register srcHigh(src->high);
        store(con, 4, &srcHigh, base, offset, lir::NoRegister, 1, false);
        store(con, 4, src, base, offset + 4, lir::NoRegister, 1, false);
      } break;

      default: abort(con);
      }
    } else { // FPR store
      // double-precision
      if (size == 8) emit(con, fstd(fpr64(src), base, offset));
      // single-precision
      else           emit(con, fsts(fpr32(src), base, offset));
    }
  } else {
    lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(con, TargetBytesPerWord, &offsetConstant,
           TargetBytesPerWord, &tmp);
    
    store(con, size, src, base, 0, tmp.low, 1, false);

    con->client->releaseTemporary(tmp.low);
  }
}

void
moveRM(Context* con, unsigned srcSize, lir::Register* src,
       unsigned dstSize UNUSED, lir::Memory* dst)
{
  assert(con, srcSize == dstSize);

  store(con, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
}

void
load(Context* con, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, lir::Register* dst,
     bool preserveIndex, bool signExtend)
{
  if (index != lir::NoRegister) {
    bool release;
    int normalized = normalize
      (con, offset, index, scale, &preserveIndex, &release);

    if (!isFpr(dst)) { // GPR load
      switch (srcSize) {
      case 1:
        if (signExtend) {
          emit(con, ldrsb(dst->low, base, normalized));
        } else {
          emit(con, ldrb(dst->low, base, normalized));
        }
        break;

      case 2:
        if (signExtend) {
          emit(con, ldrsh(dst->low, base, normalized));
        } else {
          emit(con, ldrh(dst->low, base, normalized));
        }
        break;

      case 4:
      case 8: {
        if (srcSize == 4 and dstSize == 8) {
          load(con, 4, base, 0, normalized, 1, 4, dst, preserveIndex,
               false);
          moveRR(con, 4, dst, 8, dst);
        } else if (srcSize == 8 and dstSize == 8) {
          lir::Register dstHigh(dst->high);
          load(con, 4, base, 0, normalized, 1, 4, &dstHigh,
              preserveIndex, false);
          load(con, 4, base, 4, normalized, 1, 4, dst, preserveIndex,
               false);
        } else {
          emit(con, ldr(dst->low, base, normalized));
        }
      } break;

      default: abort(con);
      }
    } else { // FPR load
      lir::Register base_(base),
                          normalized_(normalized),
                          absAddr = makeTemp(con);
      // VFP loads only have bases, so we must add the index
      addR(con, TargetBytesPerWord, &base_, &normalized_, &absAddr);
      // double-precision
      if (srcSize == 8) emit(con, fldd(fpr64(dst), absAddr.low));
      // single-precision
      else              emit(con, flds(fpr32(dst), absAddr.low));
      freeTemp(con, absAddr);
    }

    if (release) con->client->releaseTemporary(normalized);
  } else if ((srcSize == 8 and dstSize == 8)
             or abs(offset) == (abs(offset) & 0xFF)
             or (srcSize != 2
                 and (srcSize != 1 or not signExtend)
                 and abs(offset) == (abs(offset) & 0xFFF)))
  {
    if (!isFpr(dst)) { // GPR load
      switch (srcSize) {
      case 1:
        if (signExtend) {
          emit(con, ldrsbi(dst->low, base, offset));
        } else {
          emit(con, ldrbi(dst->low, base, offset));
        }
        break;

      case 2:
        if (signExtend) {
          emit(con, ldrshi(dst->low, base, offset));
        } else {
          emit(con, ldrhi(dst->low, base, offset));
        }
        break;

      case 4:
        emit(con, ldri(dst->low, base, offset));
        break;

      case 8: {
        if (dstSize == 8) {
          lir::Register dstHigh(dst->high);
          load(con, 4, base, offset, lir::NoRegister, 1, 4, &dstHigh, false,
               false);
          load(con, 4, base, offset + 4, lir::NoRegister, 1, 4, dst, false,
               false);
        } else {
          emit(con, ldri(dst->low, base, offset));
        }
      } break;

      default: abort(con);
      }
    } else { // FPR load
      // double-precision
      if (srcSize == 8) emit(con, fldd(fpr64(dst), base, offset));
      // single-precision
      else              emit(con, flds(fpr32(dst), base, offset));
    }
  } else {
    lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(con, TargetBytesPerWord, &offsetConstant, TargetBytesPerWord,
           &tmp);
    
    load(con, srcSize, base, 0, tmp.low, 1, dstSize, dst, false,
         signExtend);

    con->client->releaseTemporary(tmp.low);
  }
}

void
moveMR(Context* con, unsigned srcSize, lir::Memory* src,
       unsigned dstSize, lir::Register* dst)
{
  load(con, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true, true);
}

void
moveZMR(Context* con, unsigned srcSize, lir::Memory* src,
        unsigned dstSize, lir::Register* dst)
{
  load(con, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true, false);
}

void
andR(Context* con, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst)
{
  if (size == 8) emit(con, and_(dst->high, a->high, b->high));
  emit(con, and_(dst->low, a->low, b->low));
}

void
andC(Context* con, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst)
{
  int64_t v = a->value->value();

  if (size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::Register bh(b->high);
    lir::Register dh(dst->high);

    andC(con, 4, &al, b, dst);
    andC(con, 4, &ah, &bh, &dh);
  } else {
    uint32_t v32 = static_cast<uint32_t>(v);
    if (v32 != 0xFFFFFFFF) {
      if ((v32 & 0xFFFFFF00) == 0xFFFFFF00) {
        emit(con, bici(dst->low, b->low, (~(v32 & 0xFF)) & 0xFF));
      } else if ((v32 & 0xFFFFFF00) == 0) {
        emit(con, andi(dst->low, b->low, v32 & 0xFF));
      } else {
        // todo: there are other cases we can handle in one
        // instruction

        bool useTemporary = b->low == dst->low;
        lir::Register tmp(dst->low);
        if (useTemporary) {
          tmp.low = con->client->acquireTemporary(GPR_MASK);
        }

        moveCR(con, 4, a, 4, &tmp);
        andR(con, 4, b, &tmp, dst);
        
        if (useTemporary) {
          con->client->releaseTemporary(tmp.low);
        }
      }
    } else {
      moveRR(con, size, b, size, dst);
    }
  }
}

void
orR(Context* con, unsigned size, lir::Register* a,
    lir::Register* b, lir::Register* dst)
{
  if (size == 8) emit(con, orr(dst->high, a->high, b->high));
  emit(con, orr(dst->low, a->low, b->low));
}

void
xorR(Context* con, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst)
{
  if (size == 8) emit(con, eor(dst->high, a->high, b->high));
  emit(con, eor(dst->low, a->low, b->low));
}

void
moveAR2(Context* con, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst)
{
  assert(con, srcSize == 4 and dstSize == 4);

  lir::Constant constant(src->address);
  moveCR(con, srcSize, &constant, dstSize, dst);

  lir::Memory memory(dst->low, 0, -1, 0);
  moveMR(con, dstSize, &memory, dstSize, dst);
}

void
moveAR(Context* con, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst)
{
  moveAR2(con, srcSize, src, dstSize, dst);
}

void
compareRR(Context* con, unsigned aSize, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b)
{
  assert(con, !(isFpr(a) ^ isFpr(b))); // regs must be of the same type

  if (!isFpr(a)) { // GPR compare
    assert(con, aSize == 4 && bSize == 4);
    /**///assert(con, b->low != a->low);
    emit(con, cmp(b->low, a->low));
  } else {         // FPR compare
    assert(con, aSize == bSize);
    if (aSize == 8) emit(con, fcmpd(fpr64(b), fpr64(a))); // double
    else            emit(con, fcmps(fpr32(b), fpr32(a))); // single
    emit(con, fmstat());
  }
}

void
compareCR(Context* con, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b)
{
  assert(con, aSize == 4 and bSize == 4);

  if (!isFpr(b) && a->value->resolved() &&
      isOfWidth(a->value->value(), 8)) {
    emit(con, cmpi(b->low, a->value->value()));
  } else {
    lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
    moveCR(con, aSize, a, bSize, &tmp);
    compareRR(con, bSize, &tmp, bSize, b);
    con->client->releaseTemporary(tmp.low);
  }
}

void
compareCM(Context* con, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Memory* b)
{
  assert(con, aSize == 4 and bSize == 4);

  lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
  moveMR(con, bSize, b, bSize, &tmp);
  compareCR(con, aSize, a, bSize, &tmp);
  con->client->releaseTemporary(tmp.low);
}

void
compareRM(Context* con, unsigned aSize, lir::Register* a,
          unsigned bSize, lir::Memory* b)
{
  assert(con, aSize == 4 and bSize == 4);

  lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
  moveMR(con, bSize, b, bSize, &tmp);
  compareRR(con, aSize, a, bSize, &tmp);
  con->client->releaseTemporary(tmp.low);
}

int32_t
branch(Context* con, lir::TernaryOperation op)
{
  switch (op) {
  case lir::JumpIfEqual:
  case lir::JumpIfFloatEqual:
    return beq(0);

  case lir::JumpIfNotEqual:
  case lir::JumpIfFloatNotEqual:
    return bne(0);

  case lir::JumpIfLess:
  case lir::JumpIfFloatLess:
  case lir::JumpIfFloatLessOrUnordered:
    return blt(0);

  case lir::JumpIfGreater:
  case lir::JumpIfFloatGreater:
    return bgt(0);

  case lir::JumpIfLessOrEqual:
  case lir::JumpIfFloatLessOrEqual:
  case lir::JumpIfFloatLessOrEqualOrUnordered:
    return ble(0);

  case lir::JumpIfGreaterOrEqual:
  case lir::JumpIfFloatGreaterOrEqual:
    return bge(0);

  case lir::JumpIfFloatGreaterOrUnordered:
    return bhi(0);

  case lir::JumpIfFloatGreaterOrEqualOrUnordered:
    return bpl(0);
 
  default:
    abort(con);
  }
}

void
conditional(Context* con, int32_t branch, lir::Constant* target)
{
  appendOffsetTask(con, target->value, offset(con));
  emit(con, branch);
}

void
branch(Context* con, lir::TernaryOperation op, lir::Constant* target)
{
  conditional(con, branch(con, op), target);
}

void
branchLong(Context* con, lir::TernaryOperation op, lir::Operand* al,
           lir::Operand* ah, lir::Operand* bl,
           lir::Operand* bh, lir::Constant* target,
           BinaryOperationType compareSigned,
           BinaryOperationType compareUnsigned)
{
  compareSigned(con, 4, ah, 4, bh);

  unsigned next = 0;
  
  switch (op) {
  case lir::JumpIfEqual:
  case lir::JumpIfFloatEqual:
    next = con->code.length();
    emit(con, bne(0));

    compareSigned(con, 4, al, 4, bl);
    conditional(con, beq(0), target);
    break;

  case lir::JumpIfNotEqual:
  case lir::JumpIfFloatNotEqual:
    conditional(con, bne(0), target);

    compareSigned(con, 4, al, 4, bl);
    conditional(con, bne(0), target);
    break;

  case lir::JumpIfLess:
  case lir::JumpIfFloatLess:
    conditional(con, blt(0), target);

    next = con->code.length();
    emit(con, bgt(0));

    compareUnsigned(con, 4, al, 4, bl);
    conditional(con, blo(0), target);
    break;

  case lir::JumpIfGreater:
  case lir::JumpIfFloatGreater:
    conditional(con, bgt(0), target);

    next = con->code.length();
    emit(con, blt(0));

    compareUnsigned(con, 4, al, 4, bl);
    conditional(con, bhi(0), target);
    break;

  case lir::JumpIfLessOrEqual:
  case lir::JumpIfFloatLessOrEqual:
    conditional(con, blt(0), target);

    next = con->code.length();
    emit(con, bgt(0));

    compareUnsigned(con, 4, al, 4, bl);
    conditional(con, bls(0), target);
    break;

  case lir::JumpIfGreaterOrEqual:
  case lir::JumpIfFloatGreaterOrEqual:
    conditional(con, bgt(0), target);

    next = con->code.length();
    emit(con, blt(0));

    compareUnsigned(con, 4, al, 4, bl);
    conditional(con, bhs(0), target);
    break;

  default:
    abort(con);
  }

  if (next) {
    updateOffset
      (con->s, con->code.data + next, reinterpret_cast<intptr_t>
       (con->code.data + con->code.length()));
  }
}

void
branchRR(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Register* b,
         lir::Constant* target)
{
  if (!isFpr(a) && size > TargetBytesPerWord) {
    lir::Register ah(a->high);
    lir::Register bh(b->high);

    branchLong(con, op, a, &ah, b, &bh, target, CAST2(compareRR),
               CAST2(compareRR));
  } else {
    compareRR(con, size, a, size, b);
    branch(con, op, target);
  }
}

void
branchCR(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Register* b,
         lir::Constant* target)
{
  assert(con, !isFloatBranch(op));

  if (size > TargetBytesPerWord) {
    int64_t v = a->value->value();

    ResolvedPromise low(v & ~static_cast<target_uintptr_t>(0));
    lir::Constant al(&low);

    ResolvedPromise high((v >> 32) & ~static_cast<target_uintptr_t>(0));
    lir::Constant ah(&high);

    lir::Register bh(b->high);

    branchLong(con, op, &al, &ah, b, &bh, target, CAST2(compareCR),
               CAST2(compareCR));
  } else {
    compareCR(con, size, a, size, b);
    branch(con, op, target);
  }
}

void
branchRM(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Memory* b,
         lir::Constant* target)
{
  assert(con, !isFloatBranch(op));
  assert(con, size <= TargetBytesPerWord);

  compareRM(con, size, a, size, b);
  branch(con, op, target);
}

void
branchCM(Context* con, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Memory* b,
         lir::Constant* target)
{
  assert(con, !isFloatBranch(op));
  assert(con, size <= TargetBytesPerWord);

  compareCM(con, size, a, size, b);
  branch(con, op, target);
}

ShiftMaskPromise*
shiftMaskPromise(Context* con, Promise* base, unsigned shift, int64_t mask)
{
  return new(con->zone) ShiftMaskPromise(base, shift, mask);
}

void
moveCM(Context* con, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Memory* dst)
{
  switch (dstSize) {
  case 8: {
    lir::Constant srcHigh
      (shiftMaskPromise(con, src->value, 32, 0xFFFFFFFF));
    lir::Constant srcLow
      (shiftMaskPromise(con, src->value, 0, 0xFFFFFFFF));
    
    lir::Memory dstLow
      (dst->base, dst->offset + 4, dst->index, dst->scale);
    
    moveCM(con, 4, &srcLow, 4, &dstLow);
    moveCM(con, 4, &srcHigh, 4, dst);
  } break;

  default:
    lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
    moveCR(con, srcSize, src, dstSize, &tmp);
    moveRM(con, dstSize, &tmp, dstSize, dst);
    con->client->releaseTemporary(tmp.low);
  }
}

void
negateRR(Context* con, unsigned srcSize, lir::Register* src,
         unsigned dstSize UNUSED, lir::Register* dst)
{
  assert(con, srcSize == dstSize);

  emit(con, mvn(dst->low, src->low));
  emit(con, SETS(addi(dst->low, dst->low, 1)));
  if (srcSize == 8) {
    emit(con, mvn(dst->high, src->high));
    emit(con, adci(dst->high, dst->high, 0));
  }
}

void
callR(Context* con, unsigned size UNUSED, lir::Register* target)
{
  assert(con, size == TargetBytesPerWord);
  emit(con, blx(target->low));
}

void
callC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assert(con, size == TargetBytesPerWord);

  appendOffsetTask(con, target->value, offset(con));
  emit(con, bl(0));
}

void
longCallC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assert(con, size == TargetBytesPerWord);

  lir::Register tmp(4);
  moveCR2(con, TargetBytesPerWord, target, &tmp, offset(con));
  callR(con, TargetBytesPerWord, &tmp);
}

void
longJumpC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assert(con, size == TargetBytesPerWord);

  lir::Register tmp(4); // a non-arg reg that we don't mind clobbering
  moveCR2(con, TargetBytesPerWord, target, &tmp, offset(con));
  jumpR(con, TargetBytesPerWord, &tmp);
}

void
jumpC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assert(con, size == TargetBytesPerWord);

  appendOffsetTask(con, target->value, offset(con));
  emit(con, b(0));
}

void
return_(Context* con)
{
  emit(con, bx(LinkRegister));
}

void
trap(Context* con)
{
  emit(con, bkpt(0));
}

void
memoryBarrier(Context*) {}

// END OPERATION COMPILERS

unsigned
argumentFootprint(unsigned footprint)
{
  return max(pad(footprint, StackAlignmentInWords), StackAlignmentInWords);
}

void
nextFrame(ArchitectureContext* con, uint32_t* start, unsigned size UNUSED,
          unsigned footprint, void* link, bool,
          unsigned targetParameterFootprint UNUSED, void** ip, void** stack)
{
  assert(con, *ip >= start);
  assert(con, *ip <= start + (size / TargetBytesPerWord));

  uint32_t* instruction = static_cast<uint32_t*>(*ip);

  if ((*start >> 20) == 0xe59) {
    // skip stack overflow check
    start += 3;
  }

  if (instruction <= start) {
    *ip = link;
    return;
  }

  unsigned offset = footprint + FrameHeaderSize;

  if (instruction <= start + 2) {
    *ip = link;
    *stack = static_cast<void**>(*stack) + offset;
    return;
  }

  if (*instruction == 0xe12fff1e) { // return
    *ip = link;
    return;
  }

  if (TailCalls) {
    if (argumentFootprint(targetParameterFootprint) > StackAlignmentInWords) {
      offset += argumentFootprint(targetParameterFootprint)
        - StackAlignmentInWords;
    }

    // check for post-non-tail-call stack adjustment of the form "add
    // sp, sp, #offset":
    if ((*instruction >> 12) == 0xe24dd) {
      unsigned value = *instruction & 0xff;
      unsigned rotation = (*instruction >> 8) & 0xf;
      switch (rotation) {
      case  0: offset -= value / TargetBytesPerWord; break;
      case 15: offset -= value; break;
      default: abort(con);
      }
    }

    // todo: check for and handle tail calls
  }

  *ip = static_cast<void**>(*stack)[offset - 1];
  *stack = static_cast<void**>(*stack) + offset;
}

void
populateTables(ArchitectureContext* con)
{
  const lir::OperandType C = lir::ConstantOperand;
  const lir::OperandType A = lir::AddressOperand;
  const lir::OperandType R = lir::RegisterOperand;
  const lir::OperandType M = lir::MemoryOperand;

  OperationType* zo = con->operations;
  UnaryOperationType* uo = con->unaryOperations;
  BinaryOperationType* bo = con->binaryOperations;
  TernaryOperationType* to = con->ternaryOperations;
  BranchOperationType* bro = con->branchOperations;

  zo[lir::Return] = return_;
  zo[lir::LoadBarrier] = memoryBarrier;
  zo[lir::StoreStoreBarrier] = memoryBarrier;
  zo[lir::StoreLoadBarrier] = memoryBarrier;
  zo[lir::Trap] = trap;

  uo[index(con, lir::LongCall, C)] = CAST1(longCallC);

  uo[index(con, lir::AlignedLongCall, C)] = CAST1(longCallC);

  uo[index(con, lir::LongJump, C)] = CAST1(longJumpC);

  uo[index(con, lir::AlignedLongJump, C)] = CAST1(longJumpC);

  uo[index(con, lir::Jump, R)] = CAST1(jumpR);
  uo[index(con, lir::Jump, C)] = CAST1(jumpC);

  uo[index(con, lir::AlignedJump, R)] = CAST1(jumpR);
  uo[index(con, lir::AlignedJump, C)] = CAST1(jumpC);

  uo[index(con, lir::Call, C)] = CAST1(callC);
  uo[index(con, lir::Call, R)] = CAST1(callR);

  uo[index(con, lir::AlignedCall, C)] = CAST1(callC);
  uo[index(con, lir::AlignedCall, R)] = CAST1(callR);

  bo[index(con, lir::Move, R, R)] = CAST2(moveRR);
  bo[index(con, lir::Move, C, R)] = CAST2(moveCR);
  bo[index(con, lir::Move, C, M)] = CAST2(moveCM);
  bo[index(con, lir::Move, M, R)] = CAST2(moveMR);
  bo[index(con, lir::Move, R, M)] = CAST2(moveRM);
  bo[index(con, lir::Move, A, R)] = CAST2(moveAR);

  bo[index(con, lir::MoveZ, R, R)] = CAST2(moveZRR);
  bo[index(con, lir::MoveZ, M, R)] = CAST2(moveZMR);
  bo[index(con, lir::MoveZ, C, R)] = CAST2(moveCR);

  bo[index(con, lir::Negate, R, R)] = CAST2(negateRR);

  bo[index(con, lir::FloatAbsolute, R, R)] = CAST2(floatAbsoluteRR);
  bo[index(con, lir::FloatNegate, R, R)] = CAST2(floatNegateRR);
  bo[index(con, lir::Float2Float, R, R)] = CAST2(float2FloatRR);
  bo[index(con, lir::Float2Int, R, R)] = CAST2(float2IntRR);
  bo[index(con, lir::Int2Float, R, R)] = CAST2(int2FloatRR);
  bo[index(con, lir::FloatSquareRoot, R, R)] = CAST2(floatSqrtRR);

  to[index(con, lir::Add, R)] = CAST3(addR);

  to[index(con, lir::Subtract, R)] = CAST3(subR);

  to[index(con, lir::Multiply, R)] = CAST3(multiplyR);

  to[index(con, lir::FloatAdd, R)] = CAST3(floatAddR);
  to[index(con, lir::FloatSubtract, R)] = CAST3(floatSubtractR);
  to[index(con, lir::FloatMultiply, R)] = CAST3(floatMultiplyR);
  to[index(con, lir::FloatDivide, R)] = CAST3(floatDivideR);

  to[index(con, lir::ShiftLeft, R)] = CAST3(shiftLeftR);
  to[index(con, lir::ShiftLeft, C)] = CAST3(shiftLeftC);

  to[index(con, lir::ShiftRight, R)] = CAST3(shiftRightR);
  to[index(con, lir::ShiftRight, C)] = CAST3(shiftRightC);

  to[index(con, lir::UnsignedShiftRight, R)] = CAST3(unsignedShiftRightR);
  to[index(con, lir::UnsignedShiftRight, C)] = CAST3(unsignedShiftRightC);

  to[index(con, lir::And, R)] = CAST3(andR);
  to[index(con, lir::And, C)] = CAST3(andC);

  to[index(con, lir::Or, R)] = CAST3(orR);

  to[index(con, lir::Xor, R)] = CAST3(xorR);

  bro[branchIndex(con, R, R)] = CAST_BRANCH(branchRR);
  bro[branchIndex(con, C, R)] = CAST_BRANCH(branchCR);
  bro[branchIndex(con, C, M)] = CAST_BRANCH(branchCM);
  bro[branchIndex(con, R, M)] = CAST_BRANCH(branchRM);
}

class MyArchitecture: public Assembler::Architecture {
 public:
  MyArchitecture(System* system): con(system), referenceCount(0) {
    populateTables(&con);
  }

  virtual unsigned floatRegisterSize() {
    return vfpSupported() ? 8 : 0;
  }

  virtual const RegisterFile* registerFile() {
    return vfpSupported() ? &MyRegisterFileWithFloats : &MyRegisterFileWithoutFloats;
  }

  virtual int scratch() {
    return 5;
  }

  virtual int stack() {
    return StackRegister;
  }

  virtual int thread() {
    return ThreadRegister;
  }

  virtual int returnLow() {
    return 0;
  }

  virtual int returnHigh() {
    return 1;
  }

  virtual int virtualCallTarget() {
    return 4;
  }

  virtual int virtualCallIndex() {
    return 3;
  }

  virtual bool bigEndian() {
    return false;
  }

  virtual uintptr_t maximumImmediateJump() {
    return 0x1FFFFFF;
  }

  virtual bool reserved(int register_) {
    switch (register_) {
    case LinkRegister:
    case StackRegister:
    case ThreadRegister:
    case ProgramCounter:
      return true;

    default:
      return false;
    }
  }

  virtual unsigned frameFootprint(unsigned footprint) {
    return max(footprint, StackAlignmentInWords);
  }

  virtual unsigned argumentFootprint(unsigned footprint) {
    return local::argumentFootprint(footprint);
  }

  virtual bool argumentAlignment() {
#ifdef __APPLE__
    return false;
#else
    return true;
#endif
  }

  virtual bool argumentRegisterAlignment() {
#ifdef __APPLE__
    return false;
#else
    return true;
#endif
  }

  virtual unsigned argumentRegisterCount() {
    return 4;
  }

  virtual int argumentRegister(unsigned index) {
    assert(&con, index < argumentRegisterCount());

    return index;
  }

  virtual bool hasLinkRegister() {
    return true;
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

  virtual void updateCall(lir::UnaryOperation op UNUSED,
                          void* returnAddress,
                          void* newTarget)
  {
    switch (op) {
    case lir::Call:
    case lir::Jump:
    case lir::AlignedCall:
    case lir::AlignedJump: {
      updateOffset(con.s, static_cast<uint8_t*>(returnAddress) - 4,
                   reinterpret_cast<intptr_t>(newTarget));
    } break;

    case lir::LongCall:
    case lir::LongJump:
    case lir::AlignedLongCall:
    case lir::AlignedLongJump: {
      uint32_t* p = static_cast<uint32_t*>(returnAddress) - 2;
      *reinterpret_cast<void**>(p + (((*p & PoolOffsetMask) + 8) / 4))
        = newTarget;
    } break;

    default: abort(&con);
    }
  }

  virtual unsigned constantCallSize() {
    return 4;
  }

  virtual void setConstant(void* dst, uint64_t constant) {
    *static_cast<target_uintptr_t*>(dst) = constant;
  }

  virtual unsigned alignFrameSize(unsigned sizeInWords) {
    return pad(sizeInWords + FrameHeaderSize, StackAlignmentInWords)
      - FrameHeaderSize;
  }

  virtual void nextFrame(void* start, unsigned size, unsigned footprint,
                         void* link, bool mostRecent,
                         unsigned targetParameterFootprint, void** ip,
                         void** stack)
  {
    local::nextFrame(&con, static_cast<uint32_t*>(start), size, footprint, link,
                mostRecent, targetParameterFootprint, ip, stack);
  }

  virtual void* frameIp(void* stack) {
    return stack ? static_cast<void**>(stack)[returnAddressOffset()] : 0;
  }

  virtual unsigned frameHeaderSize() {
    return FrameHeaderSize;
  }

  virtual unsigned frameReturnAddressSize() {
    return 0;
  }

  virtual unsigned frameFooterSize() {
    return 0;
  }

  virtual int returnAddressOffset() {
    return -1;
  }

  virtual int framePointerOffset() {
    return 0;
  }
  
  virtual bool alwaysCondensed(lir::BinaryOperation) {
    return false;
  }
  
  virtual bool alwaysCondensed(lir::TernaryOperation) {
    return false;
  }
  
  virtual void plan
  (lir::UnaryOperation,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   bool* thunk)
  {
    *aTypeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
    *aRegisterMask = ~static_cast<uint64_t>(0);
    *thunk = false;
  }

  virtual void planSource
  (lir::BinaryOperation op,
   unsigned aSize, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   unsigned bSize, bool* thunk)
  {
    *thunk = false;
    *aTypeMask = ~0;
    *aRegisterMask = GPR_MASK64;

    switch (op) {
    case lir::Negate:
      *aTypeMask = (1 << lir::RegisterOperand);
      *aRegisterMask = GPR_MASK64;
      break;

    case lir::Absolute:
      *thunk = true;
      break;

    case lir::FloatAbsolute:
    case lir::FloatSquareRoot:
    case lir::FloatNegate:
    case lir::Float2Float:
      if (vfpSupported()) {
        *aTypeMask = (1 << lir::RegisterOperand);
        *aRegisterMask = FPR_MASK64;
      } else {
        *thunk = true;
      }
      break;

    case lir::Float2Int:
      // todo: Java requires different semantics than SSE for
      // converting floats to integers, we we need to either use
      // thunks or produce inline machine code which handles edge
      // cases properly.
      if (false && vfpSupported() && bSize == 4) {
        *aTypeMask = (1 << lir::RegisterOperand);
        *aRegisterMask = FPR_MASK64;
      } else {
        *thunk = true;
      }
      break;

    case lir::Int2Float:
      if (vfpSupported() && aSize == 4) {
        *aTypeMask = (1 << lir::RegisterOperand);
        *aRegisterMask = GPR_MASK64;
      } else {
        *thunk = true;
      }
      break;

    default:
      break;
    }
  }
  
  virtual void planDestination
  (lir::BinaryOperation op,
   unsigned, uint8_t aTypeMask, uint64_t,
   unsigned , uint8_t* bTypeMask, uint64_t* bRegisterMask)
  {
    *bTypeMask = (1 << lir::RegisterOperand) | (1 << lir::MemoryOperand);
    *bRegisterMask = GPR_MASK64;

    switch (op) {
    case lir::Negate:
      *bTypeMask = (1 << lir::RegisterOperand);
      *bRegisterMask = GPR_MASK64;
      break;

    case lir::FloatAbsolute:
    case lir::FloatSquareRoot:
    case lir::FloatNegate:
    case lir::Float2Float:
    case lir::Int2Float:
      *bTypeMask = (1 << lir::RegisterOperand);
      *bRegisterMask = FPR_MASK64;
      break;

    case lir::Float2Int:
      *bTypeMask = (1 << lir::RegisterOperand);
      *bRegisterMask = GPR_MASK64;
      break;

    case lir::Move:
      if (!(aTypeMask & 1 << lir::RegisterOperand)) {
        *bTypeMask = 1 << lir::RegisterOperand;
      }
      break;

    default:
      break;
    }
  }

  virtual void planMove
  (unsigned, uint8_t* srcTypeMask, uint64_t* srcRegisterMask,
   uint8_t* tmpTypeMask, uint64_t* tmpRegisterMask,
   uint8_t dstTypeMask, uint64_t dstRegisterMask)
  {
    *srcTypeMask = ~0;
    *srcRegisterMask = ~static_cast<uint64_t>(0);

    *tmpTypeMask = 0;
    *tmpRegisterMask = 0;

    if (dstTypeMask & (1 << lir::MemoryOperand)) {
      // can't move directly from memory or constant to memory
      *srcTypeMask = 1 << lir::RegisterOperand;
      *tmpTypeMask = 1 << lir::RegisterOperand;
      *tmpRegisterMask = GPR_MASK64;
    } else if (vfpSupported() &&
               dstTypeMask & 1 << lir::RegisterOperand &&
               dstRegisterMask & FPR_MASK) {
      *srcTypeMask = *tmpTypeMask = 1 << lir::RegisterOperand |
                                    1 << lir::MemoryOperand;
      *tmpRegisterMask = ~static_cast<uint64_t>(0);
    }
  }

  virtual void planSource
  (lir::TernaryOperation op,
   unsigned, uint8_t* aTypeMask, uint64_t* aRegisterMask,
   unsigned bSize, uint8_t* bTypeMask, uint64_t* bRegisterMask,
   unsigned, bool* thunk)
  {
    *aTypeMask = (1 << lir::RegisterOperand) | (1 << lir::ConstantOperand);
    *aRegisterMask = GPR_MASK64;

    *bTypeMask = (1 << lir::RegisterOperand);
    *bRegisterMask = GPR_MASK64;

    *thunk = false;

    switch (op) {
    case lir::ShiftLeft:
    case lir::ShiftRight:
    case lir::UnsignedShiftRight:
      if (bSize == 8) *aTypeMask = *bTypeMask = (1 << lir::RegisterOperand);
      break;

    case lir::Add:
    case lir::Subtract:
    case lir::Or:
    case lir::Xor:
    case lir::Multiply:
      *aTypeMask = *bTypeMask = (1 << lir::RegisterOperand);
      break;

    case lir::Divide:
    case lir::Remainder:
    case lir::FloatRemainder:
      *thunk = true;
      break;

    case lir::FloatAdd:
    case lir::FloatSubtract:
    case lir::FloatMultiply:
    case lir::FloatDivide:
      if (vfpSupported()) {
        *aTypeMask = *bTypeMask = (1 << lir::RegisterOperand);
        *aRegisterMask = *bRegisterMask = FPR_MASK64;
      } else {
        *thunk = true;
      }    
      break;

    case lir::JumpIfFloatEqual:
    case lir::JumpIfFloatNotEqual:
    case lir::JumpIfFloatLess:
    case lir::JumpIfFloatGreater:
    case lir::JumpIfFloatLessOrEqual:
    case lir::JumpIfFloatGreaterOrEqual:
    case lir::JumpIfFloatLessOrUnordered:
    case lir::JumpIfFloatGreaterOrUnordered:
    case lir::JumpIfFloatLessOrEqualOrUnordered:
    case lir::JumpIfFloatGreaterOrEqualOrUnordered:
      if (vfpSupported()) {
        *aTypeMask = *bTypeMask = (1 << lir::RegisterOperand);
        *aRegisterMask = *bRegisterMask = FPR_MASK64;
      } else {
        *thunk = true;
      }
      break;

    default:
      break;
    }
  }

  virtual void planDestination
  (lir::TernaryOperation op,
   unsigned, uint8_t, uint64_t,
   unsigned, uint8_t, const uint64_t bRegisterMask,
   unsigned, uint8_t* cTypeMask, uint64_t* cRegisterMask)
  {
    if (isBranch(op)) {
      *cTypeMask = (1 << lir::ConstantOperand);
      *cRegisterMask = 0;
    } else {
      *cTypeMask = (1 << lir::RegisterOperand);
      *cRegisterMask = bRegisterMask;
    }
  }

  virtual Assembler* makeAssembler(Allocator* allocator, Zone* zone);

  virtual void acquire() {
    ++ referenceCount;
  }

  virtual void release() {
    if (-- referenceCount == 0) {
      con.s->free(this);
    }
  }

  ArchitectureContext con;
  unsigned referenceCount;
};

class MyAssembler: public Assembler {
 public:
  MyAssembler(System* s, Allocator* a, Zone* zone, MyArchitecture* arch):
    con(s, a, zone), arch_(arch)
  { }

  virtual void setClient(Client* client) {
    assert(&con, con.client == 0);
    con.client = client;
  }

  virtual Architecture* arch() {
    return arch_;
  }

  virtual void checkStackOverflow(uintptr_t handler,
                                  unsigned stackLimitOffsetFromThread)
  {
    lir::Register stack(StackRegister);
    lir::Memory stackLimit(ThreadRegister, stackLimitOffsetFromThread);
    lir::Constant handlerConstant(new(con.zone) ResolvedPromise(handler));
    branchRM(&con, lir::JumpIfGreaterOrEqual, TargetBytesPerWord, &stack, &stackLimit,
             &handlerConstant);
  }

  virtual void saveFrame(unsigned stackOffset, unsigned ipOffset) {
    lir::Register link(LinkRegister);
    lir::Memory linkDst(ThreadRegister, ipOffset);
    moveRM(&con, TargetBytesPerWord, &link, TargetBytesPerWord, &linkDst);

    lir::Register stack(StackRegister);
    lir::Memory stackDst(ThreadRegister, stackOffset);
    moveRM(&con, TargetBytesPerWord, &stack, TargetBytesPerWord, &stackDst);
  }

  virtual void pushFrame(unsigned argumentCount, ...) {
    struct Argument {
      unsigned size;
      lir::OperandType type;
      lir::Operand* operand;
    };
    RUNTIME_ARRAY(Argument, arguments, argumentCount);

    va_list a; va_start(a, argumentCount);
    unsigned footprint = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      RUNTIME_ARRAY_BODY(arguments)[i].size = va_arg(a, unsigned);
      RUNTIME_ARRAY_BODY(arguments)[i].type = static_cast<lir::OperandType>(va_arg(a, int));
      RUNTIME_ARRAY_BODY(arguments)[i].operand = va_arg(a, lir::Operand*);
      footprint += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord);
    }
    va_end(a);

    allocateFrame(arch_->alignFrameSize(footprint));
    
    unsigned offset = 0;
    for (unsigned i = 0; i < argumentCount; ++i) {
      if (i < arch_->argumentRegisterCount()) {
        lir::Register dst(arch_->argumentRegister(i));

        apply(lir::Move,
              OperandInfo(
                RUNTIME_ARRAY_BODY(arguments)[i].size,
                RUNTIME_ARRAY_BODY(arguments)[i].type,
                RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(
                pad(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord), lir::RegisterOperand, &dst));

        offset += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord);
      } else {
        lir::Memory dst(StackRegister, offset * TargetBytesPerWord);

        apply(lir::Move,
              OperandInfo(
                RUNTIME_ARRAY_BODY(arguments)[i].size,
                RUNTIME_ARRAY_BODY(arguments)[i].type,
                RUNTIME_ARRAY_BODY(arguments)[i].operand),
              OperandInfo(
                pad(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord), lir::MemoryOperand, &dst));

        offset += ceilingDivide(RUNTIME_ARRAY_BODY(arguments)[i].size, TargetBytesPerWord);
      }
    }
  }

  virtual void allocateFrame(unsigned footprint) {
    footprint += FrameHeaderSize;

    // larger frames may require multiple subtract/add instructions
    // to allocate/deallocate, and nextFrame will need to be taught
    // how to handle them:
    assert(&con, footprint < 256);

    lir::Register stack(StackRegister);
    ResolvedPromise footprintPromise(footprint * TargetBytesPerWord);
    lir::Constant footprintConstant(&footprintPromise);
    subC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);

    lir::Register returnAddress(LinkRegister);
    lir::Memory returnAddressDst
      (StackRegister, (footprint - 1) * TargetBytesPerWord);
    moveRM(&con, TargetBytesPerWord, &returnAddress, TargetBytesPerWord,
           &returnAddressDst);
  }

  virtual void adjustFrame(unsigned difference) {
    lir::Register stack(StackRegister);
    ResolvedPromise differencePromise(difference * TargetBytesPerWord);
    lir::Constant differenceConstant(&differencePromise);
    subC(&con, TargetBytesPerWord, &differenceConstant, &stack, &stack);
  }

  virtual void popFrame(unsigned footprint) {
    footprint += FrameHeaderSize;

    lir::Register returnAddress(LinkRegister);
    lir::Memory returnAddressSrc
      (StackRegister, (footprint - 1) * TargetBytesPerWord);
    moveMR(&con, TargetBytesPerWord, &returnAddressSrc, TargetBytesPerWord,
           &returnAddress);
    
    lir::Register stack(StackRegister);
    ResolvedPromise footprintPromise(footprint * TargetBytesPerWord);
    lir::Constant footprintConstant(&footprintPromise);
    addC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);
  }

  virtual void popFrameForTailCall(unsigned footprint,
                                   int offset,
                                   int returnAddressSurrogate,
                                   int framePointerSurrogate UNUSED)
  {
    assert(&con, framePointerSurrogate == lir::NoRegister);

    if (TailCalls) {
      if (offset) {
        footprint += FrameHeaderSize;

        lir::Register link(LinkRegister);
        lir::Memory returnAddressSrc
          (StackRegister, (footprint - 1) * TargetBytesPerWord);
        moveMR(&con, TargetBytesPerWord, &returnAddressSrc, TargetBytesPerWord,
               &link);
    
        lir::Register stack(StackRegister);
        ResolvedPromise footprintPromise
          ((footprint - offset) * TargetBytesPerWord);
        lir::Constant footprintConstant(&footprintPromise);
        addC(&con, TargetBytesPerWord, &footprintConstant, &stack, &stack);

        if (returnAddressSurrogate != lir::NoRegister) {
          assert(&con, offset > 0);

          lir::Register ras(returnAddressSurrogate);
          lir::Memory dst(StackRegister, (offset - 1) * TargetBytesPerWord);
          moveRM(&con, TargetBytesPerWord, &ras, TargetBytesPerWord, &dst);
        }
      } else {
        popFrame(footprint);
      }
    } else {
      abort(&con);
    }
  }

  virtual void popFrameAndPopArgumentsAndReturn(unsigned frameFootprint,
                                                unsigned argumentFootprint)
  {
    popFrame(frameFootprint);

    assert(&con, argumentFootprint >= StackAlignmentInWords);
    assert(&con, (argumentFootprint % StackAlignmentInWords) == 0);

    unsigned offset;
    if (TailCalls and argumentFootprint > StackAlignmentInWords) {
      offset = argumentFootprint - StackAlignmentInWords;

      lir::Register stack(StackRegister);
      ResolvedPromise adjustmentPromise(offset * TargetBytesPerWord);
      lir::Constant adjustment(&adjustmentPromise);
      addC(&con, TargetBytesPerWord, &adjustment, &stack, &stack);
    } else {
      offset = 0;
    }

    return_(&con);
  }

  virtual void popFrameAndUpdateStackAndReturn(unsigned frameFootprint,
                                               unsigned stackOffsetFromThread)
  {
    popFrame(frameFootprint);

    lir::Register stack(StackRegister);
    lir::Memory newStackSrc(ThreadRegister, stackOffsetFromThread);
    moveMR(&con, TargetBytesPerWord, &newStackSrc, TargetBytesPerWord, &stack);

    return_(&con);
  }

  virtual void apply(lir::Operation op) {
    arch_->con.operations[op](&con);
  }

  virtual void apply(lir::UnaryOperation op, OperandInfo a)
  {
    arch_->con.unaryOperations[index(&(arch_->con), op, a.type)]
      (&con, a.size, a.operand);
  }

  virtual void apply(lir::BinaryOperation op, OperandInfo a, OperandInfo b)
  {
    arch_->con.binaryOperations[index(&(arch_->con), op, a.type, b.type)]
      (&con, a.size, a.operand, b.size, b.operand);
  }

  virtual void apply(lir::TernaryOperation op, OperandInfo a, OperandInfo b, OperandInfo c)
  {
    if (isBranch(op)) {
      assert(&con, a.size == b.size);
      assert(&con, c.size == TargetBytesPerWord);
      assert(&con, c.type == lir::ConstantOperand);

      arch_->con.branchOperations[branchIndex(&(arch_->con), a.type, b.type)]
        (&con, op, a.size, a.operand, b.operand, c.operand);
    } else {
      assert(&con, b.size == c.size);
      assert(&con, b.type == lir::RegisterOperand);
      assert(&con, c.type == lir::RegisterOperand);
      
      arch_->con.ternaryOperations[index(&(arch_->con), op, a.type)]
        (&con, b.size, a.operand, b.operand, c.operand);
    }
  }

  virtual void setDestination(uint8_t* dst) {
    con.result = dst;
  }

  virtual void write() {
    uint8_t* dst = con.result;
    unsigned dstOffset = 0;
    for (MyBlock* b = con.firstBlock; b; b = b->next) {
      if (DebugPool) {
        fprintf(stderr, "write block %p\n", b);
      }

      unsigned blockOffset = 0;
      for (PoolEvent* e = b->poolEventHead; e; e = e->next) {
        unsigned size = e->offset - blockOffset;
        memcpy(dst + dstOffset, con.code.data + b->offset + blockOffset, size);
        blockOffset = e->offset;
        dstOffset += size;

        unsigned poolSize = 0;
        for (PoolOffset* o = e->poolOffsetHead; o; o = o->next) {
          if (DebugPool) {
            fprintf(stderr, "visit pool offset %p %d in block %p\n",
                    o, o->offset, b);
          }

          unsigned entry = dstOffset + poolSize;

          if (needJump(b)) {
            entry += TargetBytesPerWord;
          }

          o->entry->address = dst + entry;

          unsigned instruction = o->block->start
            + padding(o->block, o->offset) + o->offset;

          int32_t v = (entry - 8) - instruction;
          expect(&con, v == (v & PoolOffsetMask));

          int32_t* p = reinterpret_cast<int32_t*>(dst + instruction);
          *p = (v & PoolOffsetMask) | ((~PoolOffsetMask) & *p);

          poolSize += TargetBytesPerWord;
        }

        bool jump = needJump(b);
        if (jump) {
          write4
            (dst + dstOffset, isa::b((poolSize + TargetBytesPerWord - 8) >> 2));
        }

        dstOffset += poolSize + (jump ? TargetBytesPerWord : 0);
      }

      unsigned size = b->size - blockOffset;

      memcpy(dst + dstOffset,
             con.code.data + b->offset + blockOffset,
             size);

      dstOffset += size;
    }

    for (Task* t = con.tasks; t; t = t->next) {
      t->run(&con);
    }

    for (ConstantPoolEntry* e = con.constantPool; e; e = e->next) {
      if (e->constant->resolved()) {
        *static_cast<target_uintptr_t*>(e->address) = e->constant->value();
      } else {
        new (e->constant->listen(sizeof(ConstantPoolListener)))
          ConstantPoolListener(con.s, static_cast<target_uintptr_t*>(e->address),
                               e->callOffset
                               ? dst + e->callOffset->value() + 8
                               : 0);
      }
//       fprintf(stderr, "constant %p at %p\n", reinterpret_cast<void*>(e->constant->value()), e->address);
    }
  }

  virtual Promise* offset(bool forTrace) {
    return local::offset(&con, forTrace);
  }

  virtual Block* endBlock(bool startNew) {
    MyBlock* b = con.lastBlock;
    b->size = con.code.length() - b->offset;
    if (startNew) {
      con.lastBlock = new (con.zone) MyBlock(&con, con.code.length());
    } else {
      con.lastBlock = 0;
    }
    return b;
  }

  virtual void endEvent() {
    MyBlock* b = con.lastBlock;
    unsigned thisEventOffset = con.code.length() - b->offset;
    if (b->poolOffsetHead) {
      int32_t v = (thisEventOffset + TargetBytesPerWord - 8)
        - b->poolOffsetHead->offset;

      if (v > 0 and v != (v & PoolOffsetMask)) {
        appendPoolEvent
          (&con, b, b->lastEventOffset, b->poolOffsetHead,
           b->lastPoolOffsetTail);

        if (DebugPool) {
          for (PoolOffset* o = b->poolOffsetHead;
               o != b->lastPoolOffsetTail->next; o = o->next)
          {
            fprintf(stderr,
                    "in endEvent, include %p %d in pool event %p at offset %d "
                    "in block %p\n",
                    o, o->offset, b->poolEventTail, b->lastEventOffset, b);
          }
        }

        b->poolOffsetHead = b->lastPoolOffsetTail->next;
        b->lastPoolOffsetTail->next = 0;
        if (b->poolOffsetHead == 0) {
          b->poolOffsetTail = 0;
        }
      }
    }
    b->lastEventOffset = thisEventOffset;
    b->lastPoolOffsetTail = b->poolOffsetTail;
  }

  virtual unsigned length() {
    return con.code.length();
  }

  virtual unsigned footerSize() {
    return 0;
  }

  virtual void dispose() {
    con.code.dispose();
  }

  Context con;
  MyArchitecture* arch_;
};

Assembler* MyArchitecture::makeAssembler(Allocator* allocator, Zone* zone) {
  return new(zone) MyAssembler(this->con.s, allocator, zone, this);
}

} // namespace

namespace avian {
namespace codegen {

Assembler::Architecture*
makeArchitectureArm(System* system, bool)
{
  return new (allocate(system, sizeof(local::MyArchitecture))) local::MyArchitecture(system);
}

} // namespace codegen
} // namespace avian
