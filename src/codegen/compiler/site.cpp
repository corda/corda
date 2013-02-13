/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "target.h"

#include "codegen/compiler/context.h"
#include "codegen/compiler/value.h"
#include "codegen/compiler/site.h"

namespace avian {
namespace codegen {
namespace compiler {


ResolvedPromise* resolved(Context* c, int64_t value);

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



Site* constantSite(Context* c, Promise* value) {
  return new(c->zone) ConstantSite(value);
}

Site* constantSite(Context* c, int64_t value) {
  return constantSite(c, resolved(c, value));
}

} // namespace compiler
} // namespace codegen
} // namespace avian