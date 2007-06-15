#ifndef HEAP_H
#define HEAP_H

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
  virtual bool isTenured(void** p) = 0;
  virtual bool markTenured(void** p);
};

#endif//HEAP_H
