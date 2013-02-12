/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_REGALLOC_H
#define AVIAN_CODEGEN_REGALLOC_H

#include "common.h"

#include "codegen/registers.h"

class Aborter;

namespace avian {
namespace codegen {
namespace regalloc {

class RegisterAllocator {
public:
  Aborter* a;
  const RegisterFile* registerFile;

  RegisterAllocator(Aborter* a, const RegisterFile* registerFile);

};

} // namespace regalloc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_REGALLOC_H