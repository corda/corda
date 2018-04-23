/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "block.h"

#include <avian/codegen/assembler.h>

namespace avian {
namespace codegen {
namespace x86 {

unsigned padding(AlignmentPadding* p,
                 unsigned index,
                 unsigned offset,
                 AlignmentPadding* limit);

MyBlock::MyBlock(unsigned offset)
    : next(0),
      firstPadding(0),
      lastPadding(0),
      offset(offset),
      start(~0),
      size(0)
{
}

unsigned MyBlock::resolve(unsigned start, Assembler::Block* next)
{
  this->start = start;
  this->next = static_cast<MyBlock*>(next);

  return start + size + padding(firstPadding, start, offset, lastPadding);
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
