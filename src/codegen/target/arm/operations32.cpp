/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "operations.h"
#include "encode.h"
#include "block.h"
#include "fixup.h"
#include "multimethod.h"

#if TARGET_BYTES_PER_WORD == 4

namespace avian {
namespace codegen {
namespace arm {

using namespace isa;
using namespace avian::util;

inline bool isOfWidth(int64_t i, int size)
{
  return static_cast<uint64_t>(i) >> size == 0;
}

inline unsigned lo8(int64_t i)
{
  return (unsigned)(i & MASK_LO8);
}

void andC(Context* con,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst);

void shiftLeftR(Context* con,
                unsigned size,
                lir::RegisterPair* a,
                lir::RegisterPair* b,
                lir::RegisterPair* t)
{
  if (size == 8) {
    Register tmp1 = newTemp(con), tmp2 = newTemp(con), tmp3 = newTemp(con);
    ResolvedPromise maskPromise(0x3F);
    lir::Constant mask(&maskPromise);
    lir::RegisterPair dst(tmp3);
    andC(con, 4, &mask, a, &dst);
    emit(con, lsl(tmp1, b->high, tmp3));
    emit(con, rsbi(tmp2, tmp3, 32));
    emit(con, orrsh(tmp1, tmp1, b->low, tmp2, LSR));
    emit(con, SETS(subi(t->high, tmp3, 32)));
    emit(con, SETCOND(mov(t->high, tmp1), MI));
    emit(con, SETCOND(lsl(t->high, b->low, t->high), PL));
    emit(con, lsl(t->low, b->low, tmp3));
    freeTemp(con, tmp1);
    freeTemp(con, tmp2);
    freeTemp(con, tmp3);
  } else {
    Register tmp = newTemp(con);
    ResolvedPromise maskPromise(0x1F);
    lir::Constant mask(&maskPromise);
    lir::RegisterPair dst(tmp);
    andC(con, size, &mask, a, &dst);
    emit(con, lsl(t->low, b->low, tmp));
    freeTemp(con, tmp);
  }
}

void moveRR(Context* con,
            unsigned srcSize,
            lir::RegisterPair* src,
            unsigned dstSize,
            lir::RegisterPair* dst);

void shiftLeftC(Context* con,
                unsigned size UNUSED,
                lir::Constant* a,
                lir::RegisterPair* b,
                lir::RegisterPair* t)
{
  assertT(con, size == vm::TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, lsli(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void shiftRightR(Context* con,
                 unsigned size,
                 lir::RegisterPair* a,
                 lir::RegisterPair* b,
                 lir::RegisterPair* t)
{
  if (size == 8) {
    Register tmp1 = newTemp(con), tmp2 = newTemp(con), tmp3 = newTemp(con);
    ResolvedPromise maskPromise(0x3F);
    lir::Constant mask(&maskPromise);
    lir::RegisterPair dst(tmp3);
    andC(con, 4, &mask, a, &dst);
    emit(con, lsr(tmp1, b->low, tmp3));
    emit(con, rsbi(tmp2, tmp3, 32));
    emit(con, orrsh(tmp1, tmp1, b->high, tmp2, LSL));
    emit(con, SETS(subi(t->low, tmp3, 32)));
    emit(con, SETCOND(mov(t->low, tmp1), MI));
    emit(con, SETCOND(asr(t->low, b->high, t->low), PL));
    emit(con, asr(t->high, b->high, tmp3));
    freeTemp(con, tmp1);
    freeTemp(con, tmp2);
    freeTemp(con, tmp3);
  } else {
    Register tmp = newTemp(con);
    ResolvedPromise maskPromise(0x1F);
    lir::Constant mask(&maskPromise);
    lir::RegisterPair dst(tmp);
    andC(con, size, &mask, a, &dst);
    emit(con, asr(t->low, b->low, tmp));
    freeTemp(con, tmp);
  }
}

void shiftRightC(Context* con,
                 unsigned size UNUSED,
                 lir::Constant* a,
                 lir::RegisterPair* b,
                 lir::RegisterPair* t)
{
  assertT(con, size == vm::TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, asri(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void unsignedShiftRightR(Context* con,
                         unsigned size,
                         lir::RegisterPair* a,
                         lir::RegisterPair* b,
                         lir::RegisterPair* t)
{
  Register tmpShift = newTemp(con);
  ResolvedPromise maskPromise(size == 8 ? 0x3F : 0x1F);
  lir::Constant mask(&maskPromise);
  lir::RegisterPair dst(tmpShift);
  andC(con, 4, &mask, a, &dst);
  emit(con, lsr(t->low, b->low, tmpShift));
  if (size == 8) {
    Register tmpHi = newTemp(con), tmpLo = newTemp(con);
    emit(con, SETS(rsbi(tmpHi, tmpShift, 32)));
    emit(con, lsl(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, addi(tmpHi, tmpShift, -32));
    emit(con, lsr(tmpLo, b->high, tmpHi));
    emit(con, orr(t->low, t->low, tmpLo));
    emit(con, lsr(t->high, b->high, tmpShift));
    freeTemp(con, tmpHi);
    freeTemp(con, tmpLo);
  }
  freeTemp(con, tmpShift);
}

void unsignedShiftRightC(Context* con,
                         unsigned size UNUSED,
                         lir::Constant* a,
                         lir::RegisterPair* b,
                         lir::RegisterPair* t)
{
  assertT(con, size == vm::TargetBytesPerWord);
  if (getValue(a) & 0x1F) {
    emit(con, lsri(t->low, b->low, getValue(a) & 0x1F));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void jumpR(Context* con, unsigned size UNUSED, lir::RegisterPair* target)
{
  assertT(con, size == vm::TargetBytesPerWord);
  emit(con, bx(target->low));
}

void swapRR(Context* con,
            unsigned aSize,
            lir::RegisterPair* a,
            unsigned bSize,
            lir::RegisterPair* b)
{
  assertT(con, aSize == vm::TargetBytesPerWord);
  assertT(con, bSize == vm::TargetBytesPerWord);

  lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
  moveRR(con, aSize, a, bSize, &tmp);
  moveRR(con, bSize, b, aSize, a);
  moveRR(con, bSize, &tmp, bSize, b);
  con->client->releaseTemporary(tmp.low);
}

void moveRR(Context* con,
            unsigned srcSize,
            lir::RegisterPair* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  bool srcIsFpr = isFpr(src);
  bool dstIsFpr = isFpr(dst);
  if (srcIsFpr || dstIsFpr) {  // FPR(s) involved
    assertT(con, srcSize == dstSize);
    const bool dprec = srcSize == 8;
    if (srcIsFpr && dstIsFpr) {  // FPR to FPR
      if (dprec)
        emit(con, fcpyd(fpr64(dst), fpr64(src)));  // double
      else
        emit(con, fcpys(fpr32(dst), fpr32(src)));  // single
    } else if (srcIsFpr) {                         // FPR to GPR
      if (dprec)
        emit(con, fmrrd(dst->low, dst->high, fpr64(src)));
      else
        emit(con, fmrs(dst->low, fpr32(src)));
    } else {  // GPR to FPR
      if (dprec)
        emit(con, fmdrr(fpr64(dst->low), src->low, src->high));
      else
        emit(con, fmsr(fpr32(dst), src->low));
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
      lir::RegisterPair srcHigh(src->high);
      lir::RegisterPair dstHigh(dst->high);

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

  default:
    abort(con);
  }
}

void moveZRR(Context* con,
             unsigned srcSize,
             lir::RegisterPair* src,
             unsigned,
             lir::RegisterPair* dst)
{
  switch (srcSize) {
  case 2:
    emit(con, lsli(dst->low, src->low, 16));
    emit(con, lsri(dst->low, dst->low, 16));
    break;

  default:
    abort(con);
  }
}

void moveCR(Context* con,
            unsigned size,
            lir::Constant* src,
            unsigned,
            lir::RegisterPair* dst);

void moveCR2(Context* con,
             unsigned size,
             lir::Constant* src,
             lir::RegisterPair* dst,
             Promise* callOffset)
{
  if (isFpr(dst)) {  // floating-point
    lir::RegisterPair tmp = size > 4 ? makeTemp64(con) : makeTemp(con);
    moveCR(con, size, src, size, &tmp);
    moveRR(con, size, &tmp, size, dst);
    freeTemp(con, tmp);
  } else if (size > 4) {
    uint64_t value = (uint64_t)src->value->value();
    ResolvedPromise loBits(value & MASK_LO32);
    lir::Constant srcLo(&loBits);
    ResolvedPromise hiBits(value >> 32);
    lir::Constant srcHi(&hiBits);
    lir::RegisterPair dstHi(dst->high);
    moveCR(con, 4, &srcLo, 4, dst);
    moveCR(con, 4, &srcHi, 4, &dstHi);
  } else if (callOffset == 0 and src->value->resolved()
             and isOfWidth(getValue(src), 8)) {
    emit(con, movi(dst->low, lo8(getValue(src))));  // fits in immediate
  } else {
    appendConstantPoolEntry(con, src->value, callOffset);
    emit(con, ldri(dst->low, ProgramCounter, 0));  // load 32 bits
  }
}

void moveCR(Context* con,
            unsigned size,
            lir::Constant* src,
            unsigned,
            lir::RegisterPair* dst)
{
  moveCR2(con, size, src, dst, 0);
}

void addR(Context* con,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, SETS(add(t->low, a->low, b->low)));
    emit(con, adc(t->high, a->high, b->high));
  } else {
    emit(con, add(t->low, a->low, b->low));
  }
}

void subR(Context* con,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, SETS(rsb(t->low, a->low, b->low)));
    emit(con, rsc(t->high, a->high, b->high));
  } else {
    emit(con, rsb(t->low, a->low, b->low));
  }
}

void addC(Context* con,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  assertT(con, size == vm::TargetBytesPerWord);

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

void subC(Context* con,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  assertT(con, size == vm::TargetBytesPerWord);

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

void multiplyR(Context* con,
               unsigned size,
               lir::RegisterPair* a,
               lir::RegisterPair* b,
               lir::RegisterPair* t)
{
  if (size == 8) {
    bool useTemporaries = b->low == t->low;
    Register tmpLow = useTemporaries ? con->client->acquireTemporary(GPR_MASK)
                                     : t->low;
    Register tmpHigh = useTemporaries ? con->client->acquireTemporary(GPR_MASK)
                                      : t->high;

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

void floatAbsoluteRR(Context* con,
                     unsigned size,
                     lir::RegisterPair* a,
                     unsigned,
                     lir::RegisterPair* b)
{
  if (size == 8) {
    emit(con, fabsd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fabss(fpr32(b), fpr32(a)));
  }
}

void floatNegateRR(Context* con,
                   unsigned size,
                   lir::RegisterPair* a,
                   unsigned,
                   lir::RegisterPair* b)
{
  if (size == 8) {
    emit(con, fnegd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fnegs(fpr32(b), fpr32(a)));
  }
}

void float2FloatRR(Context* con,
                   unsigned size,
                   lir::RegisterPair* a,
                   unsigned,
                   lir::RegisterPair* b)
{
  if (size == 8) {
    emit(con, fcvtsd(fpr32(b), fpr64(a)));
  } else {
    emit(con, fcvtds(fpr64(b), fpr32(a)));
  }
}

void float2IntRR(Context* con,
                 unsigned size,
                 lir::RegisterPair* a,
                 unsigned,
                 lir::RegisterPair* b)
{
  Register tmp = newTemp(con, FPR_MASK);
  int ftmp = fpr32(tmp);
  if (size == 8) {  // double to int
    emit(con, ftosizd(ftmp, fpr64(a)));
  } else {  // float to int
    emit(con, ftosizs(ftmp, fpr32(a)));
  }  // else thunked
  emit(con, fmrs(b->low, ftmp));
  freeTemp(con, tmp);
}

void int2FloatRR(Context* con,
                 unsigned,
                 lir::RegisterPair* a,
                 unsigned size,
                 lir::RegisterPair* b)
{
  emit(con, fmsr(fpr32(b), a->low));
  if (size == 8) {  // int to double
    emit(con, fsitod(fpr64(b), fpr32(b)));
  } else {  // int to float
    emit(con, fsitos(fpr32(b), fpr32(b)));
  }  // else thunked
}

void floatSqrtRR(Context* con,
                 unsigned size,
                 lir::RegisterPair* a,
                 unsigned,
                 lir::RegisterPair* b)
{
  if (size == 8) {
    emit(con, fsqrtd(fpr64(b), fpr64(a)));
  } else {
    emit(con, fsqrts(fpr32(b), fpr32(a)));
  }
}

void floatAddR(Context* con,
               unsigned size,
               lir::RegisterPair* a,
               lir::RegisterPair* b,
               lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, faddd(fpr64(t), fpr64(a), fpr64(b)));
  } else {
    emit(con, fadds(fpr32(t), fpr32(a), fpr32(b)));
  }
}

void floatSubtractR(Context* con,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, fsubd(fpr64(t), fpr64(b), fpr64(a)));
  } else {
    emit(con, fsubs(fpr32(t), fpr32(b), fpr32(a)));
  }
}

void floatMultiplyR(Context* con,
                    unsigned size,
                    lir::RegisterPair* a,
                    lir::RegisterPair* b,
                    lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, fmuld(fpr64(t), fpr64(a), fpr64(b)));
  } else {
    emit(con, fmuls(fpr32(t), fpr32(a), fpr32(b)));
  }
}

void floatDivideR(Context* con,
                  unsigned size,
                  lir::RegisterPair* a,
                  lir::RegisterPair* b,
                  lir::RegisterPair* t)
{
  if (size == 8) {
    emit(con, fdivd(fpr64(t), fpr64(b), fpr64(a)));
  } else {
    emit(con, fdivs(fpr32(t), fpr32(b), fpr32(a)));
  }
}

Register normalize(Context* con,
                   int offset,
                   Register index,
                   unsigned scale,
                   bool* preserveIndex,
                   bool* release)
{
  if (offset != 0 or scale != 1) {
    lir::RegisterPair normalizedIndex(
        *preserveIndex ? con->client->acquireTemporary(GPR_MASK) : index);

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

      shiftLeftC(con,
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

      lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
      moveCR(con,
             vm::TargetBytesPerWord,
             &offsetConstant,
             vm::TargetBytesPerWord,
             &tmp);
      addR(con,
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

void store(Context* con,
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
    Register normalized
        = normalize(con, offset, index, scale, &preserveIndex, &release);

    if (!isFpr(src)) {  // GPR store
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

      case 8: {  // split into 2 32-bit stores
        lir::RegisterPair srcHigh(src->high);
        store(con, 4, &srcHigh, base, 0, normalized, 1, preserveIndex);
        store(con, 4, src, base, 4, normalized, 1, preserveIndex);
      } break;

      default:
        abort(con);
      }
    } else {  // FPR store
      lir::RegisterPair base_(base), normalized_(normalized),
          absAddr = makeTemp(con);
      // FPR stores have only bases, so we must add the index
      addR(con, vm::TargetBytesPerWord, &base_, &normalized_, &absAddr);
      // double-precision
      if (size == 8)
        emit(con, fstd(fpr64(src), absAddr.low));
      // single-precision
      else
        emit(con, fsts(fpr32(src), absAddr.low));
      freeTemp(con, absAddr);
    }

    if (release)
      con->client->releaseTemporary(normalized);
  } else if (size == 8 or abs(offset) == (abs(offset) & 0xFF)
             or (size != 2 and abs(offset) == (abs(offset) & 0xFFF))) {
    if (!isFpr(src)) {  // GPR store
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

      case 8: {  // split into 2 32-bit stores
        lir::RegisterPair srcHigh(src->high);
        store(con, 4, &srcHigh, base, offset, NoRegister, 1, false);
        store(con, 4, src, base, offset + 4, NoRegister, 1, false);
      } break;

      default:
        abort(con);
      }
    } else {  // FPR store
      // double-precision
      if (size == 8)
        emit(con, fstd(fpr64(src), base, offset));
      // single-precision
      else
        emit(con, fsts(fpr32(src), base, offset));
    }
  } else {
    lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(con,
           vm::TargetBytesPerWord,
           &offsetConstant,
           vm::TargetBytesPerWord,
           &tmp);

    store(con, size, src, base, 0, tmp.low, 1, false);

    con->client->releaseTemporary(tmp.low);
  }
}

void moveRM(Context* con,
            unsigned srcSize,
            lir::RegisterPair* src,
            unsigned dstSize UNUSED,
            lir::Memory* dst)
{
  assertT(con, srcSize == dstSize);

  store(
      con, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
}

void load(Context* con,
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
        = normalize(con, offset, index, scale, &preserveIndex, &release);

    if (!isFpr(dst)) {  // GPR load
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
          load(con, 4, base, 0, normalized, 1, 4, dst, preserveIndex, false);
          moveRR(con, 4, dst, 8, dst);
        } else if (srcSize == 8 and dstSize == 8) {
          lir::RegisterPair dstHigh(dst->high);
          load(con,
               4,
               base,
               0,
               normalized,
               1,
               4,
               &dstHigh,
               preserveIndex,
               false);
          load(con, 4, base, 4, normalized, 1, 4, dst, preserveIndex, false);
        } else {
          emit(con, ldr(dst->low, base, normalized));
        }
      } break;

      default:
        abort(con);
      }
    } else {  // FPR load
      lir::RegisterPair base_(base), normalized_(normalized),
          absAddr = makeTemp(con);
      // VFP loads only have bases, so we must add the index
      addR(con, vm::TargetBytesPerWord, &base_, &normalized_, &absAddr);
      // double-precision
      if (srcSize == 8)
        emit(con, fldd(fpr64(dst), absAddr.low));
      // single-precision
      else
        emit(con, flds(fpr32(dst), absAddr.low));
      freeTemp(con, absAddr);
    }

    if (release)
      con->client->releaseTemporary(normalized);
  } else if ((srcSize == 8 and dstSize == 8)
             or abs(offset) == (abs(offset) & 0xFF)
             or (srcSize != 2 and (srcSize != 1 or not signExtend)
                 and abs(offset) == (abs(offset) & 0xFFF))) {
    if (!isFpr(dst)) {  // GPR load
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
          lir::RegisterPair dstHigh(dst->high);
          load(con, 4, base, offset, NoRegister, 1, 4, &dstHigh, false, false);
          load(con, 4, base, offset + 4, NoRegister, 1, 4, dst, false, false);
        } else {
          emit(con, ldri(dst->low, base, offset));
        }
      } break;

      default:
        abort(con);
      }
    } else {  // FPR load
      // double-precision
      if (srcSize == 8)
        emit(con, fldd(fpr64(dst), base, offset));
      // single-precision
      else
        emit(con, flds(fpr32(dst), base, offset));
    }
  } else {
    lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
    ResolvedPromise offsetPromise(offset);
    lir::Constant offsetConstant(&offsetPromise);
    moveCR(con,
           vm::TargetBytesPerWord,
           &offsetConstant,
           vm::TargetBytesPerWord,
           &tmp);

    load(con, srcSize, base, 0, tmp.low, 1, dstSize, dst, false, signExtend);

    con->client->releaseTemporary(tmp.low);
  }
}

void moveMR(Context* con,
            unsigned srcSize,
            lir::Memory* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  load(con,
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

void moveZMR(Context* con,
             unsigned srcSize,
             lir::Memory* src,
             unsigned dstSize,
             lir::RegisterPair* dst)
{
  load(con,
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

void andR(Context* con,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  if (size == 8)
    emit(con, and_(dst->high, a->high, b->high));
  emit(con, and_(dst->low, a->low, b->low));
}

void andC(Context* con,
          unsigned size,
          lir::Constant* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  int64_t v = a->value->value();

  if (size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::RegisterPair bh(b->high);
    lir::RegisterPair dh(dst->high);

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
        lir::RegisterPair tmp(dst->low);
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

void orR(Context* con,
         unsigned size,
         lir::RegisterPair* a,
         lir::RegisterPair* b,
         lir::RegisterPair* dst)
{
  if (size == 8)
    emit(con, orr(dst->high, a->high, b->high));
  emit(con, orr(dst->low, a->low, b->low));
}

void xorR(Context* con,
          unsigned size,
          lir::RegisterPair* a,
          lir::RegisterPair* b,
          lir::RegisterPair* dst)
{
  if (size == 8)
    emit(con, eor(dst->high, a->high, b->high));
  emit(con, eor(dst->low, a->low, b->low));
}

void moveAR2(Context* con,
             unsigned srcSize,
             lir::Address* src,
             unsigned dstSize,
             lir::RegisterPair* dst)
{
  assertT(con, srcSize == 4 and dstSize == 4);

  lir::Constant constant(src->address);
  moveCR(con, srcSize, &constant, dstSize, dst);

  lir::Memory memory(dst->low, 0, NoRegister, 0);
  moveMR(con, dstSize, &memory, dstSize, dst);
}

void moveAR(Context* con,
            unsigned srcSize,
            lir::Address* src,
            unsigned dstSize,
            lir::RegisterPair* dst)
{
  moveAR2(con, srcSize, src, dstSize, dst);
}

void compareRR(Context* con,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b)
{
  assertT(con, !(isFpr(a) ^ isFpr(b)));  // regs must be of the same type

  if (!isFpr(a)) {  // GPR compare
    assertT(con, aSize == 4 && bSize == 4);
    /**/  // assertT(con, b->low != a->low);
    emit(con, cmp(b->low, a->low));
  } else {  // FPR compare
    assertT(con, aSize == bSize);
    if (aSize == 8)
      emit(con, fcmpd(fpr64(b), fpr64(a)));  // double
    else
      emit(con, fcmps(fpr32(b), fpr32(a)));  // single
    emit(con, fmstat());
  }
}

void compareCR(Context* con,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::RegisterPair* b)
{
  assertT(con, aSize == 4 and bSize == 4);

  if (!isFpr(b) && a->value->resolved() && isOfWidth(a->value->value(), 8)) {
    emit(con, cmpi(b->low, a->value->value()));
  } else {
    lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
    moveCR(con, aSize, a, bSize, &tmp);
    compareRR(con, bSize, &tmp, bSize, b);
    con->client->releaseTemporary(tmp.low);
  }
}

void compareCM(Context* con,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(con, aSize == 4 and bSize == 4);

  lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
  moveMR(con, bSize, b, bSize, &tmp);
  compareCR(con, aSize, a, bSize, &tmp);
  con->client->releaseTemporary(tmp.low);
}

void compareRM(Context* con,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(con, aSize == 4 and bSize == 4);

  lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
  moveMR(con, bSize, b, bSize, &tmp);
  compareRR(con, aSize, a, bSize, &tmp);
  con->client->releaseTemporary(tmp.low);
}

int32_t branch(Context* con, lir::TernaryOperation op)
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

void conditional(Context* con, int32_t branch, lir::Constant* target)
{
  appendOffsetTask(con, target->value, offsetPromise(con));
  emit(con, branch);
}

void branch(Context* con, lir::TernaryOperation op, lir::Constant* target)
{
  conditional(con, branch(con, op), target);
}

void branchLong(Context* con,
                lir::TernaryOperation op,
                lir::Operand* al,
                lir::Operand* ah,
                lir::Operand* bl,
                lir::Operand* bh,
                lir::Constant* target,
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
    updateOffset(con->s,
                 con->code.data.begin() + next,
                 reinterpret_cast<intptr_t>(con->code.data.begin()
                                            + con->code.length()));
  }
}

void branchRR(Context* con,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  if (!isFpr(a) && size > vm::TargetBytesPerWord) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    branchLong(
        con, op, a, &ah, b, &bh, target, CAST2(compareRR), CAST2(compareRR));
  } else {
    compareRR(con, size, a, size, b);
    branch(con, op, target);
  }
}

void branchCR(Context* con,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  assertT(con, !isFloatBranch(op));

  if (size > vm::TargetBytesPerWord) {
    int64_t v = a->value->value();

    ResolvedPromise low(v & ~static_cast<vm::target_uintptr_t>(0));
    lir::Constant al(&low);

    ResolvedPromise high((v >> 32) & ~static_cast<vm::target_uintptr_t>(0));
    lir::Constant ah(&high);

    lir::RegisterPair bh(b->high);

    branchLong(
        con, op, &al, &ah, b, &bh, target, CAST2(compareCR), CAST2(compareCR));
  } else {
    compareCR(con, size, a, size, b);
    branch(con, op, target);
  }
}

void branchRM(Context* con,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::Memory* b,
              lir::Constant* target)
{
  assertT(con, !isFloatBranch(op));
  assertT(con, size <= vm::TargetBytesPerWord);

  compareRM(con, size, a, size, b);
  branch(con, op, target);
}

void branchCM(Context* con,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::Memory* b,
              lir::Constant* target)
{
  assertT(con, !isFloatBranch(op));
  assertT(con, size <= vm::TargetBytesPerWord);

  compareCM(con, size, a, size, b);
  branch(con, op, target);
}

ShiftMaskPromise* shiftMaskPromise(Context* con,
                                   Promise* base,
                                   unsigned shift,
                                   int64_t mask)
{
  return new (con->zone) ShiftMaskPromise(base, shift, mask);
}

void moveCM(Context* con,
            unsigned srcSize,
            lir::Constant* src,
            unsigned dstSize,
            lir::Memory* dst)
{
  switch (dstSize) {
  case 8: {
    lir::Constant srcHigh(shiftMaskPromise(con, src->value, 32, 0xFFFFFFFF));
    lir::Constant srcLow(shiftMaskPromise(con, src->value, 0, 0xFFFFFFFF));

    lir::Memory dstLow(dst->base, dst->offset + 4, dst->index, dst->scale);

    moveCM(con, 4, &srcLow, 4, &dstLow);
    moveCM(con, 4, &srcHigh, 4, dst);
  } break;

  default:
    lir::RegisterPair tmp(con->client->acquireTemporary(GPR_MASK));
    moveCR(con, srcSize, src, dstSize, &tmp);
    moveRM(con, dstSize, &tmp, dstSize, dst);
    con->client->releaseTemporary(tmp.low);
  }
}

void negateRR(Context* con,
              unsigned srcSize,
              lir::RegisterPair* src,
              unsigned dstSize UNUSED,
              lir::RegisterPair* dst)
{
  assertT(con, srcSize == dstSize);

  emit(con, mvn(dst->low, src->low));
  emit(con, SETS(addi(dst->low, dst->low, 1)));
  if (srcSize == 8) {
    emit(con, mvn(dst->high, src->high));
    emit(con, adci(dst->high, dst->high, 0));
  }
}

void callR(Context* con, unsigned size UNUSED, lir::RegisterPair* target)
{
  assertT(con, size == vm::TargetBytesPerWord);
  emit(con, blx(target->low));
}

void callC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assertT(con, size == vm::TargetBytesPerWord);

  appendOffsetTask(con, target->value, offsetPromise(con));
  emit(con, bl(0));
}

void longCallC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assertT(con, size == vm::TargetBytesPerWord);

  lir::RegisterPair tmp(Register(4));
  moveCR2(con, vm::TargetBytesPerWord, target, &tmp, offsetPromise(con));
  callR(con, vm::TargetBytesPerWord, &tmp);
}

void alignedLongCallC(Context* con, unsigned size, lir::Constant* target)
{
  longCallC(con, size, target);
}

void longJumpC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assertT(con, size == vm::TargetBytesPerWord);

  lir::RegisterPair tmp(
      Register(4));  // a non-arg reg that we don't mind clobbering
  moveCR2(con, vm::TargetBytesPerWord, target, &tmp, offsetPromise(con));
  jumpR(con, vm::TargetBytesPerWord, &tmp);
}

void alignedLongJumpC(Context* con, unsigned size, lir::Constant* target)
{
  longJumpC(con, size, target);
}

void jumpC(Context* con, unsigned size UNUSED, lir::Constant* target)
{
  assertT(con, size == vm::TargetBytesPerWord);

  appendOffsetTask(con, target->value, offsetPromise(con));
  emit(con, b(0));
}

void return_(Context* con)
{
  emit(con, bx(LinkRegister));
}

void trap(Context* con)
{
  emit(con, bkpt(0));
}

// todo: determine the minimal operation types and domains needed to
// implement the following barriers (see
// http://community.arm.com/groups/processors/blog/2011/10/19/memory-access-ordering-part-3--memory-access-ordering-in-the-arm-architecture).
// For now, we just use DMB SY as a conservative but not necessarily
// performant choice.

void memoryBarrier(Context* con UNUSED)
{
#ifndef AVIAN_ASSUME_ARMV6
  emit(con, dmb());
#endif
}

void loadBarrier(Context* con)
{
  memoryBarrier(con);
}

void storeStoreBarrier(Context* con)
{
  memoryBarrier(con);
}

void storeLoadBarrier(Context* con)
{
  memoryBarrier(con);
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // TARGET_BYTES_PER_WORD == 4
