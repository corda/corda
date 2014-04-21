/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"
#include "avian/alloc-vector.h"

#include <avian/util/abort.h>
#include <avian/util/math.h>

#include <avian/codegen/assembler.h>
#include <avian/codegen/promise.h>

#include "context.h"
#include "encode.h"
#include "registers.h"
#include "fixup.h"

using namespace avian::util;

namespace {

int64_t
signExtend(unsigned size, int64_t v)
{      
  if (size == 4) {
    return static_cast<int32_t>(v);
  } else if (size == 2) {
    return static_cast<int16_t>(v);
  } else if (size == 1) {
    return static_cast<int8_t>(v);
  } else {
    return v;
  }
}

} // namespace

namespace avian {
namespace codegen {
namespace x86 {


#define REX_W 0x48
#define REX_R 0x44
#define REX_X 0x42
#define REX_B 0x41
#define REX_NONE 0x40

void maybeRex(Context* c, unsigned size, int a, int index, int base, bool always) {
  if (vm::TargetBytesPerWord == 8) {
    uint8_t byte;
    if (size == 8) {
      byte = REX_W;
    } else {
      byte = REX_NONE;
    }
    if (a != lir::NoRegister and (a & 8)) byte |= REX_R;
    if (index != lir::NoRegister and (index & 8)) byte |= REX_X;
    if (base != lir::NoRegister and (base & 8)) byte |= REX_B;
    if (always or byte != REX_NONE) c->code.append(byte);
  }
}

void maybeRex(Context* c, unsigned size, lir::Register* a, lir::Register* b) {
  maybeRex(c, size, a->low, lir::NoRegister, b->low, false);
}

void alwaysRex(Context* c, unsigned size, lir::Register* a, lir::Register* b) {
  maybeRex(c, size, a->low, lir::NoRegister, b->low, true);
}

void maybeRex(Context* c, unsigned size, lir::Register* a) {
  maybeRex(c, size, lir::NoRegister, lir::NoRegister, a->low, false);
}

void maybeRex(Context* c, unsigned size, lir::Register* a, lir::Memory* b) {
  maybeRex(c, size, a->low, b->index, b->base, size == 1 and (a->low & 4));
}

void maybeRex(Context* c, unsigned size, lir::Memory* a) {
  maybeRex(c, size, lir::NoRegister, a->index, a->base, false);
}

void modrm(Context* c, uint8_t mod, int a, int b) {
  c->code.append(mod | (regCode(b) << 3) | regCode(a));
}

void modrm(Context* c, uint8_t mod, lir::Register* a, lir::Register* b) {
  modrm(c, mod, a->low, b->low);
}

void sib(Context* c, unsigned scale, int index, int base) {
  c->code.append((util::log(scale) << 6) | (regCode(index) << 3) | regCode(base));
}

void modrmSib(Context* c, int width, int a, int scale, int index, int base) {
  if (index == lir::NoRegister) {
    modrm(c, width, base, a);
    if (regCode(base) == rsp) {
      sib(c, 0x00, rsp, rsp);
    }
  } else {
    modrm(c, width, rsp, a);
    sib(c, scale, index, base);
  }
}

void modrmSibImm(Context* c, int a, int scale, int index, int base, int offset) {
  if (offset == 0 and regCode(base) != rbp) {
    modrmSib(c, 0x00, a, scale, index, base);
  } else if (vm::fitsInInt8(offset)) {
    modrmSib(c, 0x40, a, scale, index, base);
    c->code.append(offset);
  } else {
    modrmSib(c, 0x80, a, scale, index, base);
    c->code.append4(offset);
  }
}

void modrmSibImm(Context* c, lir::Register* a, lir::Memory* b) {
  modrmSibImm(c, a->low, b->scale, b->index, b->base, b->offset);
}

void opcode(Context* c, uint8_t op) {
  c->code.append(op);
}

void opcode(Context* c, uint8_t op1, uint8_t op2) {
  c->code.append(op1);
  c->code.append(op2);
}

void unconditional(Context* c, unsigned jump, lir::Constant* a) {
  appendOffsetTask(c, a->value, offsetPromise(c), 5);

  opcode(c, jump);
  c->code.append4(0);
}

void conditional(Context* c, unsigned condition, lir::Constant* a) {
  appendOffsetTask(c, a->value, offsetPromise(c), 6);
  
  opcode(c, 0x0f, condition);
  c->code.append4(0);
}

void sseMoveRR(Context* c, unsigned aSize, lir::Register* a,
          unsigned bSize UNUSED, lir::Register* b)
{
  assert(c, aSize >= 4);
  assert(c, aSize == bSize);

  if (isFloatReg(a) and isFloatReg(b)) {
    if (aSize == 4) {
      opcode(c, 0xf3);
      maybeRex(c, 4, a, b);
      opcode(c, 0x0f, 0x10);
      modrm(c, 0xc0, a, b);
    } else {
      opcode(c, 0xf2);
      maybeRex(c, 4, b, a);
      opcode(c, 0x0f, 0x10);
      modrm(c, 0xc0, a, b);
    } 
  } else if (isFloatReg(a)) {
    opcode(c, 0x66);
    maybeRex(c, aSize, a, b);
    opcode(c, 0x0f, 0x7e);
    modrm(c, 0xc0, b, a);   
  } else {
    opcode(c, 0x66);
    maybeRex(c, aSize, b, a);
    opcode(c, 0x0f, 0x6e);
    modrm(c, 0xc0, a, b);   
  }
}

void sseMoveCR(Context* c, unsigned aSize, lir::Constant* a,
          unsigned bSize, lir::Register* b)
{
  assert(c, aSize <= vm::TargetBytesPerWord);
  lir::Register tmp(c->client->acquireTemporary(GeneralRegisterMask));
  moveCR2(c, aSize, a, aSize, &tmp, 0);
  sseMoveRR(c, aSize, &tmp, bSize, b);
  c->client->releaseTemporary(tmp.low);
}

void sseMoveMR(Context* c, unsigned aSize, lir::Memory* a,
          unsigned bSize UNUSED, lir::Register* b)
{
  assert(c, aSize >= 4);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    opcode(c, 0xf3);
    opcode(c, 0x0f, 0x7e);
    modrmSibImm(c, b, a);
  } else {
    opcode(c, 0x66);
    maybeRex(c, aSize, b, a);
    opcode(c, 0x0f, 0x6e);
    modrmSibImm(c, b, a);
  }
}

void sseMoveRM(Context* c, unsigned aSize, lir::Register* a,
       UNUSED unsigned bSize, lir::Memory* b)
{
  assert(c, aSize >= 4);
  assert(c, aSize == bSize);

  if (vm::TargetBytesPerWord == 4 and aSize == 8) {
    opcode(c, 0x66);
    opcode(c, 0x0f, 0xd6);
    modrmSibImm(c, a, b);
  } else {
    opcode(c, 0x66);
    maybeRex(c, aSize, a, b);
    opcode(c, 0x0f, 0x7e);
    modrmSibImm(c, a, b);
  }
}

void branch(Context* c, lir::TernaryOperation op, lir::Constant* target) {
  switch (op) {
  case lir::JumpIfEqual:
    conditional(c, 0x84, target);
    break;

  case lir::JumpIfNotEqual:
    conditional(c, 0x85, target);
    break;

  case lir::JumpIfLess:
    conditional(c, 0x8c, target);
    break;

  case lir::JumpIfGreater:
    conditional(c, 0x8f, target);
    break;

  case lir::JumpIfLessOrEqual:
    conditional(c, 0x8e, target);
    break;

  case lir::JumpIfGreaterOrEqual:
    conditional(c, 0x8d, target);
    break;

  default:
    abort(c);
  }
}

void branchFloat(Context* c, lir::TernaryOperation op, lir::Constant* target) {
  switch (op) {
  case lir::JumpIfFloatEqual:
    // jp past the je so we don't jump to the target if unordered:
    c->code.append(0x7a);
    c->code.append(6);
    conditional(c, 0x84, target);
    break;

  case lir::JumpIfFloatNotEqual:
    conditional(c, 0x85, target);
    conditional(c, 0x8a, target);
    break;

  case lir::JumpIfFloatLess:
    conditional(c, 0x82, target);
    break;

  case lir::JumpIfFloatGreater:
    conditional(c, 0x87, target);
    break;

  case lir::JumpIfFloatLessOrEqual:
    conditional(c, 0x86, target);
    break;

  case lir::JumpIfFloatGreaterOrEqual:
    conditional(c, 0x83, target);
    break;

  case lir::JumpIfFloatLessOrUnordered:
    conditional(c, 0x82, target);
    conditional(c, 0x8a, target);
    break;

  case lir::JumpIfFloatGreaterOrUnordered:
    conditional(c, 0x87, target);
    conditional(c, 0x8a, target);
    break;

  case lir::JumpIfFloatLessOrEqualOrUnordered:
    conditional(c, 0x86, target);
    conditional(c, 0x8a, target);
    break;

  case lir::JumpIfFloatGreaterOrEqualOrUnordered:
    conditional(c, 0x83, target);
    conditional(c, 0x8a, target);
    break;

  default:
    abort(c);
  }
}

void floatRegOp(Context* c, unsigned aSize, lir::Register* a, unsigned bSize,
           lir::Register* b, uint8_t op, uint8_t mod)
{
  if (aSize == 4) {
    opcode(c, 0xf3);
  } else {
    opcode(c, 0xf2);
  }
  maybeRex(c, bSize, b, a);
  opcode(c, 0x0f, op);
  modrm(c, mod, a, b);
}

void floatMemOp(Context* c, unsigned aSize, lir::Memory* a, unsigned bSize,
           lir::Register* b, uint8_t op)
{
  if (aSize == 4) {
    opcode(c, 0xf3);
  } else {
    opcode(c, 0xf2);
  }
  maybeRex(c, bSize, b, a);
  opcode(c, 0x0f, op);
  modrmSibImm(c, b, a);
}

void moveCR(Context* c, unsigned aSize, lir::Constant* a,
       unsigned bSize, lir::Register* b);

void moveCR2(Context* c, UNUSED unsigned aSize, lir::Constant* a,
        UNUSED unsigned bSize, lir::Register* b, unsigned promiseOffset)
{
  if (vm::TargetBytesPerWord == 4 and bSize == 8) {
    int64_t v = signExtend(aSize, a->value->value());

    ResolvedPromise high((v >> 32) & 0xFFFFFFFF);
    lir::Constant ah(&high);

    ResolvedPromise low(v & 0xFFFFFFFF);
    lir::Constant al(&low);

    lir::Register bh(b->high);

    moveCR(c, 4, &al, 4, b);
    moveCR(c, 4, &ah, 4, &bh);
  } else {
    maybeRex(c, vm::TargetBytesPerWord, b);
    opcode(c, 0xb8 + regCode(b));
    if (a->value->resolved()) {
      c->code.appendTargetAddress(signExtend(aSize, a->value->value()));
    } else {
      expect(c, aSize == vm::TargetBytesPerWord);

      appendImmediateTask
        (c, a->value, offsetPromise(c), vm::TargetBytesPerWord, promiseOffset);
      c->code.appendTargetAddress(static_cast<vm::target_uintptr_t>(0));
    }
  }
}

} // namespace x86
} // namespace codegen
} // namespace avian
