/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_BLOCK_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_BLOCK_H

namespace avian {
namespace codegen {
namespace powerpc {

class JumpEvent;

class MyBlock: public Assembler::Block {
 public:
  MyBlock(Context* context, unsigned offset);

  virtual unsigned resolve(unsigned start, Assembler::Block* next);

  Context* context;
  MyBlock* next;
  JumpOffset* jumpOffsetHead;
  JumpOffset* jumpOffsetTail;
  JumpOffset* lastJumpOffsetTail;
  JumpEvent* jumpEventHead;
  JumpEvent* jumpEventTail;
  unsigned lastEventOffset;
  unsigned offset;
  unsigned start;
  unsigned size;
  bool resolved;
};

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_BLOCK_H
