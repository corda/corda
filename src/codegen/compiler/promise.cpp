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

CodePromise*
codePromise(Context* c, Promise* offset)
{
  return new (c->zone) CodePromise(c, offset);
}

} // namespace compiler
} // namespace codegen
} // namespace avian
