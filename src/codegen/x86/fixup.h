/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H
#define AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H

namespace vm {
class System;
}

namespace avian {
namespace codegen {

class Promise;

namespace x86 {

class MyBlock;
class AlignmentPadding;

ResolvedPromise* resolvedPromise(Context* c, int64_t value);

class Offset: public Promise {
 public:
  Offset(Context* c, MyBlock* block, unsigned offset, AlignmentPadding* limit);

  virtual bool resolved();
  
  virtual int64_t value();

  Context* c;
  MyBlock* block;
  unsigned offset;
  AlignmentPadding* limit;
  int value_;
};

Promise* offsetPromise(Context* c);


class Task {
 public:
  Task(Task* next): next(next) { }

  virtual void run(Context* c) = 0;

  Task* next;
};

void* resolveOffset(vm::System* s, uint8_t* instruction, unsigned instructionSize, int64_t value);

class OffsetListener: public Promise::Listener {
 public:
  OffsetListener(vm::System* s, uint8_t* instruction, unsigned instructionSize);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  uint8_t* instruction;
  unsigned instructionSize;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset, unsigned instructionSize);

  virtual void run(Context* c);

  Promise* promise;
  Promise* instructionOffset;
  unsigned instructionSize;
};

void appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset, unsigned instructionSize);

} // namespace x86
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_X86_FIXUP_H
