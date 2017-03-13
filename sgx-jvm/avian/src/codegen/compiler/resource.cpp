/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "codegen/compiler/context.h"
#include "codegen/compiler/resource.h"
#include "codegen/compiler/value.h"

namespace avian {
namespace codegen {
namespace compiler {

const bool DebugResources = false;

void steal(Context* c, Resource* r, Value* thief);

void decrementAvailableGeneralRegisterCount(Context* c)
{
  assertT(c, c->availableGeneralRegisterCount);
  --c->availableGeneralRegisterCount;

  if (DebugResources) {
    fprintf(
        stderr, "%d registers available\n", c->availableGeneralRegisterCount);
  }
}

void incrementAvailableGeneralRegisterCount(Context* c)
{
  ++c->availableGeneralRegisterCount;

  if (DebugResources) {
    fprintf(
        stderr, "%d registers available\n", c->availableGeneralRegisterCount);
  }
}

void freezeResource(Context* c, Resource* r, Value* v)
{
  if (DebugResources) {
    char buffer[256];
    r->toString(c, buffer, 256);
    fprintf(stderr, "%p freeze %s to %d\n", v, buffer, r->freezeCount + 1);
  }

  ++r->freezeCount;
}

void thawResource(Context* c, Resource* r, Value* v)
{
  if (not r->reserved) {
    if (DebugResources) {
      char buffer[256];
      r->toString(c, buffer, 256);
      fprintf(stderr, "%p thaw %s to %d\n", v, buffer, r->freezeCount - 1);
    }

    assertT(c, r->freezeCount);

    --r->freezeCount;
  }
}

Resource::Resource(bool reserved)
    : value(0),
      site(0),
      previousAcquired(0),
      nextAcquired(0),
      freezeCount(0),
      referenceCount(0),
      reserved(reserved)
{
}

RegisterResource::RegisterResource(bool reserved) : Resource(reserved)
{
}

void RegisterResource::freeze(Context* c, Value* v)
{
  if (not reserved) {
    freezeResource(c, this, v);

    if (freezeCount == 1
        and c->regFile->generalRegisters.contains(index(c))) {
      decrementAvailableGeneralRegisterCount(c);
    }
  }
}

void RegisterResource::thaw(Context* c, Value* v)
{
  if (not reserved) {
    thawResource(c, this, v);

    if (freezeCount == 0
        and c->regFile->generalRegisters.contains(index(c))) {
      incrementAvailableGeneralRegisterCount(c);
    }
  }
}

unsigned RegisterResource::toString(Context* c,
                                    char* buffer,
                                    unsigned bufferSize)
{
  return vm::snprintf(buffer, bufferSize, "register %d", index(c));
}

Register RegisterResource::index(Context* c)
{
  return Register(this - c->registerResources);
}

void RegisterResource::increment(Context* c)
{
  if (not this->reserved) {
    if (DebugResources) {
      char buffer[256];
      this->toString(c, buffer, 256);
      fprintf(stderr, "increment %s to %d\n", buffer, this->referenceCount + 1);
    }

    ++this->referenceCount;

    if (this->referenceCount == 1
        and c->regFile->generalRegisters.contains(this->index(c))) {
      decrementAvailableGeneralRegisterCount(c);
    }
  }
}

void RegisterResource::decrement(Context* c)
{
  if (not this->reserved) {
    if (DebugResources) {
      char buffer[256];
      this->toString(c, buffer, 256);
      fprintf(stderr, "decrement %s to %d\n", buffer, this->referenceCount - 1);
    }

    assertT(c, this->referenceCount > 0);

    --this->referenceCount;

    if (this->referenceCount == 0
        and c->regFile->generalRegisters.contains(this->index(c))) {
      incrementAvailableGeneralRegisterCount(c);
    }
  }
}

void FrameResource::freeze(Context* c, Value* v)
{
  freezeResource(c, this, v);
}

void FrameResource::thaw(Context* c, Value* v)
{
  thawResource(c, this, v);
}

unsigned FrameResource::toString(Context* c, char* buffer, unsigned bufferSize)
{
  return vm::snprintf(buffer, bufferSize, "frame %d", index(c));
}

unsigned FrameResource::index(Context* c)
{
  return this - c->frameResources;
}

void acquire(Context* c, Resource* resource, Value* value, Site* site)
{
  assertT(c, value);
  assertT(c, site);

  if (not resource->reserved) {
    if (DebugResources) {
      char buffer[256];
      resource->toString(c, buffer, 256);
      fprintf(stderr, "%p acquire %s\n", value, buffer);
    }

    if (resource->value) {
      assertT(c, resource->value->findSite(resource->site));
      assertT(c, not value->findSite(resource->site));

      steal(c, resource, value);
    }

    if (c->acquiredResources) {
      c->acquiredResources->previousAcquired = resource;
      resource->nextAcquired = c->acquiredResources;
    }
    c->acquiredResources = resource;

    resource->value = value;
    resource->site = site;
  }
}

void release(Context* c,
             Resource* resource,
             Value* value UNUSED,
             Site* site UNUSED)
{
  if (not resource->reserved) {
    if (DebugResources) {
      char buffer[256];
      resource->toString(c, buffer, 256);
      fprintf(stderr, "%p release %s\n", resource->value, buffer);
    }

    assertT(c, resource->value);
    assertT(c, resource->site);

    assertT(c, resource->value->isBuddyOf(value));
    assertT(c, site == resource->site);

    Resource* next = resource->nextAcquired;
    if (next) {
      next->previousAcquired = resource->previousAcquired;
      resource->nextAcquired = 0;
    }

    Resource* previous = resource->previousAcquired;
    if (previous) {
      previous->nextAcquired = next;
      resource->previousAcquired = 0;
    } else {
      assertT(c, c->acquiredResources == resource);
      c->acquiredResources = next;
    }

    resource->value = 0;
    resource->site = 0;
  }
}

}  // namespace compiler
}  // namespace codegen
}  // namespace avian
