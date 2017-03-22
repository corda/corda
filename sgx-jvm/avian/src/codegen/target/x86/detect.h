/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_DETECT_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_DETECT_H

#include <avian/codegen/assembler.h>

namespace avian {
namespace codegen {
namespace x86 {

class ArchitectureContext;

bool useSSE(ArchitectureContext* c);

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_DETECT_H
