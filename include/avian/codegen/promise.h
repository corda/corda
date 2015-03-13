/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_PROMISE_H
#define AVIAN_CODEGEN_PROMISE_H

#include <avian/util/allocator.h>
#include <avian/util/abort.h>
#include <avian/system/system.h>

namespace avian {
namespace codegen {

class Promise {
 public:
  class Listener {
   public:
    virtual bool resolve(int64_t value, void** location) = 0;

    Listener* next;
  };

  virtual int64_t value() = 0;
  virtual bool resolved() = 0;
  virtual Listener* listen(unsigned)
  {
    return 0;
  }
};

class ResolvedPromise : public Promise {
 public:
  ResolvedPromise(int64_t value) : value_(value)
  {
  }

  virtual int64_t value()
  {
    return value_;
  }

  virtual bool resolved()
  {
    return true;
  }

  int64_t value_;
};

class ShiftMaskPromise : public Promise {
 public:
  ShiftMaskPromise(Promise* base, unsigned shift, int64_t mask)
      : base(base), shift(shift), mask(mask)
  {
  }

  virtual int64_t value()
  {
    return (base->value() >> shift) & mask;
  }

  virtual bool resolved()
  {
    return base->resolved();
  }

  Promise* base;
  unsigned shift;
  int64_t mask;
};

class CombinedPromise : public Promise {
 public:
  CombinedPromise(Promise* low, Promise* high) : low(low), high(high)
  {
  }

  virtual int64_t value()
  {
    return low->value() | (high->value() << 32);
  }

  virtual bool resolved()
  {
    return low->resolved() and high->resolved();
  }

  Promise* low;
  Promise* high;
};

class OffsetPromise : public Promise {
 public:
  OffsetPromise(Promise* base, int64_t offset) : base(base), offset(offset)
  {
  }

  virtual int64_t value()
  {
    return base->value() + offset;
  }

  virtual bool resolved()
  {
    return base->resolved();
  }

  Promise* base;
  int64_t offset;
};

class ListenPromise : public Promise {
 public:
  ListenPromise(vm::System* s, util::AllocOnly* allocator)
      : s(s), allocator(allocator), listener(0)
  {
  }

  virtual int64_t value()
  {
    abort(s);
  }

  virtual bool resolved()
  {
    return false;
  }

  virtual Listener* listen(unsigned sizeInBytes)
  {
    Listener* l = static_cast<Listener*>(allocator->allocate(sizeInBytes));
    l->next = listener;
    listener = l;
    return l;
  }

  vm::System* s;
  util::AllocOnly* allocator;
  Listener* listener;
  Promise* promise;
};

class DelayedPromise : public ListenPromise {
 public:
  DelayedPromise(vm::System* s,
                 util::AllocOnly* allocator,
                 Promise* basis,
                 DelayedPromise* next)
      : ListenPromise(s, allocator), basis(basis), next(next)
  {
  }

  virtual int64_t value()
  {
    abort(s);
  }

  virtual bool resolved()
  {
    return false;
  }

  virtual Listener* listen(unsigned sizeInBytes)
  {
    Listener* l = static_cast<Listener*>(allocator->allocate(sizeInBytes));
    l->next = listener;
    listener = l;
    return l;
  }

  Promise* basis;
  DelayedPromise* next;
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_PROMISE_H
