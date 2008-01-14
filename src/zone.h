#ifndef ZONE_H
#define ZONE_H

#include "system.h"
#include "allocator.h"

namespace vm {

class Zone: public Allocator {
 public:
  class Segment {
   public:
    Segment(Segment* next, unsigned size): next(next), size(size) { }

    Segment* next;
    uintptr_t size;
    uint8_t data[0];
  };

  Zone(System* s, Allocator* allocator, bool executable,
       unsigned minimumFootprint):
    s(s),
    allocator(allocator),
    executable(executable),
    segment(0),
    position(0),
    minimumFootprint(minimumFootprint < sizeof(Segment) ? 0 :
                     minimumFootprint - sizeof(Segment))
  { }

  ~Zone() {
    dispose();
  }

  void dispose() {
    for (Segment* seg = segment, *next; seg; seg = next) {
      next = seg->next;
      allocator->free(seg, sizeof(Segment) + seg->size, executable);
    }
  }

  bool ensure(unsigned space, bool executable) {
    if (segment == 0 or position + space > segment->size) {
      unsigned size = max
        (space, max
         (minimumFootprint, segment == 0 ? 0 : segment->size * 2))
        + sizeof(Segment);

      // pad to page size
      size = (size + (LikelyPageSizeInBytes - 1))
        & ~(LikelyPageSizeInBytes - 1);

      void* p = allocator->tryAllocate(size, executable);
      if (p == 0) {
        size = space + sizeof(Segment);
        void* p = allocator->tryAllocate(size, executable);
        if (p == 0) {
          return false;
        }
      }

      segment = new (p) Segment(segment, size - sizeof(Segment));
      position = 0;
    }
    return true;
  }

  virtual void* tryAllocate(unsigned size, bool executable) {
    assert(s, executable == this->executable);

    size = pad(size);
    if (ensure(size, executable)) {
      void* r = segment->data + position;
      position += size;
      return r;
    } else {
      return 0;
    }
  }

  virtual void* allocate(unsigned size, bool executable) {
    assert(s, executable == this->executable);

    void* p = tryAllocate(size, executable);
    expect(s, p);
    return p;
  }

  virtual void free(const void*, unsigned, bool) {
    // not supported
    abort(s);
  }

  void* allocate(unsigned size) {
    return allocate(size, executable);
  }
  
  System* s;
  Allocator* allocator;
  void* context;
  bool executable;
  Segment* segment;
  unsigned position;
  unsigned minimumFootprint;
};

} // namespace vm

#endif//ZONE_H
