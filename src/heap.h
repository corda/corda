#ifndef HEAP_H
#define HEAP_H

namespace vm {

class Heap {
 public:
  enum CollectionType {
    MinorCollection,
    MajorCollection
  };

  class Visitor {
   public:
    virtual ~Visitor() { }
    virtual void visit(void**) = 0;
  };

  class Iterator {
   public:
    virtual ~Iterator() { }
    virtual void iterate(Visitor*) = 0;
  };

  virtual ~Heap() { }
  virtual void collect(CollectionType type, Iterator* it) = 0;
  virtual bool needsMark(void** p) = 0;
  virtual void mark(void** p) = 0;
  virtual void dispose() = 0;
};

} // namespace vm

#endif//HEAP_H
