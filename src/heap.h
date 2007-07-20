#ifndef HEAP_H
#define HEAP_H

#include "system.h"

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
    virtual void visit(void**) = 0;
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
    virtual unsigned sizeInWords(void*) = 0;
    virtual unsigned copiedSizeInWords(void*) = 0;
    virtual void copy(void*, void*) = 0;
    virtual void walk(void*, Walker*) = 0;
  };

  virtual ~Heap() { }
  virtual void collect(CollectionType type, Client* client) = 0;
  virtual bool needsMark(void** p) = 0;
  virtual void mark(void** p) = 0;
  virtual void* follow(void* p) = 0;
  virtual Status status(void* p) = 0;
  virtual CollectionType collectionType() = 0;
  virtual void dispose() = 0;
};

Heap* makeHeap(System* system);

} // namespace vm

#endif//HEAP_H
