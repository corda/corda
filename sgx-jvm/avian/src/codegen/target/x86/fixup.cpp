/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <string.h>

#include "avian/util/allocator.h"
#include "avian/alloc-vector.h"
#include "avian/common.h"
#include "avian/zone.h"

#include <avian/util/abort.h>
#include <avian/system/system.h>

#include "context.h"
#include "fixup.h"
#include "padding.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace x86 {

using namespace util;

ResolvedPromise* resolvedPromise(Context* c, int64_t value)
{
  return new (c->zone) ResolvedPromise(value);
}

OffsetPromise::OffsetPromise(Context* c,
                             MyBlock* block,
                             unsigned offset,
                             AlignmentPadding* limit)
    : c(c), block(block), offset(offset), limit(limit), value_(-1)
{
}

bool OffsetPromise::resolved()
{
  return block->start != static_cast<unsigned>(~0);
}

int64_t OffsetPromise::value()
{
  assertT(c, resolved());

  if (value_ == -1) {
    value_ = block->start + (offset - block->offset)
             + padding(block->firstPadding, block->start, block->offset, limit);
  }

  return value_;
}
Promise* offsetPromise(Context* c)
{
  return new (c->zone) OffsetPromise(
      c, c->lastBlock, c->code.length(), c->lastBlock->lastPadding);
}

void* resolveOffset(vm::System* s,
                    uint8_t* instruction,
                    unsigned instructionSize,
                    int64_t value)
{
  intptr_t v = reinterpret_cast<uint8_t*>(value) - instruction
               - instructionSize;

  expect(s, vm::fitsInInt32(v));

  int32_t v4 = v;
  memcpy(instruction + instructionSize - 4, &v4, 4);
  return instruction + instructionSize;
}

OffsetListener::OffsetListener(vm::System* s,
                               uint8_t* instruction,
                               unsigned instructionSize)
    : s(s), instruction(instruction), instructionSize(instructionSize)
{
}

bool OffsetListener::resolve(int64_t value, void** location)
{
  void* p = resolveOffset(s, instruction, instructionSize, value);
  if (location)
    *location = p;
  return false;
}

OffsetTask::OffsetTask(Task* next,
                       Promise* promise,
                       Promise* instructionOffset,
                       unsigned instructionSize)
    : Task(next),
      promise(promise),
      instructionOffset(instructionOffset),
      instructionSize(instructionSize)
{
}

void OffsetTask::run(Context* c)
{
  if (promise->resolved()) {
    resolveOffset(c->s,
                  c->result + instructionOffset->value(),
                  instructionSize,
                  promise->value());
  } else {
    new (promise->listen(sizeof(OffsetListener))) OffsetListener(
        c->s, c->result + instructionOffset->value(), instructionSize);
  }
}

void appendOffsetTask(Context* c,
                      Promise* promise,
                      Promise* instructionOffset,
                      unsigned instructionSize)
{
  OffsetTask* task = new (c->zone)
      OffsetTask(c->tasks, promise, instructionOffset, instructionSize);

  c->tasks = task;
}

ImmediateListener::ImmediateListener(vm::System* s,
                                     void* dst,
                                     unsigned size,
                                     unsigned offset)
    : s(s), dst(dst), size(size), offset(offset)
{
}

void copy(vm::System* s, void* dst, int64_t src, unsigned size)
{
  switch (size) {
  case 4: {
    int32_t v = src;
    memcpy(dst, &v, 4);
  } break;

  case 8: {
    int64_t v = src;
    memcpy(dst, &v, 8);
  } break;

  default:
    abort(s);
  }
}

bool ImmediateListener::resolve(int64_t value, void** location)
{
  copy(s, dst, value, size);
  if (location)
    *location = static_cast<uint8_t*>(dst) + offset;
  return offset == 0;
}

ImmediateTask::ImmediateTask(Task* next,
                             Promise* promise,
                             Promise* offset,
                             unsigned size,
                             unsigned promiseOffset)
    : Task(next),
      promise(promise),
      offset(offset),
      size(size),
      promiseOffset(promiseOffset)
{
}

void ImmediateTask::run(Context* c)
{
  if (promise->resolved()) {
    copy(c->s, c->result + offset->value(), promise->value(), size);
  } else {
    new (promise->listen(sizeof(ImmediateListener))) ImmediateListener(
        c->s, c->result + offset->value(), size, promiseOffset);
  }
}

void appendImmediateTask(Context* c,
                         Promise* promise,
                         Promise* offset,
                         unsigned size,
                         unsigned promiseOffset)
{
  c->tasks = new (c->zone)
      ImmediateTask(c->tasks, promise, offset, size, promiseOffset);
}

ShiftMaskPromise* shiftMaskPromise(Context* c,
                                   Promise* base,
                                   unsigned shift,
                                   int64_t mask)
{
  return new (c->zone) ShiftMaskPromise(base, shift, mask);
}

}  // namespace x86
}  // namespace codegen
}  // namespace avian
