/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/compiler/context.h"
#include "codegen/compiler/resource.h"

namespace avian {
namespace codegen {
namespace compiler {

const bool DebugResources = false;

void decrementAvailableGeneralRegisterCount(Context* c) {
  assert(c, c->availableGeneralRegisterCount);
  -- c->availableGeneralRegisterCount;
  
  if (DebugResources) {
    fprintf(stderr, "%d registers available\n",
            c->availableGeneralRegisterCount);
  }
}

void incrementAvailableGeneralRegisterCount(Context* c) {
  ++ c->availableGeneralRegisterCount;

  if (DebugResources) {
    fprintf(stderr, "%d registers available\n",
            c->availableGeneralRegisterCount);
  }
}

void freezeResource(Context* c, Resource* r, Value* v) {
  if (DebugResources) {
    char buffer[256]; r->toString(c, buffer, 256);
    fprintf(stderr, "%p freeze %s to %d\n", v, buffer, r->freezeCount + 1);
  }
    
  ++ r->freezeCount;
}

void thawResource(Context* c, Resource* r, Value* v) {
  if (not r->reserved) {
    if (DebugResources) {
      char buffer[256]; r->toString(c, buffer, 256);
      fprintf(stderr, "%p thaw %s to %d\n", v, buffer, r->freezeCount - 1);
    }

    assert(c, r->freezeCount);

    -- r->freezeCount;
  }
}



void RegisterResource::freeze(Context* c, Value* v) {
  if (not reserved) {
    freezeResource(c, this, v);

    if (freezeCount == 1
        and ((1 << index(c)) & c->regFile->generalRegisters.mask))
    {
      decrementAvailableGeneralRegisterCount(c);
    }
  }
}

void RegisterResource::thaw(Context* c, Value* v) {
  if (not reserved) {
    thawResource(c, this, v);

    if (freezeCount == 0
        and ((1 << index(c)) & c->regFile->generalRegisters.mask))
    {
      incrementAvailableGeneralRegisterCount(c);
    }
  }
}

unsigned RegisterResource::toString(Context* c, char* buffer, unsigned bufferSize) {
  return vm::snprintf(buffer, bufferSize, "register %d", index(c));
}

unsigned RegisterResource::index(Context* c) {
  return this - c->registerResources;
}

void RegisterResource::increment(Context* c) {
  if (not this->reserved) {
    if (DebugResources) {
      char buffer[256]; this->toString(c, buffer, 256);
      fprintf(stderr, "increment %s to %d\n", buffer, this->referenceCount + 1);
    }

    ++ this->referenceCount;

    if (this->referenceCount == 1
        and ((1 << this->index(c)) & c->regFile->generalRegisters.mask))
    {
      decrementAvailableGeneralRegisterCount(c);
    }
  }
}

void RegisterResource::decrement(Context* c) {
  if (not this->reserved) {
    if (DebugResources) {
      char buffer[256]; this->toString(c, buffer, 256);
      fprintf(stderr, "decrement %s to %d\n", buffer, this->referenceCount - 1);
    }

    assert(c, this->referenceCount > 0);

    -- this->referenceCount;

    if (this->referenceCount == 0
        and ((1 << this->index(c)) & c->regFile->generalRegisters.mask))
    {
      incrementAvailableGeneralRegisterCount(c);
    }
  }
}



void FrameResource::freeze(Context* c, Value* v) {
  freezeResource(c, this, v);
}

void FrameResource::thaw(Context* c, Value* v) {
  thawResource(c, this, v);
}

unsigned FrameResource::toString(Context* c, char* buffer, unsigned bufferSize) {
  return vm::snprintf(buffer, bufferSize, "frame %d", index(c));
}

unsigned FrameResource::index(Context* c) {
  return this - c->frameResources;
}


} // namespace compiler
} // namespace codegen
} // namespace avian