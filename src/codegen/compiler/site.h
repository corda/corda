/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_SITE_H
#define AVIAN_CODEGEN_COMPILER_SITE_H

#include <avian/codegen/architecture.h>

#include "codegen/compiler/value.h"
#include "codegen/compiler/context.h"

namespace avian {
namespace codegen {
namespace compiler {

class Context;

const unsigned RegisterCopyCost = 1;
const unsigned AddressCopyCost = 2;
const unsigned ConstantCopyCost = 3;
const unsigned MemoryCopyCost = 4;
const unsigned CopyPenalty = 10;

class SiteMask {
 public:
  SiteMask() : typeMask(~0), registerMask(~0), frameIndex(AnyFrameIndex)
  {
  }

  SiteMask(uint8_t typeMask, RegisterMask registerMask, int frameIndex)
      : typeMask(typeMask), registerMask(registerMask), frameIndex(frameIndex)
  {
  }

  SiteMask intersectionWith(const SiteMask& b);

  static SiteMask fixedRegisterMask(Register number)
  {
    return SiteMask(lir::Operand::RegisterPairMask, 1 << number.index(), NoFrameIndex);
  }

  static SiteMask lowPart(const OperandMask& mask)
  {
    return SiteMask(mask.typeMask, mask.lowRegisterMask, AnyFrameIndex);
  }

  static SiteMask highPart(const OperandMask& mask)
  {
    return SiteMask(mask.typeMask, mask.highRegisterMask, AnyFrameIndex);
  }

  uint8_t typeMask;
  RegisterMask registerMask;
  int frameIndex;
};

class Site {
 public:
  Site() : next(0)
  {
  }

  virtual Site* readTarget(Context*, Read*)
  {
    return this;
  }

  virtual unsigned toString(Context*, char*, unsigned) = 0;

  virtual unsigned copyCost(Context*, Site*) = 0;

  virtual bool match(Context*, const SiteMask&) = 0;

  virtual bool loneMatch(Context*, const SiteMask&) = 0;

  virtual bool matchNextWord(Context*, Site*, unsigned) = 0;

  virtual void acquire(Context*, Value*)
  {
  }

  virtual void release(Context*, Value*)
  {
  }

  virtual void freeze(Context*, Value*)
  {
  }

  virtual void thaw(Context*, Value*)
  {
  }

  virtual bool frozen(Context*)
  {
    return false;
  }

  virtual lir::Operand::Type type(Context*) = 0;

  virtual void asAssemblerOperand(Context*, Site*, lir::Operand*) = 0;

  virtual Site* copy(Context*) = 0;

  virtual Site* copyLow(Context*) = 0;

  virtual Site* copyHigh(Context*) = 0;

  virtual Site* makeNextWord(Context*, unsigned) = 0;

  virtual SiteMask mask(Context*) = 0;

  virtual SiteMask nextWordMask(Context*, unsigned) = 0;

  virtual unsigned registerSize(Context*);

  virtual RegisterMask registerMask(Context*)
  {
    return RegisterMask(0);
  }

  virtual bool isVolatile(Context*)
  {
    return false;
  }

  Site* next;
};

class SiteIterator {
 public:
  SiteIterator(Context* c,
               Value* v,
               bool includeBuddies = true,
               bool includeNextWord = true);

  Site** findNext(Site** p);
  bool hasMore();
  Site* next();
  void remove(Context* c);

  Context* c;
  Value* originalValue;
  Value* currentValue;
  bool includeBuddies;
  bool includeNextWord;
  uint8_t pass;
  Site** next_;
  Site** previous;
};

Site* constantSite(Context* c, Promise* value);
Site* constantSite(Context* c, int64_t value);

Promise* combinedPromise(Context* c, Promise* low, Promise* high);
Promise* shiftMaskPromise(Context* c,
                          Promise* base,
                          unsigned shift,
                          int64_t mask);

class ConstantSite : public Site {
 public:
  ConstantSite(Promise* value) : value(value)
  {
  }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize)
  {
    if (value->resolved()) {
      return vm::snprintf(buffer, bufferSize, "constant %" LLD, value->value());
    } else {
      return vm::snprintf(buffer, bufferSize, "constant unresolved");
    }
  }

  virtual unsigned copyCost(Context*, Site* s)
  {
    return (s == this ? 0 : ConstantCopyCost);
  }

  virtual bool match(Context*, const SiteMask& mask)
  {
    return mask.typeMask & lir::Operand::ConstantMask;
  }

  virtual bool loneMatch(Context*, const SiteMask&)
  {
    return true;
  }

  virtual bool matchNextWord(Context* c, Site* s, unsigned)
  {
    return s->type(c) == lir::Operand::Type::Constant;
  }

  virtual lir::Operand::Type type(Context*)
  {
    return lir::Operand::Type::Constant;
  }

  virtual void asAssemblerOperand(Context* c, Site* high, lir::Operand* result)
  {
    Promise* v = value;
    if (high != this) {
      v = combinedPromise(c, value, static_cast<ConstantSite*>(high)->value);
    }
    new (result) lir::Constant(v);
  }

  virtual Site* copy(Context* c)
  {
    return constantSite(c, value);
  }

  virtual Site* copyLow(Context* c)
  {
    return constantSite(c, shiftMaskPromise(c, value, 0, 0xFFFFFFFF));
  }

  virtual Site* copyHigh(Context* c)
  {
    return constantSite(c, shiftMaskPromise(c, value, 32, 0xFFFFFFFF));
  }

  virtual Site* makeNextWord(Context* c, unsigned)
  {
    abort(c);
  }

  virtual SiteMask mask(Context*)
  {
    return SiteMask(lir::Operand::ConstantMask, 0, NoFrameIndex);
  }

  virtual SiteMask nextWordMask(Context*, unsigned)
  {
    return SiteMask(lir::Operand::ConstantMask, 0, NoFrameIndex);
  }

  Promise* value;
};

Site* addressSite(Context* c, Promise* address);

class RegisterSite : public Site {
 public:
  RegisterSite(RegisterMask mask, Register number);

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize);

  virtual unsigned copyCost(Context* c, Site* s);

  virtual bool match(Context* c UNUSED, const SiteMask& mask);

  virtual bool loneMatch(Context* c UNUSED, const SiteMask& mask);

  virtual bool matchNextWord(Context* c, Site* s, unsigned);

  virtual void acquire(Context* c, Value* v);

  virtual void release(Context* c, Value* v);

  virtual void freeze(Context* c, Value* v);

  virtual void thaw(Context* c, Value* v);

  virtual bool frozen(Context* c UNUSED);

  virtual lir::Operand::Type type(Context*);

  virtual void asAssemblerOperand(Context* c UNUSED,
                                  Site* high,
                                  lir::Operand* result);

  virtual Site* copy(Context* c);

  virtual Site* copyLow(Context* c);

  virtual Site* copyHigh(Context* c);

  virtual Site* makeNextWord(Context* c, unsigned);

  virtual SiteMask mask(Context* c UNUSED);

  virtual SiteMask nextWordMask(Context* c, unsigned);

  virtual unsigned registerSize(Context* c);

  virtual RegisterMask registerMask(Context* c UNUSED);

  RegisterMask mask_;
  Register number;
};

Site* registerSite(Context* c, Register number);
Site* freeRegisterSite(Context* c, RegisterMask mask);

class MemorySite : public Site {
 public:
  MemorySite(Register base, int offset, Register index, unsigned scale);

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize);

  virtual unsigned copyCost(Context* c, Site* s);

  bool conflicts(const SiteMask& mask);

  virtual bool match(Context* c, const SiteMask& mask);

  virtual bool loneMatch(Context* c, const SiteMask& mask);

  virtual bool matchNextWord(Context* c, Site* s, unsigned index);

  virtual void acquire(Context* c, Value* v);

  virtual void release(Context* c, Value* v);

  virtual void freeze(Context* c, Value* v);

  virtual void thaw(Context* c, Value* v);

  virtual bool frozen(Context* c);

  virtual lir::Operand::Type type(Context*);

  virtual void asAssemblerOperand(Context* c UNUSED,
                                  Site* high UNUSED,
                                  lir::Operand* result);

  virtual Site* copy(Context* c);

  Site* copyHalf(Context* c, bool add);

  virtual Site* copyLow(Context* c);

  virtual Site* copyHigh(Context* c);

  virtual Site* makeNextWord(Context* c, unsigned index);

  virtual SiteMask mask(Context* c);

  virtual SiteMask nextWordMask(Context* c, unsigned index);

  virtual bool isVolatile(Context* c);

  bool acquired;
  Register base;
  int offset;
  Register index;
  unsigned scale;
};

MemorySite* memorySite(Context* c,
                       Register base,
                       int offset = 0,
                       Register index = NoRegister,
                       unsigned scale = 1);
MemorySite* frameSite(Context* c, int frameIndex);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_SITE_H
