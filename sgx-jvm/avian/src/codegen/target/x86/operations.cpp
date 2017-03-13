/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"
#include "avian/alloc-vector.h"
#include "avian/util/allocator.h"
#include "avian/zone.h"

#include <avian/util/abort.h>

#include <avian/codegen/assembler.h>
#include <avian/codegen/promise.h>

#include "context.h"
#include "encode.h"
#include "registers.h"
#include "detect.h"
#include "operations.h"
#include "padding.h"
#include "fixup.h"

using namespace avian::util;

namespace avian {
namespace codegen {
namespace x86 {

void return_(Context* c)
{
  opcode(c, 0xc3);
}

void trap(Context* c)
{
  opcode(c, 0xcc);
}

void ignore(Context*)
{
}

void storeLoadBarrier(Context* c)
{
  if (useSSE(c->ac)) {
    // mfence:
    c->code.append(0x0f);
    c->code.append(0xae);
    c->code.append(0xf0);
  } else {
    // lock addq $0x0,(%rsp):
    c->code.append(0xf0);
    if (vm::TargetBytesPerWord == 8) {
      c->code.append(0x48);
    }
    c->code.append(0x83);
    c->code.append(0x04);
    c->code.append(0x24);
    c->code.append(0x00);
  }
}

void callC(Context* c, unsigned size UNUSED, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  unconditional(c, 0xe8, a);
}

void longCallC(Context* c, unsigned size, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  if (vm::TargetBytesPerWord == 8) {
    lir::RegisterPair r(LongJumpRegister);
    moveCR2(c, size, a, size, &r, 11);
    callR(c, size, &r);
  } else {
    callC(c, size, a);
  }
}

void jumpR(Context* c, unsigned size UNUSED, lir::RegisterPair* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  maybeRex(c, 4, a);
  opcode(c, 0xff, 0xe0 + regCode(a));
}

void jumpC(Context* c, unsigned size UNUSED, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  unconditional(c, 0xe9, a);
}

void jumpM(Context* c, unsigned size UNUSED, lir::Memory* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  maybeRex(c, 4, a);
  opcode(c, 0xff);
  modrmSibImm(c, rsp, a->scale, a->index, a->base, a->offset);
}

void longJumpC(Context* c, unsigned size, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  if (vm::TargetBytesPerWord == 8) {
    lir::RegisterPair r(LongJumpRegister);
    moveCR2(c, size, a, size, &r, 11);
    jumpR(c, size, &r);
  } else {
    jumpC(c, size, a);
  }
}

void callR(Context* c, unsigned size UNUSED, lir::RegisterPair* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  // maybeRex.W has no meaning here so we disable it
  maybeRex(c, 4, a);
  opcode(c, 0xff, 0xd0 + regCode(a));
}

void callM(Context* c, unsigned size UNUSED, lir::Memory* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  maybeRex(c, 4, a);
  opcode(c, 0xff);
  modrmSibImm(c, rdx, a->scale, a->index, a->base, a->offset);
}

void alignedCallC(Context* c, unsigned size, lir::Constant* a)
{
  new (c->zone) AlignmentPadding(c, 1, 4);
  callC(c, size, a);
}

void alignedLongCallC(Context* c, unsigned size, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  if (vm::TargetBytesPerWord == 8) {
    new (c->zone) AlignmentPadding(c, 2, 8);
    longCallC(c, size, a);
  } else {
    alignedCallC(c, size, a);
  }
}

void alignedJumpC(Context* c, unsigned size, lir::Constant* a)
{
  new (c->zone) AlignmentPadding(c, 1, 4);
  jumpC(c, size, a);
}

void alignedLongJumpC(Context* c, unsigned size, lir::Constant* a)
{
  assertT(c, size == vm::TargetBytesPerWord);

  if (vm::TargetBytesPerWord == 8) {
    new (c->zone) AlignmentPadding(c, 2, 8);
    longJumpC(c, size, a);
  } else {
    alignedJumpC(c, size, a);
  }
}

void pushR(Context* c, unsigned size, lir::RegisterPair* a)
{
  if (vm::TargetBytesPerWord == 4 and size == 8) {
    lir::RegisterPair ah(a->high);

    pushR(c, 4, &ah);
    pushR(c, 4, a);
  } else {
    maybeRex(c, 4, a);
    opcode(c, 0x50 + regCode(a));
  }
}

void popR(Context* c, unsigned size, lir::RegisterPair* a)
{
  if (vm::TargetBytesPerWord == 4 and size == 8) {
    lir::RegisterPair ah(a->high);

    popR(c, 4, a);
    popR(c, 4, &ah);
  } else {
    maybeRex(c, 4, a);
    opcode(c, 0x58 + regCode(a));
    if (vm::TargetBytesPerWord == 8 and size == 4) {
      moveRR(c, 4, a, 8, a);
    }
  }
}

void negateR(Context* c, unsigned size, lir::RegisterPair* a)
{
  if (vm::TargetBytesPerWord == 4 and size == 8) {
    assertT(c, a->low == rax and a->high == rdx);

    ResolvedPromise zeroPromise(0);
    lir::Constant zero(&zeroPromise);

    lir::RegisterPair ah(a->high);

    negateR(c, 4, a);
    addCarryCR(c, 4, &zero, &ah);
    negateR(c, 4, &ah);
  } else {
    maybeRex(c, size, a);
    opcode(c, 0xf7, 0xd8 + regCode(a));
  }
}

void negateRR(Context* c,
              unsigned aSize,
              lir::RegisterPair* a,
              unsigned bSize UNUSED,
              lir::RegisterPair* b UNUSED)
{
  assertT(c, aSize == bSize);

  negateR(c, aSize, a);
}

void moveCR(Context* c,
            unsigned aSize,
            lir::Constant* a,
            unsigned bSize,
            lir::RegisterPair* b)
{
  if (isFloatReg(b)) {
    sseMoveCR(c, aSize, a, bSize, b);
  } else {
    moveCR2(c, aSize, a, bSize, b, 0);
  }
}

void moveZCR(Context* c,
             unsigned aSize UNUSED,
             lir::Constant* a,
             unsigned bSize UNUSED,
             lir::RegisterPair* b)
{
  assertT(c, not isFloatReg(b));
  assertT(c, aSize == 2);
  assertT(c, bSize == vm::TargetBytesPerWord);
  assertT(c, a->value->resolved());

  maybeRex(c, vm::TargetBytesPerWord, b);
  opcode(c, 0xb8 + regCode(b));
  c->code.appendTargetAddress(static_cast<uint16_t>(a->value->value()));
}

void swapRR(Context* c,
            unsigned aSize UNUSED,
            lir::RegisterPair* a,
            unsigned bSize UNUSED,
            lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);
  assertT(c, aSize == vm::TargetBytesPerWord);

  alwaysRex(c, aSize, a, b);
  opcode(c, 0x87);
  modrm(c, 0xc0, b, a);
}

void moveRR(Context* c,
            unsigned aSize,
            lir::RegisterPair* a,
            UNUSED unsigned bSize,
            lir::RegisterPair* b)
{
  if (isFloatReg(a) or isFloatReg(b)) {
    sseMoveRR(c, aSize, a, bSize, b);
    return;
  }

  if (vm::TargetBytesPerWord == 4 and aSize == 8 and bSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    if (a->high == b->low) {
      if (a->low == b->high) {
        swapRR(c, 4, a, 4, b);
      } else {
        moveRR(c, 4, &ah, 4, &bh);
        moveRR(c, 4, a, 4, b);
      }
    } else {
      moveRR(c, 4, a, 4, b);
      moveRR(c, 4, &ah, 4, &bh);
    }
  } else {
    switch (aSize) {
    case 1:
      if (vm::TargetBytesPerWord == 4 and a->low > rbx) {
        assertT(c, b->low <= rbx);

        moveRR(c, vm::TargetBytesPerWord, a, vm::TargetBytesPerWord, b);
        moveRR(c, 1, b, vm::TargetBytesPerWord, b);
      } else {
        alwaysRex(c, aSize, b, a);
        opcode(c, 0x0f, 0xbe);
        modrm(c, 0xc0, a, b);
      }
      break;

    case 2:
      alwaysRex(c, aSize, b, a);
      opcode(c, 0x0f, 0xbf);
      modrm(c, 0xc0, a, b);
      break;

    case 4:
      if (bSize == 8) {
        if (vm::TargetBytesPerWord == 8) {
          alwaysRex(c, bSize, b, a);
          opcode(c, 0x63);
          modrm(c, 0xc0, a, b);
        } else {
          if (a->low == rax and b->low == rax and b->high == rdx) {
            opcode(c, 0x99);  // cdq
          } else {
            assertT(c, b->low == rax and b->high == rdx);

            moveRR(c, 4, a, 4, b);
            moveRR(c, 4, b, 8, b);
          }
        }
      } else {
        if (a->low != b->low) {
          alwaysRex(c, aSize, a, b);
          opcode(c, 0x89);
          modrm(c, 0xc0, b, a);
        }
      }
      break;

    case 8:
      if (a->low != b->low) {
        maybeRex(c, aSize, a, b);
        opcode(c, 0x89);
        modrm(c, 0xc0, b, a);
      }
      break;
    }
  }
}

void moveMR(Context* c,
            unsigned aSize,
            lir::Memory* a,
            unsigned bSize,
            lir::RegisterPair* b)
{
  if (isFloatReg(b)) {
    sseMoveMR(c, aSize, a, bSize, b);
    return;
  }

  switch (aSize) {
  case 1:
    maybeRex(c, bSize, b, a);
    opcode(c, 0x0f, 0xbe);
    modrmSibImm(c, b, a);
    break;

  case 2:
    maybeRex(c, bSize, b, a);
    opcode(c, 0x0f, 0xbf);
    modrmSibImm(c, b, a);
    break;

  case 4:
    if (vm::TargetBytesPerWord == 8) {
      maybeRex(c, bSize, b, a);
      opcode(c, 0x63);
      modrmSibImm(c, b, a);
    } else {
      if (bSize == 8) {
        assertT(c, b->low == rax and b->high == rdx);

        moveMR(c, 4, a, 4, b);
        moveRR(c, 4, b, 8, b);
      } else {
        maybeRex(c, bSize, b, a);
        opcode(c, 0x8b);
        modrmSibImm(c, b, a);
      }
    }
    break;

  case 8:
    if (vm::TargetBytesPerWord == 4 and bSize == 8) {
      lir::Memory ah(a->base, a->offset + 4, a->index, a->scale);
      lir::RegisterPair bh(b->high);

      moveMR(c, 4, a, 4, b);
      moveMR(c, 4, &ah, 4, &bh);
    } else {
      maybeRex(c, bSize, b, a);
      opcode(c, 0x8b);
      modrmSibImm(c, b, a);
    }
    break;

  default:
    abort(c);
  }
}

void moveRM(Context* c,
            unsigned aSize,
            lir::RegisterPair* a,
            unsigned bSize UNUSED,
            lir::Memory* b)
{
  assertT(c, aSize == bSize);

  if (isFloatReg(a)) {
    sseMoveRM(c, aSize, a, bSize, b);
    return;
  }

  switch (aSize) {
  case 1:
    maybeRex(c, bSize, a, b);
    opcode(c, 0x88);
    modrmSibImm(c, a, b);
    break;

  case 2:
    opcode(c, 0x66);
    maybeRex(c, bSize, a, b);
    opcode(c, 0x89);
    modrmSibImm(c, a, b);
    break;

  case 4:
    if (vm::TargetBytesPerWord == 8) {
      maybeRex(c, bSize, a, b);
      opcode(c, 0x89);
      modrmSibImm(c, a, b);
      break;
    } else {
      opcode(c, 0x89);
      modrmSibImm(c, a, b);
    }
    break;

  case 8:
    if (vm::TargetBytesPerWord == 8) {
      maybeRex(c, bSize, a, b);
      opcode(c, 0x89);
      modrmSibImm(c, a, b);
    } else {
      lir::RegisterPair ah(a->high);
      lir::Memory bh(b->base, b->offset + 4, b->index, b->scale);

      moveRM(c, 4, a, 4, b);
      moveRM(c, 4, &ah, 4, &bh);
    }
    break;

  default:
    abort(c);
  }
}

void moveAR(Context* c,
            unsigned aSize,
            lir::Address* a,
            unsigned bSize,
            lir::RegisterPair* b)
{
  assertT(c, vm::TargetBytesPerWord == 8 or (aSize == 4 and bSize == 4));

  lir::Constant constant(a->address);
  lir::Memory memory(b->low, 0, NoRegister, 0);

  moveCR(c, aSize, &constant, bSize, b);
  moveMR(c, bSize, &memory, bSize, b);
}

void moveCM(Context* c,
            unsigned aSize UNUSED,
            lir::Constant* a,
            unsigned bSize,
            lir::Memory* b)
{
  switch (bSize) {
  case 1:
    maybeRex(c, bSize, b);
    opcode(c, 0xc6);
    modrmSibImm(c, rax, b->scale, b->index, b->base, b->offset);
    c->code.append(a->value->value());
    break;

  case 2:
    opcode(c, 0x66);
    maybeRex(c, bSize, b);
    opcode(c, 0xc7);
    modrmSibImm(c, rax, b->scale, b->index, b->base, b->offset);
    c->code.append2(a->value->value());
    break;

  case 4:
    maybeRex(c, bSize, b);
    opcode(c, 0xc7);
    modrmSibImm(c, rax, b->scale, b->index, b->base, b->offset);
    if (a->value->resolved()) {
      c->code.append4(a->value->value());
    } else {
      appendImmediateTask(c, a->value, offsetPromise(c), 4);
      c->code.append4(0);
    }
    break;

  case 8: {
    if (vm::TargetBytesPerWord == 8) {
      if (a->value->resolved() and vm::fitsInInt32(a->value->value())) {
        maybeRex(c, bSize, b);
        opcode(c, 0xc7);
        modrmSibImm(c, rax, b->scale, b->index, b->base, b->offset);
        c->code.append4(a->value->value());
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, 8, a, 8, &tmp);
        moveRM(c, 8, &tmp, 8, b);
        c->client->releaseTemporary(tmp.low);
      }
    } else {
      lir::Constant ah(shiftMaskPromise(c, a->value, 32, 0xFFFFFFFF));
      lir::Constant al(shiftMaskPromise(c, a->value, 0, 0xFFFFFFFF));

      lir::Memory bh(b->base, b->offset + 4, b->index, b->scale);

      moveCM(c, 4, &al, 4, b);
      moveCM(c, 4, &ah, 4, &bh);
    }
  } break;

  default:
    abort(c);
  }
}

void moveZRR(Context* c,
             unsigned aSize,
             lir::RegisterPair* a,
             unsigned bSize UNUSED,
             lir::RegisterPair* b)
{
  switch (aSize) {
  case 2:
    alwaysRex(c, aSize, b, a);
    opcode(c, 0x0f, 0xb7);
    modrm(c, 0xc0, a, b);
    break;

  default:
    abort(c);
  }
}

void moveZMR(Context* c,
             unsigned aSize UNUSED,
             lir::Memory* a,
             unsigned bSize UNUSED,
             lir::RegisterPair* b)
{
  assertT(c, bSize == vm::TargetBytesPerWord);
  assertT(c, aSize == 2);

  maybeRex(c, bSize, b, a);
  opcode(c, 0x0f, 0xb7);
  modrmSibImm(c, b->low, a->scale, a->index, a->base, a->offset);
}

void addCarryRR(Context* c, unsigned size, lir::RegisterPair* a, lir::RegisterPair* b)
{
  assertT(c, vm::TargetBytesPerWord == 8 or size == 4);

  maybeRex(c, size, a, b);
  opcode(c, 0x11);
  modrm(c, 0xc0, b, a);
}

void addRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    addRR(c, 4, a, 4, b);
    addCarryRR(c, 4, &ah, &bh);
  } else {
    maybeRex(c, aSize, a, b);
    opcode(c, 0x01);
    modrm(c, 0xc0, b, a);
  }
}

void addCarryCR(Context* c, unsigned size, lir::Constant* a, lir::RegisterPair* b)
{
  int64_t v = a->value->value();
  maybeRex(c, size, b);
  if (vm::fitsInInt8(v)) {
    opcode(c, 0x83, 0xd0 + regCode(b));
    c->code.append(v);
  } else {
    opcode(c, 0x81, 0xd0 + regCode(b));
    c->code.append4(v);
  }
}

void addCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (vm::TargetBytesPerWord == 4 and bSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      lir::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      lir::Constant al(&low);

      lir::RegisterPair bh(b->high);

      addCR(c, 4, &al, 4, b);
      addCarryCR(c, 4, &ah, &bh);
    } else {
      if (vm::fitsInInt32(v)) {
        maybeRex(c, aSize, b);
        if (vm::fitsInInt8(v)) {
          opcode(c, 0x83, 0xc0 + regCode(b));
          c->code.append(v);
        } else {
          opcode(c, 0x81, 0xc0 + regCode(b));
          c->code.append4(v);
        }
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, aSize, a, aSize, &tmp);
        addRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void subtractBorrowCR(Context* c,
                      unsigned size UNUSED,
                      lir::Constant* a,
                      lir::RegisterPair* b)
{
  assertT(c, vm::TargetBytesPerWord == 8 or size == 4);

  int64_t v = a->value->value();
  if (vm::fitsInInt8(v)) {
    opcode(c, 0x83, 0xd8 + regCode(b));
    c->code.append(v);
  } else {
    opcode(c, 0x81, 0xd8 + regCode(b));
    c->code.append4(v);
  }
}

void subtractCR(Context* c,
                unsigned aSize,
                lir::Constant* a,
                unsigned bSize,
                lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (vm::TargetBytesPerWord == 4 and bSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      lir::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      lir::Constant al(&low);

      lir::RegisterPair bh(b->high);

      subtractCR(c, 4, &al, 4, b);
      subtractBorrowCR(c, 4, &ah, &bh);
    } else {
      if (vm::fitsInInt32(v)) {
        maybeRex(c, aSize, b);
        if (vm::fitsInInt8(v)) {
          opcode(c, 0x83, 0xe8 + regCode(b));
          c->code.append(v);
        } else {
          opcode(c, 0x81, 0xe8 + regCode(b));
          c->code.append4(v);
        }
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, aSize, a, aSize, &tmp);
        subtractRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void subtractBorrowRR(Context* c,
                      unsigned size,
                      lir::RegisterPair* a,
                      lir::RegisterPair* b)
{
  assertT(c, vm::TargetBytesPerWord == 8 or size == 4);

  maybeRex(c, size, a, b);
  opcode(c, 0x19);
  modrm(c, 0xc0, b, a);
}

void subtractRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    subtractRR(c, 4, a, 4, b);
    subtractBorrowRR(c, 4, &ah, &bh);
  } else {
    maybeRex(c, aSize, a, b);
    opcode(c, 0x29);
    modrm(c, 0xc0, b, a);
  }
}

void andRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    andRR(c, 4, a, 4, b);
    andRR(c, 4, &ah, 4, &bh);
  } else {
    maybeRex(c, aSize, a, b);
    opcode(c, 0x21);
    modrm(c, 0xc0, b, a);
  }
}

void andCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int64_t v = a->value->value();

  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::RegisterPair bh(b->high);

    andCR(c, 4, &al, 4, b);
    andCR(c, 4, &ah, 4, &bh);
  } else {
    if (vm::fitsInInt32(v)) {
      maybeRex(c, aSize, b);
      if (vm::fitsInInt8(v)) {
        opcode(c, 0x83, 0xe0 + regCode(b));
        c->code.append(v);
      } else {
        opcode(c, 0x81, 0xe0 + regCode(b));
        c->code.append4(v);
      }
    } else {
      lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
      moveCR(c, aSize, a, aSize, &tmp);
      andRR(c, aSize, &tmp, bSize, b);
      c->client->releaseTemporary(tmp.low);
    }
  }
}

void orRR(Context* c,
          unsigned aSize,
          lir::RegisterPair* a,
          unsigned bSize UNUSED,
          lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    orRR(c, 4, a, 4, b);
    orRR(c, 4, &ah, 4, &bh);
  } else {
    maybeRex(c, aSize, a, b);
    opcode(c, 0x09);
    modrm(c, 0xc0, b, a);
  }
}

void orCR(Context* c,
          unsigned aSize,
          lir::Constant* a,
          unsigned bSize,
          lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (vm::TargetBytesPerWord == 4 and bSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      lir::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      lir::Constant al(&low);

      lir::RegisterPair bh(b->high);

      orCR(c, 4, &al, 4, b);
      orCR(c, 4, &ah, 4, &bh);
    } else {
      if (vm::fitsInInt32(v)) {
        maybeRex(c, aSize, b);
        if (vm::fitsInInt8(v)) {
          opcode(c, 0x83, 0xc8 + regCode(b));
          c->code.append(v);
        } else {
          opcode(c, 0x81, 0xc8 + regCode(b));
          c->code.append4(v);
        }
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, aSize, a, aSize, &tmp);
        orRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void xorRR(Context* c,
           unsigned aSize,
           lir::RegisterPair* a,
           unsigned bSize UNUSED,
           lir::RegisterPair* b)
{
  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    xorRR(c, 4, a, 4, b);
    xorRR(c, 4, &ah, 4, &bh);
  } else {
    maybeRex(c, aSize, a, b);
    opcode(c, 0x31);
    modrm(c, 0xc0, b, a);
  }
}

void xorCR(Context* c,
           unsigned aSize,
           lir::Constant* a,
           unsigned bSize,
           lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  int64_t v = a->value->value();
  if (v) {
    if (vm::TargetBytesPerWord == 4 and bSize == 8) {
      ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
      lir::Constant ah(&high);

      ResolvedPromise low(v & 0xFFFFFFFF);
      lir::Constant al(&low);

      lir::RegisterPair bh(b->high);

      xorCR(c, 4, &al, 4, b);
      xorCR(c, 4, &ah, 4, &bh);
    } else {
      if (vm::fitsInInt32(v)) {
        maybeRex(c, aSize, b);
        if (vm::fitsInInt8(v)) {
          opcode(c, 0x83, 0xf0 + regCode(b));
          c->code.append(v);
        } else {
          opcode(c, 0x81, 0xf0 + regCode(b));
          c->code.append4(v);
        }
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, aSize, a, aSize, &tmp);
        xorRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void multiplyRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    assertT(c, b->high == rdx);
    assertT(c, b->low != rax);
    assertT(c, a->low != rax);
    assertT(c, a->high != rax);

    c->client->save(rax);

    lir::RegisterPair axdx(rax, rdx);
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    lir::RegisterPair tmp(NoRegister);
    lir::RegisterPair* scratch;
    if (a->low == b->low) {
      tmp.low = c->client->acquireTemporary(GeneralRegisterMask.excluding(rax));
      scratch = &tmp;
      moveRR(c, 4, b, 4, scratch);
    } else {
      scratch = b;
    }

    moveRR(c, 4, b, 4, &axdx);
    multiplyRR(c, 4, &ah, 4, scratch);
    multiplyRR(c, 4, a, 4, &bh);
    addRR(c, 4, &bh, 4, scratch);

    // mul a->low,%eax%edx
    opcode(c, 0xf7, 0xe0 + a->low.index());

    addRR(c, 4, scratch, 4, &bh);
    moveRR(c, 4, &axdx, 4, b);

    if (tmp.low != NoRegister) {
      c->client->releaseTemporary(tmp.low);
    }
  } else {
    maybeRex(c, aSize, b, a);
    opcode(c, 0x0f, 0xaf);
    modrm(c, 0xc0, a, b);
  }
}

void compareRR(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);
  assertT(c, aSize <= vm::TargetBytesPerWord);

  maybeRex(c, aSize, a, b);
  opcode(c, 0x39);
  modrm(c, 0xc0, b, a);
}

void compareCR(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);
  assertT(c, vm::TargetBytesPerWord == 8 or aSize == 4);

  if (a->value->resolved() and vm::fitsInInt32(a->value->value())) {
    int64_t v = a->value->value();
    maybeRex(c, aSize, b);
    if (vm::fitsInInt8(v)) {
      opcode(c, 0x83, 0xf8 + regCode(b));
      c->code.append(v);
    } else {
      opcode(c, 0x81, 0xf8 + regCode(b));
      c->code.append4(v);
    }
  } else {
    lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
    moveCR(c, aSize, a, aSize, &tmp);
    compareRR(c, aSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void compareRM(Context* c,
               unsigned aSize,
               lir::RegisterPair* a,
               unsigned bSize UNUSED,
               lir::Memory* b)
{
  assertT(c, aSize == bSize);
  assertT(c, vm::TargetBytesPerWord == 8 or aSize == 4);

  if (vm::TargetBytesPerWord == 8 and aSize == 4) {
    moveRR(c, 4, a, 8, a);
  }
  maybeRex(c, bSize, a, b);
  opcode(c, 0x39);
  modrmSibImm(c, a, b);
}

void compareCM(Context* c,
               unsigned aSize,
               lir::Constant* a,
               unsigned bSize,
               lir::Memory* b)
{
  assertT(c, aSize == bSize);
  assertT(c, vm::TargetBytesPerWord == 8 or aSize == 4);

  if (a->value->resolved()) {
    int64_t v = a->value->value();
    maybeRex(c, aSize, b);
    opcode(c, vm::fitsInInt8(v) ? 0x83 : 0x81);
    modrmSibImm(c, rdi, b->scale, b->index, b->base, b->offset);

    if (vm::fitsInInt8(v)) {
      c->code.append(v);
    } else if (vm::fitsInInt32(v)) {
      c->code.append4(v);
    } else {
      abort(c);
    }
  } else {
    lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
    moveCR(c, aSize, a, bSize, &tmp);
    compareRM(c, bSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
  }
}

void compareFloatRR(Context* c,
                    unsigned aSize,
                    lir::RegisterPair* a,
                    unsigned bSize UNUSED,
                    lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (aSize == 8) {
    opcode(c, 0x66);
  }
  maybeRex(c, 4, a, b);
  opcode(c, 0x0f, 0x2e);
  modrm(c, 0xc0, a, b);
}

void branchLong(Context* c,
                lir::TernaryOperation op,
                lir::Operand* al,
                lir::Operand* ah,
                lir::Operand* bl,
                lir::Operand* bh,
                lir::Constant* target,
                BinaryOperationType compare)
{
  compare(c, 4, ah, 4, bh);

  unsigned next = 0;

  switch (op) {
  case lir::JumpIfEqual:
    opcode(c, 0x75);  // jne
    next = c->code.length();
    c->code.append(0);

    compare(c, 4, al, 4, bl);
    conditional(c, 0x84, target);  // je
    break;

  case lir::JumpIfNotEqual:
    conditional(c, 0x85, target);  // jne

    compare(c, 4, al, 4, bl);
    conditional(c, 0x85, target);  // jne
    break;

  case lir::JumpIfLess:
    conditional(c, 0x8c, target);  // jl

    opcode(c, 0x7f);  // jg
    next = c->code.length();
    c->code.append(0);

    compare(c, 4, al, 4, bl);
    conditional(c, 0x82, target);  // jb
    break;

  case lir::JumpIfGreater:
    conditional(c, 0x8f, target);  // jg

    opcode(c, 0x7c);  // jl
    next = c->code.length();
    c->code.append(0);

    compare(c, 4, al, 4, bl);
    conditional(c, 0x87, target);  // ja
    break;

  case lir::JumpIfLessOrEqual:
    conditional(c, 0x8c, target);  // jl

    opcode(c, 0x7f);  // jg
    next = c->code.length();
    c->code.append(0);

    compare(c, 4, al, 4, bl);
    conditional(c, 0x86, target);  // jbe
    break;

  case lir::JumpIfGreaterOrEqual:
    conditional(c, 0x8f, target);  // jg

    opcode(c, 0x7c);  // jl
    next = c->code.length();
    c->code.append(0);

    compare(c, 4, al, 4, bl);
    conditional(c, 0x83, target);  // jae
    break;

  default:
    abort(c);
  }

  if (next) {
    int8_t nextOffset = c->code.length() - next - 1;
    c->code.set(next, &nextOffset, 1);
  }
}

void branchRR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::RegisterPair* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  if (isFloatBranch(op)) {
    compareFloatRR(c, size, a, size, b);
    branchFloat(c, op, target);
  } else if (size > vm::TargetBytesPerWord) {
    lir::RegisterPair ah(a->high);
    lir::RegisterPair bh(b->high);

    branchLong(c, op, a, &ah, b, &bh, target, CAST2(compareRR));
  } else {
    compareRR(c, size, a, size, b);
    branch(c, op, target);
  }
}

void branchCR(Context* c,
              lir::TernaryOperation op,
              unsigned size,
              lir::Constant* a,
              lir::RegisterPair* b,
              lir::Constant* target)
{
  assertT(c, not isFloatBranch(op));

  if (size > vm::TargetBytesPerWord) {
    int64_t v = a->value->value();

    ResolvedPromise low(v & ~static_cast<uintptr_t>(0));
    lir::Constant al(&low);

    ResolvedPromise high((v >> 32) & ~static_cast<uintptr_t>(0));
    lir::Constant ah(&high);

    lir::RegisterPair bh(b->high);

    branchLong(c, op, &al, &ah, b, &bh, target, CAST2(compareCR));
  } else {
    compareCR(c, size, a, size, b);
    branch(c, op, target);
  }
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

void multiplyCR(Context* c,
                unsigned aSize,
                lir::Constant* a,
                unsigned bSize,
                lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    const RegisterMask mask = GeneralRegisterMask.excluding(rax).excluding(rdx);
    lir::RegisterPair tmp(c->client->acquireTemporary(mask),
                      c->client->acquireTemporary(mask));

    moveCR(c, aSize, a, aSize, &tmp);
    multiplyRR(c, aSize, &tmp, bSize, b);
    c->client->releaseTemporary(tmp.low);
    c->client->releaseTemporary(tmp.high);
  } else {
    int64_t v = a->value->value();
    if (v != 1) {
      if (vm::fitsInInt32(v)) {
        maybeRex(c, bSize, b, b);
        if (vm::fitsInInt8(v)) {
          opcode(c, 0x6b);
          modrm(c, 0xc0, b, b);
          c->code.append(v);
        } else {
          opcode(c, 0x69);
          modrm(c, 0xc0, b, b);
          c->code.append4(v);
        }
      } else {
        lir::RegisterPair tmp(c->client->acquireTemporary(GeneralRegisterMask));
        moveCR(c, aSize, a, aSize, &tmp);
        multiplyRR(c, aSize, &tmp, bSize, b);
        c->client->releaseTemporary(tmp.low);
      }
    }
  }
}

void divideRR(Context* c,
              unsigned aSize,
              lir::RegisterPair* a,
              unsigned bSize UNUSED,
              lir::RegisterPair* b UNUSED)
{
  assertT(c, aSize == bSize);

  assertT(c, b->low == rax);
  assertT(c, a->low != rdx);

  c->client->save(rdx);

  maybeRex(c, aSize, a, b);
  opcode(c, 0x99);  // cdq
  maybeRex(c, aSize, b, a);
  opcode(c, 0xf7, 0xf8 + regCode(a));
}

void remainderRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b)
{
  assertT(c, aSize == bSize);

  assertT(c, b->low == rax);
  assertT(c, a->low != rdx);

  c->client->save(rdx);

  maybeRex(c, aSize, a, b);
  opcode(c, 0x99);  // cdq
  maybeRex(c, aSize, b, a);
  opcode(c, 0xf7, 0xf8 + regCode(a));

  lir::RegisterPair dx(rdx);
  moveRR(c, vm::TargetBytesPerWord, &dx, vm::TargetBytesPerWord, b);
}

void doShift(Context* c,
             UNUSED void (*shift)(Context*,
                                  unsigned,
                                  lir::RegisterPair*,
                                  unsigned,
                                  lir::RegisterPair*),
             int type,
             UNUSED unsigned aSize,
             lir::Constant* a,
             unsigned bSize,
             lir::RegisterPair* b)
{
  int64_t v = a->value->value();

  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    c->client->save(rcx);

    lir::RegisterPair cx(rcx);
    ResolvedPromise promise(v & 0x3F);
    lir::Constant masked(&promise);
    moveCR(c, 4, &masked, 4, &cx);
    shift(c, aSize, &cx, bSize, b);
  } else {
    maybeRex(c, bSize, b);
    if (v == 1) {
      opcode(c, 0xd1, type + regCode(b));
    } else if (vm::fitsInInt8(v)) {
      opcode(c, 0xc1, type + regCode(b));
      c->code.append(v);
    } else {
      abort(c);
    }
  }
}

void shiftLeftRR(Context* c,
                 UNUSED unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    lir::RegisterPair cx(rcx);
    if (a->low != rcx) {
      c->client->save(rcx);
      ResolvedPromise promise(0x3F);
      lir::Constant mask(&promise);
      moveRR(c, 4, a, 4, &cx);
      andCR(c, 4, &mask, 4, &cx);
    }

    // shld
    opcode(c, 0x0f, 0xa5);
    modrm(c, 0xc0, b->high, b->low);

    // shl
    opcode(c, 0xd3, 0xe0 + b->low.index());

    ResolvedPromise promise(32);
    lir::Constant constant(&promise);
    compareCR(
        c, vm::TargetBytesPerWord, &constant, vm::TargetBytesPerWord, &cx);

    opcode(c, 0x7c);  // jl
    c->code.append(2 + 2);

    lir::RegisterPair bh(b->high);
    moveRR(c, 4, b, 4, &bh);  // 2 bytes
    xorRR(c, 4, b, 4, b);     // 2 bytes
  } else {
    assertT(c, a->low == rcx);

    maybeRex(c, bSize, a, b);
    opcode(c, 0xd3, 0xe0 + regCode(b));
  }
}

void shiftLeftCR(Context* c,
                 unsigned aSize,
                 lir::Constant* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  doShift(c, shiftLeftRR, 0xe0, aSize, a, bSize, b);
}

void shiftRightRR(Context* c,
                  UNUSED unsigned aSize,
                  lir::RegisterPair* a,
                  unsigned bSize,
                  lir::RegisterPair* b)
{
  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    lir::RegisterPair cx(rcx);
    if (a->low != rcx) {
      c->client->save(rcx);
      ResolvedPromise promise(0x3F);
      lir::Constant mask(&promise);
      moveRR(c, 4, a, 4, &cx);
      andCR(c, 4, &mask, 4, &cx);
    }

    // shrd
    opcode(c, 0x0f, 0xad);
    modrm(c, 0xc0, b->low, b->high);

    // sar
    opcode(c, 0xd3, 0xf8 + b->high.index());

    ResolvedPromise promise(32);
    lir::Constant constant(&promise);
    compareCR(
        c, vm::TargetBytesPerWord, &constant, vm::TargetBytesPerWord, &cx);

    opcode(c, 0x7c);  // jl
    c->code.append(2 + 3);

    lir::RegisterPair bh(b->high);
    moveRR(c, 4, &bh, 4, b);  // 2 bytes

    // sar 31,high
    opcode(c, 0xc1, 0xf8 + b->high.index());
    c->code.append(31);
  } else {
    assertT(c, a->low == rcx);

    maybeRex(c, bSize, a, b);
    opcode(c, 0xd3, 0xf8 + regCode(b));
  }
}

void shiftRightCR(Context* c,
                  unsigned aSize,
                  lir::Constant* a,
                  unsigned bSize,
                  lir::RegisterPair* b)
{
  doShift(c, shiftRightRR, 0xf8, aSize, a, bSize, b);
}

void unsignedShiftRightRR(Context* c,
                          UNUSED unsigned aSize,
                          lir::RegisterPair* a,
                          unsigned bSize,
                          lir::RegisterPair* b)
{
  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    lir::RegisterPair cx(rcx);
    if (a->low != rcx) {
      c->client->save(rcx);
      ResolvedPromise promise(0x3F);
      lir::Constant mask(&promise);
      moveRR(c, 4, a, 4, &cx);
      andCR(c, 4, &mask, 4, &cx);
    }

    // shrd
    opcode(c, 0x0f, 0xad);
    modrm(c, 0xc0, b->low, b->high);

    // shr
    opcode(c, 0xd3, 0xe8 + b->high.index());

    ResolvedPromise promise(32);
    lir::Constant constant(&promise);
    compareCR(
        c, vm::TargetBytesPerWord, &constant, vm::TargetBytesPerWord, &cx);

    opcode(c, 0x7c);  // jl
    c->code.append(2 + 2);

    lir::RegisterPair bh(b->high);
    moveRR(c, 4, &bh, 4, b);   // 2 bytes
    xorRR(c, 4, &bh, 4, &bh);  // 2 bytes
  } else {
    assertT(c, a->low == rcx);

    maybeRex(c, bSize, a, b);
    opcode(c, 0xd3, 0xe8 + regCode(b));
  }
}

void unsignedShiftRightCR(Context* c,
                          unsigned aSize UNUSED,
                          lir::Constant* a,
                          unsigned bSize,
                          lir::RegisterPair* b)
{
  doShift(c, unsignedShiftRightRR, 0xe8, aSize, a, bSize, b);
}

void floatSqrtRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x51);
}

void floatSqrtMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize UNUSED,
                 lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x51);
}

void floatAddRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x58);
}

void floatAddMR(Context* c,
                unsigned aSize,
                lir::Memory* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x58);
}

void floatSubtractRR(Context* c,
                     unsigned aSize,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x5c);
}

void floatSubtractMR(Context* c,
                     unsigned aSize,
                     lir::Memory* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x5c);
}

void floatMultiplyRR(Context* c,
                     unsigned aSize,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x59);
}

void floatMultiplyMR(Context* c,
                     unsigned aSize,
                     lir::Memory* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x59);
}

void floatDivideRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x5e);
}

void floatDivideMR(Context* c,
                   unsigned aSize,
                   lir::Memory* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x5e);
}

void float2FloatRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b)
{
  floatRegOp(c, aSize, a, 4, b, 0x5a);
}

void float2FloatMR(Context* c,
                   unsigned aSize,
                   lir::Memory* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, 4, b, 0x5a);
}

void float2IntRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  assertT(c, not isFloatReg(b));
  floatRegOp(c, aSize, a, bSize, b, 0x2c);
}

void float2IntMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  floatMemOp(c, aSize, a, bSize, b, 0x2c);
}

void int2FloatRR(Context* c,
                 unsigned aSize,
                 lir::RegisterPair* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  floatRegOp(c, bSize, a, aSize, b, 0x2a);
}

void int2FloatMR(Context* c,
                 unsigned aSize,
                 lir::Memory* a,
                 unsigned bSize,
                 lir::RegisterPair* b)
{
  floatMemOp(c, bSize, a, aSize, b, 0x2a);
}

void floatNegateRR(Context* c,
                   unsigned aSize,
                   lir::RegisterPair* a,
                   unsigned bSize UNUSED,
                   lir::RegisterPair* b)
{
  assertT(c, isFloatReg(a) and isFloatReg(b));
  // unlike most of the other floating point code, this does NOT
  // support doubles:
  assertT(c, aSize == 4);
  ResolvedPromise pcon(0x80000000);
  lir::Constant con(&pcon);
  if (a->low == b->low) {
    lir::RegisterPair tmp(c->client->acquireTemporary(FloatRegisterMask));
    moveCR(c, 4, &con, 4, &tmp);
    maybeRex(c, 4, a, &tmp);
    opcode(c, 0x0f, 0x57);
    modrm(c, 0xc0, &tmp, a);
    c->client->releaseTemporary(tmp.low);
  } else {
    moveCR(c, 4, &con, 4, b);
    if (aSize == 8)
      opcode(c, 0x66);
    maybeRex(c, 4, a, b);
    opcode(c, 0x0f, 0x57);
    modrm(c, 0xc0, a, b);
  }
}

void floatAbsoluteRR(Context* c,
                     unsigned aSize UNUSED,
                     lir::RegisterPair* a,
                     unsigned bSize UNUSED,
                     lir::RegisterPair* b)
{
  assertT(c, isFloatReg(a) and isFloatReg(b));
  // unlike most of the other floating point code, this does NOT
  // support doubles:
  assertT(c, aSize == 4);
  ResolvedPromise pcon(0x7fffffff);
  lir::Constant con(&pcon);
  if (a->low == b->low) {
    lir::RegisterPair tmp(c->client->acquireTemporary(FloatRegisterMask));
    moveCR(c, 4, &con, 4, &tmp);
    maybeRex(c, 4, a, &tmp);
    opcode(c, 0x0f, 0x54);
    modrm(c, 0xc0, &tmp, a);
    c->client->releaseTemporary(tmp.low);
  } else {
    moveCR(c, 4, &con, 4, b);
    maybeRex(c, 4, a, b);
    opcode(c, 0x0f, 0x54);
    modrm(c, 0xc0, a, b);
  }
}

void absoluteRR(Context* c,
                unsigned aSize,
                lir::RegisterPair* a,
                unsigned bSize UNUSED,
                lir::RegisterPair* b UNUSED)
{
  assertT(c, aSize == bSize and a->low == rax and b->low == rax);
  lir::RegisterPair d(c->client->acquireTemporary(rdx));
  maybeRex(c, aSize, a, b);
  opcode(c, 0x99);
  xorRR(c, aSize, &d, aSize, a);
  subtractRR(c, aSize, &d, aSize, a);
  c->client->releaseTemporary(rdx);
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
