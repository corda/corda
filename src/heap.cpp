#include "heap.h"
#include "system.h"

using namespace vm;

namespace {

class Context {
 public:
};

void
collect(Context* c, void** p)
{
  object original = *p;
  object parent = 0;

  bool needsVisit;
  *p = update(c, p, &needsVisit);

  if (not needsVisit) return;

 visit: {
    object copy = follow(original);

    class Walker : public Heap::Walker {
     public:
      Walker(Context* c, object copy, uintptr_t* bitset):
        c(c),
        copy(copy),
        bitset(bitset),
        first(0),
        last(0),
        visits(0),
        total(0)
      { }

      virtual bool visit(unsigned offset) {
        bool needsVisit;
        object childCopy = update
          (c, &cast<object>(copy, offset * sizeof(void*)), &needsVisit);
        
        ++ total;

        if (total == 3) {
          bitsetInit(bitset);
        }

        if (needsVisit) {
          ++ visits;

          if (visits == 1) {
            first = offset;
          }

          if (total >= 3) {
            bitsetClear(bitset, last, offset);
            last = offset;

            bitsetSet(bitset, offset, true);
          }          
        } else {
          cast<object>(copy, offset * sizeof(void*)) = childCopy;
        }

        return true;
      }

      Context* c;
      object copy;
      uintptr_t* bitset;
      unsigned first;
      unsigned last;
      unsigned visits;
      unsigned total;
    } walker(c, copy, bitset(original));

    c->client->walk(copy, &walker);

    if (walker.visits) {
      // descend
      if (walker.visits > 1) {
        ::parent(original) = parent;

        parent = original;
      }

      original = cast<object>(copy, walker.first * sizeof(void*));
      cast<object>(copy, walker.first * sizeof(void*)) = follow(original);
      goto visit;
    } else {
      // ascend
      original = parent;
    }
  }

  if (original) {
    object copy = follow(original);

    class Walker : public Heap::Walker {
     public:
      Walker(uintptr_t* bitset):
        bitset(bitset),
        next(0),
        total(0)
      { }

      virtual bool visit(unsigned offset) {
        switch (++ total) {
        case 1:
          return true;

        case 2:
          next = offset;
          return true;
          
        case 3:
          next = bitsetNext(bitset);
          return false;

        default:
          abort(c);
        }
      }

      uintptr_t* bitset;
      unsigned next;
      unsigned total;
    } walker(c, copy, bitset(original));

    assert(c, walker.total > 1);

    if (walker.total == 3 and bitsetHasMore(bitset(original))) {
      parent = original;
    } else {
      parent = ::parent(original);
    }

    original = cast<object>(copy, walker.next * sizeof(void*));
    cast<object>(copy, walker.next * sizeof(void*)) = follow(original);
  } else {
    return;
  }
}

void
collect2(Context* c)
{
  if (c->mode == MinorCollection and c->gen2.position()) {
    unsigned start = 0;
    unsigned end = start + c->gen2.position();
    bool dirty;
    collect(m, &(c->map), start, end, &dirty, false);
  } else if (c->mode == Gen2Collection) {
    unsigned ng2Position = c->nextGen2.position();
    collect(m, &(c->nextGen1), c->nextGen1.position());
    collect(m, &(c->nextGen2), ng2Position);
  }

  class Visitor : public Heap::Visitor {
   public:
    Visitor(Context* c): c(c) { }

    virtual void visit(void** p) {
      collect(c, p);
    }
  } v(c);

  c->iterator->iterate(&v);
}

void
collect1(Context* c)
{
  switch (c->mode) {
  case MinorCollection: {
    initNextGen1(c);

    collect2(c);

    if (c->mode == OverflowCollection) {
      c->mode = Gen2Collection;
      collect2(c);

      c->gen2.replaceWith(&(c->nextGen2));
    }

    c->gen1.replaceWith(&(c->nextGen1));
  } break;

  case MajorCollection: {
    initNextGen1(c);
    initNextGen2(c);
    
    c->map.clear();

    collect2(c);

    c->gen1.replaceWith(&(c->nextGen1));
    c->gen2.replaceWith(&(c->nextGen2));    
  } break;
  }
}

} // namespace

Heap*
makeHeap(System* system)
{
  

  HeapImp* h = static_cast<HeapImp*>(system->allocate(sizeof(HeapImp)));
  init(h, system);
  return h;
}
