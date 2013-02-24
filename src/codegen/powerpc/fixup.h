/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_ASSEMBLER_POWERPC_FIXUP_H
#define AVIAN_CODEGEN_ASSEMBLER_POWERPC_FIXUP_H

namespace avian {
namespace codegen {
namespace powerpc {


class Task {
 public:
  Task(Task* next): next(next) { }

  virtual void run(Context* c) = 0;

  Task* next;
};

class OffsetPromise: public Promise {
 public:
  OffsetPromise(Context* c, MyBlock* block, unsigned offset);

  virtual bool resolved();
  
  virtual int64_t value();

  Context* c;
  MyBlock* block;
  unsigned offset;
};

Promise* offsetPromise(Context* c);

void*
updateOffset(vm::System* s, uint8_t* instruction, bool conditional, int64_t value,
             void* jumpAddress);

class OffsetListener: public Promise::Listener {
 public:
  OffsetListener(vm::System* s, uint8_t* instruction, bool conditional,
                 void* jumpAddress);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  uint8_t* instruction;
  void* jumpAddress;
  bool conditional;
};

class OffsetTask: public Task {
 public:
  OffsetTask(Task* next, Promise* promise, Promise* instructionOffset,
             bool conditional);

  virtual void run(Context* c);

  Promise* promise;
  Promise* instructionOffset;
  void* jumpAddress;
  bool conditional;
};

class JumpOffset {
 public:
  JumpOffset(MyBlock* block, OffsetTask* task, unsigned offset);

  MyBlock* block;
  OffsetTask* task;
  JumpOffset* next;
  unsigned offset;  
};

class JumpEvent {
 public:
  JumpEvent(JumpOffset* jumpOffsetHead, JumpOffset* jumpOffsetTail,
            unsigned offset);

  JumpOffset* jumpOffsetHead;
  JumpOffset* jumpOffsetTail;
  JumpEvent* next;
  unsigned offset;
};

void appendOffsetTask(Context* c, Promise* promise, Promise* instructionOffset,
                 bool conditional);

void appendJumpEvent(Context* c, MyBlock* b, unsigned offset, JumpOffset* head,
                JumpOffset* tail);

ShiftMaskPromise* shiftMaskPromise(Context* c, Promise* base, unsigned shift, int64_t mask);

void updateImmediate(vm::System* s, void* dst, int32_t src, unsigned size, bool address);

class ImmediateListener: public Promise::Listener {
 public:
  ImmediateListener(vm::System* s, void* dst, unsigned size, unsigned offset,
                    bool address);

  virtual bool resolve(int64_t value, void** location);

  vm::System* s;
  void* dst;
  unsigned size;
  unsigned offset;
  bool address;
};

class ImmediateTask: public Task {
 public:
  ImmediateTask(Task* next, Promise* promise, Promise* offset, unsigned size,
                unsigned promiseOffset, bool address);

  virtual void run(Context* c);

  Promise* promise;
  Promise* offset;
  unsigned size;
  unsigned promiseOffset;
  bool address;
};

void
appendImmediateTask(Context* c, Promise* promise, Promise* offset,
                    unsigned size, unsigned promiseOffset, bool address);

class ConstantPoolEntry: public Promise {
 public:
  ConstantPoolEntry(Context* c, Promise* constant);

  virtual int64_t value();

  virtual bool resolved();

  Context* c;
  Promise* constant;
  ConstantPoolEntry* next;
  void* address;
};

ConstantPoolEntry* appendConstantPoolEntry(Context* c, Promise* constant);

inline int ha16(int32_t i) { 
    return ((i >> 16) + ((i & 0x8000) ? 1 : 0)) & 0xffff;
}

} // namespace powerpc
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_ASSEMBLER_POWERPC_FIXUP_H
