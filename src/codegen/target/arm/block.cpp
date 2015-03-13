/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace arm {

void resolve(MyBlock*);

unsigned padding(MyBlock*, unsigned);

MyBlock::MyBlock(Context* context, unsigned offset)
    : context(context),
      next(0),
      poolOffsetHead(0),
      poolOffsetTail(0),
      lastPoolOffsetTail(0),
      poolEventHead(0),
      poolEventTail(0),
      lastEventOffset(0),
      offset(offset),
      start(~0),
      size(0)
{
}

unsigned MyBlock::resolve(unsigned start, Assembler::Block* next)
{
  this->start = start;
  this->next = static_cast<MyBlock*>(next);

  arm::resolve(this);

  return start + size + padding(this, size);
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian
