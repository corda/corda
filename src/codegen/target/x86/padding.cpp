/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/alloc-vector.h"

#include "context.h"
#include "padding.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace x86 {

AlignmentPadding::AlignmentPadding(Context* c,
                                   unsigned instructionOffset,
                                   unsigned alignment)
    : offset(c->code.length()),
      instructionOffset(instructionOffset),
      alignment(alignment),
      next(0),
      padding(-1)
{
  if (c->lastBlock->firstPadding) {
    c->lastBlock->lastPadding->next = this;
  } else {
    c->lastBlock->firstPadding = this;
  }
  c->lastBlock->lastPadding = this;
}

unsigned padding(AlignmentPadding* p,
                 unsigned start,
                 unsigned offset,
                 AlignmentPadding* limit)
{
  unsigned padding = 0;
  if (limit) {
    if (limit->padding == -1) {
      for (; p; p = p->next) {
        if (p->padding == -1) {
          unsigned index = p->offset - offset;
          while ((start + index + padding + p->instructionOffset)
                 % p->alignment) {
            ++padding;
          }

          p->padding = padding;

          if (p == limit)
            break;
        } else {
          padding = p->padding;
        }
      }
    } else {
      padding = limit->padding;
    }
  }
  return padding;
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
