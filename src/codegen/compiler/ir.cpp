/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/compiler/context.h"
#include "codegen/compiler/ir.h"

namespace avian {
namespace codegen {
namespace compiler {

LogicalInstruction::LogicalInstruction(int index, Stack* stack, Local* locals)
    : firstEvent(0),
      lastEvent(0),
      immediatePredecessor(0),
      stack(stack),
      locals(locals),
      machineOffset(0),
      /*subroutine(0), */ index(index)
{
}

LogicalInstruction* LogicalInstruction::next(Context* c)
{
  LogicalInstruction* i = this;
  for (size_t n = i->index + 1; n < c->logicalCode.count(); ++n) {
    i = c->logicalCode[n];
    if (i)
      return i;
  }
  return 0;
}

unsigned machineOffset(Context* c, int logicalIp)
{
  return c->logicalCode[logicalIp]->machineOffset->value();
}

Block::Block(Event* head)
    : head(head), nextBlock(0), nextInstruction(0), assemblerBlock(0), start(0)
{
}

Block* block(Context* c, Event* head)
{
  return new (c->zone) Block(head);
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
