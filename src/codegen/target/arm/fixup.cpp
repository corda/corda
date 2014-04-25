/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "fixup.h"
#include "block.h"

namespace avian {
namespace codegen {
namespace arm {

using namespace util;

unsigned padding(MyBlock*, unsigned);

OffsetPromise::OffsetPromise(Context* con, MyBlock* block, unsigned offset, bool forTrace):
  con(con), block(block), offset(offset), forTrace(forTrace)
{ }

bool OffsetPromise::resolved() {
  return block->start != static_cast<unsigned>(~0);
}

int64_t OffsetPromise::value() {
  assert(con, resolved());

  unsigned o = offset - block->offset;
  return block->start + padding
    (block, forTrace ? o - vm::TargetBytesPerWord : o) + o;
}


Promise* offsetPromise(Context* con, bool forTrace) {
  return new(con->zone) OffsetPromise(con, con->lastBlock, con->code.length(), forTrace);
}


OffsetListener::OffsetListener(vm::System* s, uint8_t* instruction):
  s(s),
  instruction(instruction)
{ }

bool OffsetListener::resolve(int64_t value, void** location) {
  void* p = updateOffset(s, instruction, value);
  if (location) *location = p;
  return false;
}


OffsetTask::OffsetTask(Task* next, Promise* promise, Promise* instructionOffset):
  Task(next),
  promise(promise),
  instructionOffset(instructionOffset)
{ }

void OffsetTask::run(Context* con) {
  if (promise->resolved()) {
    updateOffset
      (con->s, con->result + instructionOffset->value(), promise->value());
  } else {
    new (promise->listen(sizeof(OffsetListener)))
      OffsetListener(con->s, con->result + instructionOffset->value());
  }
}

void appendOffsetTask(Context* con, Promise* promise, Promise* instructionOffset) {
  con->tasks = new(con->zone) OffsetTask(con->tasks, promise, instructionOffset);
}

bool bounded(int right, int left, int32_t v) {
  return ((v << left) >> left) == v and ((v >> right) << right) == v;
}

void* updateOffset(vm::System* s, uint8_t* instruction, int64_t value) {
  // ARM's PC is two words ahead, and branches drop the bottom 2 bits.
  int32_t v = (reinterpret_cast<uint8_t*>(value) - (instruction + 8)) >> 2;

  int32_t mask;
  expect(s, bounded(0, 8, v));
  mask = 0xFFFFFF;

  int32_t* p = reinterpret_cast<int32_t*>(instruction);
  *p = (v & mask) | ((~mask) & *p);

  return instruction + 4;
}

ConstantPoolEntry::ConstantPoolEntry(Context* con, Promise* constant, ConstantPoolEntry* next,
                  Promise* callOffset):
  con(con), constant(constant), next(next), callOffset(callOffset),
  address(0)
{ }

int64_t ConstantPoolEntry::value() {
  assert(con, resolved());

  return reinterpret_cast<int64_t>(address);
}

bool ConstantPoolEntry::resolved() {
  return address != 0;
}

ConstantPoolListener::ConstantPoolListener(vm::System* s, vm::target_uintptr_t* address,
                     uint8_t* returnAddress):
  s(s),
  address(address),
  returnAddress(returnAddress)
{ }

bool ConstantPoolListener::resolve(int64_t value, void** location) {
  *address = value;
  if (location) {
    *location = returnAddress ? static_cast<void*>(returnAddress) : address;
  }
  return true;
}

PoolOffset::PoolOffset(MyBlock* block, ConstantPoolEntry* entry, unsigned offset):
  block(block), entry(entry), next(0), offset(offset)
{ }

PoolEvent::PoolEvent(PoolOffset* poolOffsetHead, PoolOffset* poolOffsetTail,
          unsigned offset):
  poolOffsetHead(poolOffsetHead), poolOffsetTail(poolOffsetTail), next(0),
  offset(offset)
{ }

void appendConstantPoolEntry(Context* con, Promise* constant, Promise* callOffset) {
  if (constant->resolved()) {
    // make a copy, since the original might be allocated on the
    // stack, and we need our copy to live until assembly is complete
    constant = new(con->zone) ResolvedPromise(constant->value());
  }

  con->constantPool = new(con->zone) ConstantPoolEntry(con, constant, con->constantPool, callOffset);

  ++ con->constantPoolCount;

  PoolOffset* o = new(con->zone) PoolOffset(con->lastBlock, con->constantPool, con->code.length() - con->lastBlock->offset);

  if (DebugPool) {
    fprintf(stderr, "add pool offset %p %d to block %p\n",
            o, o->offset, con->lastBlock);
  }

  if (con->lastBlock->poolOffsetTail) {
    con->lastBlock->poolOffsetTail->next = o;
  } else {
    con->lastBlock->poolOffsetHead = o;
  }
  con->lastBlock->poolOffsetTail = o;
}

void appendPoolEvent(Context* con, MyBlock* b, unsigned offset, PoolOffset* head,
                PoolOffset* tail)
{
  PoolEvent* e = new(con->zone) PoolEvent(head, tail, offset);

  if (b->poolEventTail) {
    b->poolEventTail->next = e;
  } else {
    b->poolEventHead = e;
  }
  b->poolEventTail = e;
}

} // namespace arm
} // namespace codegen
} // namespace avian
