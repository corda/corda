/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef HEAP_H
#define HEAP_H

#include <avian/system/system.h>
#include <avian/util/allocator.h>

namespace vm {

// an object must survive TenureThreshold + 2 garbage collections
// before being copied to gen2 (must be at least 1):
const unsigned TenureThreshold = 3;

const unsigned FixieTenureThreshold = TenureThreshold + 2;

class Heap : public avian::util::Allocator {
 public:
  enum CollectionType { MinorCollection, MajorCollection };

  enum Status { Null, Reachable, Unreachable, Tenured };

  class Visitor {
   public:
    virtual void visit(void*) = 0;
  };

  class Walker {
   public:
    virtual bool visit(unsigned) = 0;
  };

  class Client {
   public:
    virtual void collect(void* context, CollectionType type) = 0;
    virtual void visitRoots(Visitor*) = 0;
    virtual bool isFixed(void*) = 0;
    virtual unsigned sizeInWords(void*) = 0;
    virtual unsigned copiedSizeInWords(void*) = 0;
    virtual void copy(void*, void*) = 0;
    virtual void walk(void*, Walker*) = 0;
  };

  virtual void setClient(Client* client) = 0;
  virtual void setImmortalHeap(uintptr_t* start, unsigned sizeInWords) = 0;
  virtual unsigned remaining() = 0;
  virtual unsigned limit() = 0;
  virtual bool limitExceeded(int pendingAllocation = 0) = 0;
  virtual void collect(CollectionType type,
                       unsigned footprint,
                       int pendingAllocation) = 0;
  virtual unsigned fixedFootprint(unsigned sizeInWords, bool objectMask) = 0;
  virtual void* allocateFixed(avian::util::Alloc* allocator,
                              unsigned sizeInWords,
                              bool objectMask) = 0;
  virtual void* allocateImmortalFixed(avian::util::Alloc* allocator,
                                      unsigned sizeInWords,
                                      bool objectMask) = 0;
  virtual void mark(void* p, unsigned offset, unsigned count) = 0;
  virtual void pad(void* p) = 0;
  virtual void* follow(void* p) = 0;

  template <class T>
  T* follow(T* p)
  {
    return static_cast<T*>(follow(static_cast<void*>(p)));
  }

  virtual void postVisit() = 0;
  virtual Status status(void* p) = 0;
  virtual CollectionType collectionType() = 0;
  virtual void disposeFixies() = 0;
  virtual void dispose() = 0;
};

Heap* makeHeap(System* system, unsigned limit);

}  // namespace vm

#endif  // HEAP_H
