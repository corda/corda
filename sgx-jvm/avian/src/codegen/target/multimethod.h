/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_TARGET_MULTIMETHOD_H
#define AVIAN_CODEGEN_TARGET_MULTIMETHOD_H

namespace avian {
namespace codegen {

class Multimethod {
 public:
  inline static unsigned index(lir::UnaryOperation operation,
                               lir::Operand::Type operand)
  {
    return operation + (lir::UnaryOperationCount * (unsigned)operand);
  }
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_TARGET_MULTIMETHOD_H
