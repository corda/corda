/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/compiler/context.h"
#include "codegen/compiler/promise.h"

namespace avian {
namespace codegen {
namespace compiler {

CodePromise::CodePromise(Context* c, CodePromise* next):
  c(c), offset(0), next(next)
{ }

CodePromise::CodePromise(Context* c, Promise* offset):
  c(c), offset(offset), next(0)
{ }

int64_t CodePromise::value() {
  if (resolved()) {
    return reinterpret_cast<intptr_t>(c->machineCode + offset->value());
  }
  
  abort(c);
}

bool CodePromise::resolved() {
  return c->machineCode != 0 and offset and offset->resolved();
}

CodePromise* codePromise(Context* c, Promise* offset) {
  return new (c->zone) CodePromise(c, offset);
}

Promise* shiftMaskPromise(Context* c, Promise* base, unsigned shift, int64_t mask) {
  return new (c->zone) ShiftMaskPromise(base, shift, mask);
}

Promise* combinedPromise(Context* c, Promise* low, Promise* high) {
  return new (c->zone) CombinedPromise(low, high);
}

Promise* resolvedPromise(Context* c, int64_t value) {
  return new (c->zone) ResolvedPromise(value);
}

} // namespace compiler
} // namespace codegen
} // namespace avian
