/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "block.h"
#include "fixup.h"
#include "encode.h"

namespace avian {
namespace codegen {
namespace powerpc {

using namespace isa;
using namespace util;

unsigned padding(MyBlock*, unsigned);

int ha16(int32_t i);

bool bounded(int right, int left, int32_t v) {
  return ((v << left) >> left) == v and ((v >> right) << right) == v;
}

OffsetPromise::OffsetPromise(Context* c, MyBlock* block, unsigned offset):
  c(c), block(block), offset(offset)
{ }

bool OffsetPromise::resolved() {
  return block->resolved;
}

int64_t OffsetPromise::value() {
  assert(c, resolved());

  unsigned o = offset - block->offset;
  return block->start + padding(block, o) + o;
}

Promise* offsetPromise(Context* c) {
  return new(c->zone) OffsetPromise(c, c->lastBlock, c->code.length());
}

void* updateOffset(vm::System* s, uint8_t* instruction, bool conditional, int64_t value,
             void* jumpAddress)
{
  int32_t v = reinterpret_cast<uint8_t*>(value) - instruction;
   
  int32_t mask;
  if (conditional) {
    if (not bounded(2, 16, v)) {
      *static_cast<uint32_t*>(jumpAddress) = isa::b(0);
      updateOffset(s, static_cast<uint8_t*>(jumpAddress), false, value, 0);

      v = static_cast<uint8_t*>(jumpAddress) - instruction;

      expect(s, bounded(2, 16, v));
    }
    mask = 0xFFFC;
  } else {
    expect(s, bounded(2, 6, v));
    mask = 0x3FFFFFC;
  }

  int32_t* p = reinterpret_cast<int32_t*>(instruction);
  *p = vm::targetV4((v & mask) | ((~mask) & vm::targetV4(*p)));

  return instruction + 4;
}

OffsetListener::OffsetListener(vm::System* s, uint8_t* instruction, bool conditional,
               void* jumpAddress):
  s(s),
  instruction(instruction),
  jumpAddress(jumpAddress),
  conditional(conditional)
{ }

bool OffsetListener::resolve(int64_t value, void** location) {
  void* p = updateOffset(s, instruction, conditional, value, jumpAddress);
  if (location) *location = p;
  return false;
}

OffsetTask::OffsetTask(Task* next, Promise* promise, Promise* instructionOffset,
           bool conditional):
  Task(next),
  promise(promise),
  instructionOffset(instructionOffset),
  jumpAddress(0),
  conditional(conditional)
{ }

void OffsetTask::run(Context* c) {
  if (promise->resolved()) {
    updateOffset
      (c->s, c->result + instructionOffset->value(), conditional,
       promise->value(), jumpAddress);
  } else {
    new (promise->listen(sizeof(OffsetListener)))
      OffsetListener(c->s, c->result + instructionOffset->value(),
                     conditional, jumpAddress);
  }
}

JumpOffset::JumpOffset(MyBlock* block, OffsetTask* task, unsigned offset):
  block(block), task(task), next(0), offset(offset)
{ }

JumpEvent::JumpEvent(JumpOffset* jumpOffsetHead, JumpOffset* jumpOffsetTail,
          unsigned offset):
  jumpOffsetHead(jumpOffsetHead), jumpOffsetTail(jumpOffsetTail), next(0),
  offset(offset)
{ }

void appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset,
                 bool conditional)
{
  OffsetTask* task = new(c->zone) OffsetTask(c->tasks, promise, instructionOffset, conditional);

  c->tasks = task;

  if (conditional) {
    JumpOffset* offset =
      new(c->zone) JumpOffset(c->lastBlock, task, c->code.length() - c->lastBlock->offset);

    if (c->lastBlock->jumpOffsetTail) {
      c->lastBlock->jumpOffsetTail->next = offset;
    } else {
      c->lastBlock->jumpOffsetHead = offset;
    }
    c->lastBlock->jumpOffsetTail = offset;
  }
}

void appendJumpEvent(Context* c, MyBlock* b, unsigned offset, JumpOffset* head,
                JumpOffset* tail)
{
  JumpEvent* e = new(c->zone) JumpEvent
    (head, tail, offset);

  if (b->jumpEventTail) {
    b->jumpEventTail->next = e;
  } else {
    b->jumpEventHead = e;
  }
  b->jumpEventTail = e;
}

ShiftMaskPromise* shiftMaskPromise(Context* c, Promise* base, unsigned shift, int64_t mask) {
  return new (c->zone) ShiftMaskPromise(base, shift, mask);
}

void
updateImmediate(vm::System* s, void* dst, int32_t src, unsigned size, bool address)
{
  switch (size) {
  case 4: {
    int32_t* p = static_cast<int32_t*>(dst);
    int r = (vm::targetV4(p[1]) >> 21) & 31;

    if (address) {
      p[0] = vm::targetV4(lis(r, ha16(src)));
      p[1] |= vm::targetV4(src & 0xFFFF);
    } else {
      p[0] = vm::targetV4(lis(r, src >> 16));
      p[1] = vm::targetV4(ori(r, r, src));
    }
  } break;

  default: abort(s);
  }
}

ImmediateListener::ImmediateListener(vm::System* s, void* dst, unsigned size, unsigned offset,
                  bool address):
  s(s), dst(dst), size(size), offset(offset), address(address)
{ }

bool ImmediateListener::resolve(int64_t value, void** location) {
  updateImmediate(s, dst, value, size, address);
  if (location) *location = static_cast<uint8_t*>(dst) + offset;
  return false;
}

ImmediateTask::ImmediateTask(Task* next, Promise* promise, Promise* offset, unsigned size,
              unsigned promiseOffset, bool address):
  Task(next),
  promise(promise),
  offset(offset),
  size(size),
  promiseOffset(promiseOffset),
  address(address)
{ }

void ImmediateTask::run(Context* c) {
  if (promise->resolved()) {
    updateImmediate
      (c->s, c->result + offset->value(), promise->value(), size, address);
  } else {
    new (promise->listen(sizeof(ImmediateListener))) ImmediateListener
      (c->s, c->result + offset->value(), size, promiseOffset, address);
  }
}

void
appendImmediateTask(Context* c, Promise* promise, Promise* offset,
                    unsigned size, unsigned promiseOffset, bool address)
{
  c->tasks = new(c->zone) ImmediateTask(c->tasks, promise, offset, size, promiseOffset, address);
}

ConstantPoolEntry::ConstantPoolEntry(Context* c, Promise* constant):
  c(c), constant(constant), next(c->constantPool), address(0)
{
  c->constantPool = this;
  ++ c->constantPoolCount;
}

int64_t ConstantPoolEntry::value() {
  assert(c, resolved());

  return reinterpret_cast<intptr_t>(address);
}

bool ConstantPoolEntry::resolved() {
  return address != 0;
}

ConstantPoolEntry* appendConstantPoolEntry(Context* c, Promise* constant) {
  return new (c->zone) ConstantPoolEntry(c, constant);
}


} // namespace powerpc
} // namespace codegen
} // namespace avian
