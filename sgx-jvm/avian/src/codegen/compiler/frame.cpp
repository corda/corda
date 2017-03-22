/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "codegen/compiler/context.h"
#include "codegen/compiler/frame.h"

#include <avian/codegen/architecture.h>

namespace avian {
namespace codegen {
namespace compiler {

unsigned totalFrameSize(Context* c)
{
  return c->alignedFrameSize + c->arch->frameHeaderSize()
         + c->arch->argumentFootprint(c->parameterFootprint);
}

int frameIndex(Context* c, int localIndex)
{
  assertT(c, localIndex >= 0);

  int index = c->alignedFrameSize + c->parameterFootprint - localIndex - 1;

  if (localIndex < static_cast<int>(c->parameterFootprint)) {
    index += c->arch->frameHeaderSize();
  } else {
    index -= c->arch->frameFooterSize();
  }

  assertT(c, index >= 0);
  assertT(c, static_cast<unsigned>(index) < totalFrameSize(c));

  return index;
}

unsigned frameIndexToOffset(Context* c, unsigned frameIndex)
{
  assertT(c, frameIndex < totalFrameSize(c));

  return (frameIndex + c->arch->frameFooterSize()) * c->targetInfo.pointerSize;
}

unsigned offsetToFrameIndex(Context* c, unsigned offset)
{
  assertT(c,
          static_cast<int>((offset / c->targetInfo.pointerSize)
                           - c->arch->frameFooterSize()) >= 0);
  assertT(c,
          ((offset / c->targetInfo.pointerSize) - c->arch->frameFooterSize())
          < totalFrameSize(c));

  return (offset / c->targetInfo.pointerSize) - c->arch->frameFooterSize();
}

unsigned frameBase(Context* c)
{
  return c->alignedFrameSize - c->arch->frameReturnAddressSize()
         - c->arch->frameFooterSize() + c->arch->frameHeaderSize();
}

FrameIterator::Element::Element(Value* value, unsigned localIndex)
    : value(value), localIndex(localIndex)
{
}

int FrameIterator::Element::frameIndex(Context* c)
{
  return compiler::frameIndex(c, this->localIndex);
}

FrameIterator::FrameIterator(Context* c,
                             Stack* stack,
                             Local* locals,
                             bool includeEmpty)
    : stack(stack),
      locals(locals),
      localIndex(c->localFootprint - 1),
      includeEmpty(includeEmpty)
{
}

bool FrameIterator::hasMore()
{
  if (not includeEmpty) {
    while (stack and stack->value == 0) {
      stack = stack->next;
    }

    while (localIndex >= 0 and locals[localIndex].value == 0) {
      --localIndex;
    }
  }

  return stack != 0 or localIndex >= 0;
}

FrameIterator::Element FrameIterator::next(Context* c)
{
  Value* v;
  unsigned li;
  if (stack) {
    Stack* s = stack;
    v = s->value;
    li = s->index + c->localFootprint;
    stack = stack->next;
  } else {
    Local* l = locals + localIndex;
    v = l->value;
    li = localIndex;
    --localIndex;
  }
  return Element(v, li);
}

Stack* stack(Context* c, Value* value, Stack* next)
{
  return new (c->zone) Stack(next ? next->index + 1 : 0, value, next);
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
