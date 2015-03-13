/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_PROMISE_H
#define AVIAN_CODEGEN_COMPILER_PROMISE_H

namespace avian {
namespace codegen {
namespace compiler {

class CodePromise : public Promise {
 public:
  CodePromise(Context* c, CodePromise* next);

  CodePromise(Context* c, Promise* offset);

  virtual int64_t value();

  virtual bool resolved();

  Context* c;
  Promise* offset;
  CodePromise* next;
};

CodePromise* codePromise(Context* c, Promise* offset);

Promise* shiftMaskPromise(Context* c,
                          Promise* base,
                          unsigned shift,
                          int64_t mask);

Promise* combinedPromise(Context* c, Promise* low, Promise* high);

Promise* resolvedPromise(Context* c, int64_t value);

Promise* ipPromise(Context* c, int logicalIp);

Promise* poolPromise(Context* c, int key);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_PROMISE_H
