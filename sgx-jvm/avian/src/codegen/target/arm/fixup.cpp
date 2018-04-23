/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "context.h"
#include "fixup.h"
#include "block.h"

namespace {

const unsigned InstructionSize = 4;

}  // namespace

namespace avian {
namespace codegen {
namespace arm {

using namespace util;

unsigned padding(MyBlock*, unsigned);

OffsetPromise::OffsetPromise(Context* con,
                             MyBlock* block,
                             unsigned offset,
                             bool forTrace)
    : con(con), block(block), offset(offset), forTrace(forTrace)
{
}

bool OffsetPromise::resolved()
{
  return block->start != static_cast<unsigned>(~0);
}

int64_t OffsetPromise::value()
{
  assertT(con, resolved());

  unsigned o = offset - block->offset;
  return block->start + padding(block, forTrace ? o - InstructionSize : o) + o;
}

Promise* offsetPromise(Context* con, bool forTrace)
{
  return new (con->zone)
      OffsetPromise(con, con->lastBlock, con->code.length(), forTrace);
}

OffsetListener::OffsetListener(vm::System* s, uint8_t* instruction)
    : s(s), instruction(instruction)
{
}

bool OffsetListener::resolve(int64_t value, void** location)
{
  void* p = updateOffset(s, instruction, value);
  if (location)
    *location = p;
  return false;
}

OffsetTask::OffsetTask(Task* next, Promise* promise, Promise* instructionOffset)
    : Task(next), promise(promise), instructionOffset(instructionOffset)
{
}

void OffsetTask::run(Context* con)
{
  if (promise->resolved()) {
    updateOffset(
        con->s, con->result + instructionOffset->value(), promise->value());
  } else {
    new (promise->listen(sizeof(OffsetListener)))
        OffsetListener(con->s, con->result + instructionOffset->value());
  }
}

void appendOffsetTask(Context* con,
                      Promise* promise,
                      Promise* instructionOffset)
{
  con->tasks = new (con->zone)
      OffsetTask(con->tasks, promise, instructionOffset);
}

bool bounded(int right, int left, int32_t v)
{
  return ((v << left) >> left) == v and ((v >> right) << right) == v;
}

void* updateOffset(vm::System* s, uint8_t* instruction, int64_t value)
{
  int32_t* p = reinterpret_cast<int32_t*>(instruction);

  int32_t v;
  int32_t mask;
  if (vm::TargetBytesPerWord == 8) {
    if ((*p >> 24) == 0x54) {
      // conditional branch
      v = ((reinterpret_cast<uint8_t*>(value) - instruction) >> 2) << 5;
      mask = 0xFFFFE0;
      expect(s, bounded(5, 8, v));
    } else {
      // unconditional branch
      v = (reinterpret_cast<uint8_t*>(value) - instruction) >> 2;
      mask = 0x3FFFFFF;
      expect(s, bounded(0, 6, v));
    }
  } else {
    v = (reinterpret_cast<uint8_t*>(value) - (instruction + 8)) >> 2;
    mask = 0xFFFFFF;
    expect(s, bounded(0, 8, v));
  }

  *p = (v & mask) | ((~mask) & *p);

  return instruction + InstructionSize;
}

ConstantPoolEntry::ConstantPoolEntry(Context* con,
                                     Promise* constant,
                                     ConstantPoolEntry* next,
                                     Promise* callOffset)
    : con(con),
      constant(constant),
      next(next),
      callOffset(callOffset),
      address(0)
{
}

int64_t ConstantPoolEntry::value()
{
  assertT(con, resolved());

  return reinterpret_cast<int64_t>(address);
}

bool ConstantPoolEntry::resolved()
{
  return address != 0;
}

ConstantPoolListener::ConstantPoolListener(vm::System* s,
                                           vm::target_uintptr_t* address,
                                           uint8_t* returnAddress)
    : s(s), address(address), returnAddress(returnAddress)
{
}

bool ConstantPoolListener::resolve(int64_t value, void** location)
{
  *address = value;
  if (location) {
    *location = returnAddress ? static_cast<void*>(returnAddress) : address;
  }
  return true;
}

PoolOffset::PoolOffset(MyBlock* block,
                       ConstantPoolEntry* entry,
                       unsigned offset)
    : block(block), entry(entry), next(0), offset(offset)
{
}

PoolEvent::PoolEvent(PoolOffset* poolOffsetHead,
                     PoolOffset* poolOffsetTail,
                     unsigned offset)
    : poolOffsetHead(poolOffsetHead),
      poolOffsetTail(poolOffsetTail),
      next(0),
      offset(offset)
{
}

void appendConstantPoolEntry(Context* con,
                             Promise* constant,
                             Promise* callOffset)
{
  if (constant->resolved()) {
    // make a copy, since the original might be allocated on the
    // stack, and we need our copy to live until assembly is complete
    constant = new (con->zone) ResolvedPromise(constant->value());
  }

  con->constantPool = new (con->zone)
      ConstantPoolEntry(con, constant, con->constantPool, callOffset);

  ++con->constantPoolCount;

  PoolOffset* o = new (con->zone)
      PoolOffset(con->lastBlock,
                 con->constantPool,
                 con->code.length() - con->lastBlock->offset);

  if (DebugPool) {
    fprintf(stderr,
            "add pool offset %p %d to block %p\n",
            o,
            o->offset,
            con->lastBlock);
  }

  if (con->lastBlock->poolOffsetTail) {
    con->lastBlock->poolOffsetTail->next = o;
  } else {
    con->lastBlock->poolOffsetHead = o;
  }
  con->lastBlock->poolOffsetTail = o;
}

void appendPoolEvent(Context* con,
                     MyBlock* b,
                     unsigned offset,
                     PoolOffset* head,
                     PoolOffset* tail)
{
  PoolEvent* e = new (con->zone) PoolEvent(head, tail, offset);

  if (b->poolEventTail) {
    b->poolEventTail->next = e;
  } else {
    b->poolEventHead = e;
  }
  b->poolEventTail = e;
}

bool needJump(MyBlock* b)
{
  return b->next or b->size != (b->size & PoolOffsetMask);
}

unsigned padding(MyBlock* b, unsigned offset)
{
  unsigned total = 0;
  for (PoolEvent* e = b->poolEventHead; e; e = e->next) {
    if (e->offset <= offset) {
      if (needJump(b)) {
        total += vm::TargetBytesPerWord;
      }
      for (PoolOffset* o = e->poolOffsetHead; o; o = o->next) {
        total += vm::TargetBytesPerWord;
      }
    } else {
      break;
    }
  }
  return total;
}

void resolve(MyBlock* b)
{
  Context* con = b->context;

  if (b->poolOffsetHead) {
    if (con->poolOffsetTail) {
      con->poolOffsetTail->next = b->poolOffsetHead;
    } else {
      con->poolOffsetHead = b->poolOffsetHead;
    }
    con->poolOffsetTail = b->poolOffsetTail;
  }

  if (con->poolOffsetHead) {
    bool append;
    if (b->next == 0 or b->next->poolEventHead) {
      append = true;
    } else {
      int32_t v
          = (b->start + b->size + b->next->size + vm::TargetBytesPerWord - 8)
            - (con->poolOffsetHead->offset + con->poolOffsetHead->block->start);

      append = (v != (v & PoolOffsetMask));

      if (DebugPool) {
        fprintf(stderr,
                "current %p %d %d next %p %d %d\n",
                b,
                b->start,
                b->size,
                b->next,
                b->start + b->size,
                b->next->size);
        fprintf(stderr,
                "offset %p %d is of distance %d to next block; append? %d\n",
                con->poolOffsetHead,
                con->poolOffsetHead->offset,
                v,
                append);
      }
    }

    if (append) {
#ifndef NDEBUG
      int32_t v
          = (b->start + b->size - 8)
            - (con->poolOffsetHead->offset + con->poolOffsetHead->block->start);

      expect(con, v == (v & PoolOffsetMask));
#endif  // not NDEBUG

      appendPoolEvent(
          con, b, b->size, con->poolOffsetHead, con->poolOffsetTail);

      if (DebugPool) {
        for (PoolOffset* o = con->poolOffsetHead; o; o = o->next) {
          fprintf(stderr,
                  "include %p %d in pool event %p at offset %d in block %p\n",
                  o,
                  o->offset,
                  b->poolEventTail,
                  b->size,
                  b);
        }
      }

      con->poolOffsetHead = 0;
      con->poolOffsetTail = 0;
    }
  }
}

}  // namespace arm
}  // namespace codegen
}  // namespace avian
