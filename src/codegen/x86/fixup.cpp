/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/assembler.h"
#include "codegen/x86/context.h"
#include "codegen/x86/fixup.h"
#include "codegen/x86/padding.h"
#include "codegen/x86/block.h"

namespace avian {
namespace codegen {
namespace x86 {

ResolvedPromise* resolvedPromise(Context* c, int64_t value) {
  return new(c->zone) ResolvedPromise(value);
}

Offset::Offset(Context* c, MyBlock* block, unsigned offset, AlignmentPadding* limit):
  c(c), block(block), offset(offset), limit(limit), value_(-1)
{ }

bool Offset::resolved() {
  return block->start != static_cast<unsigned>(~0);
}

int64_t Offset::value() {
  assert(c, resolved());

  if (value_ == -1) {
    value_ = block->start + (offset - block->offset)
      + padding(block->firstPadding, block->start, block->offset, limit);
  }

  return value_;
}
Promise* offsetPromise(Context* c) {
  return new(c->zone) Offset(c, c->lastBlock, c->code.length(), c->lastBlock->lastPadding);
}

void*
resolveOffset(vm::System* s, uint8_t* instruction, unsigned instructionSize,
              int64_t value)
{
  intptr_t v = reinterpret_cast<uint8_t*>(value)
    - instruction - instructionSize;
    
  expect(s, vm::fitsInInt32(v));

  int32_t v4 = v;
  memcpy(instruction + instructionSize - 4, &v4, 4);
  return instruction + instructionSize;
}

OffsetListener::OffsetListener(vm::System* s, uint8_t* instruction,
               unsigned instructionSize):
  s(s),
  instruction(instruction),
  instructionSize(instructionSize)
{ }

bool OffsetListener::resolve(int64_t value, void** location) {
  void* p = resolveOffset(s, instruction, instructionSize, value);
  if (location) *location = p;
  return false;
}

OffsetTask::OffsetTask(Task* next, Promise* promise, Promise* instructionOffset,
           unsigned instructionSize):
  Task(next),
  promise(promise),
  instructionOffset(instructionOffset),
  instructionSize(instructionSize)
{ }

void OffsetTask::run(Context* c) {
  if (promise->resolved()) {
    resolveOffset
      (c->s, c->result + instructionOffset->value(), instructionSize,
       promise->value());
  } else {
    new (promise->listen(sizeof(OffsetListener)))
      OffsetListener(c->s, c->result + instructionOffset->value(),
                     instructionSize);
  }
}

void
appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset,
                 unsigned instructionSize)
{
  OffsetTask* task =
    new(c->zone) OffsetTask(c->tasks, promise, instructionOffset, instructionSize);

  c->tasks = task;
}

} // namespace x86
} // namespace codegen
} // namespace avian
