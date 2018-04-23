/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "codegen/compiler/context.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/site.h"
#include "codegen/compiler/resource.h"
#include "codegen/compiler/frame.h"
#include "codegen/compiler/promise.h"

namespace avian {
namespace codegen {
namespace compiler {

int intersectFrameIndexes(int a, int b)
{
  if (a == NoFrameIndex or b == NoFrameIndex)
    return NoFrameIndex;
  if (a == AnyFrameIndex)
    return b;
  if (b == AnyFrameIndex)
    return a;
  if (a == b)
    return a;
  return NoFrameIndex;
}

SiteMask SiteMask::intersectionWith(const SiteMask& b)
{
  return SiteMask(typeMask & b.typeMask,
                  registerMask & b.registerMask,
                  intersectFrameIndexes(frameIndex, b.frameIndex));
}

SiteIterator::SiteIterator(Context* c,
                           Value* v,
                           bool includeBuddies,
                           bool includeNextWord)
    : c(c),
      originalValue(v),
      currentValue(v),
      includeBuddies(includeBuddies),
      includeNextWord(includeNextWord),
      pass(0),
      next_(findNext(&(v->sites))),
      previous(0)
{
}

Site** SiteIterator::findNext(Site** p)
{
  while (true) {
    if (*p) {
      if (pass == 0 or (*p)->registerSize(c) > c->targetInfo.pointerSize) {
        return p;
      } else {
        p = &((*p)->next);
      }
    } else {
      if (includeBuddies) {
        Value* v = currentValue->buddy;
        if (v != originalValue) {
          currentValue = v;
          p = &(v->sites);
          continue;
        }
      }

      if (includeNextWord and pass == 0) {
        Value* v = originalValue->nextWord;
        if (v != originalValue) {
          pass = 1;
          originalValue = v;
          currentValue = v;
          p = &(v->sites);
          continue;
        }
      }

      return 0;
    }
  }
}

bool SiteIterator::hasMore()
{
  if (previous) {
    next_ = findNext(&((*previous)->next));
    previous = 0;
  }
  return next_ != 0;
}

Site* SiteIterator::next()
{
  previous = next_;
  return *previous;
}

void SiteIterator::remove(Context* c)
{
  (*previous)->release(c, originalValue);
  *previous = (*previous)->next;
  next_ = findNext(previous);
  previous = 0;
}

unsigned Site::registerSize(Context* c)
{
  return c->targetInfo.pointerSize;
}

Site* constantSite(Context* c, Promise* value)
{
  return new (c->zone) ConstantSite(value);
}

Site* constantSite(Context* c, int64_t value)
{
  return constantSite(c, resolvedPromise(c, value));
}

class AddressSite : public Site {
 public:
  AddressSite(Promise* address) : address(address)
  {
  }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize)
  {
    if (address->resolved()) {
      return vm::snprintf(
          buffer, bufferSize, "address %" LLD, address->value());
    } else {
      return vm::snprintf(buffer, bufferSize, "address unresolved");
    }
  }

  virtual unsigned copyCost(Context*, Site* s)
  {
    return (s == this ? 0 : AddressCopyCost);
  }

  virtual bool match(Context*, const SiteMask& mask)
  {
    return mask.typeMask & lir::Operand::AddressMask;
  }

  virtual bool loneMatch(Context*, const SiteMask&)
  {
    return false;
  }

  virtual bool matchNextWord(Context* c, Site*, unsigned)
  {
    abort(c);
  }

  virtual lir::Operand::Type type(Context*)
  {
    return lir::Operand::Type::Address;
  }

  virtual void asAssemblerOperand(Context* c UNUSED,
                                  Site* high UNUSED,
                                  lir::Operand* result)
  {
    assertT(c, high == this);

    new (result) lir::Address(address);
  }

  virtual Site* copy(Context* c)
  {
    return addressSite(c, address);
  }

  virtual Site* copyLow(Context* c)
  {
    abort(c);
  }

  virtual Site* copyHigh(Context* c)
  {
    abort(c);
  }

  virtual Site* makeNextWord(Context* c, unsigned)
  {
    abort(c);
  }

  virtual SiteMask mask(Context*)
  {
    return SiteMask(lir::Operand::AddressMask, 0, NoFrameIndex);
  }

  virtual SiteMask nextWordMask(Context* c, unsigned)
  {
    abort(c);
  }

  Promise* address;
};

Site* addressSite(Context* c, Promise* address)
{
  return new (c->zone) AddressSite(address);
}

RegisterSite::RegisterSite(RegisterMask mask, Register number)
    : mask_(mask), number(number)
{
}

unsigned RegisterSite::toString(Context*, char* buffer, unsigned bufferSize)
{
  if (number != NoRegister) {
    return vm::snprintf(buffer, bufferSize, "%p register %d", this, number);
  } else {
    return vm::snprintf(
        buffer, bufferSize, "%p register unacquired (mask %d)", this, mask_);
  }
}

unsigned RegisterSite::copyCost(Context* c, Site* s)
{
  assertT(c, number != NoRegister);

  if (s and (this == s
             or (s->type(c) == lir::Operand::Type::RegisterPair
                 and (static_cast<RegisterSite*>(s)->mask_.contains(number))))) {
    return 0;
  } else {
    return RegisterCopyCost;
  }
}

bool RegisterSite::match(Context* c UNUSED, const SiteMask& mask)
{
  assertT(c, number != NoRegister);

  if ((mask.typeMask & lir::Operand::RegisterPairMask)) {
    return mask.registerMask.contains(number);
  } else {
    return false;
  }
}

bool RegisterSite::loneMatch(Context* c UNUSED, const SiteMask& mask)
{
  assertT(c, number != NoRegister);

  if ((mask.typeMask & lir::Operand::RegisterPairMask)) {
    return mask.registerMask.containsExactly(number);
  } else {
    return false;
  }
}

bool RegisterSite::matchNextWord(Context* c, Site* s, unsigned)
{
  assertT(c, number != NoRegister);

  if (s->type(c) != lir::Operand::Type::RegisterPair) {
    return false;
  }

  RegisterSite* rs = static_cast<RegisterSite*>(s);
  unsigned size = rs->registerSize(c);
  if (size > c->targetInfo.pointerSize) {
    assertT(c, number != NoRegister);
    return number == rs->number;
  } else {
    RegisterMask mask = c->regFile->generalRegisters;
    return mask.contains(number) and mask.contains(rs->number);
  }
}

void RegisterSite::acquire(Context* c, Value* v)
{
  Target target;
  if (number != NoRegister) {
    target = Target(number, 0);
  } else {
    target = pickRegisterTarget(c, v, mask_);
    expect(c, target.cost < Target::Impossible);
  }

  RegisterResource* resource = c->registerResources + target.index;
  compiler::acquire(c, resource, v, this);

  number = Register(target.index);
}

void RegisterSite::release(Context* c, Value* v)
{
  assertT(c, number != NoRegister);

  compiler::release(c, c->registerResources + number.index(), v, this);
}

void RegisterSite::freeze(Context* c, Value* v)
{
  assertT(c, number != NoRegister);

  c->registerResources[number.index()].freeze(c, v);
}

void RegisterSite::thaw(Context* c, Value* v)
{
  assertT(c, number != NoRegister);

  c->registerResources[number.index()].thaw(c, v);
}

bool RegisterSite::frozen(Context* c UNUSED)
{
  assertT(c, number != NoRegister);

  return c->registerResources[number.index()].freezeCount != 0;
}

lir::Operand::Type RegisterSite::type(Context*)
{
  return lir::Operand::Type::RegisterPair;
}

void RegisterSite::asAssemblerOperand(Context* c UNUSED,
                                      Site* high,
                                      lir::Operand* result)
{
  assertT(c, number != NoRegister);

  Register highNumber;
  if (high != this) {
    highNumber = static_cast<RegisterSite*>(high)->number;
    assertT(c, highNumber != NoRegister);
  } else {
    highNumber = NoRegister;
  }

  new (result) lir::RegisterPair(number, highNumber);
}

Site* RegisterSite::copy(Context* c)
{
  RegisterMask mask;

  if (number != NoRegister) {
    mask = RegisterMask(number);
  } else {
    mask = mask_;
  }

  return freeRegisterSite(c, mask);
}

Site* RegisterSite::copyLow(Context* c)
{
  abort(c);
}

Site* RegisterSite::copyHigh(Context* c)
{
  abort(c);
}

Site* RegisterSite::makeNextWord(Context* c, unsigned)
{
  assertT(c, number != NoRegister);
  assertT(c, c->regFile->generalRegisters.contains(number));

  return freeRegisterSite(c, c->regFile->generalRegisters);
}

SiteMask RegisterSite::mask(Context* c UNUSED)
{
  return SiteMask(lir::Operand::RegisterPairMask, mask_, NoFrameIndex);
}

SiteMask RegisterSite::nextWordMask(Context* c, unsigned)
{
  assertT(c, number != NoRegister);

  if (registerSize(c) > c->targetInfo.pointerSize) {
    return SiteMask(lir::Operand::RegisterPairMask, number, NoFrameIndex);
  } else {
    return SiteMask(lir::Operand::RegisterPairMask,
                    c->regFile->generalRegisters,
                    NoFrameIndex);
  }
}

unsigned RegisterSite::registerSize(Context* c)
{
  assertT(c, number != NoRegister);

  if (c->regFile->floatRegisters.contains(number)) {
    return c->arch->floatRegisterSize();
  } else {
    return c->targetInfo.pointerSize;
  }
}

RegisterMask RegisterSite::registerMask(Context* c UNUSED)
{
  assertT(c, number != NoRegister);

  return RegisterMask(number);
}

Site* registerSite(Context* c, Register number)
{
  assertT(c, number != NoRegister);
  assertT(c,
          (c->regFile->generalRegisters
                           | c->regFile->floatRegisters).contains(number));

  return new (c->zone) RegisterSite(RegisterMask(number), number);
}

Site* freeRegisterSite(Context* c, RegisterMask mask)
{
  return new (c->zone) RegisterSite(mask, NoRegister);
}

MemorySite::MemorySite(Register base, int offset, Register index, unsigned scale)
    : acquired(false), base(base), offset(offset), index(index), scale(scale)
{
}

unsigned MemorySite::toString(Context*, char* buffer, unsigned bufferSize)
{
  if (acquired) {
    return vm::snprintf(
        buffer, bufferSize, "memory %d 0x%x %d %d", base, offset, index, scale);
  } else {
    return vm::snprintf(buffer, bufferSize, "memory unacquired");
  }
}

unsigned MemorySite::copyCost(Context* c, Site* s)
{
  assertT(c, acquired);

  if (s and (this == s or (s->type(c) == lir::Operand::Type::Memory
                           and static_cast<MemorySite*>(s)->base == base
                           and static_cast<MemorySite*>(s)->offset == offset
                           and static_cast<MemorySite*>(s)->index == index
                           and static_cast<MemorySite*>(s)->scale == scale))) {
    return 0;
  } else {
    return MemoryCopyCost;
  }
}

bool MemorySite::conflicts(const SiteMask& mask)
{
  return (mask.typeMask & lir::Operand::RegisterPairMask) != 0
         and (!mask.registerMask.contains(base)
              or (index != NoRegister
                  and !mask.registerMask.contains(index)));
}

bool MemorySite::match(Context* c, const SiteMask& mask)
{
  assertT(c, acquired);

  if (mask.typeMask & lir::Operand::MemoryMask) {
    if (mask.frameIndex >= 0) {
      if (base == c->arch->stack()) {
        assertT(c, index == NoRegister);
        return static_cast<int>(frameIndexToOffset(c, mask.frameIndex))
               == offset;
      } else {
        return false;
      }
    } else {
      return true;
    }
  } else {
    return false;
  }
}

bool MemorySite::loneMatch(Context* c, const SiteMask& mask)
{
  assertT(c, acquired);

  if (mask.typeMask & lir::Operand::MemoryMask) {
    if (base == c->arch->stack()) {
      assertT(c, index == NoRegister);

      if (mask.frameIndex == AnyFrameIndex) {
        return false;
      } else {
        return true;
      }
    }
  }
  return false;
}

bool MemorySite::matchNextWord(Context* c, Site* s, unsigned index)
{
  if (s->type(c) == lir::Operand::Type::Memory) {
    MemorySite* ms = static_cast<MemorySite*>(s);
    return ms->base == this->base
           and ((index == 1
                 and ms->offset
                     == static_cast<int>(this->offset
                                         + c->targetInfo.pointerSize))
                or (index == 0
                    and this->offset
                        == static_cast<int>(ms->offset
                                            + c->targetInfo.pointerSize)))
           and ms->index == this->index and ms->scale == this->scale;
  } else {
    return false;
  }
}

void MemorySite::acquire(Context* c, Value* v)
{
  c->registerResources[base.index()].increment(c);
  if (index != NoRegister) {
    c->registerResources[index.index()].increment(c);
  }

  if (base == c->arch->stack()) {
    assertT(c, index == NoRegister);
    assertT(c, not c->frameResources[offsetToFrameIndex(c, offset)].reserved);

    compiler::acquire(
        c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
  }

  acquired = true;
}

void MemorySite::release(Context* c, Value* v)
{
  if (base == c->arch->stack()) {
    assertT(c, index == NoRegister);
    assertT(c, not c->frameResources[offsetToFrameIndex(c, offset)].reserved);

    compiler::release(
        c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
  }

  c->registerResources[base.index()].decrement(c);
  if (index != NoRegister) {
    c->registerResources[index.index()].decrement(c);
  }

  acquired = false;
}

void MemorySite::freeze(Context* c, Value* v)
{
  if (base == c->arch->stack()) {
    c->frameResources[offsetToFrameIndex(c, offset)].freeze(c, v);
  } else {
    c->registerResources[base.index()].increment(c);
    if (index != NoRegister) {
      c->registerResources[index.index()].increment(c);
    }
  }
}

void MemorySite::thaw(Context* c, Value* v)
{
  if (base == c->arch->stack()) {
    c->frameResources[offsetToFrameIndex(c, offset)].thaw(c, v);
  } else {
    c->registerResources[base.index()].decrement(c);
    if (index != NoRegister) {
      c->registerResources[index.index()].decrement(c);
    }
  }
}

bool MemorySite::frozen(Context* c)
{
  return base == c->arch->stack()
         and c->frameResources[offsetToFrameIndex(c, offset)].freezeCount != 0;
}

lir::Operand::Type MemorySite::type(Context*)
{
  return lir::Operand::Type::Memory;
}

void MemorySite::asAssemblerOperand(Context* c UNUSED,
                                    Site* high UNUSED,
                                    lir::Operand* result)
{
  // todo: endianness?
  assertT(c,
          high == this
          or (static_cast<MemorySite*>(high)->base == base
              and static_cast<MemorySite*>(high)->offset
                  == static_cast<int>(offset + c->targetInfo.pointerSize)
              and static_cast<MemorySite*>(high)->index == index
              and static_cast<MemorySite*>(high)->scale == scale));

  assertT(c, acquired);

  new (result) lir::Memory(base, offset, index, scale);
}

Site* MemorySite::copy(Context* c)
{
  return memorySite(c, base, offset, index, scale);
}

Site* MemorySite::copyHalf(Context* c, bool add)
{
  if (add) {
    return memorySite(
        c, base, offset + c->targetInfo.pointerSize, index, scale);
  } else {
    return copy(c);
  }
}

Site* MemorySite::copyLow(Context* c)
{
  return copyHalf(c, c->arch->bigEndian());
}

Site* MemorySite::copyHigh(Context* c)
{
  return copyHalf(c, not c->arch->bigEndian());
}

Site* MemorySite::makeNextWord(Context* c, unsigned index)
{
  return memorySite(c,
                    base,
                    offset + ((index == 1) xor c->arch->bigEndian()
                                  ? c->targetInfo.pointerSize
                                  : -c->targetInfo.pointerSize),
                    this->index,
                    scale);
}

SiteMask MemorySite::mask(Context* c)
{
  return SiteMask(lir::Operand::MemoryMask,
                  0,
                  (base == c->arch->stack())
                      ? static_cast<int>(offsetToFrameIndex(c, offset))
                      : NoFrameIndex);
}

SiteMask MemorySite::nextWordMask(Context* c, unsigned index)
{
  int frameIndex;
  if (base == c->arch->stack()) {
    assertT(c, this->index == NoRegister);
    frameIndex = static_cast<int>(offsetToFrameIndex(c, offset))
                 + ((index == 1) xor c->arch->bigEndian() ? 1 : -1);
  } else {
    frameIndex = NoFrameIndex;
  }
  return SiteMask(lir::Operand::MemoryMask, 0, frameIndex);
}

bool MemorySite::isVolatile(Context* c)
{
  return base != c->arch->stack();
}

MemorySite* memorySite(Context* c,
                       Register base,
                       int offset,
                       Register index,
                       unsigned scale)
{
  return new (c->zone) MemorySite(base, offset, index, scale);
}

MemorySite* frameSite(Context* c, int frameIndex)
{
  assertT(c, frameIndex >= 0);
  return memorySite(c,
                    c->arch->stack(),
                    frameIndexToOffset(c, frameIndex),
                    NoRegister,
                    0);
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
