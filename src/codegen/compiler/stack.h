/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_STACK_H
#define AVIAN_CODEGEN_COMPILER_STACK_H

namespace avian {
namespace codegen {
namespace compiler {

class Value;

class Stack {
 public:
  Stack(unsigned index, Value* value, Stack* next):
    index(index), value(value), next(next)
  { }

  unsigned index;
  Value* value;
  Stack* next;
};

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_STACK_H
