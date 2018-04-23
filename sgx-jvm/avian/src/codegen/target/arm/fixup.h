/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_ARM_PROMISE_H
#define AVIAN_CODEGEN_ASSEMBLER_ARM_PROMISE_H

#include "avian/target.h"

#include <avian/codegen/lir.h>
#include <avian/codegen/assembler.h>
#include "avian/alloc-vector.h"

namespace vm {
class System;
}

namespace avian {
namespace codegen {
namespace arm {

const bool DebugPool = false;

const int32_t PoolOffsetMask = vm::TargetBytesPerWord == 8 ? 0x1FFFFF : 0xFFF;

class Task {
 public:
  Task(Task* next) : next(next)
  {
  }

  virtual void run(Context* con) = 0;

  Task* next;
};

class OffsetPromise : public Promise {
 public:
  OffsetPromise(Context* con, MyBlock* block, unsigned offset, bool forTrace);

  virtual bool resolved();

  virtual int64_t value();

  Context* con;
  MyBlock* block;
  unsigned offset;
  bool forTrace;
};

Promise* offsetPromise(Context* con, bool forTrace = false);

class OffsetListener : public Promise::Listener {
 public:
  OffsetListener(vm::System* s, uint8_t* instruction);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  uint8_t* instruction;
};

class OffsetTask : public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset);

  virtual void run(Context* con);

  Promise* promise;
  Promise* instructionOffset;
};

void appendOffsetTask(Context* con,
                      Promise* promise,
                      Promise* instructionOffset);

void* updateOffset(vm::System* s, uint8_t* instruction, int64_t value);

class ConstantPoolEntry : public Promise {
 public:
  ConstantPoolEntry(Context* con,
                    Promise* constant,
                    ConstantPoolEntry* next,
                    Promise* callOffset);

  virtual int64_t value();

  virtual bool resolved();

  Context* con;
  Promise* constant;
  ConstantPoolEntry* next;
  Promise* callOffset;
  void* address;
  unsigned constantPoolCount;
};

class ConstantPoolListener : public Promise::Listener {
 public:
  ConstantPoolListener(vm::System* s,
                       vm::target_uintptr_t* address,
                       uint8_t* returnAddress);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  vm::target_uintptr_t* address;
  uint8_t* returnAddress;
};

class PoolOffset {
 public:
  PoolOffset(MyBlock* block, ConstantPoolEntry* entry, unsigned offset);

  MyBlock* block;
  ConstantPoolEntry* entry;
  PoolOffset* next;
  unsigned offset;
};

class PoolEvent {
 public:
  PoolEvent(PoolOffset* poolOffsetHead,
            PoolOffset* poolOffsetTail,
            unsigned offset);

  PoolOffset* poolOffsetHead;
  PoolOffset* poolOffsetTail;
  PoolEvent* next;
  unsigned offset;
};

void appendConstantPoolEntry(Context* con,
                             Promise* constant,
                             Promise* callOffset);

void appendPoolEvent(Context* con,
                     MyBlock* b,
                     unsigned offset,
                     PoolOffset* head,
                     PoolOffset* tail);

}  // namespace arm
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_ARM_PROMISE_H
