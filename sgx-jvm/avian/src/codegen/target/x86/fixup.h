/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H

#include <stdint.h>

#include <avian/codegen/promise.h>

namespace vm {
class System;
}

namespace avian {
namespace codegen {
namespace x86 {

class Context;
class MyBlock;
class AlignmentPadding;

ResolvedPromise* resolvedPromise(Context* c, int64_t value);

class Task {
 public:
  Task(Task* next) : next(next)
  {
  }

  virtual void run(Context* c) = 0;

  Task* next;
};

class OffsetPromise : public Promise {
 public:
  OffsetPromise(Context* c,
                MyBlock* block,
                unsigned offset,
                AlignmentPadding* limit);

  virtual bool resolved();

  virtual int64_t value();

  Context* c;
  MyBlock* block;
  unsigned offset;
  AlignmentPadding* limit;
  int value_;
};

Promise* offsetPromise(Context* c);

void* resolveOffset(vm::System* s,
                    uint8_t* instruction,
                    unsigned instructionSize,
                    int64_t value);

class OffsetListener : public Promise::Listener {
 public:
  OffsetListener(vm::System* s, uint8_t* instruction, unsigned instructionSize);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  uint8_t* instruction;
  unsigned instructionSize;
};

class OffsetTask : public Task {
 public:
  OffsetTask(Task* next,
             Promise* promise,
             Promise* instructionOffset,
             unsigned instructionSize);

  virtual void run(Context* c);

  Promise* promise;
  Promise* instructionOffset;
  unsigned instructionSize;
};

void appendOffsetTask(Context* c,
                      Promise* promise,
                      Promise* instructionOffset,
                      unsigned instructionSize);

class ImmediateListener : public Promise::Listener {
 public:
  ImmediateListener(vm::System* s, void* dst, unsigned size, unsigned offset);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  void* dst;
  unsigned size;
  unsigned offset;
};

class ImmediateTask : public Task {
 public:
  ImmediateTask(Task* next,
                Promise* promise,
                Promise* offset,
                unsigned size,
                unsigned promiseOffset);

  virtual void run(Context* c);

  Promise* promise;
  Promise* offset;
  unsigned size;
  unsigned promiseOffset;
};

void appendImmediateTask(Context* c,
                         Promise* promise,
                         Promise* offset,
                         unsigned size,
                         unsigned promiseOffset = 0);

ShiftMaskPromise* shiftMaskPromise(Context* c,
                                   Promise* base,
                                   unsigned shift,
                                   int64_t mask);

}  // namespace x86
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H
