/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_ARM_BLOCK_H
#define AVIAN_CODEGEN_ASSEMBLER_ARM_BLOCK_H

#include <avian/codegen/lir.h>
#include <avian/codegen/assembler.h>

namespace avian {
namespace codegen {
namespace arm {

class PoolEvent;

class MyBlock : public Assembler::Block {
 public:
  MyBlock(Context* context, unsigned offset);

  virtual unsigned resolve(unsigned start, Assembler::Block* next);

  Context* context;
  MyBlock* next;
  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  PoolOffset* lastPoolOffsetTail;
  PoolEvent* poolEventHead;
  PoolEvent* poolEventTail;
  unsigned lastEventOffset;
  unsigned offset;
  unsigned start;
  unsigned size;
};

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_ARM_BLOCK_H
