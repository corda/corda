/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_MULTIMETHOD_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_MULTIMETHOD_H

#include "avian/common.h"

#include <avian/codegen/lir.h>

namespace avian {
namespace codegen {
namespace x86 {

class ArchitectureContext;

unsigned index(ArchitectureContext*,
               lir::BinaryOperation operation,
               lir::Operand::Type operand1,
               lir::Operand::Type operand2);

unsigned index(ArchitectureContext* c UNUSED,
               lir::TernaryOperation operation,
               lir::Operand::Type operand1,
               lir::Operand::Type operand2);

unsigned branchIndex(ArchitectureContext* c UNUSED,
                     lir::Operand::Type operand1,
                     lir::Operand::Type operand2);

void populateTables(ArchitectureContext* c);

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_MULTIMETHOD_H
