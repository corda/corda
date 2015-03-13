/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "operations.h"
#include "block.h"
#include "fixup.h"
#include "multimethod.h"

#if TARGET_BYTES_PER_WORD == 8

namespace {

using namespace avian::codegen;
using namespace avian::codegen::arm;

Register fpr(Register reg)
{
  return Register(reg.index() - N_GPRS);
}

Register fpr(lir::RegisterPair* reg)
{
  return fpr(reg->low);
}

void append(Context* c, uint32_t instruction)
{
  c->code.append4(instruction);
}

uint32_t lslv(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0x9ac02000 : 0x1ac02000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t ubfm(Register Rd, Register Rn, int r, int s, unsigned size)
{
  return (size == 8 ? 0xd3400000 : 0x53000000) | (r << 16) | (s << 10)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t sbfm(Register Rd, Register Rn, int r, int s, unsigned size)
{
  return (size == 8 ? 0x93400000 : 0x13000000) | (r << 16) | (s << 10)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t lsli(Register Rd, Register Rn, int shift, unsigned size)
{
  if (size == 4) {
    return ubfm(Rd, Rn, (32 - shift) & 0x1f, 31 - shift, size);
  } else {
    return ubfm(Rd, Rn, (64 - shift) & 0x3f, 63 - shift, size);
  }
}

uint32_t asrv(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0x9ac02800 : 0x1ac02800) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t lsrv(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0x9ac02400 : 0x1ac02400) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t lsri(Register Rd, Register Rn, int shift, unsigned size)
{
  return ubfm(Rd, Rn, shift, size == 8 ? 63 : 31, size);
}

uint32_t asri(Register Rd, Register Rn, int shift, unsigned size)
{
  return sbfm(Rd, Rn, shift, size == 8 ? 63 : 31, size);
}

uint32_t sxtb(Register Rd, Register Rn)
{
  return sbfm(Rd, Rn, 0, 7, 8);
}

uint32_t sxth(Register Rd, Register Rn)
{
  return sbfm(Rd, Rn, 0, 15, 8);
}

uint32_t uxth(Register Rd, Register Rn)
{
  return ubfm(Rd, Rn, 0, 15, 4);
}

uint32_t sxtw(Register Rd, Register Rn)
{
  return sbfm(Rd, Rn, 0, 31, 8);
}

uint32_t br(Register Rn)
{
  return 0xd61f0000 | (Rn.index() << 5);
}

uint32_t fmovFdFn(Register Fd, Register Fn, unsigned size)
{
  return (size == 8 ? 0x1e604000 : 0x1e204000) | (Fn.index() << 5) | Fd.index();
}

uint32_t fmovRdFn(Register Rd, Register Fn, unsigned size)
{
  return (size == 8 ? 0x9e660000 : 0x1e260000) | (Fn.index() << 5) | Rd.index();
}

uint32_t fmovFdRn(Register Fd, Register Rn, unsigned size)
{
  return (size == 8 ? 0x9e670000 : 0x1e270000) | (Rn.index() << 5) | Fd.index();
}

uint32_t orr(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xaa000000 : 0x2a000000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t addi(Register Rd, Register Rn, int value, int shift, unsigned size)
{
  return (size == 8 ? 0x91000000 : 0x11000000) | (shift ? 0x400000 : 0)
         | (value << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t mov(Register Rd, Register Rn, unsigned size)
{
  return Rn.index() == 31 or Rd.index() == 31 ? addi(Rd, Rn, 0, 0, size)
    : orr(Rd, Register(31), Rn, size);
}

uint32_t movz(Register Rd, int value, unsigned shift, unsigned size)
{
  return (size == 8 ? 0xd2800000 : 0x52800000) | ((shift >> 4) << 21)
         | (value << 5) | Rd.index();
}

uint32_t movn(Register Rd, int value, unsigned shift, unsigned size)
{
  return (size == 8 ? 0x92800000 : 0x12800000) | ((shift >> 4) << 21)
         | (value << 5) | Rd.index();
}

uint32_t movk(Register Rd, int value, unsigned shift, unsigned size)
{
  return (size == 8 ? 0xf2800000 : 0x72800000) | ((shift >> 4) << 21)
         | (value << 5) | Rd.index();
}

uint32_t ldrPCRel(Register Rd, int offset, unsigned size)
{
  return (size == 8 ? 0x58000000 : 0x18000000) | ((offset >> 2) << 5)
         | Rd.index();
}

uint32_t add(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0x8b000000 : 0x0b000000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t sub(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xcb000000 : 0x4b000000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t and_(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0x8a000000 : 0x0a000000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t eor(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xca000000 : 0x4a000000) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t madd(Register Rd, Register Rn, Register Rm, Register Ra, unsigned size)
{
  return (size == 8 ? 0x9b000000 : 0x1b000000) | (Rm.index() << 16)
         | (Ra.index() << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t mul(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return madd(Rd, Rn, Rm, Register(31), size);
}

uint32_t subi(Register Rd, Register Rn, int value, int shift, unsigned size)
{
  return (size == 8 ? 0xd1000000 : 0x51000000) | (shift ? 0x400000 : 0)
         | (value << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t fabs_(Register Fd, Register Fn, unsigned size)
{
  return (size == 8 ? 0x1e60c000 : 0x1e20c000) | (Fn.index() << 5) | Fd.index();
}

uint32_t fneg(Register Fd, Register Fn, unsigned size)
{
  return (size == 8 ? 0x1e614000 : 0x1e214000) | (Fn.index() << 5) | Fd.index();
}

uint32_t fsqrt(Register Fd, Register Fn, unsigned size)
{
  return (size == 8 ? 0x1e61c000 : 0x1e21c000) | (Fn.index() << 5) | Fd.index();
}

uint32_t fadd(Register Fd, Register Fn, Register Fm, unsigned size)
{
  return (size == 8 ? 0x1e602800 : 0x1e202800) | (Fm.index() << 16)
         | (Fn.index() << 5) | Fd.index();
}

uint32_t fsub(Register Fd, Register Fn, Register Fm, unsigned size)
{
  return (size == 8 ? 0x1e603800 : 0x1e203800) | (Fm.index() << 16)
         | (Fn.index() << 5) | Fd.index();
}

uint32_t fmul(Register Fd, Register Fn, Register Fm, unsigned size)
{
  return (size == 8 ? 0x1e600800 : 0x1e200800) | (Fm.index() << 16)
         | (Fn.index() << 5) | Fd.index();
}

uint32_t fdiv(Register Fd, Register Fn, Register Fm, unsigned size)
{
  return (size == 8 ? 0x1e601800 : 0x1e201800) | (Fm.index() << 16)
         | (Fn.index() << 5) | Fd.index();
}

uint32_t fcvtSdDn(Register Fd, Register Fn)
{
  return 0x1e624000 | (Fn.index() << 5) | Fd.index();
}

uint32_t fcvtDdSn(Register Fd, Register Fn)
{
  return 0x1e22c000 | (Fn.index() << 5) | Fd.index();
}

uint32_t fcvtasXdDn(Register Rd, Register Fn)
{
  return 0x9e640000 | (Fn.index() << 5) | Rd.index();
}

uint32_t fcvtasWdSn(Register Rd, Register Fn)
{
  return 0x1e240000 | (Fn.index() << 5) | Rd.index();
}

uint32_t scvtfDdXn(Register Fd, Register Rn)
{
  return 0x9e620000 | (Rn.index() << 5) | Fd.index();
}

uint32_t scvtfSdWn(Register Fd, Register Rn)
{
  return 0x1e220000 | (Rn.index() << 5) | Fd.index();
}

uint32_t strFs(Register Fs, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xfc206800 : 0xbc206800) | (Rm.index() << 16)
         | (Rn.index() << 5) | Fs.index();
}

uint32_t strb(Register Rs, Register Rn, Register Rm)
{
  return 0x38206800 | (Rm.index() << 16) | (Rn.index() << 5) | Rs.index();
}

uint32_t strh(Register Rs, Register Rn, Register Rm)
{
  return 0x78206800 | (Rm.index() << 16) | (Rn.index() << 5) | Rs.index();
}

uint32_t striFs(Register Fs, Register Rn, int offset, unsigned size)
{
  return (size == 8 ? 0xfd000000 : 0xbd000000)
         | ((offset >> (size == 8 ? 3 : 2)) << 10) | (Rn.index() << 5)
         | Fs.index();
}

uint32_t str(Register Rs, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xf8206800 : 0xb8206800) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rs.index();
}

uint32_t strbi(Register Rs, Register Rn, int offset)
{
  return 0x39000000 | (offset << 10) | (Rn.index() << 5) | Rs.index();
}

uint32_t strhi(Register Rs, Register Rn, int offset)
{
  return 0x79000000 | ((offset >> 1) << 10) | (Rn.index() << 5) | Rs.index();
}

uint32_t stri(Register Rs, Register Rn, int offset, unsigned size)
{
  return (size == 8 ? 0xf9000000 : 0xb9000000)
         | ((offset >> (size == 8 ? 3 : 2)) << 10) | (Rn.index() << 5)
         | Rs.index();
}

uint32_t ldrFd(Register Fd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xfc606800 : 0xbc606800) | (Rm.index() << 16)
         | (Rn.index() << 5) | Fd.index();
}

uint32_t ldrb(Register Rd, Register Rn, Register Rm)
{
  return 0x38606800 | (Rm.index() << 16) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrsb(Register Rd, Register Rn, Register Rm)
{
  return 0x38e06800 | (Rm.index() << 16) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrh(Register Rd, Register Rn, Register Rm)
{
  return 0x78606800 | (Rm.index() << 16) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrsh(Register Rd, Register Rn, Register Rm)
{
  return 0x78e06800 | (Rm.index() << 16) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrsw(Register Rd, Register Rn, Register Rm)
{
  return 0xb8a06800 | (Rm.index() << 16) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldr(Register Rd, Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xf8606800 : 0xb8606800) | (Rm.index() << 16)
         | (Rn.index() << 5) | Rd.index();
}

uint32_t ldriFd(Register Fd, Register Rn, int offset, unsigned size)
{
  return (size == 8 ? 0xfd400000 : 0xbd400000)
         | ((offset >> (size == 8 ? 3 : 2)) << 10) | (Rn.index() << 5)
         | Fd.index();
}

uint32_t ldrbi(Register Rd, Register Rn, int offset)
{
  return 0x39400000 | (offset << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrsbi(Register Rd, Register Rn, int offset)
{
  return 0x39c00000 | (offset << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrhi(Register Rd, Register Rn, int offset)
{
  return 0x79400000 | ((offset >> 1) << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrshi(Register Rd, Register Rn, int offset)
{
  return 0x79c00000 | ((offset >> 1) << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldrswi(Register Rd, Register Rn, int offset)
{
  return 0xb9800000 | ((offset >> 2) << 10) | (Rn.index() << 5) | Rd.index();
}

uint32_t ldri(Register Rd, Register Rn, int offset, unsigned size)
{
  return (size == 8 ? 0xf9400000 : 0xb9400000)
         | ((offset >> (size == 8 ? 3 : 2)) << 10) | (Rn.index() << 5)
         | Rd.index();
}

uint32_t fcmp(Register Fn, Register Fm, unsigned size)
{
  return (size == 8 ? 0x1e602000 : 0x1e202000) | (Fm.index() << 16)
         | (Fn.index() << 5);
}

uint32_t neg(Register Rd, Register Rm, unsigned size)
{
  return (size == 8 ? 0xcb0003e0 : 0x4b0003e0) | (Rm.index() << 16)
         | Rd.index();
}

uint32_t cmp(Register Rn, Register Rm, unsigned size)
{
  return (size == 8 ? 0xeb00001f : 0x6b00001f) | (Rm.index() << 16)
         | (Rn.index() == 31 ? 0x2063ff : (Rn.index() << 5));
}

uint32_t cmpi(Register Rn, int value, unsigned shift, unsigned size)
{
  return (size == 8 ? 0xf100001f : 0x7100001f) | (shift == 12 ? 0x400000 : 0)
         | (value << 10) | (Rn.index() << 5);
}

uint32_t b(int offset)
{
  return 0x14000000 | (offset >> 2);
}

uint32_t bl(int offset)
{
  return 0x94000000 | (offset >> 2);
}

uint32_t blr(Register Rn)
{
  return 0xd63f0000 | (Rn.index() << 5);
}

uint32_t beq(int offset)
{
  return 0x54000000 | ((offset >> 2) << 5);
}

uint32_t bne(int offset)
{
  return 0x54000001 | ((offset >> 2) << 5);
}

uint32_t blt(int offset)
{
  return 0x5400000b | ((offset >> 2) << 5);
}

uint32_t bgt(int offset)
{
  return 0x5400000c | ((offset >> 2) << 5);
}

uint32_t ble(int offset)
{
  return 0x5400000d | ((offset >> 2) << 5);
}

uint32_t bge(int offset)
{
  return 0x5400000a | ((offset >> 2) << 5);
}

uint32_t bhi(int offset)
{
  return 0x54000008 | ((offset >> 2) << 5);
}

uint32_t bpl(int offset)
{
  return 0x54000005 | ((offset >> 2) << 5);
}

uint32_t brk(int flag)
{
  return 0xd4200020 | (flag << 5);
}

uint32_t dmb(int flag)
{
  return 0xd50330bf | (flag << 8);
}

}  // namespace

namespace avian {
namespace codegen {
namespace arm {

using namespace avian::util;

void shiftLeftR(Context* c,
                unsigned size,
                lir::RegisterPair* a,
                lir::RegisterPair* b,
                lir::RegisterPair* dst)
{
  append(c, lslv(dst->low, b->low, a->low, size));
}

void shiftLeftC(Context* c,
                unsigned size,
                lir::Constant* a,
                lir::RegisterPair* b,
                lir::RegisterPair* dst)
{
  uint64_t value = a->value->value();
  if (size == 4 and (value & 0x1F)) {
    append(c, lsli(dst->low, b->low, value & 0x1F, 4));
  } else if (size == 8 and (value & 0x3F)) {
    append(c, lsli(dst->low, b->low, value & 0x3F, 8));
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void shiftRightR(Context* c,
                 unsigned size,
                 lir::RegisterPair* a,
                 lir::RegisterPair* b,
                 lir::RegisterPair* dst)
{
  append(c, asrv(dst->low, b->low, a->low, size));
}

void shiftRightC(Context* c,
                 unsigned size UNUSED,
                 lir::Constant* a,
                 lir::RegisterPair* b,
                 lir::RegisterPair* dst)
{
  uint64_t value = a->value->value();
  if (size == 4 and (value & 0x1F)) {
    append(c, asri(dst->low, b->low, value & 0x1F, 4));
  } else if (size == 8 and (value & 0x3F)) {
    append(c, asri(dst->low, b->low, value & 0x3F, 8));
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void unsignedShiftRightR(Context* c,
                         unsigned size,
                         lir::RegisterPair* a,
                         lir::RegisterPair* b,
                         lir::RegisterPair* dst)
{
  append(c, lsrv(dst->low, b->low, a->low, size));
}

void unsignedShiftRightC(Context* c,
                         unsigned size UNUSED,
                         lir::Constant* a,
                         lir::RegisterPair* b,
                         lir::RegisterPair* dst)
{
  uint64_t value = a->value->value();
  if (size == 4 and (value & 0x1F)) {
    append(c, lsri(dst->low, b->low, value & 0x1F, 4));
  } else if (size == 8 and (value & 0x3F)) {
    append(c, lsri(dst->low, b->low, value & 0x3F, 8));
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void jumpR(Context* c, unsigned size UNUSED, lir::RegisterPair* target)
{
  assertT(c, size == vm::TargetBytesPerWord);
  append(c, br(target->low));
}

void moveRR(Context* c,
            unsigned srcSize,
            lir::RegisterPair* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  bool srcIsFpr = isFpr(src);
  bool dstIsFpr = isFpr(dst);
  if (srcIsFpr or dstIsFpr) {
    assertT(c, srcSize == dstSize);

    if (srcIsFpr and dstIsFpr) {
      append(c, fmovFdFn(fpr(dst), fpr(src), srcSize));
    } else if (srcIsFpr) {
      append(c, fmovRdFn(dst->low, fpr(src), srcSize));
    } else {
      append(c, fmovFdRn(fpr(dst), src->low, srcSize));
    }
  } else {
    switch (srcSize) {
    case 1:
      append(c, sxtb(dst->low, src->low));
      break;

    case 2:
      append(c, sxth(dst->low, src->low));
      break;

    case 4:
      if (dstSize == 4) {
        append(c, mov(dst->low, src->low, srcSize));
      } else {
        append(c, sxtw(dst->low, src->low));
      }
      break;

    case 8:
      append(c, mov(dst->low, src->low, srcSize));
      break;

    default:
      abort(c);
    }
  }
}

void moveZRR(Context* c,
             unsigned srcSize,
             lir::RegisterPair* src,
             unsigned,
             lir::RegisterPair* dst)
{
  switch (srcSize) {
  case 2:
    append(c, uxth(dst->low, src->low));
    break;

  default:
    abort(c);
  }
}

void moveCR2(Context* c,
             unsigned size,
             lir::Constant* src,
             lir::RegisterPair* dst,
             Promise* callOffset)
{
  if (isFpr(dst)) {
    // todo: could use a single fmov here and avoid the temporary for
    // constants that fit
    lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
    moveCR(c, size, src, size, &tmp);
    moveRR(c, size, &tmp, size, dst);
    c->client->releaseTemporary(tmp.low);
  } else if (callOffset == 0 and src->value->resolved()) {
    // todo: Is it better performance-wise to load using immediate
    // moves or via a PC-relative constant pool?  Does it depend on
    // how many significant bits there are?

    int64_t value = src->value->value();
    if (value >= 0) {
      append(c, movz(dst->low, value & 0xFFFF, 0, size));
      if (value >> 16) {
        if ((value >> 16) & 0xFFFF) {
          append(c, movk(dst->low, (value >> 16) & 0xFFFF, 16, size));
        }
        if (value >> 32) {
          if ((value >> 32) & 0xFFFF) {
            append(c, movk(dst->low, (value >> 32) & 0xFFFF, 32, size));
          }
          if (value >> 48) {
            append(c, movk(dst->low, (value >> 48) & 0xFFFF, 48, size));
          }
        }
      }
    } else {
      append(c, movn(dst->low, (~value) & 0xFFFF, 0, size));
      if (~(value >> 16)) {
        if (((value >> 16) & 0xFFFF) != 0xFFFF) {
          append(c, movk(dst->low, (value >> 16) & 0xFFFF, 16, size));
        }
        if (~(value >> 32)) {
          if (((value >> 32) & 0xFFFF) != 0xFFFF) {
            append(c, movk(dst->low, (value >> 32) & 0xFFFF, 32, size));
          }
          if (~(value >> 48)) {
            append(c, movk(dst->low, (value >> 48) & 0xFFFF, 48, size));
          }
        }
      }
    }
  } else {
    appendConstantPoolEntry(c, src->value, callOffset);
    append(c, ldrPCRel(dst->low, 0, size));
  }
}

void moveCR(Context* c,
            unsigned size,
            lir::Constant* src,
            unsigned,
            lir::RegisterPair* dst)
{
  moveCR2(c, size, src, dst, 0);
}

void addR(Context* c,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  append(c, add(dst->low, a->low, b->low, size));
}

void subR(Context* c,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  append(c, sub(dst->low, b->low, a->low, size));
}

void addC(Context* c,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  int64_t v = a->value->value();
  if (v) {
    if (v > 0 and v < 0x1000) {
      append(c, addi(dst->low, b->low, v, 0, size));
    } else if (v > 0 and v < 0x1000000 and v % 0x1000 == 0) {
      append(c, addi(dst->low, b->low, v >> 12, 12, size));
    } else {
      // todo
      abort(c);
    }
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void subC(Context* c,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  int64_t v = a->value->value();
  if (v) {
    if (v > 0 and v < 0x1000) {
      append(c, subi(dst->low, b->low, v, 0, size));
    } else if (v > 0 and v < 0x1000000 and v % 0x1000 == 0) {
      append(c, subi(dst->low, b->low, v >> 12, 12, size));
    } else {
      // todo
      abort(c);
    }
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void multiplyR(Context* c,
               unsigned size,
               lir::RegisterPair* a,
               lir::RegisterPair* b,
               lir::RegisterPair* dst)
{
  append(c, mul(dst->low, a->low, b->low, size));
}

void floatAbsoluteRR(Context* c,
                     unsigned size,
                     lir::RegisterPair* a,
                     unsigned,
                     lir::RegisterPair* b)
{
  append(c, fabs_(fpr(b), fpr(a), size));
}

void floatNegateRR(Context* c,
                   unsigned size,
                   lir::RegisterPair* a,
                   unsigned,
                   lir::RegisterPair* b)
{
  append(c, fneg(fpr(b), fpr(a), size));
}

void float2FloatRR(Context* c,
                   unsigned size,
                   lir::RegisterPair* a,
                   unsigned,
                   lir::RegisterPair* b)
{
  if (size == 8) {
    append(c, fcvtSdDn(fpr(b), fpr(a)));
  } else {
    append(c, fcvtDdSn(fpr(b), fpr(a)));
  }
}

void float2IntRR(Context* c,
                 unsigned size,
                 lir::RegisterPair* a,
                 unsigned,
                 lir::RegisterPair* b)
{
  if (size == 8) {
    append(c, fcvtasXdDn(b->low, fpr(a)));
  } else {
    append(c, fcvtasWdSn(b->low, fpr(a)));
  }
}

void int2FloatRR(Context* c,
                 unsigned,
                 lir::RegisterPair* a,
                 unsigned size,
                 lir::RegisterPair* b)
{
  if (size == 8) {
    append(c, scvtfDdXn(fpr(b), a->low));
  } else {
    append(c, scvtfSdWn(fpr(b), a->low));
  }
}

void floatSqrtRR(Context* c,
                 unsigned size,
                 lir::RegisterPair* a,
                 unsigned,
                 lir::RegisterPair* b)
{
  append(c, fsqrt(fpr(b), fpr(a), size));
}

void floatAddR(Context* c,
               unsigned size,
               lir::RegisterPair* a,
               lir::RegisterPair* b,
               lir::RegisterPair* dst)
{
  append(c, fadd(fpr(dst), fpr(b), fpr(a), size));
}

void floatSubtractR(Context* c,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* dst)
{
  append(c, fsub(fpr(dst), fpr(b), fpr(a), size));
}

void floatMultiplyR(Context* c,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* dst)
{
  append(c, fmul(fpr(dst), fpr(b), fpr(a), size));
}

void floatDivideR(Context* c,
                  unsigned size,
                  lir::RegisterPair* a,
                  lir::RegisterPair* b,
                  lir::RegisterPair* dst)
{
  append(c, fdiv(fpr(dst), fpr(b), fpr(a), size));
}

Register normalize(Context* c,
                   int offset,
                   Register index,
                   unsigned scale,
                   bool* preserveIndex,
                   bool* release)
{
  if (offset != 0 or scale != 1) {
    lir::RegisterPair normalizedIndex(
        *preserveIndex ? c->client->acquireTemporary(GPR_MASK) : index);

    if (*preserveIndex) {
      *release = true;
      *preserveIndex = false;
    } else {
      *release = false;
    }

    Register scaled;

    if (scale != 1) {
      lir::RegisterPair unscaledIndex(index);

      ResolvedPromise scalePromise(log(scale));
      lir::Constant scaleConstant(&scalePromise);

      shiftLeftC(c,
                 vm::TargetBytesPerWord,
                 &scaleConstant,
                 &unscaledIndex,
                 &normalizedIndex);

      scaled = normalizedIndex.low;
    } else {
      scaled = index;
    }

    if (offset != 0) {
      lir::RegisterPair untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      lir::Constant offsetConstant(&offsetPromise);

      lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
      moveCR(c,
             vm::TargetBytesPerWord,
             &offsetConstant,
             vm::TargetBytesPerWord,
             &tmp);
      addR(c,
           vm::TargetBytesPerWord,
           &tmp,
           &untranslatedIndex,
           &normalizedIndex);
      c->client->releaseTemporary(tmp.low);
    }

    return normalizedIndex.low;
  } else {
    *release = false;
    return index;
  }
}

void store(Context* c,
           unsigned size,
           lir::RegisterPair* src,
           Register base,
           int offset,
           Register index,
           unsigned scale,
           bool preserveIndex)
{
  if (index != NoRegister) {
    bool release;

    // todo: browsing the instruction set, it looks like we could do a
    // scaled store or load in a single instruction if the offset is
    // zero, and we could simplify things for the case of non-zero
    // offsets also

    Register normalized
        = normalize(c, offset, index, scale, &preserveIndex, &release);

    if (isFpr(src)) {
      switch (size) {
      case 4:
      case 8:
        append(c, strFs(fpr(src->low), base, normalized, size));
        break;

      default:
        abort(c);
      }
    } else {
      switch (size) {
      case 1:
        append(c, strb(src->low, base, normalized));
        break;

      case 2:
        append(c, strh(src->low, base, normalized));
        break;

      case 4:
      case 8:
        append(c, str(src->low, base, normalized, size));
        break;

      default:
        abort(c);
      }
    }

    if (release) {
      c->client->releaseTemporary(normalized);
    }
  } else if (abs(offset) == (abs(offset) & 0xFFF)) {
    if (isFpr(src)) {
      switch (size) {
      case 4:
      case 8:
        assertT(c, offset == (offset & (size == 8 ? (~7) : (~3))));
        append(c, striFs(fpr(src->low), base, offset, size));
        break;

      default:
        abort(c);
      }
    } else {  // FPR store
      switch (size) {
      case 1:
        append(c, strbi(src->low, base, offset));
        break;

      case 2:
        assertT(c, offset == (offset & (~1)));
        append(c, strhi(src->low, base, offset));
        break;

      case 4:
        assertT(c, offset == (offset & (~3)));
        append(c, stri(src->low, base, offset, size));
        break;

      case 8:
        assertT(c, offset == (offset & (~7)));
        append(c, stri(src->low, base, offset, size));
        break;

      default:
        abort(c);
      }
    }
  } else {
    lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(c,
           vm::TargetBytesPerWord,
           &offsetConstant,
           vm::TargetBytesPerWord,
           &tmp);

    store(c, size, src, base, 0, tmp.low, 1, false);

    c->client->releaseTemporary(tmp.low);
  }
}

void moveRM(Context* c,
            unsigned srcSize,
            lir::RegisterPair* src,
            unsigned dstSize UNUSED,
            lir::Memory* dst)
{
  assertT(c, srcSize == dstSize);

  if (src->low.index() == 31) {
    assertT(c, c->client == 0);  // the compiler should never ask us to
                                 // store the SP; we'll only get here
                                 // when assembling a thunk

    lir::RegisterPair tmp(Register(9));  // we're in a thunk, so we can
                                         // clobber this

    moveRR(c, srcSize, src, srcSize, &tmp);
    store(
        c, srcSize, &tmp, dst->base, dst->offset, dst->index, dst->scale, true);
  } else {
    store(
        c, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
  }
}

void load(Context* c,
          unsigned srcSize,
          Register base,
          int offset,
          Register index,
          unsigned scale,
          unsigned dstSize,
          lir::RegisterPair* dst,
          bool preserveIndex,
          bool signExtend)
{
  if (index != NoRegister) {
    bool release;
    Register normalized
        = normalize(c, offset, index, scale, &preserveIndex, &release);

    if (isFpr(dst)) {  // FPR load
      switch (srcSize) {
      case 4:
      case 8:
        append(c, ldrFd(fpr(dst->low), base, normalized, srcSize));
        break;

      default:
        abort(c);
      }
    } else {
      switch (srcSize) {
      case 1:
        if (signExtend) {
          append(c, ldrsb(dst->low, base, normalized));
        } else {
          append(c, ldrb(dst->low, base, normalized));
        }
        break;

      case 2:
        if (signExtend) {
          append(c, ldrsh(dst->low, base, normalized));
        } else {
          append(c, ldrh(dst->low, base, normalized));
        }
        break;

      case 4:
      case 8:
        if (signExtend and srcSize == 4 and dstSize == 8) {
          append(c, ldrsw(dst->low, base, normalized));
        } else {
          append(c, ldr(dst->low, base, normalized, srcSize));
        }
        break;

      default:
        abort(c);
      }
    }

    if (release) {
      c->client->releaseTemporary(normalized);
    }
  } else if (abs(offset) == (abs(offset) & 0xFFF)) {
    if (isFpr(dst)) {
      switch (srcSize) {
      case 4:
      case 8:
        assertT(c, offset == (offset & (srcSize == 8 ? (~7) : (~3))));
        append(c, ldriFd(fpr(dst->low), base, offset, srcSize));
        break;

      default:
        abort(c);
      }
    } else {
      switch (srcSize) {
      case 1:
        if (signExtend) {
          append(c, ldrsbi(dst->low, base, offset));
        } else {
          append(c, ldrbi(dst->low, base, offset));
        }
        break;

      case 2:
        assertT(c, offset == (offset & (~1)));
        if (signExtend) {
          append(c, ldrshi(dst->low, base, offset));
        } else {
          append(c, ldrhi(dst->low, base, offset));
        }
        break;

      case 4:
      case 8:
        if (signExtend and srcSize == 4 and dstSize == 8) {
          assertT(c, offset == (offset & (~3)));
          append(c, ldrswi(dst->low, base, offset));
        } else {
          assertT(c, offset == (offset & (srcSize == 8 ? (~7) : (~3))));
          append(c, ldri(dst->low, base, offset, srcSize));
        }
        break;

      default:
        abort(c);
      }
    }
  } else {
    lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(c,
           vm::TargetBytesPerWord,
           &offsetConstant,
           vm::TargetBytesPerWord,
           &tmp);

    load(c, srcSize, base, 0, tmp.low, 1, dstSize, dst, false, signExtend);

    c->client->releaseTemporary(tmp.low);
  }
}

void moveMR(Context* c,
            unsigned srcSize,
            lir::Memory* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  if (dst->low.index() == 31) {
    assertT(c, c->client == 0);  // the compiler should never ask us to
                                 // load the SP; we'll only get here
                                 // when assembling a thunk

    lir::RegisterPair tmp(Register(9));  // we're in a thunk, so we can
                                         // clobber this

    load(c, srcSize, src->base, src->offset, src->index, src->scale, dstSize, &tmp, true, true);
    moveRR(c, dstSize, &tmp, dstSize, dst);
  } else {
    load(c,
         srcSize,
         src->base,
         src->offset,
         src->index,
         src->scale,
         dstSize,
         dst,
         true,
         true);
  }
}

void moveZMR(Context* c,
             unsigned srcSize,
             lir::Memory* src,
             unsigned dstSize,
             lir::RegisterPair* dst)
{
  load(c,
       srcSize,
       src->base,
       src->offset,
       src->index,
       src->scale,
       dstSize,
       dst,
       true,
       false);
}

void andR(Context* c,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  append(c, and_(dst->low, a->low, b->low, size));
}

void andC(Context* c,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  int64_t v = a->value->value();

  if (~v) {
    bool useTemporary = b->low == dst->low;
    lir::RegisterPair tmp(dst->low);
    if (useTemporary) {
      tmp.low = c->client->acquireTemporary(GPR_MASK);
    }

    moveCR(c, size, a, size, &tmp);
    andR(c, size, b, &tmp, dst);

    if (useTemporary) {
      c->client->releaseTemporary(tmp.low);
    }
  } else {
    moveRR(c, size, b, size, dst);
  }
}

void orR(Context* c,
         unsigned size,
         lir::RegisterPair* a,
         lir::RegisterPair* b,
         lir::RegisterPair* dst)
{
  append(c, orr(dst->low, a->low, b->low, size));
}

void xorR(Context* c,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  append(c, eor(dst->low, a->low, b->low, size));
}

void moveAR(Context* c,
            unsigned srcSize,
            lir::Address* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  assertT(
      c,
      srcSize == vm::TargetBytesPerWord and dstSize == vm::TargetBytesPerWord);

  lir::Constant constant(src->address);
  moveCR(c, srcSize, &constant, dstSize, dst);

  lir::Memory memory(dst->low, 0, NoRegister, 0);
  moveMR(c, dstSize, &memory, dstSize, dst);
}

void compareRR(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b)
{
  assertT(c, not(isFpr(a) xor isFpr(b)));
  assertT(c, aSize == bSize);

  if (isFpr(a)) {
    append(c, fcmp(fpr(b), fpr(a), aSize));
  } else {
    append(c, cmp(b->low, a->low, aSize));
  }
}

void compareCR(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (!isFpr(b) && a->value->resolved()) {
    int64_t v = a->value->value();
    if (v == 0) {
      append(c, cmp(b->low, Register(31), aSize));
      return;
    } else if (v > 0 and v < 0x1000) {
      append(c, cmpi(b->low, v, 0, aSize));
      return;
    } else if (v > 0 and v < 0x1000000 and v % 0x1000 == 0) {
      append(c, cmpi(b->low, v >> 12, 12, aSize));
      return;
    }
  }

  lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
  moveCR(c, aSize, a, bSize, &tmp);
  compareRR(c, bSize, &tmp, bSize, b);
  c->client->releaseTemporary(tmp.low);
}

void compareCM(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(c, aSize == bSize);

  lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
  moveMR(c, bSize, b, bSize, &tmp);
  compareCR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void compareRM(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(c, aSize == bSize);

  lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
  moveMR(c, bSize, b, bSize, &tmp);
  compareRR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void compareMR(Context* c,
               unsigned aSize,
               lir::Memory* a,
               unsigned bSize,
               lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
  moveMR(c, aSize, a, aSize, &tmp);
  compareRR(c, aSize, &tmp, bSize, b);
  c->client->releaseTemporary(tmp.low);
}

int32_t branch(Context* c, lir::TernaryOperation op)
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
    abort(c);
  }
}

void conditional(Context* c, int32_t branch, lir::Constant* target)
{
  appendOffsetTask(c, target->value, offsetPromise(c));
  append(c, branch);
}

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target)
{
  conditional(c, branch(c, op), target);
}

void branchRR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  compareRR(c, size, a, size, b);
  branch(c, op, target);
}

void branchCR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  assertT(c, not isFloatBranch(op));

  compareCR(c, size, a, size, b);
  branch(c, op, target);
}

void branchRM(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::Memory* b,
              lir::Constant* target)
{
  assertT(c, not isFloatBranch(op));
  assertT(c, size <= vm::TargetBytesPerWord);

  if (a->low.index() == 31) {
    // Stack overflow checks need to compare to the stack pointer, but
    // we can only encode that in the opposite operand order we're
    // given, so we need to reverse everything.  Also, we can't do a
    // conditional jump further than 2^19 instructions away, which can
    // cause trouble with large code, so we branch past an
    // unconditional branch which can jump further, which reverses the
    // logic again.  Confused?  Good.
    assertT(c, op == lir::JumpIfGreaterOrEqual);
    compareMR(c, size, b, size, a);
    append(c, bge(8));
    jumpC(c, vm::TargetBytesPerWord, target);
  } else {
    compareRM(c, size, a, size, b);
    branch(c, op, target);
  }
}

void branchCM(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::Memory* b,
              lir::Constant* target)
{
  assertT(c, not isFloatBranch(op));
  assertT(c, size <= vm::TargetBytesPerWord);

  compareCM(c, size, a, size, b);
  branch(c, op, target);
}

ShiftMaskPromise* shiftMaskPromise(Context* c,
                                   Promise* base,
                                   unsigned shift,
                                   int64_t mask)
{
  return new (c->zone) ShiftMaskPromise(base, shift, mask);
}

void moveCM(Context* c,
            unsigned srcSize,
            lir::Constant* src,
            unsigned dstSize,
            lir::Memory* dst)
{
  lir::RegisterPair tmp(c->client->acquireTemporary(GPR_MASK));
  moveCR(c, srcSize, src, dstSize, &tmp);
  moveRM(c, dstSize, &tmp, dstSize, dst);
  c->client->releaseTemporary(tmp.low);
}

void negateRR(Context* c,
              unsigned srcSize,
              lir::RegisterPair* src,
              unsigned dstSize UNUSED,
              lir::RegisterPair* dst)
{
  assertT(c, srcSize == dstSize);

  append(c, neg(dst->low, src->low, srcSize));
}

void callR(Context* c, unsigned size UNUSED, lir::RegisterPair* target)
{
  assertT(c, size == vm::TargetBytesPerWord);
  append(c, blr(target->low));
}

void callC(Context* c, unsigned size UNUSED, lir::Constant* target)
{
  assertT(c, size == vm::TargetBytesPerWord);

  appendOffsetTask(c, target->value, offsetPromise(c));
  append(c, bl(0));
}

void longCallC(Context* c, unsigned size UNUSED, lir::Constant* target)
{
  assertT(c, size == vm::TargetBytesPerWord);

  lir::RegisterPair tmp(
      Register(9));  // a non-arg reg that we don't mind clobbering
  moveCR2(c, vm::TargetBytesPerWord, target, &tmp, offsetPromise(c));
  callR(c, vm::TargetBytesPerWord, &tmp);
}

void longJumpC(Context* c, unsigned size UNUSED, lir::Constant* target)
{
  assertT(c, size == vm::TargetBytesPerWord);

  lir::RegisterPair tmp(
      Register(9));  // a non-arg reg that we don't mind clobbering
  moveCR2(c, vm::TargetBytesPerWord, target, &tmp, offsetPromise(c));
  jumpR(c, vm::TargetBytesPerWord, &tmp);
}

void jumpC(Context* c, unsigned size UNUSED, lir::Constant* target)
{
  assertT(c, size == vm::TargetBytesPerWord);

  appendOffsetTask(c, target->value, offsetPromise(c));
  append(c, b(0));
}

void return_(Context* c)
{
  append(c, br(LinkRegister));
}

void trap(Context* c)
{
  append(c, brk(0));
}

// todo: determine the minimal operation types and domains needed to
// implement the following barriers (see
// http://community.arm.com/groups/processors/blog/2011/10/19/memory-access-ordering-part-3--memory-access-ordering-in-the-arm-architecture).
// For now, we just use DMB SY as a conservative but not necessarily
// performant choice.

void memoryBarrier(Context* c)
{
  append(c, dmb(0xF));
}

void loadBarrier(Context* c)
{
  memoryBarrier(c);
}

void storeStoreBarrier(Context* c)
{
  memoryBarrier(c);
}

void storeLoadBarrier(Context* c)
{
  memoryBarrier(c);
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // TARGET_BYTES_PER_WORD == 8
