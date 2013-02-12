/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/regalloc.h"

namespace avian {
namespace codegen {
namespace regalloc {

RegisterAllocator::RegisterAllocator(Aborter* a, const RegisterFile* registerFile):
  a(a),
  registerFile(registerFile)
{ }

} // namespace regalloc
} // namespace codegen
} // namespace avian