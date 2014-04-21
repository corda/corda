/* Copyright (c) 2008-2014, Avian Contributors

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



int intersectFrameIndexes(int a, int b) {
  if (a == NoFrameIndex or b == NoFrameIndex) return NoFrameIndex;
  if (a == AnyFrameIndex) return b;
  if (b == AnyFrameIndex) return a;
  if (a == b) return a;
  return NoFrameIndex;
}


SiteMask SiteMask::intersectionWith(const SiteMask& b) {
  return SiteMask(typeMask & b.typeMask, registerMask & b.registerMask,
                  intersectFrameIndexes(frameIndex, b.frameIndex));
}

SiteIterator::SiteIterator(Context* c, Value* v, bool includeBuddies,
             bool includeNextWord):
  c(c),
  originalValue(v),
  currentValue(v),
  includeBuddies(includeBuddies),
  includeNextWord(includeNextWord),
  pass(0),
  next_(findNext(&(v->sites))),
  previous(0)
{ }

Site** SiteIterator::findNext(Site** p) {
  while (true) {
    if (*p) {
      if (pass == 0 or (*p)->registerSize(c) > vm::TargetBytesPerWord) {
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

bool SiteIterator::hasMore() {
  if (previous) {
    next_ = findNext(&((*previous)->next));
    previous = 0;
  }
  return next_ != 0;
}

Site* SiteIterator::next() {
  previous = next_;
  return *previous;
}

void SiteIterator::remove(Context* c) {
  (*previous)->release(c, originalValue);
  *previous = (*previous)->next;
  next_ = findNext(previous);
  previous = 0;
}



unsigned Site::registerSize(Context*) {
  return vm::TargetBytesPerWord;
}



Site* constantSite(Context* c, Promise* value) {
  return new(c->zone) ConstantSite(value);
}

Site* constantSite(Context* c, int64_t value) {
  return constantSite(c, resolvedPromise(c, value));
}



class AddressSite: public Site {
 public:
  AddressSite(Promise* address): address(address) { }

  virtual unsigned toString(Context*, char* buffer, unsigned bufferSize) {
    if (address->resolved()) {
      return vm::snprintf
        (buffer, bufferSize, "address %" LLD, address->value());
    } else {
      return vm::snprintf(buffer, bufferSize, "address unresolved");
    }
  }

  virtual unsigned copyCost(Context*, Site* s) {
    return (s == this ? 0 : AddressCopyCost);
  }

  virtual bool match(Context*, const SiteMask& mask) {
    return mask.typeMask & (1 << lir::AddressOperand);
  }

  virtual bool loneMatch(Context*, const SiteMask&) {
    return false;
  }

  virtual bool matchNextWord(Context* c, Site*, unsigned) {
    abort(c);
  }

  virtual lir::OperandType type(Context*) {
    return lir::AddressOperand;
  }

  virtual void asAssemblerOperand(Context* c UNUSED, Site* high UNUSED,
                                  lir::Operand* result)
  {
    assert(c, high == this);

    new (result) lir::Address(address);
  }

  virtual Site* copy(Context* c) {
    return addressSite(c, address);
  }

  virtual Site* copyLow(Context* c) {
    abort(c);
  }

  virtual Site* copyHigh(Context* c) {
    abort(c);
  }

  virtual Site* makeNextWord(Context* c, unsigned) {
    abort(c);
  }

  virtual SiteMask mask(Context*) {
    return SiteMask(1 << lir::AddressOperand, 0, NoFrameIndex);
  }

  virtual SiteMask nextWordMask(Context* c, unsigned) {
    abort(c);
  }

  Promise* address;
};

Site* addressSite(Context* c, Promise* address) {
  return new(c->zone) AddressSite(address);
}


RegisterSite::RegisterSite(uint32_t mask, int number):
  mask_(mask), number(number)
{ }

unsigned RegisterSite::toString(Context*, char* buffer, unsigned bufferSize) {
  if (number != lir::NoRegister) {
    return vm::snprintf(buffer, bufferSize, "%p register %d", this, number);
  } else {
    return vm::snprintf(buffer, bufferSize,
                        "%p register unacquired (mask %d)", this, mask_);
  }
}

unsigned RegisterSite::copyCost(Context* c, Site* s) {
  assert(c, number != lir::NoRegister);

  if (s and
      (this == s or
       (s->type(c) == lir::RegisterOperand
        and (static_cast<RegisterSite*>(s)->mask_ & (1 << number)))))
  {
    return 0;
  } else {
    return RegisterCopyCost;
  }
}

bool RegisterSite::match(Context* c UNUSED, const SiteMask& mask) {
  assert(c, number != lir::NoRegister);

  if ((mask.typeMask & (1 << lir::RegisterOperand))) {
    return ((static_cast<uint64_t>(1) << number) & mask.registerMask);
  } else {
    return false;
  }
}

bool RegisterSite::loneMatch(Context* c UNUSED, const SiteMask& mask) {
  assert(c, number != lir::NoRegister);

  if ((mask.typeMask & (1 << lir::RegisterOperand))) {
    return ((static_cast<uint64_t>(1) << number) == mask.registerMask);
  } else {
    return false;
  }
}

bool RegisterSite::matchNextWord(Context* c, Site* s, unsigned) {
  assert(c, number != lir::NoRegister);

  if (s->type(c) != lir::RegisterOperand) {
    return false;
  }

  RegisterSite* rs = static_cast<RegisterSite*>(s);
  unsigned size = rs->registerSize(c);
  if (size > vm::TargetBytesPerWord) {
    assert(c, number != lir::NoRegister);
    return number == rs->number;
  } else {
    uint32_t mask = c->regFile->generalRegisters.mask;
    return ((1 << number) & mask) and ((1 << rs->number) & mask);
  }
}

void RegisterSite::acquire(Context* c, Value* v) {
  Target target;
  if (number != lir::NoRegister) {
    target = Target(number, lir::RegisterOperand, 0);
  } else {
    target = pickRegisterTarget(c, v, mask_);
    expect(c, target.cost < Target::Impossible);
  }

  RegisterResource* resource = c->registerResources + target.index;
  compiler::acquire(c, resource, v, this);

  number = target.index;
}

void RegisterSite::release(Context* c, Value* v) {
  assert(c, number != lir::NoRegister);

  compiler::release(c, c->registerResources + number, v, this);
}

void RegisterSite::freeze(Context* c, Value* v) {
  assert(c, number != lir::NoRegister);

  c->registerResources[number].freeze(c, v);
}

void RegisterSite::thaw(Context* c, Value* v) {
  assert(c, number != lir::NoRegister);

  c->registerResources[number].thaw(c, v);
}

bool RegisterSite::frozen(Context* c UNUSED) {
  assert(c, number != lir::NoRegister);

  return c->registerResources[number].freezeCount != 0;
}

lir::OperandType RegisterSite::type(Context*) {
  return lir::RegisterOperand;
}

void RegisterSite::asAssemblerOperand(Context* c UNUSED, Site* high,
                                lir::Operand* result)
{
  assert(c, number != lir::NoRegister);

  int highNumber;
  if (high != this) {
    highNumber = static_cast<RegisterSite*>(high)->number;
    assert(c, highNumber != lir::NoRegister);
  } else {
    highNumber = lir::NoRegister;
  }

  new (result) lir::Register(number, highNumber);
}

Site* RegisterSite::copy(Context* c) {
  uint32_t mask;
  
  if (number != lir::NoRegister) {
    mask = 1 << number;
  } else {
    mask = mask_;
  }

  return freeRegisterSite(c, mask);
}

Site* RegisterSite::copyLow(Context* c) {
  abort(c);
}

Site* RegisterSite::copyHigh(Context* c) {
  abort(c);
}

Site* RegisterSite::makeNextWord(Context* c, unsigned) {
  assert(c, number != lir::NoRegister);
  assert(c, ((1 << number) & c->regFile->generalRegisters.mask));

  return freeRegisterSite(c, c->regFile->generalRegisters.mask);    
}

SiteMask RegisterSite::mask(Context* c UNUSED) {
  return SiteMask(1 << lir::RegisterOperand, mask_, NoFrameIndex);
}

SiteMask RegisterSite::nextWordMask(Context* c, unsigned) {
  assert(c, number != lir::NoRegister);

  if (registerSize(c) > vm::TargetBytesPerWord) {
    return SiteMask
      (1 << lir::RegisterOperand, number, NoFrameIndex);
  } else {
    return SiteMask
      (1 << lir::RegisterOperand, c->regFile->generalRegisters.mask, NoFrameIndex);
  }
}

unsigned RegisterSite::registerSize(Context* c) {
  assert(c, number != lir::NoRegister);

  if ((1 << number) & c->regFile->floatRegisters.mask) {
    return c->arch->floatRegisterSize();
  } else {
    return vm::TargetBytesPerWord;
  }
}

unsigned RegisterSite::registerMask(Context* c UNUSED) {
  assert(c, number != lir::NoRegister);

  return 1 << number;
}



Site* registerSite(Context* c, int number) {
  assert(c, number >= 0);
  assert(c, (1 << number) & (c->regFile->generalRegisters.mask
                             | c->regFile->floatRegisters.mask));

  return new(c->zone) RegisterSite(1 << number, number);
}

Site* freeRegisterSite(Context* c, uint32_t mask) {
  return new(c->zone) RegisterSite(mask, lir::NoRegister);
}

MemorySite::MemorySite(int base, int offset, int index, unsigned scale):
  acquired(false), base(base), offset(offset), index(index), scale(scale)
{ }

unsigned MemorySite::toString(Context*, char* buffer, unsigned bufferSize) {
  if (acquired) {
    return vm::snprintf(buffer, bufferSize, "memory %d 0x%x %d %d",
                    base, offset, index, scale);
  } else {
    return vm::snprintf(buffer, bufferSize, "memory unacquired");
  }
}

unsigned MemorySite::copyCost(Context* c, Site* s) {
  assert(c, acquired);    

  if (s and
      (this == s or
       (s->type(c) == lir::MemoryOperand
        and static_cast<MemorySite*>(s)->base == base
        and static_cast<MemorySite*>(s)->offset == offset
        and static_cast<MemorySite*>(s)->index == index
        and static_cast<MemorySite*>(s)->scale == scale)))
  {
    return 0;
  } else {
    return MemoryCopyCost;
  }
}

bool MemorySite::conflicts(const SiteMask& mask) {
  return (mask.typeMask & (1 << lir::RegisterOperand)) != 0
    and (((1 << base) & mask.registerMask) == 0
         or (index != lir::NoRegister
             and ((1 << index) & mask.registerMask) == 0));
}

bool MemorySite::match(Context* c, const SiteMask& mask) {
  assert(c, acquired);

  if (mask.typeMask & (1 << lir::MemoryOperand)) {
    if (mask.frameIndex >= 0) {
      if (base == c->arch->stack()) {
        assert(c, index == lir::NoRegister);
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

bool MemorySite::loneMatch(Context* c, const SiteMask& mask) {
  assert(c, acquired);

  if (mask.typeMask & (1 << lir::MemoryOperand)) {
    if (base == c->arch->stack()) {
      assert(c, index == lir::NoRegister);

      if (mask.frameIndex == AnyFrameIndex) {
        return false;
      } else {
        return true;
      }
    }
  }
  return false;
}

bool MemorySite::matchNextWord(Context* c, Site* s, unsigned index) {
  if (s->type(c) == lir::MemoryOperand) {
    MemorySite* ms = static_cast<MemorySite*>(s);
    return ms->base == this->base
      and ((index == 1 and ms->offset == static_cast<int>
            (this->offset + vm::TargetBytesPerWord))
           or (index == 0 and this->offset == static_cast<int>
               (ms->offset + vm::TargetBytesPerWord)))
      and ms->index == this->index
      and ms->scale == this->scale;
  } else {
    return false;
  }
}

void MemorySite::acquire(Context* c, Value* v) {
  c->registerResources[base].increment(c);
  if (index != lir::NoRegister) {
    c->registerResources[index].increment(c);
  }

  if (base == c->arch->stack()) {
    assert(c, index == lir::NoRegister);
    assert
      (c, not c->frameResources[offsetToFrameIndex(c, offset)].reserved);

    compiler::acquire
      (c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
  }

  acquired = true;
}

void MemorySite::release(Context* c, Value* v) {
  if (base == c->arch->stack()) {
    assert(c, index == lir::NoRegister);
    assert
      (c, not c->frameResources[offsetToFrameIndex(c, offset)].reserved);

    compiler::release
      (c, c->frameResources + offsetToFrameIndex(c, offset), v, this);
  }

  c->registerResources[base].decrement(c);
  if (index != lir::NoRegister) {
    c->registerResources[index].decrement(c);
  }

  acquired = false;
}

void MemorySite::freeze(Context* c, Value* v) {
  if (base == c->arch->stack()) {
    c->frameResources[offsetToFrameIndex(c, offset)].freeze(c, v);
  } else {
    c->registerResources[base].increment(c);
    if (index != lir::NoRegister) {
      c->registerResources[index].increment(c);
    }
  }
}

void MemorySite::thaw(Context* c, Value* v) {
  if (base == c->arch->stack()) {
    c->frameResources[offsetToFrameIndex(c, offset)].thaw(c, v);
  } else {
    c->registerResources[base].decrement(c);
    if (index != lir::NoRegister) {
      c->registerResources[index].decrement(c);
    }
  }
}

bool MemorySite::frozen(Context* c) {
  return base == c->arch->stack()
    and c->frameResources[offsetToFrameIndex(c, offset)].freezeCount != 0;
}

lir::OperandType MemorySite::type(Context*) {
  return lir::MemoryOperand;
}

void MemorySite::asAssemblerOperand(Context* c UNUSED, Site* high UNUSED,
                                lir::Operand* result)
{
  // todo: endianness?
  assert(c, high == this
         or (static_cast<MemorySite*>(high)->base == base
             and static_cast<MemorySite*>(high)->offset
             == static_cast<int>(offset + vm::TargetBytesPerWord)
             and static_cast<MemorySite*>(high)->index == index
             and static_cast<MemorySite*>(high)->scale == scale));

  assert(c, acquired);

  new (result) lir::Memory(base, offset, index, scale);
}

Site* MemorySite::copy(Context* c) {
  return memorySite(c, base, offset, index, scale);
}

Site* MemorySite::copyHalf(Context* c, bool add) {
  if (add) {
    return memorySite(c, base, offset + vm::TargetBytesPerWord, index, scale);
  } else {
    return copy(c);
  }
}

Site* MemorySite::copyLow(Context* c) {
  return copyHalf(c, c->arch->bigEndian());
}

Site* MemorySite::copyHigh(Context* c) {
  return copyHalf(c, not c->arch->bigEndian());
}

Site* MemorySite::makeNextWord(Context* c, unsigned index) {
  return memorySite
    (c, base, offset + ((index == 1) xor c->arch->bigEndian()
                        ? vm::TargetBytesPerWord : -vm::TargetBytesPerWord),
     this->index, scale);
}

SiteMask MemorySite::mask(Context* c) {
  return SiteMask(1 << lir::MemoryOperand, 0, (base == c->arch->stack())
                  ? static_cast<int>(offsetToFrameIndex(c, offset))
                  : NoFrameIndex);
}

SiteMask MemorySite::nextWordMask(Context* c, unsigned index) {
  int frameIndex;
  if (base == c->arch->stack()) {
    assert(c, this->index == lir::NoRegister);
    frameIndex = static_cast<int>(offsetToFrameIndex(c, offset))
      + ((index == 1) xor c->arch->bigEndian() ? 1 : -1);
  } else {
    frameIndex = NoFrameIndex;
  }
  return SiteMask(1 << lir::MemoryOperand, 0, frameIndex);
}

bool MemorySite::isVolatile(Context* c) {
  return base != c->arch->stack();
}


MemorySite* memorySite(Context* c, int base, int offset, int index, unsigned scale) {
  return new(c->zone) MemorySite(base, offset, index, scale);
}

MemorySite* frameSite(Context* c, int frameIndex) {
  assert(c, frameIndex >= 0);
  return memorySite
    (c, c->arch->stack(), frameIndexToOffset(c, frameIndex), lir::NoRegister, 0);
}

} // namespace compiler
} // namespace codegen
} // namespace avian
