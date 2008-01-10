#ifndef HEAP_H
#define HEAP_H

#include "system.h"
#include "zone.h"

namespace vm {

class Heap {
 public:
  enum CollectionType {
    MinorCollection,
    MajorCollection
  };

  enum Status {
    Null,
    Reachable,
    Unreachable,
    Tenured
  };

  class Visitor {
   public:
    virtual ~Visitor() { }
    virtual void visit(void*) = 0;
  };

  class Walker {
   public:
    virtual ~Walker() { }
    virtual bool visit(unsigned) = 0;
  };

  class Client {
   public:
    virtual ~Client() { }
    virtual void visitRoots(Visitor*) = 0;
    virtual bool isFixed(void*) = 0;
    virtual unsigned sizeInWords(void*) = 0;
    virtual unsigned copiedSizeInWords(void*) = 0;
    virtual void copy(void*, void*) = 0;
    virtual void walk(void*, Walker*) = 0;
    virtual void dispose() = 0;
  };

  virtual ~Heap() { }
  virtual void collect(CollectionType type, unsigned footprint) = 0;
  virtual void* allocateFixed(Allocator* allocator, unsigned sizeInWords,
                              bool objectMask, unsigned* totalInBytes) = 0;
  virtual void* allocateImmortal(Allocator* allocator, unsigned sizeInWords,
                                 bool objectMask, unsigned* totalInBytes) = 0;
  virtual bool needsMark(void* p) = 0;
  virtual void mark(void* p, unsigned offset, unsigned count) = 0;
  virtual void pad(void* p, unsigned extra) = 0;
  virtual void* follow(void* p) = 0;
  virtual Status status(void* p) = 0;
  virtual CollectionType collectionType() = 0;
  virtual void dispose() = 0;
};

Heap* makeHeap(System* system, Heap::Client* client);

} // namespace vm

#endif//HEAP_H
