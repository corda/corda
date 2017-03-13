/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_FRAME_H
#define AVIAN_CODEGEN_COMPILER_FRAME_H

namespace avian {
namespace codegen {
namespace compiler {

unsigned totalFrameSize(Context* c);

int frameIndex(Context* c, int localIndex);

unsigned frameIndexToOffset(Context* c, unsigned frameIndex);

unsigned offsetToFrameIndex(Context* c, unsigned offset);

unsigned frameBase(Context* c);

class FrameIterator {
 public:
  class Element {
   public:
    Element(Value* value, unsigned localIndex);

    int frameIndex(Context* c);

    Value* const value;
    const unsigned localIndex;
  };

  FrameIterator(Context* c,
                Stack* stack,
                Local* locals,
                bool includeEmpty = false);

  bool hasMore();

  Element next(Context* c);

  Stack* stack;
  Local* locals;
  int localIndex;
  bool includeEmpty;
};

class Local {
 public:
  Value* value;
};

class Stack {
 public:
  Stack(unsigned index, Value* value, Stack* next)
      : index(index), value(value), next(next)
  {
  }

  unsigned index;
  Value* value;
  Stack* next;
};

Stack* stack(Context* c, Value* value, Stack* next);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_FRAME_H
