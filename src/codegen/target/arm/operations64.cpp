/* Copyright (c) 2008-2014, Avian Contributors

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

#if AVIAN_TARGET_ARCH == AVIAN_ARCH_ARM64

namespace {

void append(Context* c, uint32_t instruction, unsigned size)
{
  c->code.append4(instruction | (size == 8 ? 0x80000000 : 0));
}

uint32_t lslv(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0x9ac12000 : 0x1ac02000) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t ubfm(int Rd, int Rn, int r, int s, unsigned size)
{
  return (size == 8 ? 0xd3608000 : 0x53000000) | (r << 16) | (s << 10) | (Rn << 5) | Rd;
}

uint32_t sbfm(int Rd, int Rn, int r, int s, unsigned size)
{
  return (size == 8 ? 0x93408000 : 0x13000000) | (r << 16) | (s << 10) | (Rn << 5) | Rd;
}

uint32_t lsli(int Rd, int Rn, int shift, unsigned size)
{
  if (size == 4) {
    return ubfm(Rd, Rn, (32 - shift) & 0x1f, 31 - shift, size);
  } else {
    return ubfm(Rd, Rn, (64 - shift) & 0x3f, 63 - shift, size);
  }
}

uint32_t asrv(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0x9ac02800 : 0x1ac02800) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t lsrv(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0x9ac02400 : 0x1ac02400) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t lsri(int Rd, int Rn, int shift, unsigned size)
{
  return ubfm(Rd, Rn, shift, size == 8 ? 63 : 31, size);
}

uint32_t asri(int Rd, int Rn, int shift, unsigned size)
{
  return sbfm(Rd, Rn, shift, size == 8 ? 63 : 31, size);
}

uint32_t sxtb(int Rd, int Rn)
{
  return sbfm(Rd, Rn, 0, 7, 8);
}

uint32_t sxth(int Rd, int Rn)
{
  return sbfm(Rd, Rn, 0, 15, 8);
}

uint32_t uxth(int Rd, int Rn)
{
  return ubfm(Rd, Rn, 0, 15, 4);
}

uint32_t sxtw(int Rd, int Rn)
{
  return sbfm(Rd, Rn, 0, 31, 8);
}

uint32_t br(int Rn)
{
  return 0xd61f0000 | (Rn << 5);
}

uint32_t fmovFdFn(int Fd, int Fn, unsigned size)
{
  return (size == 8 ? 0x1e604000 : 0x1e204000) | (Fn << 5) | Fd;
}

uint32_t fmovRdFn(int Rd, int Fn, unsigned size)
{
  return (size == 8 ? 0x9e660000 : 0x1e260000) | (Fn << 5) | Rd;
}

uint32_t fmovFdRn(int Fd, int Rn, unsigned size)
{
  return (size == 8 ? 0x9e670000 : 0x1e270000) | (Rn << 5) | Fd;
}

uint32_t orr(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0xaa0003e0 : 0x2a0003e0) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t mov(int Rd, int Rn, unsigned size)
{
  return orr(Rd, 31, Rn, size);
}

uint32_t ldrPCRel(int Rd, int offset, unsigned size)
{
  return (size == 8 ? 0x58000000 : 0x18000000) | (offset << 5) | Rd;
}

uint32_t add(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0x8b000000 : 0x0b000000) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t sub(int Rd, int Rn, int Rm, unsigned size)
{
  return (size == 8 ? 0xcb000000 : 0x4b000000) | (Rm << 16) | (Rn << 5) | Rd;
}

uint32_t madd(int Rd, int Rn, int Rm, int Ra, unsigned size)
{
  return (size == 8 ? 0x9b000000 : 0x1b000000)
    | (Rm << 16) | (Ra << 10) | (Rn << 5) | Rd;
}

uint32_t mul(int Rd, int Rn, int Rm, unsigned size)
{
  return madd(Rd, Rn, Rm, 31, size);
}

uint32_t addi(int Rd, int Rn, int value, int shift, unsigned size)
{
  return (size == 8 ? 0x91000000 : 0x11000000) | (shift ? 0x400000 : 0)
    | (value << 10) | (Rn << 5) | Rd;
}

uint32_t subi(int Rd, int Rn, int value, int shift, unsigned size)
{
  return (size == 8 ? 0xd1000000 : 0x51000000) | (shift ? 0x400000 : 0)
    | (value << 10) | (Rn << 5) | Rd;
}

} // namespace

namespace avian {
namespace codegen {
namespace arm {

using namespace isa;
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
    append(c, lsli(dst->low, b->low, value, 4));
  } else (size == 8 and (value & 0x3F)) {
    append(c, lsli(dst->low, b->low, value, 8));
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
    append(c, lsri(dst->low, b->low, value, 4), 4);
  } else (size == 8 and (value & 0x3F)) {
    append(c, lsri(dst->low, b->low, value, 8), 8);
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
    append(c, asri(dst->low, b->low, value, 4), 4);
  } else (size == 8 and (value & 0x3F)) {
    append(c, asri(dst->low, b->low, value, 8), 8);
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
      append(c, fmovRdFn(fpr(dst), fpr(src), srcSize));
    } else {
      append(c, fmovFdRn(fpr(dst), fpr(src), srcSize));
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
    aapend(c, uxth(dst->low, src->low));
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
    lir::Register tmp(c->client->acquireTemporary(GPR_MASK));
    moveCR(c, size, src, size, &tmp);
    moveRR(c, size, &tmp, size, dst);
    c->client->releaseTemporary(tmp.low);
  } else if (src->value->resolved()) {
    int64_t value = src->value->value();
    if (value > 0) {
      append(c, mov(dst->low, value & 0xFFFF));
      if (value >> 16) {
        append(c, movk(dst->low, (value >> 16) & 0xFFFF), 16);
        if (value >> 32) {
          append(c, movk(dst->low, (value >> 32) & 0xFFFF), 32);
          if (value >> 48) {
            append(c, movk(dst->low, (value >> 48) & 0xFFFF), 48);
          }
        }
      }
    } else if (value < 0) {
      append(c, movn(dst->low, (~value) & 0xFFFF));
      if (~(value >> 16)) {
        append(c, movk(dst->low, (value >> 16) & 0xFFFF), 16);
        if (~(value >> 32)) {
          append(c, movk(dst->low, (value >> 32) & 0xFFFF), 32);
          if (~(value >> 48)) {
            append(c, movk(dst->low, (value >> 48) & 0xFFFF), 48);
          }
        }
      }
    }
  } else {
    appendConstantPoolEntry(c, src->value, callOffset);
    append(c, ldrPCRel(dst->low, 0));
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
  append(c, add(dst, a, b, size));
}

void subR(Context* c,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  append(c, sub(dst, a, b, size));
}

void addC(Context* c,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  int32_t v = a->value->value();
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
  int32_t v = a->value->value();
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
  append(c, mul(dst->low, a->low, b->low));
}

void floatAbsoluteRR(Context* c,
                     unsigned size,
                     lir::RegisterPair* a,
                     unsigned,
                     lir::RegisterPair* b)
{
  append(c, fabs(fpr(b), fpr(a), size));
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
    append(c, fcvtasWdDn(b->low, fpr(a)));
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
    append(c, scvtfDdWn(fpr(b), b->low));
  } else {
    append(c, scvtfSdWn(fpr(b), b->low));
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
  append(c, fadd(fpr, dst, fpr(b), fpr(a), size));
}

void floatSubtractR(Context* c,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* dst)
{
  append(c, fsub(fpr, dst, fpr(b), fpr(a), size));
}

void floatMultiplyR(Context* c,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* dst)
{
  append(c, fmul(fpr, dst, fpr(b), fpr(a), size));
}

void floatDivideR(Context* c,
                  unsigned size,
                  lir::RegisterPair* a,
                  lir::RegisterPair* b,
                  lir::RegisterPair* dst)
{
  append(c, fdiv(fpr, dst, fpr(b), fpr(a), size));
}

int normalize(Context* c,
              int offset,
              int index,
              unsigned scale,
              bool* preserveIndex,
              bool* release)
{
  if (offset != 0 or scale != 1) {
    lir::Register normalizedIndex(
        *preserveIndex ? con->client->acquireTemporary(GPR_MASK) : index);

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
      lir::Register untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      lir::Constant offsetConstant(&offsetPromise);

      lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
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
      con->client->releaseTemporary(tmp.low);
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
           int base,
           int offset,
           int index,
           unsigned scale,
           bool preserveIndex)
{
  if (index != lir::NoRegister) {
    bool release;
    int normalized
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
  } else if (abs(offset) == (abs(offset) & 0xFF)) {
    if (isFpr(src)) {
      switch (size) {
      case 4:
      case 8:
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
        append(c, strhi(src->low, base, offset));
        break;

      case 4:
      case 8:
        append(c, stri(src->low, base, offset, size));
        break;

      default:
        abort(c);
      }
    }
  } else {
    lir::Register tmp(c->client->acquireTemporary(GPR_MASK));
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

  store(
      c, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
}

void load(Context* c,
          unsigned srcSize,
          int base,
          int offset,
          int index,
          unsigned scale,
          unsigned dstSize,
          lir::RegisterPair* dst,
          bool preserveIndex,
          bool signExtend)
{
  if (index != lir::NoRegister) {
    bool release;
    int normalized
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
  } else if (abs(offset) == (abs(offset) & 0xFF)) {
    if (isFpr(dst)) {
      switch (srcSize) {
      case 4:
      case 8:
        append(c, ldriFd(fpr(dst->low), base, offset));
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
        if (signExtend) {
          append(c, ldrshi(dst->low, base, offset));
        } else {
          append(c, ldrhi(dst->low, base, offset));
        }
        break;

      case 4:
      case 8:
        if (signExtend and srcSize == 4 and dstSize == 8) {
          append(c, ldrswi(dst->low, base, offset));
        } else {
          append(c, ldri(dst->low, base, offset, size));
        }
        break;

      default:
        abort(c);
      }
    }
  } else {
    lir::Register tmp(c->client->acquireTemporary(GPR_MASK));
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
    lir::Register tmp(dst->low);
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
  assertT(c, srcSize == TargetBytesPerWord and dstSize == TargetBytesPerWord);

  lir::Constant constant(src->address);
  moveCR(c, srcSize, &constant, dstSize, dst);

  lir::Memory memory(dst->low, 0, -1, 0);
  moveMR(c, dstSize, &memory, dstSize, dst);
}

void compareRR(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b)
{
  assertT(c, not (isFpr(a) xor isFpr(b)));
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
               unsigned bSize,
               lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int32_t v = a->value->value();
  if (v) {
    if (v > 0 and v < 0x1000) {
      append(c, cmpi(b->low, v, 0, size));
    } else if (v > 0 and v < 0x1000000 and v % 0x1000 == 0) {
      append(c, cmpi(b->low, v >> 12, 12, size));
    } else {
      // todo
      abort(c);
    }
  }
}

void compareCM(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(c, aSize == bSize);

  lir::Register tmp(c->client->acquireTemporary(GPR_MASK));
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

  lir::Register tmp(c->client->acquireTemporary(GPR_MASK));
  moveMR(c, bSize, b, bSize, &tmp);
  compareRR(c, aSize, a, bSize, &tmp);
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
  appendOffsetTask(c, target->value, offsetPromise(con));
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

  compareRM(c, size, a, size, b);
  branch(c, op, target);
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
  return new (con->zone) ShiftMaskPromise(base, shift, mask);
}

void moveCM(Context* c,
            unsigned srcSize,
            lir::Constant* src,
            unsigned dstSize,
            lir::Memory* dst)
{
  switch (dstSize) {
  case 8: {
    lir::Constant srcHigh(shiftMaskPromise(c, src->value, 32, 0xFFFFFFFF));
    lir::Constant srcLow(shiftMaskPromise(c, src->value, 0, 0xFFFFFFFF));

    lir::Memory dstLow(dst->base, dst->offset + 4, dst->index, dst->scale);

    moveCM(c, 4, &srcLow, 4, &dstLow);
    moveCM(c, 4, &srcHigh, 4, dst);
  } break;

  default:
    lir::Register tmp(con->client->acquireTemporary(GPR_MASK));
    moveCR(c, srcSize, src, dstSize, &tmp);
    moveRM(c, dstSize, &tmp, dstSize, dst);
    con->client->releaseTemporary(tmp.low);
  }
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

  lir::Register tmp(9);  // a non-arg reg that we don't mind clobbering
  moveCR2(c, vm::TargetBytesPerWord, target, &tmp, offsetPromise(c));
  callR(c, vm::TargetBytesPerWord, &tmp);
}

void longJumpC(Context* c, unsigned size UNUSED, lir::Constant* target)
{
  assertT(c, size == vm::TargetBytesPerWord);

  lir::Register tmp(9);  // a non-arg reg that we don't mind clobbering
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
  append(c, dmb());
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

#endif // AVIAN_TARGET_ARCH == AVIAN_ARCH_ARM64
