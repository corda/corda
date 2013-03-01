/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"
#include "avian/common.h"
#include "encode.h"
#include "operations.h"
#include "fixup.h"
#include "multimethod.h"

using namespace vm;

namespace avian {
namespace codegen {
namespace powerpc {

using namespace isa;
using namespace util;

const int64_t MASK_LO32 = 0x0ffffffff;
const int     MASK_LO16 = 0x0ffff;
const int     MASK_LO8  = 0x0ff;
// inline int lo32(int64_t i) { return (int)(i & MASK_LO32); }
// inline int hi32(int64_t i) { return lo32(i >> 32); }
inline int lo16(int64_t i) { return (int)(i & MASK_LO16); }
inline int hi16(int64_t i) { return lo16(i >> 16); }
// inline int lo8(int64_t i) { return (int)(i & MASK_LO8); }
// inline int hi8(int64_t i) { return lo8(i >> 8); }

inline int carry16(target_intptr_t v) {
  return static_cast<int16_t>(v) < 0 ? 1 : 0;
}

void andC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst);

void shiftLeftR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if(size == 8) {
    lir::Register Tmp(newTemp(con), newTemp(con)); lir::Register* tmp = &Tmp;
    emit(con, subfic(tmp->high, a->low, 32));
    emit(con, slw(t->high, b->high, a->low));
    emit(con, srw(tmp->low, b->low, tmp->high));
    emit(con, or_(t->high, t->high, tmp->low));
    emit(con, addi(tmp->high, a->low, -32));
    emit(con, slw(tmp->low, b->low, tmp->high));
    emit(con, or_(t->high, t->high, tmp->low));
    emit(con, slw(t->low, b->low, a->low));
    freeTemp(con, tmp->high); freeTemp(con, tmp->low);
  } else {
    emit(con, slw(t->low, b->low, a->low));
  }
}

void moveRR(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst);

void shiftLeftC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t) {
  int sh = getValue(a);
  if (size == 8) {
    sh &= 0x3F;
    if (sh) {
      if (sh < 32) {
        emit(con, rlwinm(t->high,b->high,sh,0,31-sh));
        emit(con, rlwimi(t->high,b->low,sh,32-sh,31));
        emit(con, slwi(t->low, b->low, sh));
      } else {
        emit(con, rlwinm(t->high,b->low,sh-32,0,63-sh));
        emit(con, li(t->low,0));
      }
    } else {
      moveRR(con, size, b, size, t);
    }
  } else {
    emit(con, slwi(t->low, b->low, sh & 0x1F));
  }
}

void shiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if(size == 8) {
    lir::Register Tmp(newTemp(con), newTemp(con)); lir::Register* tmp = &Tmp;
    emit(con, subfic(tmp->high, a->low, 32));
    emit(con, srw(t->low, b->low, a->low));
    emit(con, slw(tmp->low, b->high, tmp->high));
    emit(con, or_(t->low, t->low, tmp->low));
    emit(con, addic(tmp->high, a->low, -32));
    emit(con, sraw(tmp->low, b->high, tmp->high));
    emit(con, ble(8));
    emit(con, ori(t->low, tmp->low, 0));
    emit(con, sraw(t->high, b->high, a->low));
    freeTemp(con, tmp->high); freeTemp(con, tmp->low);
  } else {
    emit(con, sraw(t->low, b->low, a->low));
  }
}

void shiftRightC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t) {
  int sh = getValue(a);
  if(size == 8) {
    sh &= 0x3F;
    if (sh) {
      if (sh < 32) {
        emit(con, rlwinm(t->low,b->low,32-sh,sh,31));
        emit(con, rlwimi(t->low,b->high,32-sh,0,sh-1));
        emit(con, srawi(t->high,b->high,sh));
      } else {
        emit(con, srawi(t->high,b->high,31));
        emit(con, srawi(t->low,b->high,sh-32));
      }
    } else {
      moveRR(con, size, b, size, t);
    }
  } else {
    emit(con, srawi(t->low, b->low, sh & 0x1F));
  }
}

void unsignedShiftRightR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  emit(con, srw(t->low, b->low, a->low));
  if(size == 8) {
    lir::Register Tmp(newTemp(con), newTemp(con)); lir::Register* tmp = &Tmp;
    emit(con, subfic(tmp->high, a->low, 32));
    emit(con, slw(tmp->low, b->high, tmp->high));
    emit(con, or_(t->low, t->low, tmp->low));
    emit(con, addi(tmp->high, a->low, -32));
    emit(con, srw(tmp->low, b->high, tmp->high));
    emit(con, or_(t->low, t->low, tmp->low));
    emit(con, srw(t->high, b->high, a->low));
    freeTemp(con, tmp->high); freeTemp(con, tmp->low);
  }
}

void unsignedShiftRightC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t) {
  int sh = getValue(a);
  if (size == 8) {
    if (sh & 0x3F) {
      if (sh == 32) {
        lir::Register high(b->high);
        moveRR(con, 4, &high, 4, t);
        emit(con, li(t->high,0));
      } else if (sh < 32) {
        emit(con, srwi(t->low, b->low, sh));
        emit(con, rlwimi(t->low,b->high,32-sh,0,sh-1));
        emit(con, rlwinm(t->high,b->high,32-sh,sh,31));
      } else {
        emit(con, rlwinm(t->low,b->high,64-sh,sh-32,31));
        emit(con, li(t->high,0));
      }
    } else {
      moveRR(con, size, b, size, t);
    }
  } else {
    if (sh & 0x1F) {
      emit(con, srwi(t->low, b->low, sh & 0x1F));
    } else {
      moveRR(con, size, b, size, t);
    }
  }
}

void jumpR(Context* c, unsigned size UNUSED, lir::Register* target) {
  assert(c, size == TargetBytesPerWord);

  emit(c, mtctr(target->low));
  emit(c, bctr());
}

void swapRR(Context* c, unsigned aSize, lir::Register* a,
       unsigned bSize, lir::Register* b) {
  assert(c, aSize == TargetBytesPerWord);
  assert(c, bSize == TargetBytesPerWord);

  lir::Register tmp(c->client->acquireTemporary());
  moveRR(c, aSize, a, bSize, &tmp);
  moveRR(c, bSize, b, aSize, a);
  moveRR(c, bSize, &tmp, bSize, b);
  c->client->releaseTemporary(tmp.low);
}

void moveRR(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize, lir::Register* dst) {
  switch (srcSize) {
  case 1:
    emit(c, extsb(dst->low, src->low));
    break;
    
  case 2:
    emit(c, extsh(dst->low, src->low));
    break;
    
  case 4:
  case 8:
    if (srcSize == 4 and dstSize == 8) {
      moveRR(c, 4, src, 4, dst);
      emit(c, srawi(dst->high, src->low, 31));
    } else if (srcSize == 8 and dstSize == 8) {
      lir::Register srcHigh(src->high);
      lir::Register dstHigh(dst->high);

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
      emit(c, mr(dst->low, src->low));
    }
    break;

  default: abort(c);
  }
}

void moveZRR(Context* c, unsigned srcSize, lir::Register* src,
        unsigned, lir::Register* dst) {
  switch (srcSize) {
  case 2:
    emit(c, andi(dst->low, src->low, 0xFFFF));
    break;

  default: abort(c);
  }
}

void moveCR2(Context* c, unsigned, lir::Constant* src,
       unsigned dstSize, lir::Register* dst, unsigned promiseOffset) {
  if (dstSize <= 4) {
    if (src->value->resolved()) {
      int32_t v = src->value->value();
      if (fitsInInt16(v)) {
        emit(c, li(dst->low, v));
      } else {
        emit(c, lis(dst->low, v >> 16));
        emit(c, ori(dst->low, dst->low, v));
      }
    } else {
      appendImmediateTask
        (c, src->value, offsetPromise(c), TargetBytesPerWord, promiseOffset, false);
      emit(c, lis(dst->low, 0));
      emit(c, ori(dst->low, dst->low, 0));
    }
  } else {
    abort(c); // todo
  }
}

void moveCR(Context* c, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Register* dst) {
  moveCR2(c, srcSize, src, dstSize, dst, 0);
}

void addR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if(size == 8) {
    emit(con, addc(t->low, a->low, b->low));
    emit(con, adde(t->high, a->high, b->high));
  } else {
    emit(con, add(t->low, a->low, b->low));
  }
}

void addC(Context* con, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t) {
  assert(con, size == TargetBytesPerWord);

  int32_t i = getValue(a);
  if(i) {
    emit(con, addi(t->low, b->low, lo16(i)));
    if(not fitsInInt16(i))
      emit(con, addis(t->low, t->low, hi16(i) + carry16(i)));
  } else {
    moveRR(con, size, b, size, t);
  }
}

void subR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if(size == 8) {
    emit(con, subfc(t->low, a->low, b->low));
    emit(con, subfe(t->high, a->high, b->high));
  } else {
    emit(con, subf(t->low, a->low, b->low));
  }
}

void subC(Context* c, unsigned size, lir::Constant* a, lir::Register* b, lir::Register* t) {
  assert(c, size == TargetBytesPerWord);

  ResolvedPromise promise(- a->value->value());
  lir::Constant constant(&promise);
  addC(c, size, &constant, b, t);
}

void multiplyR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  if(size == 8) {
    bool useTemporaries = b->low == t->low;
    int tmpLow;
    int tmpHigh;
    if (useTemporaries) {
      tmpLow = con->client->acquireTemporary();
      tmpHigh = con->client->acquireTemporary();
    } else {
      tmpLow = t->low;
      tmpHigh = t->high;
    }

    emit(con, mullw(tmpHigh, a->high, b->low));
    emit(con, mullw(tmpLow, a->low, b->high));
    emit(con, add(t->high, tmpHigh, tmpLow));
    emit(con, mulhwu(tmpLow, a->low, b->low));
    emit(con, add(t->high, t->high, tmpLow));
    emit(con, mullw(t->low, a->low, b->low));

    if (useTemporaries) {
      con->client->releaseTemporary(tmpLow);
      con->client->releaseTemporary(tmpHigh);
    }
  } else {
    emit(con, mullw(t->low, a->low, b->low));
  }
}

void divideR(Context* con, unsigned size UNUSED, lir::Register* a, lir::Register* b, lir::Register* t) {
  assert(con, size == 4);
  emit(con, divw(t->low, b->low, a->low));
}

void remainderR(Context* con, unsigned size, lir::Register* a, lir::Register* b, lir::Register* t) {
  bool useTemporary = b->low == t->low;
  lir::Register tmp(t->low);
  if (useTemporary) {
    tmp.low = con->client->acquireTemporary();
  }

  divideR(con, size, a, b, &tmp);
  multiplyR(con, size, a, &tmp, &tmp);
  subR(con, size, &tmp, b, t);

  if (useTemporary) {
    con->client->releaseTemporary(tmp.low);
  }
}

int
normalize(Context* c, int offset, int index, unsigned scale, 
          bool* preserveIndex, bool* release) {
  if (offset != 0 or scale != 1) {
    lir::Register normalizedIndex
      (*preserveIndex ? c->client->acquireTemporary() : index);
    
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
      
      shiftLeftC(c, TargetBytesPerWord, &scaleConstant,
                 &unscaledIndex, &normalizedIndex);

      scaled = normalizedIndex.low;
    } else {
      scaled = index;
    }

    if (offset != 0) {
      lir::Register untranslatedIndex(scaled);

      ResolvedPromise offsetPromise(offset);
      lir::Constant offsetConstant(&offsetPromise);

      addC(c, TargetBytesPerWord, &offsetConstant,
           &untranslatedIndex, &normalizedIndex);
    }

    return normalizedIndex.low;
  } else {
    *release = false;
    return index;
  }
}

void store(Context* c, unsigned size, lir::Register* src,
      int base, int offset, int index, unsigned scale, bool preserveIndex) {
  if (index != lir::NoRegister) {
    bool release;
    int normalized = normalize
      (c, offset, index, scale, &preserveIndex, &release);

    switch (size) {
    case 1:
      emit(c, stbx(src->low, base, normalized));
      break;

    case 2:
      emit(c, sthx(src->low, base, normalized));
      break;

    case 4:
      emit(c, stwx(src->low, base, normalized));
      break;

    case 8: {
      lir::Register srcHigh(src->high);
      store(c, 4, &srcHigh, base, 0, normalized, 1, preserveIndex);
      store(c, 4, src, base, 4, normalized, 1, preserveIndex);
    } break;

    default: abort(c);
    }

    if (release) c->client->releaseTemporary(normalized);
  } else {
    switch (size) {
    case 1:
      emit(c, stb(src->low, base, offset));
      break;

    case 2:
      emit(c, sth(src->low, base, offset));
      break;

    case 4:
      emit(c, stw(src->low, base, offset));
      break;

    case 8: {
      lir::Register srcHigh(src->high);
      store(c, 4, &srcHigh, base, offset, lir::NoRegister, 1, false);
      store(c, 4, src, base, offset + 4, lir::NoRegister, 1, false);
    } break;

    default: abort(c);
    }
  }
}

void moveRM(Context* c, unsigned srcSize, lir::Register* src,
       unsigned dstSize UNUSED, lir::Memory* dst) {
  assert(c, srcSize == dstSize);

  store(c, srcSize, src, dst->base, dst->offset, dst->index, dst->scale, true);
}

void moveAndUpdateRM(Context* c, unsigned srcSize UNUSED, lir::Register* src,
                unsigned dstSize UNUSED, lir::Memory* dst) {
  assert(c, srcSize == TargetBytesPerWord);
  assert(c, dstSize == TargetBytesPerWord);

  if (dst->index == lir::NoRegister) {
    emit(c, stwu(src->low, dst->base, dst->offset));
  } else {
    assert(c, dst->offset == 0);
    assert(c, dst->scale == 1);
    
    emit(c, stwux(src->low, dst->base, dst->index));
  }
}

void load(Context* c, unsigned srcSize, int base, int offset, int index,
     unsigned scale, unsigned dstSize, lir::Register* dst,
     bool preserveIndex, bool signExtend) {
  if (index != lir::NoRegister) {
    bool release;
    int normalized = normalize
      (c, offset, index, scale, &preserveIndex, &release);

    switch (srcSize) {
    case 1:
      emit(c, lbzx(dst->low, base, normalized));
      if (signExtend) {
        emit(c, extsb(dst->low, dst->low));
      }
      break;

    case 2:
      if (signExtend) {
        emit(c, lhax(dst->low, base, normalized));
      } else {
        emit(c, lhzx(dst->low, base, normalized));
      }
      break;

    case 4:
    case 8: {
      if (srcSize == 4 and dstSize == 8) {
        load(c, 4, base, 0, normalized, 1, 4, dst, preserveIndex, false);
        moveRR(c, 4, dst, 8, dst);
      } else if (srcSize == 8 and dstSize == 8) {
        lir::Register dstHigh(dst->high);
        load(c, 4, base, 0, normalized, 1, 4, &dstHigh, preserveIndex, false);
        load(c, 4, base, 4, normalized, 1, 4, dst, preserveIndex, false);
      } else {
        emit(c, lwzx(dst->low, base, normalized));
      }
    } break;

    default: abort(c);
    }

    if (release) c->client->releaseTemporary(normalized);
  } else {
    switch (srcSize) {
    case 1:
      emit(c, lbz(dst->low, base, offset));
      if (signExtend) {
        emit(c, extsb(dst->low, dst->low));
      }
      break;

    case 2:
      if (signExtend) {
        emit(c, lha(dst->low, base, offset));
      } else {
        emit(c, lha(dst->low, base, offset));
      }
      break;

    case 4:
      emit(c, lwz(dst->low, base, offset));
      break;

    case 8: {
      if (dstSize == 8) {
        lir::Register dstHigh(dst->high);
        load(c, 4, base, offset, lir::NoRegister, 1, 4, &dstHigh, false, false);
        load(c, 4, base, offset + 4, lir::NoRegister, 1, 4, dst, false, false);
      } else {
        emit(c, lwzx(dst->low, base, offset));
      }
    } break;

    default: abort(c);
    }
  }
}

void moveMR(Context* c, unsigned srcSize, lir::Memory* src,
       unsigned dstSize, lir::Register* dst) {
  load(c, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true, true);
}

void moveZMR(Context* c, unsigned srcSize, lir::Memory* src,
        unsigned dstSize, lir::Register* dst) {
  load(c, srcSize, src->base, src->offset, src->index, src->scale,
       dstSize, dst, true, false);
}

void andR(Context* c, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst) {
  if (size == 8) {
    lir::Register ah(a->high);
    lir::Register bh(b->high);
    lir::Register dh(dst->high);
    
    andR(c, 4, a, b, dst);
    andR(c, 4, &ah, &bh, &dh);
  } else {
    emit(c, and_(dst->low, a->low, b->low));
  }
}

void andC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst) {
  int64_t v = a->value->value();

  if (size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::Register bh(b->high);
    lir::Register dh(dst->high);

    andC(c, 4, &al, b, dst);
    andC(c, 4, &ah, &bh, &dh);
  } else {
    // bitmasks of the form regex 0*1*0* can be handled in a single
    // rlwinm instruction, hence the following:

    uint32_t v32 = static_cast<uint32_t>(v);
    unsigned state = 0;
    unsigned start = 0;
    unsigned end = 31;
    for (unsigned i = 0; i < 32; ++i) {
      unsigned bit = (v32 >> i) & 1;
      switch (state) {
      case 0:
        if (bit) {
          start = i;
          state = 1;
        }
        break;

      case 1:
        if (bit == 0) {
          end = i - 1;
          state = 2;
        }
        break;

      case 2:
        if (bit) {
          // not in 0*1*0* form.  We can only use andi(s) if either
          // the topmost or bottommost 16 bits are zero.

          if ((v32 >> 16) == 0) {
            emit(c, andi(dst->low, b->low, v32));
          } else if ((v32 & 0xFFFF) == 0) {
            emit(c, andis(dst->low, b->low, v32 >> 16));
          } else {
            bool useTemporary = b->low == dst->low;
            lir::Register tmp(dst->low);
            if (useTemporary) {
              tmp.low = c->client->acquireTemporary();
            }

            moveCR(c, 4, a, 4, &tmp);
            andR(c, 4, b, &tmp, dst);

            if (useTemporary) {
              c->client->releaseTemporary(tmp.low);
            }
          }
          return;
        }
        break;
      }
    }

    if (state) {
      if (start != 0 or end != 31) {
        emit(c, rlwinm(dst->low, b->low, 0, 31 - end, 31 - start));
      } else {
        moveRR(c, 4, b, 4, dst);
      }
    } else {
      emit(c, li(dst->low, 0));
    }
  }
}

void orR(Context* c, unsigned size, lir::Register* a,
    lir::Register* b, lir::Register* dst) {
  if (size == 8) {
    lir::Register ah(a->high);
    lir::Register bh(b->high);
    lir::Register dh(dst->high);
    
    orR(c, 4, a, b, dst);
    orR(c, 4, &ah, &bh, &dh);
  } else {
    emit(c, or_(dst->low, a->low, b->low));
  }
}

void orC(Context* c, unsigned size, lir::Constant* a,
    lir::Register* b, lir::Register* dst) {
  int64_t v = a->value->value();

  if (size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::Register bh(b->high);
    lir::Register dh(dst->high);

    orC(c, 4, &al, b, dst);
    orC(c, 4, &ah, &bh, &dh);
  } else {
    emit(c, ori(b->low, dst->low, v));
    if (v >> 16) {
      emit(c, oris(dst->low, dst->low, v >> 16));
    }
  }
}

void xorR(Context* c, unsigned size, lir::Register* a,
     lir::Register* b, lir::Register* dst) {
  if (size == 8) {
    lir::Register ah(a->high);
    lir::Register bh(b->high);
    lir::Register dh(dst->high);
    
    xorR(c, 4, a, b, dst);
    xorR(c, 4, &ah, &bh, &dh);
  } else {
    emit(c, xor_(dst->low, a->low, b->low));
  }
}

void xorC(Context* c, unsigned size, lir::Constant* a,
     lir::Register* b, lir::Register* dst) {
  uint64_t v = a->value->value();

  if (size == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::Register bh(b->high);
    lir::Register dh(dst->high);

    xorC(c, 4, &al, b, dst);
    xorC(c, 4, &ah, &bh, &dh);
  } else {
    if (v >> 16) {
      emit(c, xoris(b->low, dst->low, v >> 16));
      emit(c, xori(dst->low, dst->low, v));
    } else {
      emit(c, xori(b->low, dst->low, v));
    }
  }
}

void moveAR2(Context* c, unsigned srcSize UNUSED, lir::Address* src,
        unsigned dstSize, lir::Register* dst, unsigned promiseOffset) {
  assert(c, srcSize == 4 and dstSize == 4);

  lir::Memory memory(dst->low, 0, -1, 0);
  
  appendImmediateTask
    (c, src->address, offsetPromise(c), TargetBytesPerWord, promiseOffset, true);
  
  emit(c, lis(dst->low, 0));
  moveMR(c, dstSize, &memory, dstSize, dst);
}

void moveAR(Context* c, unsigned srcSize, lir::Address* src,
       unsigned dstSize, lir::Register* dst) {
  moveAR2(c, srcSize, src, dstSize, dst, 0);
}

void compareRR(Context* c, unsigned aSize UNUSED, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b) {
  assert(c, aSize == 4 and bSize == 4);
  
  emit(c, cmpw(b->low, a->low));
}

void compareCR(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b) {
  assert(c, aSize == 4 and bSize == 4);

  if (a->value->resolved() and fitsInInt16(a->value->value())) {
    emit(c, cmpwi(b->low, a->value->value()));
  } else {
    lir::Register tmp(c->client->acquireTemporary());
    moveCR(c, aSize, a, bSize, &tmp);
    compareRR(c, bSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void compareCM(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Memory* b) {
  assert(c, aSize == 4 and bSize == 4);

  lir::Register tmp(c->client->acquireTemporary());
  moveMR(c, bSize, b, bSize, &tmp);
  compareCR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void compareRM(Context* c, unsigned aSize, lir::Register* a,
          unsigned bSize, lir::Memory* b) {
  assert(c, aSize == 4 and bSize == 4);

  lir::Register tmp(c->client->acquireTemporary());
  moveMR(c, bSize, b, bSize, &tmp);
  compareRR(c, aSize, a, bSize, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void compareUnsignedRR(Context* c, unsigned aSize UNUSED, lir::Register* a,
                  unsigned bSize UNUSED, lir::Register* b) {
  assert(c, aSize == 4 and bSize == 4);
  
  emit(c, cmplw(b->low, a->low));
}

void compareUnsignedCR(Context* c, unsigned aSize, lir::Constant* a,
                  unsigned bSize, lir::Register* b) {
  assert(c, aSize == 4 and bSize == 4);

  if (a->value->resolved() and (a->value->value() >> 16) == 0) {
    emit(c, cmplwi(b->low, a->value->value()));
  } else {
    lir::Register tmp(c->client->acquireTemporary());
    moveCR(c, aSize, a, bSize, &tmp);
    compareUnsignedRR(c, bSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

int32_t
branch(Context* c, lir::TernaryOperation op) {
  switch (op) {
  case lir::JumpIfEqual:
    return beq(0);
    
  case lir::JumpIfNotEqual:
    return bne(0);
    
  case lir::JumpIfLess:
    return blt(0);
    
  case lir::JumpIfGreater:
    return bgt(0);
    
  case lir::JumpIfLessOrEqual:
    return ble(0);
    
  case lir::JumpIfGreaterOrEqual:
    return bge(0);
    
  default:
    abort(c);
  }
}

void conditional(Context* c, int32_t branch, lir::Constant* target) {
  appendOffsetTask(c, target->value, offsetPromise(c), true);
  emit(c, branch);
}

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target) {
  conditional(c, branch(c, op), target);
}

void branchLong(Context* c, lir::TernaryOperation op, lir::Operand* al,
           lir::Operand* ah, lir::Operand* bl,
           lir::Operand* bh, lir::Constant* target,
           BinaryOperationType compareSigned,
           BinaryOperationType compareUnsigned) {
  compareSigned(c, 4, ah, 4, bh);

  unsigned next = 0;
  
  switch (op) {
  case lir::JumpIfEqual:
    next = c->code.length();
    emit(c, bne(0));

    compareSigned(c, 4, al, 4, bl);
    conditional(c, beq(0), target);
    break;

  case lir::JumpIfNotEqual:
    conditional(c, bne(0), target);

    compareSigned(c, 4, al, 4, bl);
    conditional(c, bne(0), target);
    break;

  case lir::JumpIfLess:
    conditional(c, blt(0), target);

    next = c->code.length();
    emit(c, bgt(0));

    compareUnsigned(c, 4, al, 4, bl);
    conditional(c, blt(0), target);
    break;

  case lir::JumpIfGreater:
    conditional(c, bgt(0), target);

    next = c->code.length();
    emit(c, blt(0));

    compareUnsigned(c, 4, al, 4, bl);
    conditional(c, bgt(0), target);
    break;

  case lir::JumpIfLessOrEqual:
    conditional(c, blt(0), target);

    next = c->code.length();
    emit(c, bgt(0));

    compareUnsigned(c, 4, al, 4, bl);
    conditional(c, ble(0), target);
    break;

  case lir::JumpIfGreaterOrEqual:
    conditional(c, bgt(0), target);

    next = c->code.length();
    emit(c, blt(0));

    compareUnsigned(c, 4, al, 4, bl);
    conditional(c, bge(0), target);
    break;

  default:
    abort(c);
  }

  if (next) {
    updateOffset
      (c->s, c->code.data + next, true, reinterpret_cast<intptr_t>
       (c->code.data + c->code.length()), 0);
  }
}

void branchRR(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Register* b,
         lir::Constant* target) {
  if (size > TargetBytesPerWord) {
    lir::Register ah(a->high);
    lir::Register bh(b->high);

    branchLong(c, op, a, &ah, b, &bh, target, CAST2(compareRR),
               CAST2(compareUnsignedRR));
  } else {
    compareRR(c, size, a, size, b);
    branch(c, op, target);
  }
}

void branchCR(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Register* b,
         lir::Constant* target) {
  if (size > TargetBytesPerWord) {
    int64_t v = a->value->value();

    ResolvedPromise low(v & ~static_cast<target_uintptr_t>(0));
    lir::Constant al(&low);

    ResolvedPromise high((v >> 32) & ~static_cast<target_uintptr_t>(0));
    lir::Constant ah(&high);

    lir::Register bh(b->high);

    branchLong(c, op, &al, &ah, b, &bh, target, CAST2(compareCR),
               CAST2(compareUnsignedCR));
  } else {
    compareCR(c, size, a, size, b);
    branch(c, op, target);
  }
}

void branchRM(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Register* a, lir::Memory* b,
         lir::Constant* target) {
  assert(c, size <= TargetBytesPerWord);

  compareRM(c, size, a, size, b);
  branch(c, op, target);
}

void branchCM(Context* c, lir::TernaryOperation op, unsigned size,
         lir::Constant* a, lir::Memory* b,
         lir::Constant* target) {
  assert(c, size <= TargetBytesPerWord);

  compareCM(c, size, a, size, b);
  branch(c, op, target);
}

void moveCM(Context* c, unsigned srcSize, lir::Constant* src,
       unsigned dstSize, lir::Memory* dst) {
  switch (dstSize) {
  case 8: {
    lir::Constant srcHigh
      (shiftMaskPromise(c, src->value, 32, 0xFFFFFFFF));
    lir::Constant srcLow
      (shiftMaskPromise(c, src->value, 0, 0xFFFFFFFF));
    
    lir::Memory dstLow
      (dst->base, dst->offset + 4, dst->index, dst->scale);
    
    moveCM(c, 4, &srcLow, 4, &dstLow);
    moveCM(c, 4, &srcHigh, 4, dst);
  } break;

  default:
    lir::Register tmp(c->client->acquireTemporary());
    moveCR(c, srcSize, src, dstSize, &tmp);
    moveRM(c, dstSize, &tmp, dstSize, dst);
    c->client->releaseTemporary(tmp.low);
  }
}

void negateRR(Context* c, unsigned srcSize, lir::Register* src,
         unsigned dstSize UNUSED, lir::Register* dst) {
  assert(c, srcSize == dstSize);

  if (srcSize == 8) {
    lir::Register dstHigh(dst->high);

    emit(c, subfic(dst->low, src->low, 0));
    emit(c, subfze(dst->high, src->high));
  } else {
    emit(c, neg(dst->low, src->low));
  }
}

void callR(Context* c, unsigned size UNUSED, lir::Register* target) {
  assert(c, size == TargetBytesPerWord);

  emit(c, mtctr(target->low));
  emit(c, bctrl());
}

void callC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  appendOffsetTask(c, target->value, offsetPromise(c), false);
  emit(c, bl(0));
}

void longCallC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  lir::Register tmp(0);
  moveCR2(c, TargetBytesPerWord, target, TargetBytesPerWord, &tmp, 12);
  callR(c, TargetBytesPerWord, &tmp);
}

void alignedLongCallC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  lir::Register tmp(c->client->acquireTemporary());
  lir::Address address(appendConstantPoolEntry(c, target->value));
  moveAR2(c, TargetBytesPerWord, &address, TargetBytesPerWord, &tmp, 12);
  callR(c, TargetBytesPerWord, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void longJumpC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  lir::Register tmp(0);
  moveCR2(c, TargetBytesPerWord, target, TargetBytesPerWord, &tmp, 12);
  jumpR(c, TargetBytesPerWord, &tmp);
}

void alignedLongJumpC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  lir::Register tmp(c->client->acquireTemporary());
  lir::Address address(appendConstantPoolEntry(c, target->value));
  moveAR2(c, TargetBytesPerWord, &address, TargetBytesPerWord, &tmp, 12);
  jumpR(c, TargetBytesPerWord, &tmp);
  c->client->releaseTemporary(tmp.low);
}

void jumpC(Context* c, unsigned size UNUSED, lir::Constant* target) {
  assert(c, size == TargetBytesPerWord);

  appendOffsetTask(c, target->value, offsetPromise(c), false);
  emit(c, b(0));
}

void return_(Context* c) {
  emit(c, blr());
}

void trap(Context* c) {
  emit(c, isa::trap());
}

void memoryBarrier(Context* c) {
  emit(c, sync(0));
}

} // namespace powerpc
} // namespace codegen
} // namespace avian
