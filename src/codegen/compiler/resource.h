/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_RESOURCE_H
#define AVIAN_CODEGEN_COMPILER_RESOURCE_H

#include "codegen/compiler/context.h"

namespace avian {
namespace codegen {
namespace compiler {

class Value;
class Site;

class Resource {
 public:
  Resource(bool reserved = false):
    value(0), site(0), previousAcquired(0), nextAcquired(0), freezeCount(0),
    referenceCount(0), reserved(reserved)
  { }

  virtual void freeze(Context*, Value*) = 0;

  virtual void thaw(Context*, Value*) = 0;

  virtual unsigned toString(Context*, char*, unsigned) = 0;

  Value* value;
  Site* site;
  Resource* previousAcquired;
  Resource* nextAcquired;
  uint8_t freezeCount;
  uint8_t referenceCount;
  bool reserved;
};

class RegisterResource: public Resource {
 public:
  RegisterResource(bool reserved):
    Resource(reserved)
  { }

  virtual void freeze(Context*, Value*);

  virtual void thaw(Context*, Value*);

  virtual unsigned toString(Context* c, char* buffer, unsigned bufferSize);

  virtual unsigned index(Context*);

  void increment(Context*);

  void decrement(Context*);
};

class FrameResource: public Resource {
 public:
  virtual void freeze(Context*, Value*);

  virtual void thaw(Context*, Value*);

  virtual unsigned toString(Context* c, char* buffer, unsigned bufferSize);

  virtual unsigned index(Context*);
};

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_RESOURCE_H
