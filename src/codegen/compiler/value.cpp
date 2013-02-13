/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "target.h"

#include "codegen/compiler/regalloc.h"
#include "codegen/compiler/site.h"

namespace avian {
namespace codegen {
namespace compiler {

Value::Value(Site* site, Site* target, lir::ValueType type):
  reads(0), lastRead(0), sites(site), source(0), target(target), buddy(this),
  nextWord(this), home(NoFrameIndex), type(type), wordIndex(0)
{ }

bool Value::findSite(Site* site) {
  for (Site* s = this->sites; s; s = s->next) {
    if (s == site) return true;
  }
  return false;
}

bool Value::isBuddyOf(Value* b) {
  Value* a = this;
  if (a == b) return true;
  for (Value* p = a->buddy; p != a; p = p->buddy) {
    if (p == b) return true;
  }
  return false;
}

} // namespace regalloc
} // namespace codegen
} // namespace avian
