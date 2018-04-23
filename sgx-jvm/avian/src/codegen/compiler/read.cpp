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
#include "codegen/compiler/read.h"

namespace avian {
namespace codegen {
namespace compiler {

SingleRead::SingleRead(const SiteMask& mask, Value* successor)
    : next_(0), mask(mask), high_(0), successor_(successor)
{
}

bool SingleRead::intersect(SiteMask* mask, unsigned)
{
  *mask = mask->intersectionWith(this->mask);

  return true;
}

Value* SingleRead::high(Context*)
{
  return high_;
}

Value* SingleRead::successor()
{
  return successor_;
}

bool SingleRead::valid()
{
  return true;
}

void SingleRead::append(Context* c UNUSED, Read* r)
{
  assertT(c, next_ == 0);
  next_ = r;
}

Read* SingleRead::next(Context*)
{
  return next_;
}

MultiRead::MultiRead()
    : reads(0), lastRead(0), firstTarget(0), lastTarget(0), visited(false)
{
}

bool MultiRead::intersect(SiteMask* mask, unsigned depth)
{
  if (depth > 0) {
    // short-circuit recursion to avoid poor performance in
    // deeply-nested branches
    return reads != 0;
  }

  bool result = false;
  if (not visited) {
    visited = true;
    for (List<Read*>** cell = &reads; *cell;) {
      Read* r = (*cell)->item;
      bool valid = r->intersect(mask, depth + 1);
      if (valid) {
        result = true;
        cell = &((*cell)->next);
      } else {
        *cell = (*cell)->next;
      }
    }
    visited = false;
  }
  return result;
}

Value* MultiRead::successor()
{
  return 0;
}

bool MultiRead::valid()
{
  bool result = false;
  if (not visited) {
    visited = true;
    for (List<Read*>** cell = &reads; *cell;) {
      Read* r = (*cell)->item;
      if (r->valid()) {
        result = true;
        cell = &((*cell)->next);
      } else {
        *cell = (*cell)->next;
      }
    }
    visited = false;
  }
  return result;
}

void MultiRead::append(Context* c, Read* r)
{
  List<Read*>* cell = cons<Read*>(c, r, 0);
  if (lastRead == 0) {
    reads = cell;
  } else {
    lastRead->next = cell;
  }
  lastRead = cell;

  if (false) {
    fprintf(stderr, "append %p to %p for %p\n", r, lastTarget, this);
  }

  lastTarget->item = r;
}

Read* MultiRead::next(Context* c)
{
  abort(c);
}

void MultiRead::allocateTarget(Context* c)
{
  List<Read*>* cell = cons<Read*>(c, 0, 0);

  if (false) {
    fprintf(stderr, "allocate target for %p: %p\n", this, cell);
  }

  if (lastTarget) {
    lastTarget->next = cell;
  } else {
    firstTarget = cell;
  }
  lastTarget = cell;
}

Read* MultiRead::nextTarget()
{
  if (false) {
    fprintf(stderr, "next target for %p: %p\n", this, firstTarget);
  }

  Read* r = firstTarget->item;
  firstTarget = firstTarget->next;
  return r;
}

StubRead::StubRead() : next_(0), read(0), visited(false), valid_(true)
{
}

bool StubRead::intersect(SiteMask* mask, unsigned depth)
{
  if (not visited) {
    visited = true;
    if (read) {
      bool valid = read->intersect(mask, depth);
      if (not valid) {
        read = 0;
      }
    }
    visited = false;
  }
  return valid_;
}

Value* StubRead::successor()
{
  return 0;
}

bool StubRead::valid()
{
  return valid_;
}

void StubRead::append(Context* c UNUSED, Read* r)
{
  assertT(c, next_ == 0);
  next_ = r;
}

Read* StubRead::next(Context*)
{
  return next_;
}

SingleRead* read(Context* c, const SiteMask& mask, Value* successor)
{
  assertT(c,
          (mask.typeMask != lir::Operand::MemoryMask) or mask.frameIndex >= 0);

  return new (c->zone) SingleRead(mask, successor);
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
