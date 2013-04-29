/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"
#include "avian/common.h"

namespace avian {
namespace codegen {
namespace powerpc {

void resolve(MyBlock*);

unsigned padding(MyBlock*, unsigned);

MyBlock::MyBlock(Context* context, unsigned offset):
  context(context), next(0), jumpOffsetHead(0), jumpOffsetTail(0),
  lastJumpOffsetTail(0), jumpEventHead(0), jumpEventTail(0),
  lastEventOffset(0), offset(offset), start(~0), size(0), resolved(false)
{ }

unsigned MyBlock::resolve(unsigned start, Assembler::Block* next) {
  this->start = start;
  this->next = static_cast<MyBlock*>(next);

  powerpc::resolve(this);

  this->resolved = true;

  return start + size + padding(this, size);
}

} // namespace powerpc
} // namespace codegen
} // namespace avian
